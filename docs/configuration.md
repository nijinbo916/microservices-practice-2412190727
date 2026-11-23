# 配置管理说明（Nacos Config）

> 第 10 周作业：把「可共享、非敏感、需要按环境区分」的配置从各服务的
> `application.yml` 迁到 Nacos，做到改配置不重新打包；敏感值仍然只走环境变量。

## 1. 总体结构

```
nacos-config/
├── dev/                        # 本地开发环境（Group = PETCLINIC_DEV）
│   ├── pet-service.yaml
│   ├── doctor-service.yaml
│   ├── appointment-service.yaml
│   └── gateway-service.yaml
└── test/                       # 自动化测试 / 演示环境（Group = PETCLINIC_TEST）
    ├── pet-service.yaml
    ├── doctor-service.yaml
    ├── appointment-service.yaml
    └── gateway-service.yaml
```

服务端通过 Spring Config Data 导入：

```yaml
spring:
  config:
    import: optional:nacos:${spring.application.name}.yaml?group=${PETCLINIC_CONFIG_GROUP:PETCLINIC_DEV}&refreshEnabled=true
```

- **Data ID = 服务名 + `.yaml`**，一个服务一份，避免「一个巨大配置文件所有人抢着改」
- **Group 区分环境**：`PETCLINIC_DEV` / `PETCLINIC_TEST`
- `optional:` 前缀：配置中心暂时不可用时按本地默认值启动，不阻断开发
- `refreshEnabled=true`：Nacos 推送变更后客户端自动刷新（`@RefreshScope` 生效）

## 2. Data ID / Group / 用途一览

| Data ID | Group | 用途 | 关键配置项 |
|---|---|---|---|
| `pet-service.yaml` | `PETCLINIC_DEV` | 本地开发的宠物服务 | `spring.datasource.hikari.maximum-pool-size=5`、`logging.level.com.zjgsu.njb.pet=debug` |
| `pet-service.yaml` | `PETCLINIC_TEST` | 测试/演示的宠物服务 | 连接池 10、日志级别 info |
| `doctor-service.yaml` | `PETCLINIC_DEV` | 本地开发的医生服务 | 连接池 4、`com.zjgsu.njb.doctor=debug` |
| `doctor-service.yaml` | `PETCLINIC_TEST` | 测试/演示的医生服务 | 连接池 8、日志级别 info |
| `appointment-service.yaml` | `PETCLINIC_DEV` | 本地开发的预约服务 | 连接池 5、Feign 3000/5000、`petclinic.booking.max-advance-days=30` |
| `appointment-service.yaml` | `PETCLINIC_TEST` | 测试/演示的预约服务 | 连接池 10、Feign 2000/4000、`max-advance-days=7`（动态验证对象） |
| `gateway-service.yaml` | `PETCLINIC_DEV` | 本地开发的网关 | `logging.level.com.zjgsu.njb.gateway=debug` |
| `gateway-service.yaml` | `PETCLINIC_TEST` | 测试/演示的网关 | 日志级别 info |

命名空间：沿用第 7 周的 `NACOS_NAMESPACE`（留空即 public），环境隔离不靠 namespace，靠 Group——因为作业要求明确列出 Group，且个人项目共用 public 命名空间更省资源。

## 3. 两个环境的边界

| | dev | test |
|---|---|---|
| 启动方式 | 本机 `./mvnw spring-boot:run` 或 IDE 启动 | `docker compose up -d` |
| Spring Profile | `dev`（默认） | `test`（`spring.profiles.group.test=docker`，复用容器化连接参数） |
| Nacos Group | `PETCLINIC_DEV` | `PETCLINIC_TEST` |
| 数据库 | 本机 `localhost:3306`，root | 容器内 `mysql:3306`，各服务专属账号 |
| 连接池 | 小（4–5） | 大（8–10） |
| 日志 | 业务包 debug，便于开发 | info，避免刷屏 |
| 配置来源 | 本地默认值 + Nacos `PETCLINIC_DEV` | `application-docker.yml` + Nacos `PETCLINIC_TEST` |

