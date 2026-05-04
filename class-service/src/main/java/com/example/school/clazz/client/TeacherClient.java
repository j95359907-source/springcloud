package com.example.school.clazz.client;

import com.example.school.clazz.client.fallback.TeacherClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 老师服务 Feign 客户端（跨模块调用关键点）。
 */
@FeignClient(name = "teacher-service", fallback = TeacherClientFallback.class)
public interface TeacherClient {

    /**
     * 调 teacher-service 的 /teacher/{id} 接口。
     */
    @GetMapping("/teacher/{id}")
    Map<String, Object> getTeacher(@PathVariable("id") Long id);
}
