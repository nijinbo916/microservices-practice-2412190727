# 第 5 周作业：Docker 容器化

- **姓名**：倪金博
- **学号**：2412190727
- **日期**：2026-10-19
- **项目**：萌宠诊所 · 宠物医院管理系统（单体工程 `monolith/`）
- **镜像名**：`petclinic-monolith:week05`（565.5 MB）
- **数据库镜像**：`mysql:8.4.11`

---

## 一、本周完成情况对照

| 评分项 | 分值 | 要求 | 完成情况 |
| --- | --- | --- | --- |
| Dockerfile 与镜像构建 | 25% | 多阶段构建成功，忽略规则合理 | ✅ 两阶段（maven → jre），构建成功，镜像 565.5 MB |
| Compose 编排与启动 | 30% | 一键启动，配置完整 | ✅ `docker compose up -d --build` 一次成功，双服务 healthy |
| 业务 API 验证 | 20% | 个人业务接口可访问 | ✅ 健康检查 + 建档(201) + 预约(201) + 查询(200) + 规则(409) |
| 数据持久化验证 | 15% | 容器重建后数据仍在 | ✅ down 后 up，同一条记录 id 与 createdAt 完全不变 |
| 文档与证明材料 | 10% | 命令可执行、证据完整 | ✅ README 第五章命令 + 本记录 + 16 张截图 |

---

## 二、Dockerfile 与镜像构建

### 2.1 多阶段构建的设计

文件位置：`monolith/Dockerfile`

```dockerfile
# 阶段一：build —— 用 Maven 镜像编译打包
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests clean package

# 阶段二：runtime —— 只用 JRE 运行
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN groupadd --system --gid 1001 appuser \
 && useradd --system --uid 1001 --gid appuser --home-dir /app appuser
COPY --from=build --chown=appuser:appuser \
     /workspace/target/monolith-0.0.1-SNAPSHOT.jar /app/app.jar
USER appuser
EXPOSE 8080
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
```

**两个阶段的分工与作用：**

1. **build 阶段**（`maven:3.9-eclipse-temurin-25`）：镜像自带 Maven 与 JDK 25，负责把源码编译成可执行 JAR。
   构建过程**完全在容器内完成**，宿主机不需要装 Java、Maven，也不需要 `mvnw` 下载 Maven 发行包。
2. **runtime 阶段**（`eclipse-temurin:25-jre`）：只通过 `COPY --from=build` 取走最终 JAR，
   Maven、JDK、源码、`~/.m2` 依赖缓存全部被丢弃，因此最终镜像只有 JRE + 一个 JAR，
   体积小、攻击面小。`docker history` 可以直接印证：runtime 阶段最大的层只有那个 62.2 MB 的 JAR。

**几个细节：**

- `COPY pom.xml` 单独一层并执行 `dependency:go-offline`：依赖不变时改源码可命中缓存，不必重新下载几百 MB 依赖。
- `RUN mvn ... -DskipTests`：构建镜像时跳过测试（第 04 周已验证 24 个用例），缩短构建时间。
- 创建 `appuser` 并 `USER appuser`：容器内进程不以 root 运行。
- `ENTRYPOINT` 用 `sh -c "exec java ..."`：`exec` 让 java 成为 PID 1，能正确接收 `docker stop` 的 SIGTERM 优雅关闭。

### 2.2 .dockerignore

文件位置：`monolith/.dockerignore`

| 排除内容 | 原因 |
| --- | --- |
| `target/`、`**/target/`、`*.jar`、`*.class` | 本地构建产物，镜像内会重新编译；打进去既浪费上下文又可能带入旧包 |
| `.git/`、`.github/` | 版本历史与 CI 配置，构建用不到 |
| `.idea/`、`.vscode/`、`*.iml` 等 | IDE 配置属于个人环境 |
| `.env`、`*.env`、`*.pem`、`*.key`、`secrets/` | **密码与密钥绝不能进镜像**（镜像层可被任何人 `docker save` 导出） |
| `.mvn/`、`mvnw`、`mvnw.cmd` | 构建用镜像内的 `mvn`，不需要 Wrapper |
| `docs/`、`*.md`、`*.log` | 文档与日志和运行无关 |

### 2.3 构建命令与结果

在**仓库根目录**执行（构建上下文指向 `./monolith`）：

```bash
docker build -t petclinic-monolith:week05 ./monolith
```

实际构建输出（关键步骤）：

