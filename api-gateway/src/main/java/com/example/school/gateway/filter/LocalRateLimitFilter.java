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
 * 网关限流过滤器。
 *
 * 大项目里，网关经常会部署多个实例。
 * 如果只用 JVM 内存计数，不同网关实例之间的限流数据无法共享。
 *
 * 所以这里升级成：
 * 1. 优先使用 Redis 做分布式固定窗口限流；
 * 2. Redis 不可用时，回退到本地内存限流，保证网关仍可运行；
 * 3. 响应头返回限流信息，方便前端和测试排查。
 */
@Component
public class LocalRateLimitFilter implements GlobalFilter, Ordered {

    private final GatewayRateLimitProperties props;
    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 本地兜底限流桶。
     *
     * 只有 Redis 调用失败时才会使用。
     */
    private final Map<String, Deque<Long>> localRequestRecords = new ConcurrentHashMap<>();

    public LocalRateLimitFilter(GatewayRateLimitProperties props,
                                ReactiveStringRedisTemplate redisTemplate) {
        // 注入限流配置。
        this.props = props;
        // 注入响应式 Redis 客户端。
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取当前请求路径，用于判断是否限流、属于哪个业务分组。
        String path = exchange.getRequest().getURI().getPath();
        if (isBypassPath(path)) {
            return chain.filter(exchange);
        }

        // 根据路径解析业务分组，例如 student、teacher、class。
        String group = resolveGroup(path);
        // 根据业务分组读取对应限流阈值。
        int groupLimit = resolveGroupLimit(group);
        // 根据用户或 IP 生成限流主体。
        String subject = resolveSubject(exchange);
        // 最终限流桶 key，同一个主体访问同一组接口会进入同一个桶。
        String bucketKey = subject + ":" + group;

        // 先尝试 Redis 分布式限流。
        return passRedisRateLimit(exchange, bucketKey, group, groupLimit)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(ex -> {
                    // Redis 不可用时走本地兜底，避免 Redis 故障直接拖垮网关。
                    if (!passLocalRateLimit(exchange, bucketKey, group, groupLimit)) {
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }

    /**
     * Redis 分布式限流。
     *
     * 这里采用固定窗口算法：
     * 1. Redis key = rate-limit:{subject}:{group}:{windowStart}
     * 2. 每次请求 INCR 一次；
     * 3. 第一次创建 key 时设置过期时间；
     * 4. 超过阈值返回 429。
     */
    private Mono<Boolean> passRedisRateLimit(ServerWebExchange exchange,
                                             String bucketKey,
                                             String group,
                                             int groupLimit) {
        long now = Instant.now().getEpochSecond();
        long windowSeconds = props.getWindowSeconds();
        // 计算当前固定窗口的起始时间，例如 10 秒一个窗口。
        long windowStart = now / windowSeconds * windowSeconds;
        // Redis key 中带窗口起始时间，窗口结束后自然切换到新 key。
        String redisKey = "gateway:rate-limit:" + bucketKey + ":" + windowStart;

        // INCR 是 Redis 原子递增操作，适合多网关实例共享计数。
        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    // 第一次创建 key 时设置过期时间，避免 Redis 中残留大量旧窗口 key。
                    Mono<Boolean> expireMono = count == 1
                            ? redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds + 1))
                            : Mono.just(true);

                    return expireMono.map(ignored -> {
                        // 把当前限流状态写入响应头，方便前端或测试排查。
                        addRateLimitHeaders(exchange, bucketKey, group, groupLimit, Math.max(0, groupLimit - count));
                        if (count > groupLimit) {
                            rejectTooManyRequests(exchange, bucketKey, group, groupLimit);
                            return false;
                        }
                        return true;
                    });
                });
    }

    /**
     * 本地内存兜底限流。
     */
    private boolean passLocalRateLimit(ServerWebExchange exchange,
                                       String bucketKey,
                                       String group,
                                       int groupLimit) {
        long now = Instant.now().getEpochSecond();
        Deque<Long> queue = localRequestRecords.computeIfAbsent(bucketKey, key -> new ConcurrentLinkedDeque<>());

        synchronized (queue) {
            // 清理窗口外的旧请求时间戳。
            while (!queue.isEmpty() && now - queue.peekFirst() >= props.getWindowSeconds()) {
                queue.pollFirst();
            }
            // 本地桶达到阈值时返回 429。
            if (queue.size() >= groupLimit) {
                rejectTooManyRequests(exchange, bucketKey, group, groupLimit);
                return false;
            }
            // 记录本次请求时间。
            queue.addLast(now);
            addRateLimitHeaders(exchange, bucketKey, group, groupLimit, Math.max(0, groupLimit - queue.size()));
            return true;
        }
    }

    private void rejectTooManyRequests(ServerWebExchange exchange,
                                       String bucketKey,
                                       String group,
                                       int groupLimit) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        addRateLimitHeaders(exchange, bucketKey, group, groupLimit, 0);
        safeSetHeader(exchange, "Retry-After", String.valueOf(props.getWindowSeconds()));
    }

    private void addRateLimitHeaders(ServerWebExchange exchange,
                                     String bucketKey,
                                     String group,
                                     int groupLimit,
                                     long remaining) {
        safeSetHeader(exchange, "X-RateLimit-Key", bucketKey);
        safeSetHeader(exchange, "X-RateLimit-Group", group);
        safeSetHeader(exchange, "X-RateLimit-Limit", String.valueOf(groupLimit));
        safeSetHeader(exchange, "X-RateLimit-Remaining", String.valueOf(remaining));
        safeSetHeader(exchange, "X-RateLimit-Window", String.valueOf(props.getWindowSeconds()));
    }

    // 安全写响应头：响应头只读时不再抛异常，避免限流提示头影响业务请求。
    private void safeSetHeader(ServerWebExchange exchange, String name, String value) {
        try {
            if (!exchange.getResponse().isCommitted()) {
                exchange.getResponse().getHeaders().set(name, value);
            }
        } catch (UnsupportedOperationException ignored) {
            // Gateway 某些阶段会把 headers 包装成只读对象，提示头写失败不应该影响主流程。
        }
    }

    private String resolveSubject(ServerWebExchange exchange) {
        String user = exchange.getRequest().getHeaders().getFirst("X-Auth-User");
        if (user != null && !user.isBlank()) {
            return "user:" + user.trim();
        }
        return "ip:" + getClientIp(exchange);
    }

    private String resolveGroup(String path) {
        if (path.startsWith("/student")) return "student";
        if (path.startsWith("/teacher")) return "teacher";
        if (path.startsWith("/class")) return "class";
        return "public";
    }

    private int resolveGroupLimit(String group) {
        return switch (group) {
            case "student" -> props.getStudentMaxRequests();
            case "teacher" -> props.getTeacherMaxRequests();
            case "class" -> props.getClassMaxRequests();
            case "public" -> props.getPublicMaxRequests();
            default -> props.getMaxRequests();
        };
    }

    private boolean isBypassPath(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/login")
                || path.startsWith("/register")
                || path.startsWith("/auth/");
    }

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
        return -120;
    }
}
