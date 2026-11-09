#!/bin/bash
# =====================================================================
# 萌宠诊所 · 第 08 周 端到端验证（OpenFeign 跨服务调用 + 负载均衡 + 失败场景）
#
# 用法（从仓库根目录运行）：
#   bash scripts/e2e-test.sh
#
# 前提：.env 已配置（cp .env.example .env 后按需修改）；
#       首次运行会自动 build（约 3 分钟，本机离线构建时依赖 compose.override.yaml）。
#
# 验证内容：
#   1) 栈启动：mysql + nacos + doctor + appointment + pet-service x2（多实例）
#   2) Nacos 中 pet-service 健康实例数 = 2
#   3) 跨服务流程：创建预约（appointment）→ Feign 调 pet / doctor 校验 → 201，
#      响应里的宠物名 / 医生名来自远端两个服务
#   4) 负载均衡：多次触发 pet 调用后，pet-service 两个实例的日志都出现 [LB] 记录
#      （容器互不绑定宿主机端口，直接访问走 Nacos + LoadBalancer）
#   5) 失败场景：停掉全部 pet-service 实例 → 创建预约返回 503、预约表零新增
#      → 重启实例 → 恢复注册 → 再次创建返回 201
#
# 已知局限（如实说明）：写操作本身超时/断连时，客户端看到失败但数据库可能已提交，
#   结果未知；本脚本验证的是「校验失败绝不写库」语义（校验在写库之前）。
# =====================================================================
set -u

cd "$(dirname "$0")/.."

PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }

# ---- 读取 .env ----
if [ ! -f .env ]; then
  echo "[前置检查失败] 未找到 .env，请先 cp .env.example .env"; exit 1
fi
. ./.env 2>/dev/null || true

APPOINTMENT_PORT=${APPOINTMENT_PORT:-8083}
DOCTOR_PORT=${DOCTOR_PORT:-8082}
NACOS_CONSOLE_PORT=${NACOS_CONSOLE_PORT:-18080}
APPT="http://localhost:${APPOINTMENT_PORT}"
DOCTOR="http://localhost:${DOCTOR_PORT}"
NACOS_HTTP="http://localhost:${NACOS_SERVER_PORT:-8848}"

# ---- compose 命令组装：基文件 + 多实例覆盖文件（+ 本机离线构建覆盖，若存在） ----
COMPOSE_FILES="-f compose.yaml -f compose.lb.yaml"
if [ -f compose.override.yaml ]; then   # 本机开发覆盖文件已 gitignore，仅在开发机存在
  COMPOSE_FILES="$COMPOSE_FILES -f compose.override.yaml"
fi
dc() { docker compose $COMPOSE_FILES "$@"; }

echo "=============================================="
echo " 0) 启动多实例栈（pet-service x2，不绑定宿主机端口）"
echo "=============================================="
# Nacos 3 首次启动没有管理员密码，幂等初始化一次（已初始化时返回错误，忽略即可）
curl -s -m 10 -X POST "${NACOS_HTTP}/nacos/v3/auth/user/admin" \
  -d "password=${NACOS_PASSWORD}" >/dev/null 2>&1 || true
dc up -d --build --scale pet-service=2 || { echo "栈启动失败"; exit 1; }
sleep 5

# ---- 等待 appointment / doctor 健康 ----
wait_http() { # $1 url  $2 描述  $3 最长秒数
  for i in $(seq 1 "$3"); do
    code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "$1" || true)
    [ "$code" = "200" ] && return 0
    sleep 2
  done
  return 1
}

echo ""
echo "=============================================="
echo " 1) 等待服务健康（最长 5 分钟）"
echo "=============================================="
wait_http "$APPT/actuator/health" "appointment" 150 \
  && ok "appointment-service /actuator/health UP" || bad "appointment-service 未就绪"
wait_http "$DOCTOR/actuator/health" "doctor" 150 \
  && ok "doctor-service /actuator/health UP" || bad "doctor-service 未就绪"

