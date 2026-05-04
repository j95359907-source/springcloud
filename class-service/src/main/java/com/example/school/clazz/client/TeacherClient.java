package com.example.school.clazz.client;

import com.example.school.clazz.client.fallback.TeacherClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 调用 teacher-service 的 Feign 客户端
 */
@FeignClient(name = "teacher-service", fallback = TeacherClientFallback.class)
public interface TeacherClient {

    @GetMapping("/teacher/{id}")
    Map<String, Object> getTeacher(@PathVariable("id") Long id);
}
