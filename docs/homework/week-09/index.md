# 第 09 周 · API Gateway 与 JWT 认证

> 本周目标：给已有的三个微服务加统一入口（API Gateway）和 JWT 认证，
> 做到底座（网关）挡在前面、身份（token）在网关验、权限（角色）按接口区分，
> 并且绕过网关直连服务也要被拒。
>
> 新增工程：`services/gateway-service`。
> 认证代码：`services/pet-service`（用户/档案服务，账号表与宠物表同库）。
> 编排：根目录 `compose.yaml`。测试脚本：`scripts/auth-test.sh`。
> 设计文档：[../../security.md](../../security.md)。

## 运行命令

```bash
# 0) 首次：准备环境变量（JWT_SECRET / TRUSTED_CALLER_SECRET / APP_SEED_PASSWORD）
cp .env.example .env
#    三个密钥用 openssl rand -base64 48 生成；JWT_SECRET 网关与 pet-service 必须一致

# 1) 起全套（mysql + nacos + 三服务 + gateway，共 6 个容器）
docker compose up -d --build
docker compose ps

# 2) 认证与鉴权自动测试
export APP_SEED_PASSWORD='<.env 里的值>'
bash scripts/auth-test.sh

# 3) 单元测试
cd services/gateway-service && ./mvnw test    # 11 个
cd services/pet-service     && ./mvnw test    # 12 个（含 6 个认证用例）
```

## 一、API Gateway

### 实现思路

1. **独立工程**：`services/gateway-service`，自己的 pom、Dockerfile、配置。
   依赖用 `spring-cloud-starter-gateway-server-webflux` —— Gateway 5.x 把 starter
   拆成了 server-webflux / server-webmvc 两个，旧名 `spring-cloud-starter-gateway` 已不再发布。
2. **必须 REACTIVE**：工程里**不能**出现 `spring-boot-starter-web`。
   一旦 classpath 上有 Servlet 实现，网关会以 Servlet 模式启动，
   路由基础设施不装配、`/actuator/gateway/routes` 返回空数组，
   而且启动不报错、只表现为全部 404。这是本周第一个坑。
3. **配置前缀也变了**：Gateway 5.x 用 `spring.cloud.gateway.server.webflux.routes`，
   旧的 `spring.cloud.gateway.routes` 不报错但静默不生效（第二个坑）。
4. **4 条路由，目标全写服务名**：

| # | 路由 id | 路径谓词 | 目标 |
|---|---|---|---|
| 1 | `pet-service` | `/api/pets/**` | `lb://pet-service` |
| 2 | `doctor-service` | `/api/doctors/**` | `lb://doctor-service` |
| 3 | `appointment-service` | `/api/appointments/**` | `lb://appointment-service` |
| 4 | `auth-service` | `/api/auth/**`、`/api/admin/**` | `lb://pet-service` |

`lb://` 表示走 Spring Cloud LoadBalancer，实例列表来自 Nacos，
网关配置里没有任何 IP 或端口，扩缩容不用改网关。

5. **健康检查**：`/api/health` 由网关自己的 `HealthController` 返回，
   `/actuator/health` 用 actuator 端点。两个都在鉴权白名单里，探活不需要 token。
   刻意不做「下游聚合健康」——一个服务挂掉就让整个探活变红会掩盖故障点。

### 运行结果

- 6 个容器全部 healthy：`petclinic-mysql` / `petclinic-nacos` /
  `petclinic-pet-service` / `petclinic-doctor-service` /
  `petclinic-appointment-service` / `petclinic-gateway`。
- `GET http://localhost:8080/actuator/gateway/routes` 返回 4 条路由，
  `uri` 为 `lb://...`。
- `GET http://localhost:8080/api/health` 返回 `{"status":"UP","service":"gateway-service"}`。
- 从网关访问三个业务路径都通：`/api/pets`、`/api/doctors`、`/api/appointments`。

截图：`01-gateway-routes.png`（路由表）、`02-gateway-health-containers.png`（健康 + 六容器）、
`03-routes-yml.png`（路由配置）。

## 二、认证服务

### 实现思路

放在 `pet-service`（用户与档案同域，账号表 `sys_user` 与 `pet` 表同库），不新建服务。

1. **账号表 `sys_user`**：`username`（唯一）、`password_hash`、`display_name`、
   `role`、`enabled`、`created_at`。角色用字符串存库（`ADMIN` / `DOCTOR` / `OWNER`），
   避免枚举顺序变化导致历史数据错位。
2. **密码存储**：BCrypt，强度 10。库里只有 60 字符的哈希（`$2a$10$...`），
   没有明文也没有可逆密文。同一密码两次入库结果不同（自带随机盐），
   彩虹表撞不出来。
3. **登录接口** `POST /api/auth/login`：按用户名取账号 → `BCrypt.matches` 校验 →
   签发 JWT。查询**不做**「用户名+密码」的 SQL 比对，因为每次盐不同，
   SQL 里比不了。
4. **防用户名枚举**：用户不存在时也对着一个假哈希跑一次 BCrypt，
   让两种失败的耗时接近；三种失败（不存在 / 密码错 / 停用）统一返回
   `401 INVALID_CREDENTIALS「用户名或密码错误」`。
