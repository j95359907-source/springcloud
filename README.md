# Spring Cloud School Demo（微服务实战项目）

这是一个基于 Spring Cloud 的教学型微服务项目，围绕“学生、老师、班级”三类核心业务，演示了从网关到服务治理、从配置中心到熔断降级、从鉴权到限流的一整套实践。

## 1. 项目模块

- `registry-server`：注册中心（Eureka）
- `config-server`：配置中心服务端（Spring Cloud Config Server）
- `config-repo`：配置中心仓库（YAML 配置文件）
- `api-gateway`：统一网关（路由、鉴权、限流、熔断、重试）
- `student-service`：学生服务（CRUD）
- `teacher-service`：老师服务（CRUD）
- `class-service`：班级服务（CRUD + 聚合查询 + Feign + MQ）

## 2. 微服务技术清单

### 2.1 服务注册与发现（Eureka）

- 使用技术：`Spring Cloud Netflix Eureka`
- 作用：各微服务启动后注册到注册中心，调用方不需要写死 IP 和端口，而是通过服务名发现实例。
- 本项目落地：
- `registry-server` 作为 Eureka Server
- 各服务通过 `eureka.client.service-url.defaultZone` 指向 `http://localhost:8761/eureka/`
- 网关和服务间调用使用 `lb://service-name`

### 2.2 配置中心（Spring Cloud Config）

- 使用技术：`Spring Cloud Config Server`
- 作用：集中管理端口、数据库、日志、熔断、限流等配置。
- 本项目落地：
- `config-server` 负责下发配置
- `config-repo` 保存配置文件
- 各服务通过 `spring.config.import` 从配置中心拉取远程配置
- 当前采用 `native` 模式读取本地 `config-repo`
- 额外提供了一个教学用配置管理页面和编辑接口

### 2.3 API 网关（Spring Cloud Gateway）

- 使用技术：`Spring Cloud Gateway`
- 作用：作为统一入口，集中处理路由、鉴权、限流、熔断、重试和跨域。
- 本项目落地：
- 路由配置：`api-gateway/.../config/GatewayRouteConfig.java`
- JWT 鉴权：`api-gateway/.../security/JwtAuthGlobalFilter.java`
- 本地限流：`api-gateway/.../filter/LocalRateLimitFilter.java`
- 链路追踪日志：`api-gateway/.../filter/GatewayTraceFilter.java`
- 熔断回退：`api-gateway/.../controller/GatewayFallbackController.java`

### 2.4 负载均衡（Spring Cloud LoadBalancer）

- 使用技术：`Spring Cloud LoadBalancer`
- 作用：按服务名调用时，从多个服务实例中选择一个进行转发。
- 本项目落地：
- 使用 `lb://student-service`、`lb://teacher-service`、`lb://class-service`
- 已开启 `spring.cloud.loadbalancer.retry.enabled: true`

### 2.5 服务间调用（OpenFeign）

- 使用技术：`Spring Cloud OpenFeign`
- 作用：通过接口声明式方式完成跨服务调用。
- 本项目落地：
- `class-service` 调用 `student-service` 和 `teacher-service`
- Feign 客户端在 `class-service/.../client/*`
- Feign 降级在 `class-service/.../client/fallback/*`

### 2.6 熔断降级与超时控制（Resilience4j）

- 使用技术：`Resilience4j CircuitBreaker + TimeLimiter`
- 作用：下游异常或响应过慢时快速失败，避免故障扩散。
- 本项目落地：
- 网关 `class` 路由配置了熔断回退
- `class-service` 使用了熔断降级能力
- 相关参数在网关配置里统一维护

### 2.7 限流（网关过滤器）

- 使用技术：自定义 `GlobalFilter`
- 作用：限制高频请求，保护网关和下游服务。
- 本项目落地：
- 使用滑动窗口思路统计请求数
- 支持按 `student/teacher/class/public` 分组限流

### 2.8 统一鉴权（JWT）

- 使用技术：`JWT`
- 作用：登录成功后签发 token，请求经过网关时统一校验。
- 本项目落地：
- 登录注册：`AuthController`
- token 生成和解析：`JwtService`
- 网关统一校验：`JwtAuthGlobalFilter`

### 2.9 消息队列（RabbitMQ）

- 使用技术：`Spring AMQP + RabbitMQ`
- 作用：实现异步解耦和事件驱动。
- 本项目落地：
- 主要放在 `class-service/.../mq/*`
- 演示了交换机、队列、生产者、消费者的基本用法

### 2.10 数据访问（JPA）

