package com.example.school.config.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置中心管理接口（教学用）
 * 提供：配置文件列表、读取内容、保存内容。
 */
@RestController
@RequestMapping("/admin/config")
public class ConfigAdminController {

    // 与 application.yml 的 search-locations 保持一致：兼容两种启动目录
    private final Path repoDir = resolveRepoDir();
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/files")
    public List<String> files() throws IOException {
        if (!Files.exists(repoDir)) {
            return List.of();
        }
        try (var stream = Files.list(repoDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    @GetMapping("/files/{fileName}")
    public ResponseEntity<?> read(@PathVariable("fileName") String fileName) throws IOException {
        Path path = safePath(fileName);
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return ResponseEntity.ok(Map.of("fileName", fileName, "content", content));
    }

    @PutMapping("/files/{fileName}")
    public Map<String, Object> save(@PathVariable("fileName") String fileName, @RequestBody Map<String, String> payload) throws IOException {
        Path path = safePath(fileName);
        Files.createDirectories(repoDir);
        String content = payload.getOrDefault("content", "");
        Files.writeString(path, content, StandardCharsets.UTF_8);

        // 保存后自动触发对应服务刷新配置（调用 /actuator/refresh）
        Map<String, Object> refreshResult = triggerRefresh(fileName);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("fileName", fileName);
        result.put("path", path.toString());
        result.put("refresh", refreshResult);
        return result;
    }

    private Path safePath(String fileName) {
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("非法文件名");
        }
        if (!fileName.endsWith(".yml")) {
            throw new IllegalArgumentException("仅允许 yml 文件");
        }
        return repoDir.resolve(fileName);
    }

    private Path resolveRepoDir() {
        Path candidate1 = Paths.get("../config-repo").toAbsolutePath().normalize();
        if (Files.exists(candidate1)) {
            return candidate1;
        }
        Path candidate2 = Paths.get("./config-repo").toAbsolutePath().normalize();
        if (Files.exists(candidate2)) {
            return candidate2;
        }
        // 都不存在时默认返回 ../config-repo，后续保存会自动创建
        return candidate1;
    }

    private Map<String, Object> triggerRefresh(String fileName) {
        String url = resolveRefreshUrl(fileName);
        if (url == null) {
            return Map.of("triggered", false, "message", "当前文件未配置自动刷新目标");
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
            return Map.of(
                    "triggered", true,
                    "url", url,
                    "statusCode", response.getStatusCode().value()
            );
        } catch (Exception e) {
            return Map.of(
                    "triggered", false,
                    "url", url,
                    "message", e.getMessage()
            );
        }
    }

    private String resolveRefreshUrl(String fileName) {
        return switch (fileName) {
            case "student-service.yml" -> "http://localhost:9011/actuator/refresh";
            case "teacher-service.yml" -> "http://localhost:9012/actuator/refresh";
            case "class-service.yml" -> "http://localhost:9013/actuator/refresh";
            case "api-gateway.yml" -> "http://localhost:9000/actuator/refresh";
            default -> null;
        };
    }
}
