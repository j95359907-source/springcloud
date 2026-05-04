package com.example.school.clazz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 班级服务
 */
@SpringBootApplication
@EnableFeignClients // 开启 Feign：用于声明式调用其他微服务
public class ClassServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClassServiceApplication.class, args);
    }
}