切换方式只有一处：`PETCLINIC_CONFIG_GROUP` 环境变量。`compose.yaml` 已固定为 `PETCLINIC_TEST`；本地不设时默认 `PETCLINIC_DEV`。

## 4. 动态配置

被选的观察点是预约规则：

```
petclinic.booking.max-advance-days   # 预约最多可提前多少天
```

代码位置：

- `services/appointment-service/.../config/BookingProperties.java`：`@RefreshScope` + `@ConfigurationProperties`
- `services/appointment-service/.../service/AppointmentService.java`：创建预约时校验
- `services/appointment-service/.../web/ConfigInfoController.java`：`GET /api/config/effective` 暴露当前生效值（只读、非敏感）

验证逻辑：test 组初始 `max-advance-days=7`，创建「20 天后」的预约返回 **400**；把配置改成 `60` 后不重启服务，同一请求返回 **201**。

## 5. 什么放进了配置中心，什么没有

**放进去（非敏感、可共享、按环境不同）**

- 数据库连接池大小、最小空闲连接
- Feign 连接/读取超时
- 日志级别
- 业务参数（预约提前天数）

**没有放（安全边界）**

| 配置 | 原因 | 实际位置 |
|---|---|---|
| 数据库密码 | 泄露即失守 | `.env` → compose 环境变量 |
| `JWT_SECRET` | 泄露可伪造任意身份 | `.env`，网关与 pet-service 共用 |
| `TRUSTED_CALLER_SECRET` | 泄露可绕过网关直连下游 | `.env` |
| Nacos 账号密码 | 配置中心自身的凭据 | `.env` |

一句话原则：**配置中心管「改了不影响安全」的参数，环境变量管「泄露就出事」的秘密。**

## 6. 发布与验证

```bash
# 发布 dev + test 两套配置并做全部验证（推荐）
bash scripts/config-test.sh

# 只发布、不验证：直接调 Nacos OpenAPI
curl -X POST "http://localhost:8848/nacos/v1/cs/configs" \
  -d "accessToken=<token>" \
  --data-urlencode "dataId=appointment-service.yaml" \
  --data-urlencode "group=PETCLINIC_TEST" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content@nacos-config/test/appointment-service.yaml"
```

控制台方式：`http://localhost:18080` → 配置管理 → 新建配置，Data ID 与 Group 按上表填写。

验证接口：

```bash
curl -s http://127.0.0.1:8083/api/config/effective
```

预期（test 环境）：

```json
{"service":"appointment-service","activeProfiles":["test","docker"],"env":"test",
 "configGroup":"PETCLINIC_TEST","configServer":"nacos:8848","maxAdvanceDays":7,
 "hikariMaxPoolSize":"10","feignReadTimeoutMs":"4000","appointmentLogLevel":"info"}
```

## 7. 排错记录

1. **`spring.config.import` 没写会启动失败**：引入 nacos-config starter 后，
   `spring.cloud.nacos.config.import-check.enabled` 默认为 true，不写 import 直接报
   「No spring.config.import property has been defined」。解法就是显式写 import（本仓库已写）。
2. **Group 写错，配置“读不到”**：服务用 `PETCLINIC_DEV`、配置发到 `PETCLINIC_TEST` 时不会报错，
   只是所有值都是本地默认值。用 `/api/config/effective` 看 `configGroup` 最容易发现。
3. **改了配置不生效**：先确认 import 里带 `refreshEnabled=true`，再确认接收方 bean 有
   `@RefreshScope`；两者缺一个都只会「启动时读一次」。
4. **敏感值误传**：`nacos-config/` 目录只放非敏感项，提交前用
   `grep -rniE "password|secret" nacos-config/` 自查（应无命中）。
