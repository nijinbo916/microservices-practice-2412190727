# 第 08 周 · 服务间同步调用（OpenFeign + 负载均衡 + 端到端验证）

> 本周目标：在微服务项目中实现服务间同步调用，并通过测试验证服务协作流程。
> 调用方：`services/appointment-service`；被调用方：`services/pet-service`、`services/doctor-service`。
> 编排：根目录 `compose.yaml`（多实例覆盖 `compose.lb.yaml`）；验证脚本：`scripts/e2e-test.sh`（从仓库根目录运行）。
> 调用链路图见 [../service-communication.md](../service-communication.md)。

## 运行命令

```bash
# 端到端验证（从仓库根目录，自动 build + 起多实例栈 + 六段验证）
bash scripts/e2e-test.sh

# 只想手动跑多实例栈
docker compose -f compose.yaml -f compose.lb.yaml up -d --scale pet-service=2

# 单元测试（Feign 错误处理 / 业务规则 / API 契约，共 22 个）
cd services/appointment-service && ./mvnw test
```

## 一、选择一个跨服务业务流程

**流程**：创建预约（POST `/api/appointments`）。
预约数据归 appointment-service 独占（`petclinic_appointment` 库），但一次合法预约必须同时满足「宠物存在」与「医生当日号源未满」，这两条信息分别在 pet-service（`petclinic_pet` 库）和 doctor-service（`petclinic_doctor` 库）——**本服务不持有对方数据，跨服务关联只保存对方的 ID**，校验数据必须实时远端取得。

**边界（校验全部卡在写库之前，任何一步失败都不产生预约记录）**：

| 步骤 | 调用 | 失败语义 |
|---|---|---|
| 1 | Feign 调 pet-service `GET /api/pets/{petId}` | 404 → `404 PET_NOT_FOUND`；依赖不可用 → `503 DEPENDENCY_UNAVAILABLE` |
| 2 | Feign 调 doctor-service `GET /api/doctors/{doctorId}` | 404 / 503 同上（顺带取 `dailyCapacity`） |
| 3 | 本地校验时段重叠、每日号源上限 | 409 `SLOT_CONFLICT` / 409 `CAPACITY_FULL` |
| 4 | 全部通过 → 写入预约 | 201，响应体的 `petName` / `doctorName` 来自实时远端 |

**完成情况**：`scripts/e2e-test.sh` 六段验证全过（多实例栈 → Nacos 双实例 → 201 创建且响应含远端宠物/医生名 → 负载均衡双实例日志 → 全实例下线 503 且零写入 → 恢复后 201）。

**关键结论**：跨服务写流程的正确姿势是「先校验后写库」——远端校验失败时本地零副作用；响应体中的对方字段每次实时取得，不存在本地缓存导致的数据陈旧。

## 二、使用 OpenFeign

**实现**（appointment-service 为调用方）：

- 依赖：`spring-cloud-starter-openfeign`（版本由 Spring Cloud 2025.1.0 BOM 管理），启动类 `@EnableFeignClients(basePackages = "...remote")`。
- 两个声明式客户端，**按服务名调用，不写死容器 IP**：
  - `remote/PetFeignClient.java`：`@FeignClient(name = "${pet.service-name}", path = "/api/pets")`
  - `remote/DoctorFeignClient.java`：`@FeignClient(name = "${doctor.service-name}", path = "/api/doctors")`
  - 服务名走配置占位符，底层由 Spring Cloud LoadBalancer 结合 Nacos 实例列表寻址。
- 门面 `PetClient` / `DoctorClient` 统一翻译错误：`FeignException.NotFound` → `ResourceNotFoundException`（404），连接拒绝 / 超时 / 其余 5xx → `DependencyUnavailableException`（503），错误响应为结构化 JSON（`error=DEPENDENCY_UNAVAILABLE`），不让调用失败被当成业务成功。
- 超时不可省（依赖卡死不能拖死本服务）：`spring.cloud.openfeign.client.config.default` 连接 3s / 读取 5s。
- 第 07 周的手写 `RestTemplate + DiscoveryClient` 寻址组件（`ServiceLookup`、`RemoteHttpConfig`）已整体移除，调用方式从「手写 URL 拼接」变为「接口声明」。

