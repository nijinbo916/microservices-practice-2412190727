# 第 10 周 · Nacos Config 配置管理

> 作业目标：用 Nacos Config 集中管理配置，区分 dev / test 环境，并给出动态变更证据。
> 结论先行：**8 个 Data ID（4 服务 × 2 环境）已发布并回读成功；`config-test.sh` 32 项断言全部通过；
> 动态验证证明改配置后不重启即生效（同一请求 400 → 201，容器启动时间未变）。**

## 0. 做了什么

| 项 | 内容 |
|---|---|
| 新增依赖 | 四个服务加 `spring-cloud-starter-alibaba-nacos-config`（2025.1.0.0，BOM 管理） |
| 配置导入 | profile 专属文件：`application-dev.yml` → PETCLINIC_DEV 组；`application-docker.yml` → PETCLINIC_TEST 组 |
| 配置源文件 | `nacos-config/dev/*.yaml`、`nacos-config/test/*.yaml`（4 服务 × 2 环境 = 8 份） |
| 动态参数 | `petclinic.booking.max-advance-days`（预约最多提前几天），`@RefreshScope` 接收 |
| 观测接口 | `GET /api/config/effective`（appointment-service，只返回非敏感值） |
| 验证脚本 | `scripts/config-test.sh`：发布 → 回读 → 服务实读 → 动态改值 → 还原，32 项断言 |
| 文档 | `docs/configuration.md`（Data ID / Group / 用途 / 边界 / 排错） |

## 1. 配置中心

**实现思路**：选了四类非敏感配置迁进 Nacos——数据库连接池参数、Feign 超时、日志级别、
业务参数（预约提前天数）。Data ID = `<服务名>.yaml`，一个服务一份；Group 区分环境。
敏感值（数据库密码、JWT_SECRET、TRUSTED_CALLER_SECRET、Nacos 账号）不进配置中心，仍走 `.env`。

关键配置（appointment-service 为例）：

```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        group: ${PETCLINIC_CONFIG_GROUP:PETCLINIC_DEV}
        file-extension: yaml
        refresh-enabled: true
```

```yaml
# application-dev.yml / application-docker.yml
spring:
  config:
    import: optional:nacos:${spring.application.name}.yaml?group=${PETCLINIC_CONFIG_GROUP:PETCLINIC_TEST}&refreshEnabled=true
```

**运行命令**：

```bash
docker compose up -d                    # 起 Nacos + MySQL + 四个服务（compose 已设 test,docker）
bash scripts/config-test.sh             # 发布 + 全部验证
curl -s http://127.0.0.1:8083/api/config/effective
```

**结果（2026-11-23 实测）**：

- 8 个 Data ID 全部发布成功并回读成功（见 `logs/nacos-config-list.txt`）
- 服务实际读到的值与 Nacos 一致：`configGroup=PETCLINIC_TEST`、连接池 10、Feign 读超时 4000、
  `maxAdvanceDays=7`——都不是本地 `application.yml` 的默认值，证明**配置被服务读取**
- 单测 50 个全绿（pet 12 / doctor 5 / appointment 22 / gateway 11）

截图：`screenshots/01-nacos-config-list.png`、`02-nacos-config-detail.png`、
`03-import-config.png`、`04-effective-before.png`、`09/10-config-test-part*.png`。

## 2. 多环境配置

**设计**：环境隔离用 **Group** 而不是 namespace（作业要求明确列出 Group，且个人项目共用
public 命名空间更省资源）；Profile 只负责「连接参数从哪来」。

| | dev（本地开发） | test（自动测试 / 演示） |
|---|---|---|
| 启动方式 | 本机 `./mvnw spring-boot:run` | `docker compose up -d` |
| Profile | `dev`（默认） | `test,docker`（compose 设置） |
| Nacos Group | `PETCLINIC_DEV` | `PETCLINIC_TEST` |
| 数据库 | 本机 MySQL，root | 容器 MySQL，各服务专属账号 |
| 连接池 | 4–5 | 8–10 |
| 日志 | 业务包 debug | info |
| 预约提前天数 | 30 | 7 |

切换只有一处：`PETCLINIC_CONFIG_GROUP` 环境变量；`compose.yaml` 固定为 `PETCLINIC_TEST`。

完整的 Data ID / Group / 用途表见 [`docs/configuration.md`](../../configuration.md) 第 2 节
（本作业第二题要求的文档就是它）。

