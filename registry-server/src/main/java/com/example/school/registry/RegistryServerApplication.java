package com.example.school.registry;

// 引入 Spring Boot 启动工具类。
import org.springframework.boot.SpringApplication;
// 引入 Spring Boot 主启动注解，表示这是一个可独立运行的应用。
import org.springframework.boot.autoconfigure.SpringBootApplication;
// 引入 Eureka Server 注解，开启注册中心能力。
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * 注册中心启动类（Eureka Server）。
 *
 * 这个模块代码看起来很少，但核心能力并不少。
 * 原因是 Eureka Server 的大部分逻辑都已经由 Spring Cloud Netflix 封装好了。
 *
 * 我们只需要：
 * 1. 引入 Eureka Server 依赖；
 * 2. 在启动类上加 @EnableEurekaServer；
 * 3. 配置端口、服务名和少量基础参数；
 * 就可以得到一个可运行的注册中心。
 *
 * 注册中心在微服务架构中的职责：
 * 1. 接收各个微服务的注册请求；
 * 2. 保存服务名和实例地址的映射关系；
 * 3. 接收客户端定时心跳，判断实例是否存活；
 * 4. 给调用方提供服务发现能力；
 * 5. 配合负载均衡组件按服务名调用实例。
 *
 * 如果你想专门看“注册表、心跳续约、实例剔除、自我保护”的教学代码，
 * 可以继续阅读：
 * com.example.school.registry.learning.EurekaCoreConceptDemo
 */
@SpringBootApplication
@EnableEurekaServer // 开启 Eureka Server，让当前应用具备注册中心能力。
public class RegistryServerApplication {

    public static void main(String[] args) {
        // 启动注册中心服务。
        SpringApplication.run(RegistryServerApplication.class, args);
    }

    /*
     * ====================== 面试题（Eureka 注册中心） ======================
     * Q1：注册中心在微服务架构里解决了什么问题？
     * A：解决“服务地址动态变化”的问题，让调用方按服务名调用，而不是写死 IP 和端口。
     *
     * Q2：为什么注册中心模块代码这么少？
     * A：因为 Eureka Server 是成熟框架能力，注册表维护、续约、剔除、控制台等逻辑都由框架提供。
     *
     * Q3：服务提供者注册到 Eureka 时会带上哪些信息？
     * A：通常会带服务名、实例 IP、端口、状态、元数据等信息。
     *
     * Q4：什么是心跳续约？
     * A：服务实例定时告诉注册中心“我还活着”，注册中心据此刷新最后心跳时间。
     *
     * Q5：什么是实例剔除？
     * A：如果某个实例长时间没发送心跳，注册中心会把它从注册表移除，避免流量继续打到坏节点。
     *
     * Q6：什么是自我保护机制？
     * A：当短时间内大量实例心跳丢失时，Eureka 可能判断是网络问题而不是实例全挂，
     *    所以会暂缓剔除，避免误删健康实例。
     *
     * Q7：如果注册中心挂了，系统是不是立刻完全不可用？
     * A：不是。短时间内客户端通常还有本地注册表缓存，老实例之间还能继续调用；
     *    但新实例注册、实例变更同步、长时间一致性都会受影响。
     * =====================================================================
     */
}
