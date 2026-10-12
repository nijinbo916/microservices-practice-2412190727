# 第 4 周作业：单体应用与数据库持久化

- **姓名**：倪金博
- **学号**：2412190727
- **日期**：2026-10-12
- **项目**：萌宠诊所 · 宠物医院管理系统（沿用第 02 周选题）
- **基础包名**：`com.zjgsu.njb`（继续沿用第 03 周）
- **本周范围**：业务建模 + 分层实现（model / repository / service / controller）+ MySQL 持久化 + 完整测试

---

## 一、业务建模

### 1.1 本周实现的核心业务流程

**「宠物主人为宠物预约就诊」**（就诊链的入口段）：

```
主人提交预约 → 校验时段合法 → 校验号源不冲突 → 校验每日号源上限
            → 创建预约(BOOKED) → 可查询/可取消(释放号源)
```

流程细节与图示见 [project-proposal.md §3.1](../../project-proposal.md)。

### 1.2 业务实体与关系

| 实体 | 角色 | 关系 |
| --- | --- | --- |
| `Pet` 宠物档案 | 流程主体：谁来看病 | 1 只宠物 → N 条预约 |
| `Doctor` 医生 | 号源资源：谁来接诊、每天放多少号 | 1 位医生 → N 条预约 |
| `Appointment` 预约单 | 流程载体：绑定宠物 + 医生 + 时段 | 多对一 → Pet / Doctor |

### 1.3 Service 中的业务规则（4 条）

| # | 规则 | 实现 | 触发结果 |
| --- | --- | --- | --- |
| 1 | 结束时间必须晚于开始时间 | `AppointmentService#createAppointment` 入口判断 | 400 |
| 2 | 预约开始时间必须在未来 | `@Future` 参数校验 + 规则一 | 400 |
| 3 | **同一医生同一时段不可重复预约（防号源超卖）** | `AppointmentRepository#countOverlapping` 区间重叠统计 | 409 |
| 4 | **某医生当日有效预约数不超过 `dailyCapacity`** | `AppointmentRepository#countByDoctorOnDay` | 409 |
| 5 | 已取消/已完成的预约不能再次取消 | `cancelAppointment` 状态机校验 | 409 |

> **为什么规则写在 Service 而不是数据库唯一约束？**
> 「时段重叠」是区间比较（`已有.start < 新.end AND 已有.end > 新.start`）而非等值比较，
> 唯一索引表达不了；且已取消的预约应释放号源，唯一约束无法感知状态。

## 二、单体应用实现

### 2.1 分层结构

```
monolith/src/main/java/com/zjgsu/njb/
├── domain/        实体层：Pet / Doctor / Appointment / AppointmentStatus（JPA 映射）
├── dto/           传输层：CreatePetRequest / CreateAppointmentRequest / *Response（record）
├── repository/    数据访问层：PetRepository / DoctorRepository / AppointmentRepository
├── service/       业务层：PetService / DoctorService / AppointmentService（业务规则在此）
├── web/           接口层：PetController / DoctorController / AppointmentController
├── exception/     全局异常：BusinessRuleException→409、ResourceNotFound→404、校验失败→400
└── MonolithApplication.java
```

Controller 只做 HTTP 输入输出转换，业务判断与数据访问全部在 Service / Repository，分层职责清晰。

### 2.2 REST 接口清单（9 个，均有真实运行验证）

| 方法 | 路径 | 说明 | 成功 | 失败 |
| --- | --- | --- | --- | --- |
| POST | `/api/pets` | 宠物建档 | 201 | 400 |
| GET | `/api/pets` | 宠物列表（可按主人/物种过滤） | 200 | — |
| GET | `/api/pets/{id}` | 宠物详情 | 200 | 404 |
| GET | `/api/doctors` | 医生列表 | 200 | — |
| GET | `/api/doctors/{id}` | 医生详情 | 200 | 404 |
| POST | `/api/appointments` | **创建预约（业务规则校验）** | 201 | 400 / 404 / 409 |
| GET | `/api/appointments` | 预约列表（可按 petId/doctorId 过滤） | 200 | — |
| GET | `/api/appointments/{id}` | 预约详情 | 200 | 404 |
| POST | `/api/appointments/{id}/cancel` | 取消预约（释放号源） | 200 | 404 / 409 |

### 2.3 运行验证结果（H2 profile，11 个场景全部符合预期）

启动：`./mvnw spring-boot:run -Dspring-boot.run.profiles=h2`

| 场景 | 期望 | 实测 |
| --- | --- | --- |
| `GET /actuator/health` | 200 UP（含 db 组件） | ✅ 200，`"db":{"database":"H2"},"status":"UP"` |
| `POST /api/pets` 建档 | 201 + Location | ✅ `Location: /api/pets/1` |
| `GET /api/pets/1` | 200 字段完整 | ✅ |
| `GET /api/doctors` | 200，3 名种子医生 | ✅ 李医生(内科,4)/王医生(外科,3)/赵医生(影像,6) |
| `POST /api/appointments` 创建预约 | 201，状态 BOOKED | ✅ |
| 同一医生同时段重复预约 | **409 号源冲突** | ✅ `BUSINESS_RULE_VIOLATION: 号源冲突：医生「李医生」在…已有预约` |
| 预约时间设为过去 | 400 | ✅ `VALIDATION_FAILED: slotStart: 预约开始时间必须晚于当前时间` |
| `GET /api/appointments?petId=1` | 200 | ✅ |
| `POST /api/appointments/1/cancel` | 200，状态 CANCELED | ✅ |
| 重复取消 | 409 | ✅ |
| 查询不存在宠物 | 404 | ✅ `NOT_FOUND: 宠物不存在：id=999999` |
| 取消后同时段重新预约 | 201（号源已释放） | ✅ |

