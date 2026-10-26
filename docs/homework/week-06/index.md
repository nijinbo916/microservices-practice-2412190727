# 第 6 周作业：微服务拆分

- **姓名**：倪金博　**学号**：2412190727
- **日期**：2026-10-26
- **单体版本**：`monolith/`（原样保留）｜ **微服务版本**：`services/`
- **服务清单**：pet-service（8084）、doctor-service（8082）、appointment-service（8083）
- **设计文档**：[docs/microservices-design.md](../../microservices-design.md)

---

## 一、服务拆分设计

> 详细边界论证、数据归属表、调用关系与「远端成功、本地失败」窗口分析见
> [docs/microservices-design.md](../../microservices-design.md)，此处记录实现思路与结果。

**实现思路**：按数据归属而非技术分层切分。单体的三个内聚模块各自升级为独立服务：

| 服务 | 端口 | 独立数据库 | 数据表 | 职责 |
| --- | --- | --- | --- | --- |
| pet-service | 8084 | `petclinic_pet` | `pet` | 宠物档案 CRUD，宠物数据唯一权威来源 |
| doctor-service | 8082 | `petclinic_doctor` | `doctor` | 医生主数据与每日号源上限（幂等种子 3 名医生） |
| appointment-service | 8083 | `petclinic_appointment` | `appointment` | 预约流程与号源规则；创建前经 HTTP 校验宠物/医生 |

**关键结论**：

1. 预约表的 `pet_id` / `doctor_id` 改为普通 BIGINT 列（逻辑外键），
   **没有任何跨库外键、跨库 JOIN，也不调用其他服务的 Repository**；
   宠物/医生的详细信息只通过 HTTP API 获取。
2. 三个服务共用一个 MySQL 8.4.11 实例，但**库与账号完全隔离**
   （`docker/mysql-init/01-init-databases.sh` 建三库三账号，各自只能读写自己的库）。
3. 跨服务调用用 RestTemplate：创建预约强依赖两个服务，查询展示弱依赖（可降级）。

架构图（Mermaid）与拆分映射见设计文档第四节。

## 二、代码拆分

**实现思路**：每个服务是独立 Spring Boot 4.0.8 / Java 25 工程
（独立 `pom.xml`、`src/`、`Dockerfile`、测试），基础包名延续 `com.zjgsu.njb.<service>`。

```
services/
├── pet-service/          # controller / service / repository / domain / dto / exception 各 1 组
├── doctor-service/       # 同上；种子数据 data.sql（H2 测试）+ db/seed-mysql.sql（容器幂等）
└── appointment-service/  # 同上 + remote/（PetClient、DoctorClient）+ config/（RestTemplate 超时）
```

**跨服务调用（预约前校验宠物/医生）**：

- 服务地址写在 `application.yml`（`pet.base-url` / `doctor.base-url`），
  可用环境变量 `PET_SERVICE_URL` / `DOCTOR_SERVICE_URL` 覆盖（compose 注入 `http://pet-service:8080` 等）；
- RestTemplate 配置连接超时 3s、读取超时 5s（`config/RemoteHttpConfig`）；
- **失败语义**：远端 404 → 返回 404「宠物不存在（pet-service 校验返回 404）」；
  远端 5xx / 连接失败 / 超时 → 返回 503 `DEPENDENCY_UNAVAILABLE`；
  两种情况都**不会写入本地任何数据**（有专门的单测断言 `appointmentRepository.count()==0`）。

**运行命令**（单个服务本地跑，无需 Docker；默认连本机 MySQL，也可用 H2 profile）：

```bash
cd services/pet-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2   # 其余同理
```

**测试命令**（在 README「运行说明」中同样说明）：

```bash
cd services/pet-service         && ./mvnw test    # 6 个用例
cd services/doctor-service      && ./mvnw test    # 5 个用例
cd services/appointment-service && ./mvnw test    # 11 个用例（含 MockRestServiceServer 模拟远端）
```

**结果**：三个服务 22 个测试全部通过（`Tests run: 22, Failures: 0, Errors: 0`）。
测试覆盖：建档/查询 API、404 语义、医生种子数据、跨服务创建预约成功流、
宠物不存在中止写入、依赖 500 中止写入、号源冲突 409、重复取消 409。

## 三、本地编排

**实现思路**：根目录 `compose.yaml` 一键启动 mysql + 三个微服务：

- 每个服务独立构建上下文 `./services/<服务名>`，容器内统一监听 8080，宿主机映射 8084/8082/8083；
- MySQL 单实例 + 初始化脚本建 `petclinic_pet` / `petclinic_doctor` / `petclinic_appointment`
  三库与 `pet_user` / `doctor_user` / `appointment_user` 三账号（最小权限，互相访问不了别人的库）；
- 服务间通过 Compose 服务名通信：appointment-service 的环境变量注入
  `PET_SERVICE_URL=http://pet-service:8080`、`DOCTOR_SERVICE_URL=http://doctor-service:8080`，
  **不使用 localhost**；
