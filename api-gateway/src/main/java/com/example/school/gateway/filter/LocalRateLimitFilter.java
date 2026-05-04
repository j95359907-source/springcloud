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

/**
 * 网关本地限流过滤器（简化滑动窗口实现）
 * 规则：同一个 IP 在 10 秒内最多请求 20 次。
 */
@Component
public class LocalRateLimitFilter implements GlobalFilter, Ordered {

    private static final long WINDOW_SECONDS = 10;
    private static final int MAX_REQUESTS = 20;

    // key=客户端IP，value=该IP最近请求时间戳队列
    private final Map<String, Deque<Long>> requestRecords = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = getClientIp(exchange);
        long now = Instant.now().getEpochSecond();

        Deque<Long> queue = requestRecords.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        synchronized (queue) {
            // 清理窗口外的旧请求
            while (!queue.isEmpty() && now - queue.peekFirst() >= WINDOW_SECONDS) {
                queue.pollFirst();
            }

            if (queue.size() >= MAX_REQUESTS) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
            queue.addLast(now);
        }

        return chain.filter(exchange);
    }

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
        return -100;
    }
}
