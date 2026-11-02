# 第 7 周作业：接入 Nacos 服务发现

- **姓名**：倪金博　**学号**：2412190727
- **日期**：2026-11-02
- **工程**：继续使用第 06 周的 `services/`（未重建工程，只做增量改造）
- **设计文档**：[docs/microservices-design.md](../../microservices-design.md)

---

## 一、部署 Nacos 并注册服务

**实现思路**：Nacos 作为独立容器并入根目录 `compose.yaml`，单机模式 + 鉴权 + 命名数据卷；
三个业务服务在原有 Dockerfile / 配置基础上增加 Discovery Starter 与注册参数，
启动顺序为「Nacos → 初始化管理员密码 → MySQL + 业务服务」。

**关键配置（compose.yaml）**：

| 项目 | 值 | 说明 |
| --- | --- | --- |
| 镜像 | `nacos/nacos-server:v3.2.4` | 课程指定版本 |
| 模式 | `MODE=standalone` | 单机，数据落 Derby |
| 控制台端口 | `18080:8080` | Nacos 3 控制台独立于 8848，浏览器访问 `http://localhost:18080` |
| 服务端端口 | `8848:8848` | 注册/发现 HTTP API、SDK 通信 |
| SDK 端口 | `9848:9848` | gRPC 长连接，客户端注册必需，缺了注册会失败 |
| 鉴权 | `NACOS_AUTH_ENABLE=true`、`NACOS_AUTH_ADMIN_ENABLE=true`、`NACOS_AUTH_CONSOLE_ENABLE=true` | 同时开启客户端、控制台、管理员三类鉴权 |
| Token | `NACOS_AUTH_TOKEN`（Base64，解码后 ≥ 32 字节） | 只放本地 `.env` |
| Server identity | `NACOS_AUTH_IDENTITY_KEY` / `VALUE` | 只放本地 `.env` |
| 数据卷 | `nacos-data:/home/nacos/data`、`nacos-logs:/home/nacos/logs` | 容器重建后注册数据与配置不丢 |

**初始化管理员密码**（Nacos 3 不再内置默认密码，必须首次初始化）：

```bash
curl -X POST 'http://localhost:8848/nacos/v3/auth/user/admin' -d "password=${NACOS_PASSWORD}"
```

**服务注册配置**（三个服务相同结构，`application.yml`）：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        group: ${NACOS_GROUP:PERSONAL_GROUP}
        namespace: ${NACOS_NAMESPACE:}
        register-enabled: ${NACOS_REGISTER_ENABLED:true}
```

- 服务名沿用第 06 周设计：`pet-service` / `doctor-service` / `appointment-service`
  （与 `docs/microservices-design.md` 一致；不同服务不同名，多实例才会同名）；
- 依赖由课程统一 BOM 管理：`spring-cloud-dependencies 2025.1.0` +
  `spring-cloud-alibaba-dependencies 2025.1.0.0`，服务内**不覆盖** Nacos Client 版本，**未引入** Config Starter；
- 宿主机端口保持互不冲突：pet 8084 / doctor 8082 / appointment 8083（容器内均 8080）。

**实测结果**：`GET http://localhost:8848/nacos/` 返回 200，控制台 `http://localhost:18080` 可打开；
三个服务启动日志均打印
`nacos registry, PERSONAL_GROUP <服务名> <容器IP>:8080 register finished`
（如 `pet-service 172.19.0.3:8080`）；`scripts/smoke-test.sh` 全量 **PASS=13 FAIL=0**。

## 二、配置隔离并完成服务发现调用

**隔离配置**：

| 项目 | 值 |
| --- | --- |
| 命名空间 | `public`（默认命名空间，未自定义，`NACOS_NAMESPACE` 留空） |
| 分组 | `PERSONAL_GROUP`（个人项目专用，未与课堂示例混用） |
| 注册中心地址 | compose 中 `NACOS_SERVER_ADDR=nacos:8848`（服务名，不用 localhost） |
| 凭据 | `NACOS_USERNAME` / `NACOS_PASSWORD` 来自 `.env`（`.env` 已 gitignore，`.env.example` 只留占位） |

**改造点（appointment-service）**：

1. 新增 `remote/ServiceLookup.java`：封装 `DiscoveryClient.getInstances(服务名)`，
   取第一个实例的 `getUri()`；**每次调用都重新查询**，不在启动时缓存永久地址；
2. `PetClient` / `DoctorClient` 删除 `@Value("${pet.base-url}")` 等固定地址字段，
   改为「服务名（`pet.service-name` / `doctor.service-name`）+ ServiceLookup」拼出
   `实例URI + 原业务路径`，例如 `http://172.18.0.5:8080/api/pets/1`；
3. 实例列表为空 → 抛 `DependencyUnavailableException` → 全局异常处理返回 **503**（不写库）；
   远端 404 保留为 **404**；连接失败/超时仍为 503；
4. 删除配置项：`pet.base-url`、`doctor.base-url`、compose 中的 `PET_SERVICE_URL` / `DOCTOR_SERVICE_URL`；
   保留注册中心地址、分组、凭据与数据库配置；
5. RestTemplate 的连接超时 3s / 读取超时 5s 保持不变。

**调用方向**：`appointment-service → pet-service`（校验宠物）、`appointment-service → doctor-service`
（校验医生并取每日号源上限）。

**请求示例与预期**：

| 请求 | 预期 |
| --- | --- |
| `POST /api/appointments`（petId 有效、doctorId 有效） | 201，响应含远端取回的 petName / doctorName |
| `POST /api/appointments`（petId=999999） | 404「宠物不存在」 |
| 停掉 pet-service 后 `POST /api/appointments` | 503「没有可用实例」或连接失败，本地不新增记录 |

