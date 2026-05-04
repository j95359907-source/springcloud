package com.example.school.config;

// 引入 Spring Boot 启动工具类。
import org.springframework.boot.SpringApplication;
// 引入 Spring Boot 主启动注解。
import org.springframework.boot.autoconfigure.SpringBootApplication;
// 引入 Config Server 注解，开启配置中心服务端能力。
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * 配置中心启动类（Spring Cloud Config Server）。
 *
 * 这个模块的职责是：
 * 1. 统一托管各个微服务的配置文件；
 * 2. 在服务启动时，把对应服务名的配置返回给客户端；
 * 3. 让配置从“分散在每个模块里”变成“集中管理、统一变更”；
 * 4. 让不同环境、不同服务的配置可以解耦维护。
 *
 * 为什么这个模块代码也不多：
 * 1. Config Server 本身就是成熟的基础设施组件；
 * 2. 配置读取、按服务名定位资源、对外暴露配置接口等核心逻辑都由框架提供；
 * 3. 我们主要负责依赖接入、启动注解、配置方式和少量管理扩展。
 *
 * 当前项目采用的是 native 模式：
 * 1. 配置文件存放在本地目录 config-repo；
 * 2. Config Server 启动后从这个目录读取 yml 文件；
 * 3. 各客户端通过 spring.config.import 从这里拉取远程配置。
 *
 * 如果你想专门看“配置拉取、native 模式、refresh 刷新机制”的教学代码，
 * 可以继续阅读：
 * com.example.school.config.learning.ConfigCenterConceptDemo
 */
@SpringBootApplication
@EnableConfigServer // 开启配置中心服务端能力。
public class ConfigServerApplication {

    public static void main(String[] args) {
        // 启动配置中心服务。
        SpringApplication.run(ConfigServerApplication.class, args);
    }

    /*
     * ==================== 面试题（Config 配置中心） ====================
     * Q1：为什么要有配置中心，而不是每个服务都自己维护 application.yml？
     * A：因为集中配置更方便统一管理、统一修改、环境隔离和降低重复配置成本。
     *
     * Q2：配置中心客户端是怎么找到自己配置文件的？
     * A：通常根据 spring.application.name，再结合 profile、label 等维度定位远程配置。
     *
     * Q3：Config Server 的 native 模式和 Git 模式有什么区别？
     * A：native 模式直接读本地目录，简单直观；Git 模式更适合版本管理、审计、协作和回滚。
     *
     * Q4：为什么配置改了，服务不一定立刻生效？
     * A：因为大部分服务启动时只拉取一次配置；若要运行期生效，需要 refresh 或总线广播机制。
     *
     * Q5：配置中心和注册中心有什么关系？
     * A：它们职责不同但经常一起使用：注册中心解决服务发现，配置中心解决配置集中化。
     *
     * Q6：配置中心挂了会怎样？
     * A：已启动服务通常还能继续运行，因为配置已加载到内存；但新启动服务可能拿不到远程配置。
     * ==================================================================
     */
}