**验证方法**：① 单元测试用 `@MockitoBean` 替换两个 Feign 接口，覆盖 404/500/无实例/成功四类分支（`PetClientErrorTranslationTest`）；② e2e 第 3 段真实调用返回 201 且响应含远端字段。

**结果**：appointment-service 22/22 单测通过；e2e 创建预约 201，响应 `petName=e2e宠物`、`doctorName=e2e医生`（实时远端取得）。

## 三、负载均衡验证

**实现**：

- `compose.lb.yaml`（随仓库提交）：用 Compose `!reset` 标签清空 pet-service 的固定 `ports` 与 `container_name`——第二实例不再因宿主机端口冲突 / 容器重名起不来；各实例只在容器网络内监听 8080，**互不绑定宿主机端口**。
- 实例标识：pet-service 新增 `web/InstanceTagFilter.java`，每个实例用容器 hostname 生成稳定实例 ID，在响应头 `X-Instance-Id` 返回，并在被调用时打印 `[LB] instance=<id> GET /api/pets/...` 日志行——「仅展示注册列表不算完成分发验证」，这里用**被调用方处理日志**证明流量真实到达。
- 启动 2 实例：`docker compose -f compose.yaml -f compose.lb.yaml up -d --scale pet-service=2`，Nacos 中 pet-service 健康实例数 = 2。

**验证方法**：调用方 appointment-service 连续触发 16 次 Feign 调用（详情接口每次都会实时调 pet-service），再统计两个实例日志中 `[LB] instance=` 的去重实例数。

**结果**：两个实例（`instance=pet-service-1` / `instance=pet-service-2` 标识）均实际处理了请求，LoadBalancer 轮询分发成立。

## 四、测试

三层测试，全部可重复：

1. **Feign 错误处理单元测试**（`PetClientErrorTranslationTest`）：mock Feign 接口抛 404 / 500 / 连接拒绝，断言分别翻译为 404 / 503 / 503，空响应体不抛异常。
2. **业务规则与 API 测试**（`AppointmentServiceTest` 15 个 + `AppointmentApiTest` 5 个）：时段冲突 409、每日上限 409、宠物 404、依赖 503、成功 201，全用 `@MockitoBean` 桩，不起 Nacos 不发真实网络请求。
3. **端到端脚本**（`scripts/e2e-test.sh`，六段 PASS）：多实例栈 → 双实例注册 → 跨服务 201 → 负载均衡双实例日志 → **模拟依赖不可用**（`docker compose stop pet-service`，等 Nacos 实例数归零）→ 依赖恢复后 201。

**失败语义（重点）**：依赖不可用时创建预约返回 `503` 且错误体为结构化 JSON，**预约表零新增**（脚本比对停机前后记录数相等）——调用失败不会被当成业务成功。同时如实说明已知局限：**写操作本身超时/断连时**，客户端看到失败但数据库可能已提交，结果未知；本脚本验证的是「校验失败绝不写库」语义（校验全部在写库之前，依赖不可用根本走不到写库那一步）。

**结果**：单元测试 22/22 通过；e2e 最终 `PASS=15 FAIL=0`。

## 截图（docs/homework/week-08/screenshots/）

| 文件 | 证明内容 |
|---|---|
| 01-instances-nacos.png | Nacos 控制台 pet-service 2 个健康实例 |
| 02-create-201.png | 跨服务创建预约 201，响应含远端宠物/医生名 |
| 03-lb-two-instances.png | 两个实例日志均出现 `[LB]` 处理记录 |
| 04-feign-timeout-config.png | OpenFeign 超时配置与客户端代码 |
| 04b-openfeign-yml.png | application.yml 中 openfeign 超时配置 |
| 05-fail-503-zero-write.png | 依赖下线 → 503 结构化错误 + 零写入 |
| 06-recover-201.png | 实例恢复重新注册 → 再次创建 201 |
| 07-unit-tests.png | appointment-service 22 个单测全过 |
