package com.example.school.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 网关降级返回控制器。
 *
 * 当路由上的熔断器打开或下游不可用时，
 * 会转发到这些 fallback 端点返回统一兜底结果。
 */
@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    /**
     * class-service 的兜底响应。
     */
    @GetMapping(value = "/class", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> classFallback() {
        return Map.of(
                "success", false,
                "code", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "message", "class-service is unavailable, fallback response from gateway"
        );
    }

    /**
     * student-service 的兜底响应。
     */
    @GetMapping(value = "/student", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> studentFallback() {
        return Map.of(
                "success", false,
                "code", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "message", "student-service is unavailable, fallback response from gateway"
        );
    }

    /**
     * teacher-service 的兜底响应。
     */
    @GetMapping(value = "/teacher", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> teacherFallback() {
        return Map.of(
                "success", false,
                "code", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "message", "teacher-service is unavailable, fallback response from gateway"
        );
    }
}
