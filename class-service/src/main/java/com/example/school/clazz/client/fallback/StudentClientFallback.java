package com.example.school.clazz.client.fallback;

import com.example.school.clazz.client.StudentClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * student-service 不可用时的 Feign 降级实现。
 *
 * 面试可讲点：
 * - 远程调用失败不抛给上层，避免聚合接口整体失败。
 * - 返回语义化兜底数据，前端仍可感知“这是降级结果”。
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