5. **JWT**：HS256，默认 60 分钟。payload 只有 `sub`（用户名）、`uid`、`roles`、
   `iss`、`iat`、`exp`。**不放**手机号、宠物列表等业务数据 —— JWT 是 Base64 编码不是加密，
   谁都能解开看。
6. **密钥**：`JWT_SECRET` 从环境变量来，不足 32 字节直接启动失败（不降级成弱密钥）。
   网关与认证服务用同一个值；代码里没有可用的默认密钥，仓库里只有明显是占位符的串。
7. **测试账号**：启动时若账号不存在则创建，密码来自 `APP_SEED_PASSWORD`，
   用 BCrypt 现场生成 —— 不在 SQL 脚本里写死固定哈希。

### 运行结果

| 账号 | 角色 | 登录结果 |
|---|---|---|
| `admin` | `ADMIN` | 200，返回 `accessToken`，`role=ADMIN` |
| `doctor` | `DOCTOR` | 200，`role=DOCTOR` |
| `owner` | `OWNER` | 200，`role=OWNER` |
| `admin` + 错密码 | — | 401 `INVALID_CREDENTIALS` |
| 不存在的用户名 | — | 401，提示与上面完全相同 |

- 数据库里查 `sys_user`：`password_hash` 全部是 `$2a$10$...` 形式，无明文。
- 单元测试 12 个全过，其中 `AuthApiTest` 6 个覆盖：登录成功、密码错、用户不存在、
  哈希格式、`/api/auth/me` 无头 401、`/api/auth/me` 带头 200。

截图：`04-login-three-roles.png`（三角色登录）、`05-login-bad-creds.png`（错密码 / 假 token）、
`06-jwt-decoded.png`（解开 token 看 payload）、`07-auth-service-code.png`（登录与验密代码）、
`08-seed-accounts-code.png`（种子账号 BCrypt 入库）。

## 三、网关鉴权

### 实现思路

`GatewayAuthFilter`（GlobalFilter，order = -100）按固定顺序做五件事：

1. **先删客户端自带的身份头**：`X-Auth-User`、`X-Auth-Uid`、`X-Auth-Roles`、
   `X-Trusted-Caller`。**必须在验签之前**，否则客户端加个 `X-Auth-Roles: ADMIN`
   就成管理员了。
2. **白名单放行**：`/api/auth/login`、`/api/health`、`/actuator/health`。
   注意放行时也带着「已清理」的头，伪造头不会流到下游。
3. **验签**：没有 `Authorization: Bearer` → 401；签名不符 / 过期 / `iss` 不对 → 401。
4. **查角色规则**（`RolePolicy`，先匹配先生效）：

| 规则 | 需要角色 |
|---|---|
| `GET /api/admin/**` | `ADMIN` |
| `DELETE /api/pets/**` | `ADMIN` |
| `POST /api/pets` | `OWNER` / `ADMIN` |
| `POST /api/appointments` | `OWNER` / `DOCTOR` / `ADMIN` |
| `* /api/**`（兜底） | 登录即可 |

5. **注入可信身份**：`X-Auth-User` / `X-Auth-Uid` / `X-Auth-Roles` 来自 token claims，
   另加 `X-Trusted-Caller` 供下游确认「这个请求确实过了网关」。

### 伪造身份头

- 网关先删后验，客户端伪造的 `X-Auth-Roles: ADMIN` 无效。
- 实测：用 `owner` 的 token 加 `X-Auth-Roles: ADMIN` 访问 `/api/admin/summary`，
  仍然 **403**（不是 200）。

### 绕过网关直连

两道防线，见 [security.md 第 6.2 节](../../security.md)：

| 层 | 做法 |
|---|---|
| 网络 | `compose.yaml` 里业务服务端口绑 `127.0.0.1`，只有 gateway 绑 `0.0.0.0:8080` |
| 应用 | `pet-service` 的 `GatewayTrustFilter` 校验 `X-Trusted-Caller` |

- 直连 `http://127.0.0.1:8084/api/pets`：**401 `DIRECT_ACCESS_DENIED`**，
  带不带伪造身份头都一样。
- 跨服务调用（appointment → pet）不经过网关，由 `appointment-service` 的
  Feign `RequestInterceptor` 补同一个头，否则内部调用会被自己的信任过滤器拒掉。
  这点是改代码时才发现的：加了过滤器后创建预约立刻 503，补上拦截器才恢复。

### 运行结果

| 场景 | 结果 |
|---|---|
| 无 token 访问 `/api/pets` | 401 |
| 只有 `X-Auth-Roles: ADMIN` 没有 token | 401 |
| 假 token | 401 |
| `owner` token 访问 `/api/admin/summary` | 403 |
| `doctor` token 访问 `/api/admin/summary` | 403 |
| `admin` token 访问 `/api/admin/summary` | 200 |
| `owner` token + 伪造 `X-Auth-Roles: ADMIN` | 403 |
| 直连 8084（无凭证 / 带伪造头） | 401 |
| `owner` 删宠物 | 403 |
| `admin` 删不存在的宠物 | 404（说明鉴权通过，卡在业务层） |

