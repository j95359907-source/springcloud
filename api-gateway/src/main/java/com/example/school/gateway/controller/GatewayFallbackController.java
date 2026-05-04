package com.example.school.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关降级返回
 */
@RestController
public class GatewayFallbackController {

    @GetMapping("/fallback/class")
    public String classFallback() {
        return "class-service 当前不可用，已触发网关熔断降级";
    }
}
