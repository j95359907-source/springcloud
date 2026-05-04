package com.example.school.clazz.client;

import com.example.school.clazz.client.fallback.StudentClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 调用 student-service 的 Feign 客户端
 * name 对应注册中心中的服务名，自动配合 LoadBalancer 做负载均衡。
 */
@FeignClient(name = "student-service", fallback = StudentClientFallback.class)
public interface StudentClient {

    @GetMapping("/student/{id}")
    Map<String, Object> getStudent(@PathVariable("id") Long id);
}