```
[build]   FROM docker.io/library/maven:3.9-eclipse-temurin-25
[build]   WORKDIR /workspace
[build]   COPY docker/maven-settings.xml /root/.m2/settings.xml
[build]   COPY pom.xml ./
[build]   RUN mvn -B -DskipTests dependency:go-offline
                [INFO] BUILD SUCCESS                    (#15  58.26 s)
[build]   COPY src ./src
[build]   RUN mvn -B -DskipTests clean package
                [INFO] BUILD SUCCESS                    (#17  11.70 s)
[runtime] FROM docker.io/library/eclipse-temurin:25-jre
[runtime] WORKDIR /app
[runtime] RUN groupadd ... appuser
[runtime] COPY --from=build /workspace/target/monolith-0.0.1-SNAPSHOT.jar /app/app.jar
#19 naming to docker.io/library/petclinic-monolith:week05
```

两次 `BUILD SUCCESS` 分别对应「依赖预下载」与「编译打包」两个阶段，构建一次通过。

### 2.4 镜像查看

```bash
docker images petclinic-monolith
docker image inspect petclinic-monolith:week05 --format '{{.Size}}'
```

| 项 | 值 |
| --- | --- |
| 镜像 ID | `cf04c01ecf8d` |
| 镜像大小 | 565.5 MB（`docker images` 显示磁盘占用 593 MB / 内容 173 MB） |
| 运行阶段最大层 | 62.2 MB（`COPY` 进来的应用 JAR） |
| 运行用户 | `appuser`（非 root） |

---

## 三、Compose 编排与启动

### 3.1 服务拓扑

```
                     宿主机
          ┌──────────────┴───────────────┐
          │ :8081                        │ :3307
     ┌────▼─────┐                  ┌─────▼──────┐
     │   app    │ ── petclinic-net ──│   mysql    │
     │ (JRE 25) │  按服务名 mysql 连接│  (8.4.11)  │
     └──────────┘                  └─────┬──────┘
                                         │
                              ┌──────────▼───────────┐
                              │ 命名卷 petclinic-mysql-data │
                              └──────────────────────┘
```

> 端口用 8081/3307 而不是 8080/3306：本机 8080、3306 已被此前课内练习的
> `course-app` / `course-mysql` 容器占用。只需改 `.env` 里的两行即可，无需改动任何配置文件——
> 这正是「端口也走环境变量」的好处。

### 3.2 配置与要求的对应关系

| 作业要求 | compose.yaml 中的实现 |
| --- | --- |
| 至少包含 app 与 mysql 8.4.11 | `app`（build ./monolith）+ `mysql`（`image: mysql:8.4.11`） |
| 应用构建上下文为 ./monolith | `build.context: ./monolith`、`build.dockerfile: Dockerfile` |
| 端口映射 | `app: ${APP_PORT:-8080}:8080`、`mysql: ${MYSQL_PORT:-3306}:3306` |
| 共享网络 | 自定义 bridge 网络 `petclinic-net`，两个服务都加入 |
| 应用通过服务名连 MySQL | `DB_URL=jdbc:mysql://mysql:3306/petclinic?...`，`mysql` 由 Compose 内建 DNS 解析 |
| 命名数据卷保存 MySQL 数据 | `volumes: mysql-data:/var/lib/mysql`，卷名 `petclinic-mysql-data` |
| 环境变量配置连接地址/账号/密码 | `${MYSQL_*}`、`${DB_URL}`、`${DB_USERNAME}`、`${DB_PASSWORD}` 全部来自 `.env` |
| 提供 .env.example，.env 加入 .gitignore | 根目录 `.env.example`；根目录 `.gitignore` 含 `.env`、`.env.*`（`!.env.example` 例外） |
| MySQL 健康检查 | `mysqladmin ping`，`interval 10s / timeout 5s / retries 10 / start_period 30s` |
| 应用等待数据库健康后启动 | `depends_on.mysql.condition: service_healthy` |
| 不使用废弃的 version 字段 | 文件首行即为 `name: petclinic`，没有 `version:` |

应用容器也配置了健康检查（`/dev/tcp` 探测 8080 端口），`docker compose ps` 中两个服务均显示 `(healthy)`。

### 3.3 环境变量说明

`.env.example` 中的变量分三类：