- 使用技术：`Spring Data JPA + MySQL`
- 作用：完成学生、老师、班级的持久化和 CRUD。
- 本项目落地：
- 各服务均采用 `Entity + Repository + Controller` 结构

### 2.11 可观测性与运维（Actuator）

- 使用技术：`Spring Boot Actuator`
- 作用：暴露健康检查和运维端点，方便联调和排障。
- 本项目落地：
- 多个服务均开启了 `management.endpoints.web.exposure.include=*`

## 3. 为什么注册中心模块代码很少

很多人第一次看 `registry-server` 会觉得奇怪：为什么只有一个启动类和一个配置文件？

原因不是“功能少”，而是“框架帮你写好了”。

- `Eureka Server` 本身是 Spring Cloud 提供的成熟基础设施组件。
- 你只需要引入依赖、加上 `@EnableEurekaServer`、再写少量配置，框架就会自动完成注册中心的大部分能力装配。

这些能力包括：

- 维护服务注册表
- 接收服务注册请求
- 接收心跳续约请求
- 剔除长时间失联的实例
- 提供 Eureka 控制台页面
- 给客户端返回可用实例列表

所以 `registry-server` 的重点不在“写很多代码”，而在：

- 理解服务注册与发现原理
- 理解心跳续约和实例剔除
- 理解自我保护机制
- 知道它在整个微服务体系中的位置

## 4. 为什么配置中心模块代码也不多

`config-server` 看起来代码也不多，核心原因和注册中心类似：它本质上也是一个“基础设施模块”，大量能力来自框架自动装配。

它主要依赖：

- `spring-cloud-config-server`
- `@EnableConfigServer`
- `application.yml` 里的配置源设置

框架会自动帮你做这些事：

- 根据服务名定位远程配置
- 对外暴露配置读取接口
- 读取本地目录或 Git 仓库中的配置文件
- 把配置以标准格式返回给客户端

本项目里额外扩展了两部分教学功能：

- 配置管理页面：`config-admin.html`
- 配置管理接口：可以列文件、读文件、改文件，并在保存后尝试调用对应服务的 `/actuator/refresh`

所以配置中心模块的重点也不是“代码量大”，而是理解下面几个问题：

- 为什么微服务需要集中配置
- native 模式和 Git 模式的区别
- 配置改了为什么不一定立刻生效
- `/actuator/refresh` 在配置刷新中的作用
- 配置中心和注册中心分别解决什么问题

## 5. 网关请求执行链路

一个请求进入网关后的典型顺序：

1. 全局限流过滤器 `LocalRateLimitFilter`
2. 全局 JWT 鉴权过滤器 `JwtAuthGlobalFilter`
3. 全局追踪日志过滤器 `GatewayTraceFilter`
4. 路由匹配（Path + Method）
5. 路由过滤器（重试、熔断、响应头处理）
6. `lb://` 负载均衡选择实例并转发
7. 下游服务返回，网关统一响应给前端

## 6. 已用与未用技术

- 已用：Eureka、Config Server、Gateway、LoadBalancer、OpenFeign、Resilience4j、JWT、RabbitMQ、JPA、Actuator
- 未用：Hystrix、Sentinel、OAuth2/OIDC SSO（如 Keycloak）

## 7. 启动顺序

1. `registry-server`
2. `config-server`
3. `student-service`
4. `teacher-service`
5. `class-service`
6. `api-gateway`

## 8. 常用访问地址

- Eureka 控制台：`http://localhost:8761/`
- 配置中心管理页：`http://localhost:8888/config-admin.html`
- 网关首页：`http://localhost:9000/`

## 9. 常见面试追问

1. 为什么注册中心可以代码这么少，但功能却不少？
2. Eureka 的心跳续约机制和实例剔除机制是怎样工作的？
3. 什么是 Eureka 的自我保护机制？为什么它既是优点也是风险点？
4. 为什么配置中心代码不多，却能给多个服务统一下发配置？
5. native 模式和 Git 模式各适合什么场景？
6. 如果注册中心挂了，系统为什么不会立刻全部不可用？
7. 为什么写请求默认不建议自动重试？如何保证幂等？
8. 本地限流在多实例网关场景下有什么问题？如何升级成分布式限流？
9. 为什么 MQ 推荐 JSON，而不是 Java 原生序列化对象？

## 10. 开发提示

- 修改配置中心内容后，如果服务没生效，先确认服务读取的是远程配置还是本地配置。
- 网关配置调整后，建议重启 `config-server` 和 `api-gateway` 再验证。
- 如果接口出现 `401/403`，优先检查 JWT 是否携带、是否过期、角色是否满足权限要求。