**实测结果**：见第四节脚本输出与截图；预约创建成功时服务日志打印
`服务发现：pet-service -> http://<容器IP>:8080（共 1 个实例）`，随后请求该实例地址完成校验。

## 三、健康状态、下线与恢复

**操作与结果**（`scripts/smoke-test.sh --with-dependency-down` 自动执行，也可手工重复）：

| 步骤 | 命令 | 结果 |
| --- | --- | --- |
| 1. 正常 | 三服务健康 + `POST /api/appointments` | 201，`petclinic_appointment.appointment` +1 行 |
| 2. 下线 | `docker compose stop pet-service` | 容器停止，Nacos 实例列表先变为不健康、随后摘除 |
| 3. 失败 | 等待约 20s 后 `POST /api/appointments`（新的有效参数） | **503** `DEPENDENCY_UNAVAILABLE`，预约表**无新增**（直查 MySQL 复核） |
| 4. 恢复 | `docker compose start pet-service` | 约 10~30s 重新注册并可访问，健康接口 UP |
| 5. 恢复后 | 新建档 + `POST /api/appointments` | 201，重新通过服务发现拿到实例并成功写库 |

**三种「健康」不是一回事**（结合本系统说明）：

- **SDK 连接存活**：Nacos Client 与 9848 的 gRPC 长连接是否正常。它只说明「客户端能连上注册中心」，
  断连时客户端会重连并可能保留旧实例缓存 —— 业务仍可能调用到已失效地址；
- **注册实例状态**：Nacos 服务列表中实例的 `healthy` / `enabled` 字段，由客户端心跳与
  服务端健康检查决定。实例被摘除/标不健康后，其他服务的 `DiscoveryClient` 才会拿到新列表；
- **Actuator 应用健康**：本服务 `/actuator/health` 的聚合结果（DB 连通、磁盘等），
  由 Spring Boot 自己判断。**它返回 DOWN 不会自动让 Nacos 摘除实例**，需要
  `management.endpoint.health.probes` + 注册中心侧的健康检查配合；本周不假设两者自动联动。

**事务窗口**：服务发现只解决「地址从哪来」，不消除「远端校验成功、本地写入失败」的部分成功窗口，
本周按课程要求不做补偿/分布式事务。

## 四、验证脚本

`scripts/smoke-test.sh`（从仓库根目录运行，任一步失败即返回非零）：

| 检查项 | 内容 |
| --- | --- |
| 0 | Nacos 控制台 HTTP（`http://localhost:${NACOS_CONSOLE_PORT:-18080}`）可访问，**不再使用旧版 8848/nacos** |
| 1 | 三个服务 `/actuator/health` 返回 UP |
| 2 | doctor-service 医生主数据 ≥ 3 |
| 3 | pet-service 建档 201 |
| 4 | appointment-service 跨服务创建预约 201（响应含远端宠物名/医生名） |
| 5 | 预约详情 200 且字段一致 |
| 6 | 宠物不存在 → 404 |
| 7（可选参数） | 停 pet-service → 503 且不写库 → 重启 → 恢复后再成功 |

**退出码**：脚本末尾 `[ "$FAIL" -eq 0 ]`，存在失败项时返回 1，便于 CI/作业检查。

**测试边界**：单元测试不依赖 Nacos 与 MySQL —— test profile 关闭
`spring.cloud.nacos.discovery.enabled`，用 `@MockitoBean DiscoveryClient`
模拟「实例存在 / 实例列表为空」，用 `MockRestServiceServer` 模拟远端响应，数据源为 H2。

| 服务 | 测试命令 | 用例数 |
| --- | --- | --- |
| pet-service | `cd services/pet-service && ./mvnw test` | 6 |
| doctor-service | `cd services/doctor-service && ./mvnw test` | 5 |
| appointment-service | `cd services/appointment-service && ./mvnw test` | 13（含实例列表为空 503） |

## 五、截图索引

见 [screenshots/](screenshots/)：

| 提交项 | 截图 |
| --- | --- |
| 1. 部署与注册 | 03-register-config（注册配置）、04-compose-nacos（Nacos 服务定义） |
| 2. 服务发现调用 | 01-discovery-code（实时寻址）、02-pet-client（实例 URI 拼装）、06-flow-create（跨服务 201） |
| 3. 健康与失败 | 05-health（三服务 UP）、07-fail-404（404 不写库）、10-service-down（停掉 pet-service 后容器消失、Nacos 中 `ipCount:0`）、11-empty-instance-test（实例列表为空 → 503 且不写库） |
| 4. 脚本与测试 | 08-smoke-all（PASS=13 FAIL=0）、09-tests（三服务 24 用例） |

## 六、遇到的问题与解决

1. **Nacos 3 控制台不在 8848**：Nacos 3.x 把控制台独立到容器 8080 端口，
   所以映射 `18080:8080`，8848 仍是服务端 API，9848 是 SDK gRPC —— 三个端口各司其职，缺一不可。
2. **Nacos 3 无默认管理员密码**：首次必须调用 `/nacos/v3/auth/user/admin` 初始化，
   否则控制台进不去、客户端注册被拒。
3. **Spring Cloud 自带多个 `@Primary` DiscoveryClient**：`compositeDiscoveryClient`、
   `simpleDiscoveryClient` 与自定义 Bean 冲突（`NoUniqueBeanDefinitionException`），
   测试改用 `@MockitoBean` 替换 DiscoveryClient，避免自定义 Bean 竞争。
4. **Boot 4 与 Nacos 依赖版本匹配**：选用 `spring-cloud-dependencies 2025.1.0`
   （对应 Spring Boot 4.0.x）+ `spring-cloud-alibaba-dependencies 2025.1.0.0`，
   由 BOM 统一管理，服务内不覆盖 nacos-client 版本。