完整命令与响应见截图 `09~11`。

## 三、数据库持久化

### 3.1 表设计

3 张表 `pet` / `doctor` / `appointment`，主键自增，`appointment` 通过外键指向 `pet.id` 与 `doctor.id`。
完整字段、约束、索引与建表 DDL 见 [docs/database.md](../../database.md)。

关键约束：

- `appointment.pet_id` / `doctor_id`：`NOT NULL` 外键 —— 不存在脱离宠物或医生的孤立预约
- `status` 存枚举字符串（可读性优先）
- 索引 `idx_appointment_doctor_slot (doctor_id, slot_start, slot_end)` 支撑号源重叠检测

### 3.2 JPA Repository

三个接口继承 `JpaRepository` 获得基础 CRUD；`AppointmentRepository` 额外用 `@Query` 手写两条业务查询：

```java
// 号源重叠统计（防超卖的核心查询）
select count(a) from Appointment a
where a.doctor.id = :doctorId and a.status in :statuses
  and a.slotStart < :slotEnd and a.slotEnd > :slotStart
```

### 3.3 MySQL 接入配置

`application.yml`（默认 profile）已接入 MySQL：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/petclinic?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
  jpa:
    hibernate:
      ddl-auto: update
```

本机开发环境暂未安装 MySQL 服务，因此本次运行验证使用 H2 内存库（`h2` profile）完成；
数据库表结构、种子数据与读写逻辑完全一致（由 Hibernate 按同一套实体生成）。
本机 MySQL 的安装、建库与连接步骤已写入 README「运行说明」。

### 3.4 测试数据准备（可重复执行）

`monolith/src/main/resources/data.sql` 提供 3 名医生种子数据：

- H2（测试/演示）：内嵌库默认自动执行，每次重建表后重新灌入 → 天然可重复
- MySQL：`mysql -u root -p petclinic < monolith/src/main/resources/data.sql` 手工执行一次

测试配置（`src/test/resources/application-test.yml`）中 `defer-datasource-initialization: true`
保证 data.sql 在 Hibernate 建表之后执行。

## 四、测试与验证

### 4.1 测试矩阵（3 层全覆盖）

| 测试类 | 层次 | 用例数 | 覆盖内容 |
| --- | --- | --- | --- |
| `PetRepositoryTest` | Repository | 3 | save→findById 回读、count/delete、按主人姓名模糊查询 |
| `AppointmentRepositoryTest` | Repository | 4 | 关联落库、时段重叠统计（含边界：首尾相接不冲突）、取消释放号源、按日统计 |
| `AppointmentServiceTest` | Service | 7 | 4 条业务规则逐条验证 + 宠物/医生不存在 |
| `PetAppointmentApiTest` | API（真实 HTTP） | 7 | 201/200/400/404/409 正常与错误响应 |
| `HelloControllerTest` + `MonolithApplicationTests` | 基础 | 3 | 第 03 周回归 |

### 4.2 执行结果

```console
$ cd monolith
$ ./mvnw test
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS   （Total time: 17.6 s，H2 内存库，可重复运行）
```

测试不依赖本机 MySQL（test profile 自动切换 H2），在任何机器上克隆后即可 `./mvnw test` 复现。

## 五、本周完成情况对照

| 作业要求 | 状态 |
| --- | --- |
| 更新 `docs/project-proposal.md`（流程/实体/规则） | ✅ |
| 至少 2 个业务模型 | ✅ 3 个（Pet / Doctor / Appointment） |
| 至少 1 个 Service | ✅ 3 个 |
| 至少 4 个 `/api` 接口（含查询与创建） | ✅ 9 个 |
| 至少 1 条选题相关业务规则（Service 实现） | ✅ 4 条 |
| Controller 不含数据访问逻辑 | ✅ 全部委托 Service |
| `docs/database.md`（≥2 张表关系/字段/主键/约束） | ✅ 3 张表 + DDL + 索引设计 |
| Spring Data JPA，数据访问在 repository 包 | ✅ |
| DTO/请求对象承载 API 输入 | ✅ record + Validation 注解 |
| 可重复执行的测试数据准备（data.sql） | ✅ |
| README 说明 MySQL 安装/建库/连接 | ✅ |
| Repository / Service / API 测试可重复通过 | ✅ 24/24 |

## 六、截图索引

见 [screenshots/](screenshots/)：

| 文件 | 内容 |
| --- | --- |
| `01-project-structure.png` | 分层源码结构 |
| `02-entity-pet.png` / `03-entity-appointment.png` / `04-entity-status-enum.png` | 实体与 JPA 映射 |
| `05-repository.png` | Repository + @Query 业务查询 |
| `06-service-business-rule.png` | Service 业务规则实现 |
| `07-controller-appointment.png` | Controller 接口定义 |
| `08-dto-request.png` | DTO 请求对象与校验注解 |
| `09-api-run-create.png` | 启动 + 建档 + 创建预约（201） |
| `10-api-run-business-rules.png` | 业务规则响应（409/400/404） |
| `11-api-run-query-cancel.png` | 医生列表/按宠物查询/取消/号源释放 |
| `12-mvnw-test.png` | `./mvnw test` 24 个测试全部通过 |
| `13-database-schema.png` | Hibernate 自动建表 DDL（真实启动日志） |
| `14-seed-data.png` | data.sql 种子数据与入库结果 |

## 七、下一步计划

1. 第 05 周：医生接诊 → 电子病历（`MedicalRecord`）与处方（`Prescription`），打通就诊链后半段。
2. 认证授权：主人/医生/管理员 JWT 登录与 RBAC。
3. 服务拆分准备：把预约、档案、收费按模块边界整理为独立部署单元。
