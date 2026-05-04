package com.example.school.clazz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 班级服务启动入口。
 *
 * 面试可讲点：
 * - @EnableFeignClients 开启 Feign 声明式远程调用能力。
 * - 班级服务通过 Feign 调 student/teacher 服务做聚合。
 */
@SpringBootApplication
@EnableFeignClients // 扫描并注册 @FeignClient 接口代理对象。
public class ClassServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClassServiceApplication.class, args);
    }
}
