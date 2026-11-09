# 第 08 周作业 · 提交文案（按小题）

> 仓库：https://github.com/nijinbo916/microservices-practice-2412190727
> 截图存放：`docs/homework/week-08/screenshots/`（共 8 张，PNG）

---

## 小题 1 · 选择一个跨服务业务流程（≥50 字符）

**完成情况**：选择「创建预约时查询宠物和医生信息」流程：appointment-service 作为调用方，创建预约前通过 OpenFeign 按服务名调用 pet-service 校验宠物存在性、调用 doctor-service 校验医生并获取每日号源上限（dailyCapacity），任一校验失败（宠物不存在 404 / 医生不存在 404 / 依赖不可用 503）都在写库之前中止，跨服务关联只保存对方 ID，不复制对方数据。

**关键结论**：校验全部前置到写库之前，依赖故障时预约表零写入；响应中的 petName / doctorName 由被调用方实时返回（e2e 断言「不复制数据」，见截图 02）。

- 调用链路图：`docs/service-communication.md`
- 记录文档：`docs/homework/week-08/index.md` 第一节
- 证明截图：`02-create-201.png`

## 小题 2 · 使用 OpenFeign（≥20 字符）

**完成结果**：appointment-service 引入 `spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer`，`@EnableFeignClients` 扫描 remote 包；`PetFeignClient` / `DoctorFeignClient` 两个 @FeignClient 接口均以服务名（`name = "pet-service"` / `"doctor-service"`）声明，不写死 IP，路径与原 HTTP 契约一致。失败处理：Feign 异常翻译为业务语义——404 → `ResourceNotFoundException`（「宠物不存在/医生不存在」），5xx / 连接失败 / 无可用实例 → `DependencyUnavailableException` → 全局处理器返回 503 结构化 JSON。超时连接 3s / 读取 5s（yml 配置）。原手写 RestTemplate 寻址组件（ServiceLookup、RemoteHttpConfig）已删除。

**验证方法**：22 个单元测试中 8 个直接覆盖 Feign 错误翻译（Mockito 打桩 404/500/null）；e2e 第 3 段真实创建预约 201，响应含被调用方实时返回的宠物名与医生名。

- 代码：`services/appointment-service/src/main/java/com/zjgsu/njb/appointment/remote/`
- 证明截图：`04-feign-timeout-config.png`、`04b-openfeign-yml.png`

## 小题 3 · 负载均衡验证（≥20 字符）

**完成结果**：`docker compose -f compose.yaml -f compose.lb.yaml up -d --scale pet-service=2` 启动两个 pet-service 实例（compose.lb.yaml 用 `!reset` 清掉单实例模式的固定宿主机端口与 container_name，两实例均不绑定宿主机端口，容器网络内自由寻址）。pet-service 内置 `InstanceTagFilter`：给每个响应加 `X-Instance-Id` 头（容器 ID 前 12 位）并在日志打印 `[LB] instance=xxx GET /api/pets/1`。调用方多次发起请求，e2e 收集实际参与过服务的实例标识，断言两个实例都出现过。

**验证方法**：e2e 第 4 段 `PASS：两个 pet-service 实例都实际处理了调用方请求（共 2 个实例标识）`，标识为 `instance=09627aac92fe` 与 `instance=666ca0db5efc`——证明 Spring Cloud LoadBalancer 真实在两实例间分发流量，而非仅展示注册列表。

- 编排：`compose.lb.yaml`（多实例覆盖文件）
- 证明截图：`01-instances-nacos.png`（Nacos 健康实例数=2）、`03-lb-two-instances.png`

## 小题 4 · 测试（≥20 字符）

**完成结果**：两层测试。① 单元测试 22 个（appointment-service）：service 层业务规则 + Feign 错误翻译（404→业务 404、500/超时→503、空响应），全部通过；② 端到端 `scripts/e2e-test.sh`（仓库根目录可运行）：健康等待 → 种子数据（幂等，utf8mb4）→ 跨服务创建 201 → 负载均衡双实例标识断言 → 依赖下线 503 且预约表零新增（直查数据库比对记录数 26→26）→ 实例恢复重新注册 → 再次创建 201，最终 `PASS=15 FAIL=0`。

**验证方法与已知局限**：模拟依赖不可用的方式是 `docker compose stop` pet-service 两实例并等待 Nacos 摘除实例，确认 503 + 零写入。如实说明局限：**写操作本身**在超时/断连时结果未知（客户端收到失败但数据库可能已提交）；本作业验证的语义是「校验失败绝不写库」——校验全部位于写库之前，依赖不可用时根本走不到写库步骤。

- 脚本：`scripts/e2e-test.sh`（仓库根目录 `bash scripts/e2e-test.sh`）
- 证明截图：`05-fail-503-zero-write.png`、`06-recover-201.png`、`07-unit-tests.png`

---

## 图片上传对照表（作业系统按小题上传）

| 小题 | 上传截图（按顺序） |
|---|---|
| 2 使用 OpenFeign | 04-feign-timeout-config.png、04b-openfeign-yml.png |
| 3 负载均衡验证 | 01-instances-nacos.png、03-lb-two-instances.png |
| 4 测试 | 05-fail-503-zero-write.png、06-recover-201.png、07-unit-tests.png |
| 1 业务流程 | 02-create-201.png |
