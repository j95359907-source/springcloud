package com.example.school.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * 网关路由配置。
 *
 * Spring Cloud Gateway 的核心职责之一就是“路由”：
 * 1. 根据请求路径和请求方法判断应该进入哪个服务；
 * 2. 通过 lb://服务名 交给 Spring Cloud LoadBalancer 做负载均衡；
 * 3. 对不同路由添加重试、熔断、降级等策略。
 */
@Configuration
public class GatewayRouteConfig {

    /**
     * 注册路由表 Bean，网关会按照这里定义的规则转发请求。
     */
    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // 学生查询路由：GET /student/** -> student-service。
                .route("student_query_route", r -> r
                        .path("/student/**")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f
                                // GET 通常是幂等的，可以安全重试，提升临时故障下的可用性。
                                .retry(c -> c.setRetries(2).setMethods(HttpMethod.GET)))
                        // lb:// 表示根据服务名从注册中心拿实例，再做负载均衡。
                        .uri("lb://student-service"))

                // 学生写路由：POST/PUT/DELETE /student/** -> student-service。
                .route("student_write_route", r -> r
                        .path("/student/**")
                        .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                        // 写请求默认不重试，避免新增、修改、删除被重复执行。
                        .uri("lb://student-service"))

                // 老师查询路由：GET /teacher/** -> teacher-service。
                .route("teacher_query_route", r -> r
                        .path("/teacher/**")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f.retry(c -> c.setRetries(2).setMethods(HttpMethod.GET)))
                        .uri("lb://teacher-service"))

                // 老师写路由：POST/PUT/DELETE /teacher/** -> teacher-service。
                .route("teacher_write_route", r -> r
                        .path("/teacher/**")
                        .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                        .uri("lb://teacher-service"))

                // 班级查询路由：GET /class/** -> class-service，带重试 + 熔断降级。
                .route("class_query_route", r -> r
                        .path("/class/**")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f
                                .retry(c -> c.setRetries(2).setMethods(HttpMethod.GET))
                                // 下游故障时转发到网关本地 fallback 接口，避免页面直接看到连接失败。
                                .circuitBreaker(c -> c.setName("classCircuit").setFallbackUri("forward:/fallback/class")))
                        .uri("lb://class-service"))

                // 班级写路由：POST/PUT/DELETE /class/** -> class-service，保留熔断保护。
                .route("class_write_route", r -> r
                        .path("/class/**")
                        .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                        .filters(f -> f.circuitBreaker(c -> c.setName("classCircuit").setFallbackUri("forward:/fallback/class")))
                        .uri("lb://class-service"))
                .build();
    }

    /*
     * ==================== 面试题（Gateway 路由 + 负载均衡）====================
     * Q1：为什么 URI 使用 lb://student-service，而不是 http://localhost:9011？
     * A：lb:// 会按服务名从注册中心发现实例，并交给 Spring Cloud LoadBalancer 选择具体实例；写死 IP 不适合多实例部署。
     *
     * Q2：为什么 GET 可以重试，而 POST/PUT/DELETE 默认不重试？
     * A：GET 通常幂等，重试风险较低；写请求可能重复新增或重复扣减，必须结合幂等键才能安全重试。
     *
     * Q3：熔断和降级的区别是什么？
     * A：熔断是发现下游持续失败后暂时不再调用；降级是调用失败或熔断打开后返回兜底结果。
     *
     * Q4：Gateway 过滤器顺序怎么设计？
     * A：一般先做来源保护和限流，再做鉴权，再做链路追踪和安全响应头，最后路由到下游服务。
     * ================================================================
     */
}