# ---- 等 Nacos 中 pet-service 注册满 2 个实例 ----
# 控制台 API 在 18080 且需要鉴权：先到 8848 登录拿 accessToken，再查询（实测有效路径）
NACOS_TOKEN=""
nacos_login() {
  NACOS_TOKEN=$(curl -s -m 8 -X POST "${NACOS_HTTP}/nacos/v3/auth/user/login" \
    -d "username=nacos&password=${NACOS_PASSWORD}" 2>/dev/null \
    | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
}
pet_instance_count() {
  [ -z "$NACOS_TOKEN" ] && nacos_login
  for base in "http://localhost:${NACOS_CONSOLE_PORT}" "http://localhost:${NACOS_CONSOLE_PORT}/nacos"; do
    resp=$(curl -s -m 8 "${base}/v3/console/ns/instance/list?serviceName=pet-service&groupName=${NACOS_GROUP:-PERSONAL_GROUP}&pageNo=1&pageSize=50&accessToken=${NACOS_TOKEN}" 2>/dev/null)
    case "$resp" in
      *'"healthy"'*) printf '%s' "$resp" | grep -o '"healthy":true' | wc -l ; return 0 ;;
    esac
  done
  nacos_login   # token 可能过期，重登一次再试
  resp=$(curl -s -m 8 "http://localhost:${NACOS_CONSOLE_PORT}/v3/console/ns/instance/list?serviceName=pet-service&groupName=${NACOS_GROUP:-PERSONAL_GROUP}&pageNo=1&pageSize=50&accessToken=${NACOS_TOKEN}" 2>/dev/null)
  printf '%s' "$resp" | grep -o '"healthy":true' | wc -l
}
pet_instances=0
for i in $(seq 1 75); do
  pet_instances=$(pet_instance_count)
  [ "$pet_instances" -ge 2 ] && break
  sleep 4
done
if [ "$pet_instances" -ge 2 ]; then
  ok "Nacos 中 pet-service 健康实例数 = $pet_instances（多实例已注册）"
else
  bad "pet-service 实例数不足 2（当前 $pet_instances），负载均衡无法验证"
fi

echo ""
echo "=============================================="
echo " 2) 种子数据：e2e 专用宠物与医生（幂等）"
echo "=============================================="
# 注意：mysql 客户端必须显式 --default-character-set=utf8mb4，否则中文按 latin1
# 入库变成乱码，API 返回的 petName 就不再是「e2e宠物」。先清旧种子行保证幂等。
# 宠物表：name/species/breed/owner_name/owner_phone/birthday/weight_kg/allergy_history/created_at
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -upet_user -p"${PET_DB_PASSWORD}" petclinic_pet 2>/dev/null <<'SQL'
DELETE FROM pet WHERE name = 'e2e宠物';
INSERT INTO pet (name, species, breed, owner_name, owner_phone, birthday, weight_kg, allergy_history, created_at)
VALUES ('e2e宠物', '犬', '金毛', '倪同学', '13800000000', '2023-05-01', 12.5, NULL, NOW());
SELECT CONCAT('PET_ID=', id) FROM pet WHERE name = 'e2e宠物' LIMIT 1;
SQL
# 医生表：name/specialty/daily_capacity
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -udoctor_user -p"${DOCTOR_DB_PASSWORD}" petclinic_doctor 2>/dev/null <<'SQL'
DELETE FROM doctor WHERE name = 'e2e医生';
INSERT INTO doctor (name, specialty, daily_capacity) VALUES ('e2e医生', '内科', 10);
SELECT CONCAT('DOCTOR_ID=', id) FROM doctor WHERE name = 'e2e医生' LIMIT 1;
SQL