| 类别 | 变量 | 说明 |
| --- | --- | --- |
| 数据库初始化 | `MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD` | 仅在**首次创建数据卷**时由 MySQL 镜像执行；之后再改不会影响已有数据卷 |
| 应用连接 | `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | 传给 Spring Boot，覆盖 `application-docker.yml` 里的默认值 |
| 端口映射 | `APP_PORT`、`MYSQL_PORT` | 避免与宿主机已占用端口冲突 |

实际使用的 `.env`（本机）：`APP_PORT=8081`、`MYSQL_PORT=3307`，其余与模板一致。

### 3.4 启动命令与结果

```bash
cp .env.example .env        # 准备配置（首次）
docker compose up -d --build
```

实际输出（关键行）：

```
Image petclinic-monolith:week05 Built
Volume petclinic-mysql-data Creating / Created
Network petclinic_petclinic-net Creating / Created
Container petclinic-mysql Creating / Created / Starting / Started
Container petclinic-mysql Waiting / Healthy          ← 健康检查通过后 app 才启动
Container petclinic-app  Creating / Created / Starting / Started
```

`docker compose ps`：

```
NAME              STATUS                        PORTS
petclinic-app     Up About a minute (healthy)   0.0.0.0:8081->8080/tcp
petclinic-mysql   Up About a minute (healthy)   0.0.0.0:3307->3306/tcp
```

应用启动日志关键行（`docker compose logs app`）：

```
The following 1 profile is active: "docker"
... HikariPool-1 - Start completed.
Database JDBC URL [jdbc:mysql://mysql:3306/petclinic?...]   ← 服务名解析成功
Database version: 8.4.11
Tomcat started on port 8080 (http) with context path '/'
Started MonolithApplication in 11.693 seconds (process running for 13.727)
```

---

## 四、业务 API 验证

容器把应用 8080 端口映射到宿主机 **8081**，以下请求全部发往 `http://localhost:8081`，
验证的是**容器化之后**的第 04 周真实业务实现。

### 4.1 健康检查

```bash
curl -s http://localhost:8081/actuator/health
```

结果：`"status":"UP"`，且 `components.db` 显示 `"database":"MySQL"`，
说明健康检查已经覆盖到数据库连接（不是只有进程活着）。

### 4.2 创建宠物（POST /api/pets）→ 201

```bash
curl -i -X POST http://localhost:8081/api/pets \
  -H "Content-Type: application/json" \
  -d '{"name":"豆豆","species":"犬","breed":"金毛","ownerName":"倪先生","ownerPhone":"13800000001","birthday":"2021-05-20","weightKg":28.50,"allergyHistory":"青霉素过敏"}'
```

响应：`HTTP 201`，`{"id":1,"name":"豆豆",...,"createdAt":"2026-10-19T20:12:32.607248458"}`，
中文字段在容器化的 MySQL 中读写均正常。

### 4.3 创建预约（POST /api/appointments）→ 201

```bash
curl -i -X POST http://localhost:8081/api/appointments \
  -H "Content-Type: application/json" \
  -d '{"petId":1,"doctorId":1,"slotStart":"2026-11-02T10:00:00","slotEnd":"2026-11-02T10:30:00","reason":"食欲不振，需要检查"}'
```

响应：`HTTP 201`，`{"id":1,"petName":"豆豆","doctorName":"李医生","status":"BOOKED",...}`。
医生列表来自容器首次启动时灌入的种子数据（3 名医生）。

### 4.4 查询预约（GET /api/appointments?petId=1 与 GET /api/appointments/1）→ 200

按宠物查询返回 1 条预约，详情接口返回同一条记录，字段与创建时一致。

### 4.5 业务规则仍然生效（同医生交叉时段 → 409）

同一医生再约一个交叉时段，返回：

```
HTTP 409
{"status":409,"error":"BUSINESS_RULE_VIOLATION",
 "message":"号源冲突：医生「李医生」在 2026-11-02T10:15 ~ 2026-11-02T10:45 时段已有预约",
 "path":"/api/appointments"}
```

说明第 04 周写在 Service 层的号源规则在容器环境中依旧生效。

---

## 五、数据持久化验证

### 5.1 验证方法

1. 通过业务接口创建一条预约（`id=1`），记录响应内容；
2. `docker compose down`（**不加 `-v`**）删除两个容器与网络，命名卷保留；
3. `docker compose up -d` 重新创建容器，等待 app 变为 `healthy`（约 30 秒）；
4. 查询同一条记录，比对 `id`、字段与 `createdAt`。

### 5.2 重建前后的查询对比

| 字段 | 重建前 | 重建后 | 结论 |
| --- | --- | --- | --- |
| `id` | 1 | 1 | ✅ 一致 |
| `petName` / `doctorName` | 豆豆 / 李医生 | 豆豆 / 李医生 | ✅ 一致 |
| `slotStart` / `slotEnd` | 2026-11-02T10:00 / 10:30 | 同 | ✅ 一致 |
| `status` | BOOKED | BOOKED | ✅ 一致 |
| `createdAt` | 2026-10-19T20:12:33.508077 | 2026-10-19T20:12:33.508077 | ✅ **完全一致**（不是新建的记录） |
| `pet` 表 | 1 行（豆豆/犬/倪先生） | 1 行 | ✅ 一致 |
| `doctor` 表 | 3 行 | 3 行 | ✅ 没有因重启重复插入 |

`createdAt` 是最有说服力的证据：如果数据被重建，时间戳必然变成第二次启动的时间。

直接在 MySQL 容器内查询（`docker compose exec mysql mysql ...`）与 API 查询结果一致，
证明数据确实落在数据库而不是应用内存。

### 5.3 命名数据卷的作用

```
$ docker volume inspect petclinic-mysql-data
"Mountpoint": "/var/lib/docker/volumes/petclinic-mysql-data/_data"
"Labels": { "com.docker.compose.project": "petclinic", "com.docker.compose.volume": "mysql-data" }
```

- 容器的可写层是**临时的**：`docker compose down` 删除容器后，写在该层的文件一起消失；
- MySQL 的数据目录被挂到命名卷 `petclinic-mysql-data`，卷的生命周期独立于容器，容器删了数据还在；
- `docker compose down` 默认**不删**命名卷（上面 `down` 后 `docker volume ls` 仍能看到它），
  只有 `docker compose down -v` 或 `docker volume rm` 才会删除 → 本作业的验证刻意避开 `-v`。

补充：容器使用的种子 SQL 是幂等写法（`WHERE NOT EXISTS`，见 `monolith/src/main/resources/db/seed-mysql.sql`），
所以反复重启医生表始终是 3 行，不会越重启越多。

---

## 六、截图索引

见 [screenshots/](screenshots/)，16 张，按四个提交项分组：

| 提交项 | 截图 |
| --- | --- |
| 1. Dockerfile 与镜像构建 | `01-dockerfile.png`（多阶段构建源码）、`02-dockerignore.png`（忽略规则）、`03-build-success.png`（构建成功）、`04-image-list.png`（镜像与镜像层） |
| 2. Compose 编排与启动 | `05-compose-file.png`（compose.yaml）、`06-compose-up.png`（一键启动）、`07-compose-ps.png`（双服务 healthy）、`08-logs-app.png`（应用启动日志） |
| 3. 业务 API 验证 | `09-health.png`（健康检查）、`10-api-create.png`（建档+预约 201）、`11-api-query.png`（查询 200）、`12-business-rule.png`（号源冲突 409） |
| 4. 数据持久化验证 | `13-persist-before.png`（重建前）、`14-persist-rebuild.png`（down + up）、`15-persist-after.png`（重建后数据仍在）、`16-volume.png`（命名数据卷） |

所有终端截图均基于真实命令输出渲染（原始输出保存在工作区 evidence/ 中，未入库）。

---

## 七、遇到的问题与解决

**1. 容器内 Maven 依赖下载极慢**

第一次 `docker build` 时，容器内直连 Maven Central 只有 4~30 KB/s，`dependency:go-offline` 预计要数小时。
解决：在构建阶段注入 `monolith/docker/maven-settings.xml`，把 central 镜像到
`https://maven.aliyun.com/repository/public`，实测提升到 MB/s 级别，两阶段合计约 70 秒完成。
该配置只影响「构建镜像时下载依赖」，不影响运行时行为。

**2. 宿主机端口冲突**

本机 8080/3306 已被此前练习的容器占用，`docker compose up` 会绑定失败。
解决：端口本身就走环境变量（`APP_PORT` / `MYSQL_PORT`），改 `.env` 两行即可，配置文件零改动。

**3. MySQL 客户端里中文显示 `??`**

`docker compose exec mysql mysql -e "select ..."` 查出来的中文变成问号。
原因：mysql 客户端默认字符集是 latin1，属于**显示问题**而非数据问题（API 返回的中文正常）。
解决：加 `--default-character-set=utf8mb4` 后显示正常，也印证库里存的就是正确的 UTF-8 数据。

---

## 八、下一步计划

1. 第 06 周起按课程进度继续：医生接诊 → 电子病历与处方（就诊链后半段）；
2. 把镜像推送到镜像仓库（如 Docker Hub / 阿里云 ACR），体验完整的构建-发布-部署链路；
3. 为应用补充 liveness/readiness 探针与结构化日志，为后续多容器编排做准备。
