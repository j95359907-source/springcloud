package com.example.school.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关路由配置
 */
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // student 服务路由：通过服务名走 lb://，由 LoadBalancer 选择实例
                .route("student_route", r -> r.path("/student/**")
                        .uri("lb://student-service"))
                // teacher 服务路由
                .route("teacher_route", r -> r.path("/teacher/**")
                        .uri("lb://teacher-service"))
                // class 服务路由，并加熔断降级
                .route("class_route", r -> r.path("/class/**")
                        .filters(f -> f.circuitBreaker(c -> c
                                .setName("classCircuit")
                                .setFallbackUri("forward:/fallback/class")))
                        .uri("lb://class-service"))
                .build();
    }
}