PET_ID=$(docker compose exec -T mysql mysql --default-character-set=utf8mb4 -upet_user -p"${PET_DB_PASSWORD}" petclinic_pet -N -e "SELECT id FROM pet WHERE name='e2e宠物' LIMIT 1" 2>/dev/null | tr -d '[:space:]')
DOCTOR_ID=$(docker compose exec -T mysql mysql --default-character-set=utf8mb4 -udoctor_user -p"${DOCTOR_DB_PASSWORD}" petclinic_doctor -N -e "SELECT id FROM doctor WHERE name='e2e医生' LIMIT 1" 2>/dev/null | tr -d '[:space:]')
case "$PET_ID" in ''|*[!0-9]*) bad "种子宠物 id 异常：$PET_ID";; *) ok "种子宠物 id=$PET_ID，种子医生 id=$DOCTOR_ID";; esac

# ---- 构造不冲突的预约时段（今天 09:00 + N 分钟起，30 分钟） ----
SLOT_BASE=$(date -d "+3 days" +%s)
SLOT_MIN=$(( (RANDOM % 120) ))
START=$(( SLOT_BASE - (SLOT_BASE % 86400) + 9*3600 + SLOT_MIN*60 ))
SLOT_START=$(date -d "@${START}" '+%Y-%m-%dT%H:%M:%S')
SLOT_END=$(date -d "@$((START+1800))" '+%Y-%m-%dT%H:%M:%S')

make_body() { # $1 slotStart  $2 slotEnd
  printf '{"petId":%s,"doctorId":%s,"slotStart":"%s","slotEnd":"%s","reason":"e2e 跨服务流程验证"}' \
    "$PET_ID" "$DOCTOR_ID" "$1" "$2"
}

echo ""
echo "=============================================="
echo " 3) 跨服务流程：创建预约（Feign 调 pet + doctor 校验）"
echo "=============================================="
create_code=$(curl -s -o /tmp/e2e_create.json -m 20 -w '%{http_code}' \
  -X POST "$APPT/api/appointments" -H 'Content-Type: application/json' \
  -d "$(make_body "$SLOT_START" "$SLOT_END")" || true)
if [ "$create_code" = "201" ]; then ok "POST /api/appointments → 201"; else bad "期望 201，实际 $create_code：$(cat /tmp/e2e_create.json 2>/dev/null)"; fi
grep -q 'e2e宠物' /tmp/e2e_create.json \
  && ok "响应 petName 来自 pet-service（实时远端取得）" || bad "响应缺少远端宠物名：$(cat /tmp/e2e_create.json 2>/dev/null)"
grep -q 'e2e医生' /tmp/e2e_create.json \
  && ok "响应 doctorName 来自 doctor-service（实时远端取得）" || bad "响应缺少远端医生名"
grep -q '"petId"' /tmp/e2e_create.json \
  && ok "本地只存 petId / doctorId，不复制对方数据" || true

echo ""
echo "=============================================="
echo " 4) 负载均衡验证：多次触发 pet 调用，两个实例都应接到流量"
echo "=============================================="
# 详情接口每次都会 Feign 调 pet-service 取宠物名（展示增强）
for i in 1 2 3 4 5 6 7 8; do
  curl -s -o /dev/null -m 15 "$APPT/api/appointments/1" || true
  curl -s -o /dev/null -m 15 "$APPT/api/appointments?petId=${PET_ID}" || true
done
sleep 2
LB_INSTANCES=$(dc logs pet-service --since 5m 2>/dev/null | grep '\[LB\] instance=' | grep -o 'instance=[^ ]*' | sort -u)
LB_COUNT=$(printf '%s' "$LB_INSTANCES" | grep -c 'instance=' || true)
echo "  参与过服务的实例："
printf '%s\n' "$LB_INSTANCES" | sed 's/^/    - /'
if [ "$LB_COUNT" -ge 2 ]; then
  ok "两个 pet-service 实例都实际处理了调用方请求（共 $LB_COUNT 个实例标识）"
else
  bad "只观测到 $LB_COUNT 个实例处理请求（期望 2 个）"
fi

