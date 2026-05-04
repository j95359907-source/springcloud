package com.example.school.clazz.controller;

import com.example.school.clazz.client.StudentClient;
import com.example.school.clazz.client.TeacherClient;
import com.example.school.clazz.model.SchoolClass;
import com.example.school.clazz.mq.ClassEventPublisher;
import com.example.school.clazz.repository.SchoolClassRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 班级聚合接口：聚合学生和老师信息
 */
@RestController
@RequestMapping("/class")
public class ClassController {
    private static final String CLASS_DETAIL_CACHE_KEY_PREFIX = "class:detail:";

    private final StudentClient studentClient;
    private final TeacherClient teacherClient;
    private final SchoolClassRepository repository;
    private final ClassEventPublisher classEventPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public ClassController(StudentClient studentClient,
                           TeacherClient teacherClient,
                           SchoolClassRepository repository,
                           ClassEventPublisher classEventPublisher,
                           StringRedisTemplate stringRedisTemplate,
                           ObjectMapper objectMapper) {
        this.studentClient = studentClient;
        this.teacherClient = teacherClient;
        this.repository = repository;
        this.classEventPublisher = classEventPublisher;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<SchoolClass> list() {
        return repository.findAll();
    }

    @GetMapping("/{classId}")
    public SchoolClass getById(@PathVariable("classId") Long classId) {
        return repository.findById(classId).orElse(null);
    }

    @PostMapping
    public SchoolClass create(@RequestBody SchoolClass schoolClass) {
        schoolClass.setId(null);
        SchoolClass created = repository.save(schoolClass);
        // 发送 RabbitMQ 事件：班级新增
        classEventPublisher.publish("CREATE", created.getId());
        return created;
    }

    @PutMapping("/{classId}")
    public SchoolClass update(@PathVariable("classId") Long classId, @RequestBody SchoolClass schoolClass) {
        schoolClass.setId(classId);
        SchoolClass updated = repository.save(schoolClass);
        // 更新后删除缓存，确保下次查询拿到最新数据
        evictClassDetailCache(classId);
        // 发送 RabbitMQ 事件：班级更新
        classEventPublisher.publish("UPDATE", classId);
        return updated;
    }

    @DeleteMapping("/{classId}")
    public Map<String, Object> delete(@PathVariable("classId") Long classId) {
        boolean exists = repository.existsById(classId);
        if (exists) {
            repository.deleteById(classId);
            evictClassDetailCache(classId);
            // 发送 RabbitMQ 事件：班级删除
            classEventPublisher.publish("DELETE", classId);
        }
        return Map.of("success", exists, "id", classId);
    }

    @GetMapping("/{classId}/detail")
    @CircuitBreaker(name = "classAggregate", fallbackMethod = "fallbackClassInfo")
    public Map<String, Object> getClassInfo(@PathVariable("classId") Long classId) {
        String cacheKey = CLASS_DETAIL_CACHE_KEY_PREFIX + classId;
        // 1) 先查 Redis 缓存，命中则直接返回，减少数据库和远程调用压力
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<Map<String, Object>>() { });
            } catch (Exception ignored) {
                // 缓存解析异常时降级为重新计算，不影响主流程
            }
        }

        SchoolClass clazz = repository.findById(classId).orElse(null);
        if (clazz == null) {
            return Map.of("success", false, "message", "班级不存在", "classId", classId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classId", classId);
        result.put("className", clazz.getName());

        // 关键点：Feign 根据服务名调用 + LoadBalancer 自动选择可用实例
        result.put("student", studentClient.getStudent(clazz.getStudentId()));
        result.put("teacher", teacherClient.getTeacher(clazz.getTeacherId()));

        // 2) 写入 Redis，后续查询直接命中
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result));
        } catch (Exception ignored) {
            // 缓存写失败不影响主流程
        }
        return result;
    }

    /**
     * 熔断降级方法：当 getClassInfo 失败时返回兜底数据
     */
    public Map<String, Object> fallbackClassInfo(Long classId, Throwable throwable) {
        return Map.of(
                "classId", classId,
                "className", "三年二班",
                "remark", "class-service 熔断降级",
                "reason", throwable.getClass().getSimpleName()
        );
    }

    private void evictClassDetailCache(Long classId) {
        stringRedisTemplate.delete(CLASS_DETAIL_CACHE_KEY_PREFIX + classId);
    }
}
