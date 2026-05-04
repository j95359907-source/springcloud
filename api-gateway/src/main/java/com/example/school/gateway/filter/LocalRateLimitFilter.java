package com.example.school.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

// 网关本地限流过滤器：滑动窗口 + 用户/IP + 分组配额。
@Component
public class LocalRateLimitFilter implements GlobalFilter, Ordered {

    // 限流配置对象（来自 application.yml）。
    private final GatewayRateLimitProperties props;
    // 限流计数桶：key -> 时间戳队列。
    private final Map<String, Deque<Long>> requestRecords = new ConcurrentHashMap<>();

    public LocalRateLimitFilter(GatewayRateLimitProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 读取路径。
        String path = exchange.getRequest().getURI().getPath();
        // 白名单路径不做限流。
        if (isBypassPath(path)) {
            return chain.filter(exchange);
        }

        // 按路径解析接口组（student/teacher/class/public）。
        String group = resolveGroup(path);
        // 读取该组阈值。
        int groupLimit = resolveGroupLimit(group);
        // 解析主体（登录用户优先，其次 IP）。
        String subject = resolveSubject(exchange);
        // 组成限流桶 key。
        String bucketKey = subject + ":" + group;

        // 当前秒级时间戳。
        long now = Instant.now().getEpochSecond();
        // 获取或创建该桶队列。
        Deque<Long> queue = requestRecords.computeIfAbsent(bucketKey, k -> new ConcurrentLinkedDeque<>());

        // 对单桶加锁，保证并发安全。
        synchronized (queue) {
            // 清理窗口外旧请求。
            while (!queue.isEmpty() && now - queue.peekFirst() >= props.getWindowSeconds()) {
                queue.pollFirst();
            }

            // 若达到阈值，返回 429。
            if (queue.size() >= groupLimit) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().add("X-RateLimit-Key", bucketKey);
                exchange.getResponse().getHeaders().add("X-RateLimit-Group", group);
                exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(groupLimit));
                exchange.getResponse().getHeaders().add("X-RateLimit-Window", String.valueOf(props.getWindowSeconds()));
                exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(props.getWindowSeconds()));
                return exchange.getResponse().setComplete();
            }

            // 记录本次请求。
            queue.addLast(now);
            // 返回当前配额信息。
            exchange.getResponse().getHeaders().add("X-RateLimit-Key", bucketKey);
            exchange.getResponse().getHeaders().add("X-RateLimit-Group", group);
            exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(groupLimit));
            exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(Math.max(0, groupLimit - queue.size())));
        }

        // 未触发限流则放行。
        return chain.filter(exchange);
    }

    // 解析主体：先看用户头，再回退到 IP。
    private String resolveSubject(ServerWebExchange exchange) {
        String user = exchange.getRequest().getHeaders().getFirst("X-Auth-User");
        if (user != null && !user.isBlank()) {
            return "user:" + user.trim();
        }
        return "ip:" + getClientIp(exchange);
    }

    // 按路径分组。
    private String resolveGroup(String path) {
        if (path.startsWith("/student")) return "student";
        if (path.startsWith("/teacher")) return "teacher";
        if (path.startsWith("/class")) return "class";
        return "public";
    }

    // 读取对应分组阈值。
    private int resolveGroupLimit(String group) {
        return switch (group) {
            case "student" -> props.getStudentMaxRequests();
            case "teacher" -> props.getTeacherMaxRequests();
            case "class" -> props.getClassMaxRequests();
            case "public" -> props.getPublicMaxRequests();
            default -> props.getMaxRequests();
        };
    }

    // 限流豁免路径。
    private boolean isBypassPath(String path) {
        return path.startsWith("/actuator") || path.startsWith("/login") || path.startsWith("/register") || path.startsWith("/auth/");
    }

    // 获取客户端 IP。
    private String getClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() == null) {
            return "unknown";
        }
        return String.valueOf(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }

    @Override
    public int getOrder() {
        // 让限流最先执行（比鉴权和追踪都更早），优先保护网关与下游服务。
        // 当前链路顺序：LocalRateLimit(-120) -> JwtAuth(-100) -> GatewayTrace(-90)。
        return -120;
    }

    // ==================== 面试题（网关限流） ====================
    // Q1：这里实现的是哪种限流算法？
    // A：滑动时间窗口（Sliding Window）近似实现，按窗口内请求数限流。
    // Q2：为什么限流 key 要区分 user 和 ip？
    // A：登录后按用户更精准；未登录时回退到 IP，避免匿名流量失控。
    // Q3：为什么按 student/teacher/class 分组不同阈值？
    // A：不同业务接口负载特征不同，分组配额更贴近真实生产治理。
    // ============================================================
}
