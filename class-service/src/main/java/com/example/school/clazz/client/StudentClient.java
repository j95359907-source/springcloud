package com.example.school.clazz.client;

import com.example.school.clazz.client.fallback.StudentClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 学生服务 Feign 客户端（跨模块调用关键点）。
 *
 * 面试可讲点：
 * - name = "student-service" 对应注册中心里的服务名，不写死 IP。
 * - 底层通过 Spring Cloud LoadBalancer 从可用实例中选一个调用。
 * - fallback 指定降级实现，远程失败时返回兜底数据。
 */
@FeignClient(name = "student-service", fallback = StudentClientFallback.class)
public interface StudentClient {

    /**
     * 调 student-service 的 /student/{id} 接口。
     */
    @GetMapping("/student/{id}")
    Map<String, Object> getStudent(@PathVariable("id") Long id);

    /*
     * ====================== 面试题（Feign 跨服务调用） ======================
     * Q1：Feign 和 RestTemplate/WebClient 相比优势是什么？
     * A：声明式接口调用，代码更简洁，和 Spring MVC 注解风格一致。
     *
     * Q2：Feign 的服务发现与负载均衡如何生效？
     * A：name 对应服务名，调用时通过注册中心发现实例，再由 LoadBalancer 选择目标实例。
     *
     * Q3：为什么要配置 fallback？
     * A：远程失败时提供兜底结果，避免异常向上抛导致聚合接口整体失败。
     * ======================================================================
     */
}
