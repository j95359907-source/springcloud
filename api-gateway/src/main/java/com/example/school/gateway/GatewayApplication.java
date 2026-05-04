package com.example.school.gateway;

// 引入 Spring Boot 启动工具类。
import org.springframework.boot.SpringApplication;
// 引入 Spring Boot 主启动注解。
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类。
 *
 * 这个模块是整个项目的统一入口，主要负责：
 * 1. 路由转发；
 * 2. JWT 统一鉴权；
 * 3. 全局限流；
 * 4. 熔断降级；
 * 5. 访问日志与链路追踪。
 *
 * 如果你想专门看“过滤器链、路由匹配、JWT 鉴权、限流、熔断”的教学代码，
 * 可以继续阅读：
 * com.example.school.gateway.learning.GatewayConceptDemo
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        // 启动 Spring 容器，并初始化网关相关 Bean（路由、过滤器、鉴权等）。
        SpringApplication.run(GatewayApplication.class, args);
    }
}