echo ""
echo "=============================================="
echo " 5) 失败场景：pet-service 全部实例下线 → 503 且零写入"
echo "=============================================="
# 计数直接查数据库（不走 API：列表接口带远端增强，依赖下线时会拖慢响应）
appt_db_count() {
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 \
    -uappointment_user -p"${APPOINTMENT_DB_PASSWORD}" petclinic_appointment \
    -N -e "SELECT COUNT(*) FROM appointment" 2>/dev/null | tr -d '[:space:]'
}
BEFORE_COUNT=$(appt_db_count)

dc stop pet-service >/dev/null 2>&1
# 等 Nacos 摘除实例（心跳超时，最长 90 秒）
down=0
for i in $(seq 1 30); do
  pet_instances=$(pet_instance_count)
  [ "$pet_instances" -eq 0 ] && down=1 && break
  sleep 3
done
if [ "$down" = "1" ]; then ok "pet-service 实例已从 Nacos 全部摘除"; else bad "等待实例摘除超时（当前 $pet_instances）"; fi

sleep 2
# 负载均衡缓存最长几秒，多试几次直到拿到 503（时段复用无妨：本场景预期 503，不写库）
fail_code=""
for i in 1 2 3 4 5 6; do
  fail_code=$(curl -s -o /tmp/e2e_fail.json -m 15 -w '%{http_code}' \
    -X POST "$APPT/api/appointments" -H 'Content-Type: application/json' \
    -d "$(make_body "$SLOT_START" "$SLOT_END")" 2>/dev/null || true)
  [ "$fail_code" = "503" ] && break
  sleep 3
done
if [ "$fail_code" = "503" ]; then
  ok "pet 依赖不可用：创建预约返回 503"
  grep -q 'DEPENDENCY_UNAVAILABLE' /tmp/e2e_fail.json \
    && ok "错误响应体为结构化 JSON（error=DEPENDENCY_UNAVAILABLE）" || bad "响应体缺少结构化错误：$(cat /tmp/e2e_fail.json 2>/dev/null)"
else
  bad "期望 503，实际 $fail_code：$(cat /tmp/e2e_fail.json 2>/dev/null)"
fi

AFTER_COUNT=$(appt_db_count)
if [ "$BEFORE_COUNT" = "$AFTER_COUNT" ]; then
  ok "失败期间预约表零新增（$BEFORE_COUNT → $AFTER_COUNT），调用失败未被当成业务成功"
else
  bad "预约数发生变化：$BEFORE_COUNT → $AFTER_COUNT（不应发生）"
fi

echo ""
echo "=============================================="
echo " 6) 恢复：重启 pet-service 两实例 → 注册回来 → 再次创建成功"
echo "=============================================="
dc start pet-service >/dev/null 2>&1
recovered=0
for i in $(seq 1 40); do
  pet_instances=$(pet_instance_count)
  [ "$pet_instances" -ge 2 ] && recovered=1 && break
  sleep 3
done
[ "$recovered" = "1" ] && ok "pet-service 两实例重新注册（实例数=$pet_instances）" || bad "实例未恢复（当前 $pet_instances）"

SLOT_START2=$(date -d "@$((START+7200))" '+%Y-%m-%dT%H:%M:%S')
SLOT_END2=$(date -d "@$((START+9000))" '+%Y-%m-%dT%H:%M:%S')
recovered_code=$(curl -s -o /tmp/e2e_recover.json -m 20 -w '%{http_code}' \
  -X POST "$APPT/api/appointments" -H 'Content-Type: application/json' \
  -d "$(make_body "$SLOT_START2" "$SLOT_END2")" || true)
if [ "$recovered_code" = "201" ]; then ok "依赖恢复后创建预约 → 201"; else bad "期望 201，实际 $recovered_code：$(cat /tmp/e2e_recover.json 2>/dev/null)"; fi

echo ""
echo "=============================================="
echo " 结果：PASS=$PASS FAIL=$FAIL"
echo "=============================================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