- 命名数据卷 `petclinic-ms-mysql-data` 保存数据；三个应用容器均配健康检查；
  配置全部走环境变量（`.env.example` 提供模板，真实 `.env` 已 gitignore）。

**运行命令与结果**：

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

实测结果：`petclinic-mysql`、`petclinic-pet-service`、`petclinic-doctor-service`、
`petclinic-appointment-service` 四个容器全部 `Up (healthy)`，
（真实输出见 screenshots/ 的 compose-up 与 compose-ps 截图）。

## 四、验证

**脚本**：`scripts/smoke-test.sh`，从仓库根目录运行：

```bash
bash scripts/smoke-test.sh                          # 基础验证
bash scripts/smoke-test.sh --with-dependency-down   # 额外执行依赖停机场景
```

脚本内容：① 三个服务健康检查（`/actuator/health` 返回 UP）；
② doctor-service 医生列表 ≥ 3；③ pet-service 建档（201，取回 id）；
④ appointment-service 创建预约（201，且响应中宠物名/医生名来自远端 HTTP 查询）；
⑤ 查询预约详情（200，字段一致）；⑥ 失败场景 A：预约不存在的宠物（petId=999999）→ 404，
本地不写数据；⑦（可选）失败场景 B：`docker compose stop pet-service` 后创建预约 → 503
`DEPENDENCY_UNAVAILABLE`，随后自动重启 pet-service。

**完整跨服务流程实测**（请求、状态码、数据变化）：

| 步骤 | 请求 | 状态码 | 数据变化 |
| --- | --- | --- | --- |
| 建档 | `POST pet:8084/api/pets`（冒烟宠物+时间戳/犬/倪先生） | 201 | `petclinic_pet.pet` +1 行 |
| 建预约 | `POST appt:8083/api/appointments`（petId=新建 id，doctorId=1） | 201 | `petclinic_appointment.appointment` +1 行，仅存 pet_id/doctor_id |
| 查详情 | `GET appt:8083/api/appointments/{id}` | 200 | 无变化（petName/doctorName 来自两次 HTTP 查询） |
| 宠物不存在 | `POST appt:8083/api/appointments`（petId=999999） | 404 | **无任何写入** |
| pet-service 停机 | 同上（petId=有效 id） | 503 | **无任何写入** |

**脚本实测结果**：`PASS=13 FAIL=0`（含 `--with-dependency-down` 场景）。
再用 `mysql` 客户端直查三库复核：`petclinic_pet.pet` 1 行、`petclinic_doctor.doctor` 3 行（种子）、
`petclinic_appointment.appointment` 仅 1 行 —— 两个失败场景确实**没有写入任何数据**（见截图 14-db-isolation）。

**「远端成功、本地失败」窗口说明**（详细分析见设计文档第五节）：
创建预约是「HTTP 校验远端 → 写本地库」的两段操作，存在两个非原子窗口 ——
① 校验成功后、本地提交前崩溃：远端无副作用，重试安全；
② 本地提交成功后、响应返回前断连：客户端误以为失败而重试，第二次会命中「号源冲突 409」
或产生重复预约。这是典型的本地已成功、调用方感知失败的窗口；
本周按课程要求不做原子事务/补偿/消息机制，演进方向为幂等键或本地消息表。

## 五、截图索引

见 [screenshots/](screenshots/)：

| 提交项 | 截图 |
| --- | --- |
| 1. 服务拆分设计 | 01-design-doc（设计文档）、02-design-mermaid（架构图渲染） |
| 2. 代码拆分 | 03-services-tree（目录结构）、04-tests-pass（22 个测试）、05-remote-client（跨服务客户端代码）、06-appointment-code（预约服务代码） |
| 3. 本地编排 | 07-compose-file（compose.yaml）、08-compose-up（一键启动）、09-compose-ps（四容器 healthy） |
| 4. 验证 | 10-smoke-success（冒烟测试通过）、11-flow-create（跨服务预约成功）、12-fail-missing-pet（404）、13-fail-dependency-down（503）、14-db-isolation（三库隔离+失败未写库） |

## 六、遇到的问题与解决

1. **Spring Boot 4 的 `@AutoConfigureMockMvc` 换包了**：
   从 `org.springframework.boot.test.autoconfigure.web.servlet` 移到
   `org.springframework.boot.webmvc.test.autoconfigure`，且需要新增
   `spring-boot-starter-webmvc-test` 测试依赖（Boot 4 模块化拆分所致）。
2. **H2 测试数据串扰**：同一 Spring 上下文内多个测试共享内存库，
   「按主人查询」断言被前面用例的数据干扰 → 改用 `System.nanoTime()` 生成唯一主人名。
3. **数据库账号只在首次初始化时创建**：MySQL 官方镜像只在数据卷为空时执行初始化脚本，
   之后改 `.env` 密码不会生效 —— 已在 `.env.example` 注释中说明。
