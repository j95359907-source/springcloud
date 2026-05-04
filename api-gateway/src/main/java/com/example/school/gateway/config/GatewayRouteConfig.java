package com.example.school.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * 网关路由配置类。
 *
 * 作用：
 * 1. 定义“什么请求”转发到“什么服务”；
 * 2. 在路由级别定义重试、熔断等策略；
 * 3. 使用 lb://service-name 交给 Spring Cloud LoadBalancer 做服务发现和负载均衡。
 */
@Configuration
public class GatewayRouteConfig {

    /**
     * 注册路由表 Bean。
     * Spring Cloud Gateway 启动时会读取这个 Bean，构建内存路由表。
     */
    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        // routes()：开始构建多条路由规则。
        return builder.routes()
                // student 查询：匹配 GET /student/**，转发到 student-service。
                .route("student_query_route", r -> r
                        // Path 断言：只匹配 /student 开头路径。
                        .path("/student/**")
                        // Method 断言：只匹配 GET。
                        .and().method(HttpMethod.GET)
                        // filters：只对这条路由生效的过滤器。
                        .filters(f -> f
                                // GET 通常是幂等的，允许重试 2 次提高可用性。
                                .retry(c -> c.setRetries(2).setMethods(HttpMethod.GET)))
                        // lb:// 表示按服务名路由，不写死 IP。
                        .uri("lb://student-service"))

                // student 写操作：POST/PUT/DELETE /student/**。
                .route("student_write_route", r -> r
                        .path("/student/**")
                        .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                        // 写请求默认不重试，避免重复写入。
                        .uri("lb://student-service"))

                // teacher 查询。
                .route("teacher_query_route", r -> r
                        .path("/teacher/**")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f.retry(c -> c.setRetries(2).setMethods(HttpMethod.GET)))
                        .uri("lb://teacher-service"))

                // teacher 写操作。
                .route("teacher_write_route", r -> r
                        .path("/teacher/**")
                        .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                        .uri("lb://teacher-service"))

                // class 查询：加重试 + 熔断降级。
                .route("class_query_route", r -> r
                        .path("/class/**")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f
                                .retry(c -> c.setRetries(2).setMethods(HttpMethod.GET))
                                // 下游异常时走网关本地 fallback 端点。
                                .circuitBreaker(c -> c.setName("classCircuit").setFallbackUri("forward:/fallback/class")))
                        .uri("lb://class-service"))

                // class 写操作：加熔断，不加重试。
                .route("class_write_route", r -> r
                        .path("/class/**")
                        .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                        .filters(f -> f.circuitBreaker(c -> c.setName("classCircuit").setFallbackUri("forward:/fallback/class")))
                        .uri("lb://class-service"))
                // build()：结束路由构建。
                .build();
    }
}