**关键结论**：单测走 `test` profile，两个 profile 专属文件都不加载，所以测试不连配置中心、
不拖慢构建；dev 与 test 的差异全部收敛在 Nacos 的两个 Group 里。

## 3. 动态配置验证

**观察点**：`petclinic.booking.max-advance-days`（预约最多提前几天，超出返回 400）。

代码：

- `config/BookingProperties.java`：`@RefreshScope` + `@ConfigurationProperties`
- `service/AppointmentService.java`：创建预约时用当前值校验
- `web/ConfigInfoController.java`：`/api/config/effective` 暴露生效值

**运行命令与结果（2026-11-23 实测，`logs/` 有原始响应）**：

```bash
# 1) 改配置前：创建「20 天后」的预约
curl -X POST http://127.0.0.1:8083/api/appointments -d '{"petId":1,...,"slotStart":"2026-12-13T10:00:00",...}'
# -> HTTP 400  message="预约最多提前 7 天，本次选择的日期超出范围：2026-12-13"

# 2) Nacos 把 PETCLINIC_TEST 组的 max-advance-days 从 7 改成 60（脚本自动做）
# 3) 不重启，几秒内服务读到新值
curl -s http://127.0.0.1:8083/api/config/effective   # -> maxAdvanceDays: 60
# 4) 同一请求再发一次
# -> HTTP 201（预约 id=30 创建成功），随后脚本取消它并把配置还原成 7
```

**无重启证明**：`docker inspect` 的容器 `StartedAt` 改配置前后完全一致
（`2026-11-23T05:19:23.924573351Z`）。

`scripts/config-test.sh` 把以上五步串成 32 项断言，全过退出码 0（`logs/config-test-run.log`）。

截图：`screenshots/07-dynamic-before-400.png`、`08-dynamic-after-201.png`、
`10-config-test-part2.png`、`11-booking-properties-code.png`、`12-appointment-rule-code.png`。

## 4. 安全配置

**设计**：配置中心只放「改了不影响安全」的参数；「泄露就出事」的秘密全部走环境变量。

| 配置 | 位置 | 为什么不进 Nacos |
|---|---|---|
| 数据库密码 | `.env` → compose 环境变量 | 泄露即失守 |
| `JWT_SECRET` | `.env`（网关与 pet-service 共用） | 泄露可伪造任意身份 |
| `TRUSTED_CALLER_SECRET` | `.env` | 泄露可绕过网关直连下游 |
| Nacos 账号密码 | `.env` | 配置中心自身凭据 |

- `.env` 已被 `.gitignore` 排除；仓库只有 `.env.example` 占位符（截图 `13-env-example.png`）
- README「九、Nacos Config」写明本地环境变量设置方法与 `openssl rand -base64 48` 生成方式
- 自查：`grep -rniE "password|secret" nacos-config/` 无命中；`git status` 不含 `.env`

## 5. 踩坑记录

1. **单测卡死 3 分钟**：把 `spring.config.import` 写在 `application.yml` 里后，
   单测上下文启动时会尝试连 `localhost:8848` 反复重试。解法：import 移到
   `application-dev.yml` / `application-docker.yml`（profile 专属），单测的 `test`
   profile 不加载它们，再在测试配置里关掉 `spring.cloud.nacos.config.enabled`。
2. **`import-check` 强校验**：引入 nacos-config starter 后，不写 `spring.config.import`
   启动直接报错。因为 import 改成了按 profile 提供，全局关闭
   `spring.cloud.nacos.config.import-check.enabled=false` 并注释原因。
3. **Nacos 3 的 OpenAPI 变了**：发布/读取配置要用 `/nacos/v3/admin/cs/config`、
   `/nacos/v3/client/cs/config`（参数名是 `groupName`，token 放 header），
   第 7 周用的 v1 地址在 3.x 已不可用，脚本里全部换成 v3。
4. **容器内 Nacos 客户端写快照失败**：应用以非 root 运行，`/app/nacos/` 不可写，
   日志刷 `save snapshot error`。不影响功能（配置读取与刷新正常），记录在案。

## 6. 复现步骤

```bash
cp .env.example .env          # 填好密钥（README 第九节有说明）
docker compose up -d          # 六个容器全部 healthy
bash scripts/config-test.sh   # 预期输出：32 项全部通过
```
