package com.example.school.config.controller;

// 引入 HTTP 实体类型，便于构造远程刷新请求。
import org.springframework.http.HttpEntity;
// 引入 HTTP 方法枚举。
import org.springframework.http.HttpMethod;
// 引入响应实体类型。
import org.springframework.http.ResponseEntity;
// 引入 GET 映射注解。
import org.springframework.web.bind.annotation.GetMapping;
// 引入路径变量注解。
import org.springframework.web.bind.annotation.PathVariable;
// 引入 PUT 映射注解。
import org.springframework.web.bind.annotation.PutMapping;
// 引入请求体注解。
import org.springframework.web.bind.annotation.RequestBody;
// 引入统一请求路径注解。
import org.springframework.web.bind.annotation.RequestMapping;
// 引入 REST 控制器注解。
import org.springframework.web.bind.annotation.RestController;
// 引入 RestTemplate，用于调用各服务的 refresh 接口。
import org.springframework.web.client.RestTemplate;

// 引入 IO 异常类型。
import java.io.IOException;
// 引入 UTF-8 字符集。
import java.nio.charset.StandardCharsets;
// 引入文件操作工具类。
import java.nio.file.Files;
// 引入文件路径类型。
import java.nio.file.Path;
// 引入路径工具类。
import java.nio.file.Paths;
// 引入 HashMap。
import java.util.HashMap;
// 引入列表类型。
import java.util.List;
// 引入 Map 类型。
import java.util.Map;
// 引入流式收集器。
import java.util.stream.Collectors;

/**
 * 配置中心管理接口（教学增强版）。
 *
 * 这个控制器不是 Spring Cloud Config Server 的必需部分，
 * 而是本项目额外加的一层“教学友好工具”：
 * 1. 可以列出 config-repo 里的配置文件；
 * 2. 可以读取某个配置文件内容；
 * 3. 可以修改配置文件并保存；
 * 4. 保存后自动尝试调用对应服务的 /actuator/refresh。
 *
 * 这样做的价值是：
 * 1. 方便你从页面直接观察“配置中心仓库”和“服务刷新”之间的关系；
 * 2. 面试时也能说明：配置中心不仅能托管配置，还能配合刷新机制让变更生效。
 */
@RestController
@RequestMapping("/admin/config")
public class ConfigAdminController {

    // 配置仓库目录；解析逻辑和 application.yml 的 search-locations 保持一致。
    private final Path repoDir = resolveRepoDir();
    // HTTP 调用工具，用于保存配置后通知对应服务刷新。
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/files")
    public List<String> files() throws IOException {
        // 如果配置仓库目录不存在，则返回空列表。
        if (!Files.exists(repoDir)) {
            return List.of();
        }
        // 列出目录中全部 yml 文件，按名称排序返回。
        try (var stream = Files.list(repoDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    @GetMapping("/files/{fileName}")
    public ResponseEntity<?> read(@PathVariable("fileName") String fileName) throws IOException {
        // 先校验并生成安全路径，防止目录穿越。
        Path path = safePath(fileName);
        // 文件不存在则返回 404。
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        // 按 UTF-8 读取文件内容。
        String content = Files.readString(path, StandardCharsets.UTF_8);
        // 返回文件名和内容，供前端展示。
        return ResponseEntity.ok(Map.of("fileName", fileName, "content", content));
    }

    @PutMapping("/files/{fileName}")
    public Map<String, Object> save(@PathVariable("fileName") String fileName,
                                    @RequestBody Map<String, String> payload) throws IOException {
        // 先校验文件名并生成目标路径。
        Path path = safePath(fileName);
        // 确保配置仓库目录存在。
        Files.createDirectories(repoDir);
        // 从请求体中读取新内容，不传则按空字符串处理。
        String content = payload.getOrDefault("content", "");
        // 以 UTF-8 方式写回配置文件。
        Files.writeString(path, content, StandardCharsets.UTF_8);

        // 保存成功后，自动尝试刷新对应微服务配置。
        Map<String, Object> refreshResult = triggerRefresh(fileName);

        // 组装返回结果给前端。
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("fileName", fileName);
        result.put("path", path.toString());
        result.put("refresh", refreshResult);
        return result;
    }

    private Path safePath(String fileName) {
        // 禁止出现目录穿越或路径分隔符，避免读写任意文件。
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("非法文件名");
        }
        // 只允许编辑 yml 文件，避免误修改其他内容。
        if (!fileName.endsWith(".yml")) {
            throw new IllegalArgumentException("仅允许编辑 yml 文件");
        }
        // 生成配置仓库中的目标文件路径。
        return repoDir.resolve(fileName);
    }

    private Path resolveRepoDir() {
        // 候选目录 1：从模块目录启动时，通常上一级就是项目根目录。
        Path candidate1 = Paths.get("../config-repo").toAbsolutePath().normalize();
        if (Files.exists(candidate1)) {
            return candidate1;
        }
        // 候选目录 2：从项目根目录启动时，当前目录下可能直接就有 config-repo。
        Path candidate2 = Paths.get("./config-repo").toAbsolutePath().normalize();
        if (Files.exists(candidate2)) {
            return candidate2;
        }
        // 若两个目录当前都不存在，则默认返回 candidate1，后续保存时会自动创建。
        return candidate1;
    }

    private Map<String, Object> triggerRefresh(String fileName) {
        // 根据文件名找到对应服务的 refresh 地址。
        String url = resolveRefreshUrl(fileName);
        // 如果没有对应服务，就只保存文件，不触发刷新。
        if (url == null) {
            return Map.of("triggered", false, "message", "当前文件未配置自动刷新目标");
        }
        try {
            // 调用目标服务的 /actuator/refresh，让远程配置重新加载。
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
            return Map.of(
                    "triggered", true,
                    "url", url,
                    "statusCode", response.getStatusCode().value()
            );
        } catch (Exception exception) {
            // 刷新失败不影响文件保存，但会把失败原因返回给前端。
            return Map.of(
                    "triggered", false,
                    "url", url,
                    "message", exception.getMessage()
            );
        }
    }

    private String resolveRefreshUrl(String fileName) {
        // 按配置文件名映射到对应微服务的 refresh 地址。
        return switch (fileName) {
            case "student-service.yml" -> "http://localhost:9011/actuator/refresh";
            case "teacher-service.yml" -> "http://localhost:9012/actuator/refresh";
            case "class-service.yml" -> "http://localhost:9019/actuator/refresh";
            case "api-gateway.yml" -> "http://localhost:9000/actuator/refresh";
            default -> null;
        };
    }

    /*
     * ================== 面试题（配置管理扩展） ==================
     * Q1：Config Server 原生就带“在线编辑配置页面”吗？
     * A：不是。Spring Cloud Config 主要提供配置读取与下发能力，这个控制器是项目额外扩展。
     *
     * Q2：为什么保存配置后还要调 /actuator/refresh？
     * A：因为文件改了不等于客户端自动重新读取，refresh 才会触发运行中服务重新加载配置。
     *
     * Q3：为什么这里要做 safePath 校验？
     * A：因为这是文件读写接口，若不限制文件名，可能被恶意构造路径读取或覆盖任意文件。
     *
     * Q4：生产环境是否推荐直接暴露这种“在线改文件”接口？
     * A：通常不推荐直接裸暴露，生产更常见的是 Git 仓库 + 审批流程 + 审计 + 权限控制。
     * ==========================================================
     */
}
