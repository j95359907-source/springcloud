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
 * 班级聚合控制器（面试高频）：
 * 技术点组合：
 * 1) JPA CRUD（SchoolClassRepository）
 * 2) Feign 远程调用（StudentClient / TeacherClient）
 * 3) Redis 缓存（StringRedisTemplate）
 * 4) RabbitMQ 事件发布（ClassEventPublisher）
 * 5) 熔断降级（Resilience4j @CircuitBreaker）
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
        // 发送 CREATE 事件，供审计/通知/异步任务消费。
        classEventPublisher.publish("CREATE", created.getId());
        return created;
    }

    @PutMapping("/{classId}")
    public SchoolClass update(@PathVariable("classId") Long classId, @RequestBody SchoolClass schoolClass) {
        schoolClass.setId(classId);
        SchoolClass updated = repository.save(schoolClass);
        // 修改后删除缓存，避免脏数据。
        evictClassDetailCache(classId);
        // 发送 UPDATE 事件。
        classEventPublisher.publish("UPDATE", classId);
        return updated;
    }

    @DeleteMapping("/{classId}")
    public Map<String, Object> delete(@PathVariable("classId") Long classId) {
        boolean exists = repository.existsById(classId);
        if (exists) {
            repository.deleteById(classId);
            evictClassDetailCache(classId);
            // 发送 DELETE 事件。
            classEventPublisher.publish("DELETE", classId);
        }
        return Map.of("success", exists, "id", classId);
    }

    /**
     * 班级聚合详情：
     * - 先查 Redis 缓存
     * - 缓存未命中再查 DB + Feign
     * - 写回缓存
     * - 配置熔断降级
     */
    @GetMapping("/{classId}/detail")
    @CircuitBreaker(name = "classAggregate", fallbackMethod = "fallbackClassInfo")
    public Map<String, Object> getClassInfo(@PathVariable("classId") Long classId) {
        String cacheKey = CLASS_DETAIL_CACHE_KEY_PREFIX + classId;

        // 1) 优先命中缓存，降低 DB 和远程调用压力。
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<Map<String, Object>>() { });
            } catch (Exception ignored) {
                // 缓存解析失败时回退到实时计算，不影响主流程。
            }
        }

        // 2) 查班级基础信息。
        SchoolClass clazz = repository.findById(classId).orElse(null);
        if (clazz == null) {
            return Map.of("success", false, "message", "班级不存在", "classId", classId);
        }

        // 3) 聚合远程数据：学生+老师。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classId", classId);
        result.put("className", clazz.getName());
        result.put("student", studentClient.getStudent(clazz.getStudentId()));
        result.put("teacher", teacherClient.getTeacher(clazz.getTeacherId()));

        // 4) 回写缓存。
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result));
        } catch (Exception ignored) {
            // 缓存写失败不影响结果返回。
        }
        return result;
    }

    /**
     * 熔断降级方法：当聚合流程失败时，返回兜底结构。
     */
    public Map<String, Object> fallbackClassInfo(Long classId, Throwable throwable) {
        return Map.of(
                "classId", classId,
                "className", "默认班级",
                "remark", "class-service 熔断降级",
                "reason", throwable.getClass().getSimpleName()
        );
    }

    private void evictClassDetailCache(Long classId) {
        stringRedisTemplate.delete(CLASS_DETAIL_CACHE_KEY_PREFIX + classId);
    }

    /*
     * =================== 面试题（聚合 + 缓存 + 熔断 + MQ） ===================
     * Q1：为什么 class 详情接口要先查 Redis，再查 DB/Feign？
     * A：热点数据先走缓存可显著降低数据库与远程调用压力，提高响应速度。
     *
     * Q2：为什么更新/删除后要删缓存，而不是直接更新缓存？
     * A：删除缓存（Cache Aside）实现简单且一致性更稳，避免复杂并发更新问题。
     *
     * Q3：@CircuitBreaker + fallbackMethod 的价值是什么？
     * A：下游异常时快速降级，保证主接口仍可返回可解释结果，避免雪崩。
     *
     * Q4：为什么增删改后发送 RabbitMQ 事件？
     * A：用异步事件解耦后续流程（审计、通知、统计），避免主链路阻塞。
     * ======================================================================
     */
}
