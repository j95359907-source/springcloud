package com.example.school.clazz.client.fallback;

import com.example.school.clazz.client.TeacherClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * teacher-service 调用失败时的降级返回
 */
@Component
public class TeacherClientFallback implements TeacherClient {
    @Override
    public Map<String, Object> getTeacher(Long id) {
        return Map.of(
                "id", id,
                "name", "默认老师",
                "title", "未知",
                "remark", "teacher-service 熔断降级"
        );
    }
}
