package com.example.school.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

// 声明这是配置类，Spring 启动时会加载其中的 @Bean。
@Configuration
public class GatewayRouteConfig {
    // ================== 网关过滤器执行顺序图（文字版） ==================
    // 入口请求
    //   -> GlobalFilter(LocalRateLimitFilter, order=-120)：先限流，超额直接 429，减少无效下游开销
    //   -> GlobalFilter(JwtAuthGlobalFilter, order=-100)：再鉴权，未登录/无权限直接 401/403
    //   -> GlobalFilter(GatewayTraceFilter, order=-90)：记录链路与耗时（请求结束时打印日志）
    //   -> Route Predicate：匹配 path + method 命中具体路由
    //   -> Route Filters：执行当前路由的 Retry / CircuitBreaker / AddResponseHeader 等
    //   -> lb://service-id：交给 Spring Cloud LoadBalancer 选择实例并转发
    //   -> 下游服务处理并返回
    //   -> 响应回传网关（触发响应头追加、去重等默认过滤器）
    //   -> 返回前端
    //
    // 面试回答技巧：
    // 1) 为什么限流在鉴权之前？
    //    因为限流是“流量闸门”，优先挡住洪峰，保护网关与鉴权模块 CPU。
    // 2) 为什么追踪放在鉴权后？
    //    这样日志里能保留“被拒绝请求”和“已放行请求”的统一耗时视角。
    // 3) 为什么路由过滤器在 GlobalFilter 后？
    //    GlobalFilter 作用于全局；Route Filter 只对命中的具体路由生效。
    // ==============================================================

    // 注册路由表 Bean，网关会按这里定义的规则转发请求。
    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        // 开始构建路由集合。
        return builder.routes()
                // 学生查询路由：仅匹配 GET /student/**。
                .route("student_query_route", r -> r
                        .path("/student/**")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f
                                // GET 可安全重试，提高可用性。
                                .retry(c -> c.setRetries(2).setMethods(HttpMethod.GET))
                                // 回写路由标识头，便于排查请求命中哪条路由。
                                .addResponseHeader("X-Gateway-Route", "student-query"))
                        // 使用服务名走负载均衡，不写死 IP。
                        .uri("lb://student-service"))

                // 学生写路由：匹配 POST/PUT/DELETE /student/**。
                .route("student_write_route", r -> r
                        .path("/student/**")
                        .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                        .filters(f -> f
                                // 写请求不重试，避免重复写入风险。
                                .addResponseHeader("X-Gateway-Route", "student-write"))
                        .uri("lb://student-service"))

                // 老师查询路由。
                .route("teacher_query_route", r -> r
                        .path("/teacher/**")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f
                                .retry(c -> c.setRetries(2).setMethods(HttpMethod.GET))
                                .addResponseHeader("X-Gateway-Route", "teacher-query"))
                        .uri("lb://teacher-service"))

                // 老师写路由。
                .route("teacher_write_route", r -> r
                        .path("/teacher/**")
                        .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "teacher-write"))
                        .uri("lb://teacher-service"))

                // 班级查询路由：带重试 + 熔断降级。
                .route("class_query_route", r -> r
                        .path("/class/**")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f
                                .retry(c -> c.setRetries(2).setMethods(HttpMethod.GET))
                                // 下游故障时转发到网关本地 fallback 接口。
                                .circuitBreaker(c -> c.setName("classCircuit").setFallbackUri("forward:/fallback/class"))
                                .addResponseHeader("X-Gateway-Route", "class-query"))
                        .uri("lb://class-service"))

                // 班级写路由：保留熔断，避免下游雪崩。
                .route("class_write_route", r -> r
                        .path("/class/**")
                        .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                        .filters(f -> f
                                .circuitBreaker(c -> c.setName("classCircuit").setFallbackUri("forward:/fallback/class"))
                                .addResponseHeader("X-Gateway-Route", "class-write"))
                        .uri("lb://class-service"))
                // 完成路由构建。
                .build();
    }

    // ================== 高阶延伸面试题（不依赖本项目实现） ==================
    // 1) Gateway 在多实例部署时，如何做全局一致限流？内存限流与 Redis 限流的误差模型如何评估？
    // 2) 为什么写请求默认不应重试？若业务要求 POST 可重试，你如何定义幂等键与幂等窗口？
    // 3) 熔断、重试、超时三个策略顺序如何设计？错误顺序会造成什么放大效应？
    // 4) 如果网关同时承担鉴权、限流、灰度、降级，如何设计过滤器顺序避免互相干扰？
    // 5) 如何实现按租户/用户/接口/版本四维路由与配额，并支持热更新与快速回滚？
    // =====================================================================
}
