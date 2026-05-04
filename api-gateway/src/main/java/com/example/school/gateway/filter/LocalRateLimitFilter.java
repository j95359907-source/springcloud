package com.example.school.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 网关限流过滤器（Redis 优先 + 本地兜底）。
 *
 * 设计目标：
 * 1. 正常情况下使用 Redis 做“多网关实例共享”的限流计数；
 * 2. Redis 不可用时，自动退化为单机内存限流，保证网关可用性；
 * 3. 通过响应头回传限流状态，便于前端和测试观察。
 */
@Component
public class LocalRateLimitFilter implements GlobalFilter, Ordered {

    // 限流参数配置对象，对应 gateway.rate-limit.*。
    private final GatewayRateLimitProperties props;

    // Reactive Redis 客户端，用于分布式计数。
    private final ReactiveStringRedisTemplate redisTemplate;

    // 本地兜底桶：key -> 时间戳队列（秒）。
    private final Map<String, Deque<Long>> localRequestRecords = new ConcurrentHashMap<>();

    public LocalRateLimitFilter(GatewayRateLimitProperties props, ReactiveStringRedisTemplate redisTemplate) {
        this.props = props;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 当前请求路径。
        String path = exchange.getRequest().getURI().getPath();

        // 放行路径（如登录、注册、actuator）不做限流。
        if (isBypassPath(path)) {
            return chain.filter(exchange);
        }

        // 根据路径归类业务分组：student / teacher / class / public。
        String group = resolveGroup(path);

        // 读取该组的限流阈值。
        int groupLimit = resolveGroupLimit(group);

        // 限流主体：优先 user，其次客户端 IP。
        String subject = resolveSubject(exchange);

        // 同一主体 + 同一分组，共用一个桶。
        String bucketKey = subject + ":" + group;

        // 先走 Redis 分布式限流。
        return passRedisRateLimit(exchange, bucketKey, group, groupLimit)
                .flatMap(allowed -> {
                    // 超限直接结束响应。
                    if (!allowed) {
                        return exchange.getResponse().setComplete();
                    }
                    // 未超限继续下游。
                    return chain.filter(exchange);
                })
                .onErrorResume(ex -> {
                    // Redis 出错时切到本地限流，避免 Redis 故障拖垮网关。
                    if (!passLocalRateLimit(exchange, bucketKey, group, groupLimit)) {
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }

    /**
     * Redis 固定窗口限流。
     *
     * 算法：
     * 1. key = gateway:rate-limit:{bucketKey}:{windowStart}
     * 2. 每次请求对 key 做 INCR
     * 3. 第一次创建 key 时设置 TTL
     * 4. count > limit 时返回 429
     */
    private Mono<Boolean> passRedisRateLimit(ServerWebExchange exchange, String bucketKey, String group, int groupLimit) {
        long now = Instant.now().getEpochSecond();
        long windowSeconds = props.getWindowSeconds();

        // 计算当前窗口起点，例如 10 秒窗口会落在 0,10,20... 边界。
        long windowStart = now / windowSeconds * windowSeconds;

        // 构造 Redis 计数 key。
        String redisKey = "gateway:rate-limit:" + bucketKey + ":" + windowStart;

        // INCR 是原子操作，适合并发计数。
        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    // 第一次计数时设置过期，避免 key 无限增长。
                    Mono<Boolean> expireMono = count == 1
                            ? redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds + 1))
                            : Mono.just(true);

                    return expireMono.map(ignored -> {
                        // 回写限流响应头。
                        addRateLimitHeaders(exchange, bucketKey, group, groupLimit, Math.max(0, groupLimit - count));

                        // 超限时写 429。
                        if (count > groupLimit) {
                            rejectTooManyRequests(exchange, bucketKey, group, groupLimit);
                            return false;
                        }

                        return true;
                    });
                });
    }

    /**
     * 本地内存限流兜底。
     */
    private boolean passLocalRateLimit(ServerWebExchange exchange, String bucketKey, String group, int groupLimit) {
        long now = Instant.now().getEpochSecond();

        // 取当前桶，没有就创建。
        Deque<Long> queue = localRequestRecords.computeIfAbsent(bucketKey, key -> new ConcurrentLinkedDeque<>());

        // 同一桶加锁，保证并发安全。
        synchronized (queue) {
            // 清理窗口外旧时间戳。
            while (!queue.isEmpty() && now - queue.peekFirst() >= props.getWindowSeconds()) {
                queue.pollFirst();
            }

            // 达到阈值，返回 429。
            if (queue.size() >= groupLimit) {
                rejectTooManyRequests(exchange, bucketKey, group, groupLimit);
                return false;
            }

            // 记录本次请求。
            queue.addLast(now);

            // 回写限流响应头。
            addRateLimitHeaders(exchange, bucketKey, group, groupLimit, Math.max(0, groupLimit - queue.size()));
            return true;
        }
    }

    // 统一处理“超限”响应。
    private void rejectTooManyRequests(ServerWebExchange exchange, String bucketKey, String group, int groupLimit) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        addRateLimitHeaders(exchange, bucketKey, group, groupLimit, 0);
        safeSetHeader(exchange, "Retry-After", String.valueOf(props.getWindowSeconds()));
    }

    // 回写限流信息头。
    private void addRateLimitHeaders(ServerWebExchange exchange, String bucketKey, String group, int groupLimit, long remaining) {
        safeSetHeader(exchange, "X-RateLimit-Key", bucketKey);
        safeSetHeader(exchange, "X-RateLimit-Group", group);
        safeSetHeader(exchange, "X-RateLimit-Limit", String.valueOf(groupLimit));
        safeSetHeader(exchange, "X-RateLimit-Remaining", String.valueOf(remaining));
        safeSetHeader(exchange, "X-RateLimit-Window", String.valueOf(props.getWindowSeconds()));
    }

    // 安全写响应头，防止只读 headers 引发异常影响主链路。
    private void safeSetHeader(ServerWebExchange exchange, String name, String value) {
        try {
            if (!exchange.getResponse().isCommitted()) {
                exchange.getResponse().getHeaders().set(name, value);
            }
        } catch (UnsupportedOperationException ignored) {
        }
    }

    // 限流主体优先用用户身份，其次用 IP。
    private String resolveSubject(ServerWebExchange exchange) {
        String user = exchange.getRequest().getHeaders().getFirst("X-Auth-User");
        if (user != null && !user.isBlank()) {
            return "user:" + user.trim();
        }
        return "ip:" + getClientIp(exchange);
    }

    // 路径分组规则。
    private String resolveGroup(String path) {
        if (path.startsWith("/student")) return "student";
        if (path.startsWith("/teacher")) return "teacher";
        if (path.startsWith("/class")) return "class";
        return "public";
    }

    // 读取分组阈值。
    private int resolveGroupLimit(String group) {
        return switch (group) {
            case "student" -> props.getStudentMaxRequests();
            case "teacher" -> props.getTeacherMaxRequests();
            case "class" -> props.getClassMaxRequests();
            case "public" -> props.getPublicMaxRequests();
            default -> props.getMaxRequests();
        };
    }

    // 放行路径判断。
    private boolean isBypassPath(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/login")
                || path.startsWith("/register")
                || path.startsWith("/auth/");
    }

    // 获取客户端 IP（优先 X-Forwarded-For）。
    private String getClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() == null
                || exchange.getRequest().getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        // 在 JWT(-100) 之前执行：先抗流量，再做鉴权，保护网关资源。
        return -120;
    }
}
