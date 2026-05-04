# Spring Cloud School Demo

这是一个教学用微服务样例，包含：
- 注册中心：Eureka (`registry-server`)
- 配置中心：Spring Cloud Config (`config-server` + `config-repo`)
- 网关：Spring Cloud Gateway (`api-gateway`)
- 业务服务：`student-service`、`teacher-service`、`class-service`
- 负载均衡：Spring Cloud LoadBalancer（通过 `lb://服务名` 与 Feign 自动生效）
- 熔断：Resilience4j（网关路由熔断 + class-service 接口熔断）
- 限流：网关全局过滤器（10秒20次）
- CRUD：学生、老师、班级完整增删改查
- 页面：网关管理页面 + 配置中心管理页面

## 启动顺序
1. registry-server
2. config-server
3. student-service
4. teacher-service
5. class-service
6. api-gateway

## 访问示例
- 通过网关访问学生：`GET http://localhost:9000/student/1`
- 通过网关访问老师：`GET http://localhost:9000/teacher/1`
- 通过网关访问班级聚合：`GET http://localhost:9000/class/1/detail`

## 页面地址
- 注册中心页面：`http://localhost:8761/`
- 网关业务管理页（学生/老师/班级 CRUD）：`http://localhost:9000/`
- 配置中心管理页（编辑 yml）：`http://localhost:8888/config-admin.html`

## 常用 CRUD 接口
- 学生
- `GET /student`
- `GET /student/{id}`
- `POST /student`
- `PUT /student/{id}`
- `DELETE /student/{id}`
- 老师
- `GET /teacher`
- `GET /teacher/{id}`
- `POST /teacher`
- `PUT /teacher/{id}`
- `DELETE /teacher/{id}`
- 班级
- `GET /class`
- `GET /class/{id}`
- `POST /class`
- `PUT /class/{id}`
- `DELETE /class/{id}`
- `GET /class/{id}/detail`（聚合学生+老师）

## 关键技术点
- 服务发现：所有服务注册到 Eureka
- 配置中心：业务服务端口来自 `config-repo/*.yml`
- 负载均衡：`lb://student-service`、`@FeignClient(name=\"student-service\")`
- 熔断降级：`@CircuitBreaker` + Feign fallback
- 限流：`LocalRateLimitFilter`

## IDEA 报错修复（java 文件位于模块根源之外）
出现这个提示通常是 IDEA 没把 `springcloud-school-demo` 当作 Maven 多模块工程导入。

修复步骤：
1. 在 IDEA 中 `File -> Open`，直接打开 `springcloud-school-demo/pom.xml`（不是上层 `springaop-ioc`）。
2. 右侧 Maven 面板点 `Reload All Maven Projects`。
3. 若仍有红色目录：右键模块目录 `Mark Directory as -> Sources Root`，应指向 `src/main/java`。
4. 用 Maven 启动子模块，例如：
   `../mvnw.cmd -pl registry-server spring-boot:run`
