package com.example.school.clazz.client.fallback;

import com.example.school.clazz.client.StudentClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * student-service 调用失败时的降级返回
 */
@Component
public class StudentClientFallback implements StudentClient {
    @Override
    public Map<String, Object> getStudent(Long id) {
        return Map.of(
                "id", id,
                "name", "默认学生",
                "age", -1,
                "remark", "student-service 熔断降级"
        );
    }
}
