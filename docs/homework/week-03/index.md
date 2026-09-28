# 第 3 周作业：创建可运行的 Spring Boot 工程

- **姓名**：倪金博
- **学号**：2412190727
- **日期**：2026-09-28
- **项目**：萌宠诊所 · 宠物医院管理系统（沿用第 02 周选题）
- **本周范围**：只做工程骨架与运行验证，不涉及业务建模、REST API、Service、Repository 和数据库

---

## 一、本周计划与完成情况

| 计划项 | 状态 |
| --- | --- |
| 在仓库根目录建立 `monolith/` Maven 工程（pom.xml 与 src 同属一个工程） | ✅ 完成 |
| 使用 Java 25、Spring Boot 4.0.x、Maven，配置统一用 `application.yml` | ✅ 完成 |
| Group / Package 设为 `com.zjgsu.njb`（倪金博 → njb） | ✅ 完成 |
| 提供可执行 `./mvnw test` 与 `./mvnw spring-boot:run` | ✅ 完成 |
| 提供验证运行状态的 GET 接口 `/api/hello`，端口默认 8080 | ✅ 完成 |
| `@SpringBootTest` 启动测试（contextLoads）可通过 | ✅ 完成 |
| 记录启动命令、接口响应与测试结果，截图入库 | ✅ 完成 |

## 二、工程结构

```
monolith/                                  ← 本周新建的 Maven 工程（独立可运行）
├── pom.xml                                ← Spring Boot 4.0.8 parent + Web + Actuator + Test
├── mvnw / mvnw.cmd                        ← Maven Wrapper，无需本机安装 Maven
├── .mvn/wrapper/maven-wrapper.properties
├── .gitignore
└── src/
    ├── main/
    │   ├── java/com/zjgsu/njb/
    │   │   ├── MonolithApplication.java    ← 启动类
    │   │   └── web/HelloController.java    ← GET /api/hello
    │   └── resources/application.yml       ← 端口 8080、应用名、Actuator 配置
    └── test/java/com/zjgsu/njb/
        ├── MonolithApplicationTests.java   ← @SpringBootTest 启动测试 contextLoads
        └── HelloControllerTest.java        ← 接口验证测试（随机端口真实发起 HTTP 请求）
```

仓库根目录保留 `README.md`、`docs/`，与 `monolith/` 同级。

## 三、关键配置

**pom.xml（要点）**

| 配置项 | 值 |
| --- | --- |
| parent | `org.springframework.boot:spring-boot-starter-parent:4.0.8` |
| groupId / artifactId | `com.zjgsu.njb` / `monolith` |
| java.version | 25（编译目标 Java 25） |
| 依赖 | `spring-boot-starter-web`、`spring-boot-starter-actuator`、`spring-boot-starter-test` |

**application.yml（要点）**

```yaml
spring:
  application:
    name: petclinic-monolith
server:
  port: 8080                      # 默认端口，未做修改
app:
  project:
    name: 萌宠诊所 · 宠物医院管理系统
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

## 四、启动命令与接口响应

### 4.1 启动命令

```console
$ cd monolith
$ ./mvnw spring-boot:run
```

启动成功日志（节选）：

```text
Tomcat started on port 8080 (http) with context path '/'
Started MonolithApplication in 2.303 seconds (process running for 2.717)
```

### 4.2 GET /api/hello

```console
$ curl -i http://localhost:8080/api/hello
HTTP/1.1 200
Content-Type: application/json
Content-Length: 167

{"project":"萌宠诊所 · 宠物医院管理系统","message":"萌宠诊所服务已启动，欢迎使用！","status":"RUNNING","time":"2026-09-28T14:02:13.792585"}
```

### 4.3 GET /actuator/health

```console
$ curl -i http://localhost:8080/actuator/health
HTTP/1.1 200
Content-Type: application/vnd.spring-boot.actuator.v3+json

{"components":{"diskSpace":{"status":"UP"},"livenessState":{"status":"UP"},"ping":{"status":"UP"},"readinessState":{"status":"UP"},"ssl":{"status":"UP"}},"groups":["liveness","readiness"],"status":"UP"}
```

健康检查整体状态为 **UP**。

## 五、启动测试与接口测试结果

### 5.1 测试命令

```console
$ cd monolith
$ ./mvnw test
```

### 5.2 测试结果

```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.zjgsu.njb.MonolithApplicationTests
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.zjgsu.njb.HelloControllerTest
[INFO] Results:
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| 测试类 | 测试内容 | 结果 |
| --- | --- | --- |
| `MonolithApplicationTests#contextLoads` | Spring 应用上下文能否加载 | ✅ 通过 |
| `HelloControllerTest#helloEndpointShouldReturnProjectInfo` | 真实 HTTP 请求 `/api/hello` 返回 200 且含项目名与 RUNNING | ✅ 通过 |
| `HelloControllerTest#healthEndpointShouldReportUp` | 真实 HTTP 请求 `/actuator/health` 返回 200 且 status 为 UP | ✅ 通过 |

## 六、过程中遇到的问题与解决

| 问题 | 现象 | 解决 |
| --- | --- | --- |
| `TestRestTemplate` 编译失败 | Spring Boot 4.0 已移除 `org.springframework.boot.test.web.client.TestRestTemplate`，导致测试编译报错 | 改用 JDK 原生 `java.net.http.HttpClient` 编写接口测试，不依赖具体测试框架客户端 API |
| 端口被环境变量覆盖 | 启动时报 `Port 54265 was already in use`，与 application.yml 中的 8080 不符 | 定位到本机环境变量 `SERVER__PORT` 被 Spring Boot 宽松绑定识别为 `server.port`；启动时排除该变量后正常使用 8080 |

## 七、本周截图

见 [screenshots/](screenshots/) 目录：

| 文件 | 内容 |
| --- | --- |
| `01-project-structure.png` | monolith 工程目录结构 |
| `02-mvnw-test.png` | `./mvnw test` 测试通过（Tests run: 3, BUILD SUCCESS） |
| `03-run-hello.png` | `./mvnw spring-boot:run` 启动日志与 `/api/hello` 响应 |
| `04-health-check.png` | `/actuator/health` 响应（status UP） |

## 八、下一步计划

1. 第 04 周：业务建模与数据库设计——Pet、Owner、Doctor、Appointment 等核心实体的表结构设计。
2. 后续：接入数据库持久化（JPA/MyBatis），实现「预约 → 病历」主流程的 REST API 与 Service 分层。
3. 长期：按模块边界拆分微服务，引入服务注册与网关、消息队列、分布式事务、监控与容器化部署。