截图：`09-protected-401-200.png`、`10-role-403-200.png`、`11-forged-header-direct.png`、
`12-gateway-filter-code.png`（网关过滤器）、`13-trust-filter-code.png`（下游信任过滤器）。

## 四、测试脚本

`scripts/auth-test.sh`，四段主流程 + 三组加固验证，共 20 个断言：

| 组 | 验证内容 | 期望 |
|---|---|---|
| 0 | `/api/health` 公开可访问 | 200 |
| 1 | 未登录访问受保护接口 | 401 |
| 1b | 只有伪造身份头、没有 token | 401 |
| 2 | 三个账号登录拿 token | 200 |
| 2b | 错误密码 | 401 |
| 2c | 假 token | 401 |
| 3 | 带 token 访问 pets / me / doctors / appointments | 200 |
| 4 | 低权限访问管理接口（owner、doctor） | 403 |
| 4b | 管理员访问管理接口 | 200 |
| 5 | 伪造 `X-Auth-Roles` | 403 |
| 6 | 直连绕过网关（无凭证 / 伪造头） | 401 |
| 7 | 删除接口的角色区分 | owner 403 / admin 404 |

实现要点：

- 只用 `curl`，不依赖 `jq`（token 用 `sed` 提取）。
- **可重复运行**：不写任何数据，`DELETE` 用不存在的 id（期望 404 而不是 403），
  所以跑多少次结果都一样，不会把演示数据删掉。
- 密码从 `APP_SEED_PASSWORD` 读，脚本里没有硬编码口令；没设置直接报错退出（退出码 2）。
- 每条用例打印 `PASS/FAIL + 期望码 + 实际码`，失败时附响应体前 300 字节；
  最后汇总，全过退出码 0，否则 1 —— 可以直接接 CI。

运行结果：**20 项全部通过**（2026-11-16 实测，完整输出存在 `logs/auth-test-run.log`，
截图 `14-auth-test-part1.png` / `15-auth-test-part2.png`）。
单元测试：gateway 11 个、pet-service 12 个（含 `AuthApiTest` 6 个认证用例）全绿，
截图 `16-unit-tests.png`。证据的原始请求/响应用
`scripts/collect_week9_evidence.py` 存档在 `logs/`。

## 五、提交材料对照

| 作业项 | 位置 |
|---|---|
| gateway-service 代码与路由配置 | `services/gateway-service/` |
| 登录 / 签发 / 验签 / 角色 / 密码安全 | `services/pet-service/src/main/java/com/zjgsu/njb/pet/{security,service,web}/` |
| 网关鉴权与角色规则 | `services/gateway-service/.../security/GatewayAuthFilter.java`、`RolePolicy.java` |
| 受保护接口的角色校验 | `services/pet-service/.../web/AdminController.java`、`PetController#delete` |
| 认证流程 / 权限 / 路由表 | `docs/security.md` |
| 自动测试 | `scripts/auth-test.sh` |
| 测试账号与角色 | 根目录 `README.md` 第 8.2 节 |

## 六、踩到的坑

1. **Gateway 5.x 的 starter 改名**：`spring-cloud-starter-gateway` 在 5.0 不再发布，
   要写 `spring-cloud-starter-gateway-server-webflux`，否则依赖解析直接失败。
2. **配置前缀迁移**：`spring.cloud.gateway.routes` → `spring.cloud.gateway.server.webflux.routes`。
   旧前缀不报错、启动正常、访问全 404，`/actuator/gateway/routes` 返回 `[]`。
   排查方式是先看 actuator 有没有路由，再看前缀。
3. **Servlet 模式**：只要 classpath 上有 spring-boot-starter-web，网关就不装配路由。
   本工程刻意不引 web starter。
4. **兜底异常处理器吞掉 401/403**：`@ExceptionHandler(Exception.class)` 会把
   `ResponseStatusException` 也接住，导致 `/api/auth/me` 缺头本该 401 却返回 500。
   必须显式加 `@ExceptionHandler(ResponseStatusException.class)`。
5. **信任过滤器误伤内部调用**：给 pet-service 加 `GatewayTrustFilter` 后，
   appointment 的 Feign 调用没有 `X-Trusted-Caller`，创建预约立刻 503。
   补 Feign `RequestInterceptor` 后恢复。教训：加「只允许网关访问」的规则时，
   要把「服务间调用」这条路径单独想一遍，否则会把自己人挡在门外。
6. **响应体结构不一致**：网关最初的错误体用 `code` 字段，业务服务用 `error` 字段，
   测试脚本按 `code` 取不到值。统一成业务服务的 `{timestamp,status,error,message,path}`。
7. **`lb://` 缺负载均衡器，静默 503**：Gateway 5.0 不再传递
   `spring-cloud-starter-loadbalancer`。路由能匹配、日志无异常，
   但转发时解析不了服务名，统一回 503 `Service Unavailable`。
   排查思路：登录请求 503 但日志干净 → 检查 jar 里有没有 loadbalancer → 补依赖后恢复。
   教训：「503 但日志无错」优先怀疑缺依赖而不是下游挂了。
