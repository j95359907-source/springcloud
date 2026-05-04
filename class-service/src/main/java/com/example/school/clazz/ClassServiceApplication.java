package com.example.school.clazz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 班级服务启动入口。
 *
 * 这个模块是业务服务里最适合阅读微服务协作的模块，因为它同时用到了：
 * 1. Feign 跨服务调用；
 * 2. Resilience4j 熔断降级；
 * 3. RabbitMQ 事件消息；
 * 4. Redis 缓存；
 * 5. JPA 数据库访问。
 *
 * 教学演示类阅读入口：
 * 1. Feign：com.example.school.clazz.learning.FeignCallConceptDemo
 * 2. 熔断：com.example.school.clazz.learning.CircuitBreakerConceptDemo
 * 3. RabbitMQ：com.example.school.clazz.learning.RabbitMqConceptDemo
 */
@SpringBootApplication
@EnableFeignClients
public class ClassServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClassServiceApplication.class, args);
    }
}
