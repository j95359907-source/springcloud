package com.example.school.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类。
 *
 * 该模块是系统统一入口，主要承担：
 * 1. 路由转发
 * 2. JWT 统一鉴权
 * 3. 限流、熔断、降级
 * 4. 访问日志与链路追踪
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 容器并加载网关相关 Bean。
        SpringApplication.run(GatewayApplication.class, args);
    }
}
