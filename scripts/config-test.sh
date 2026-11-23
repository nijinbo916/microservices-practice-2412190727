#!/usr/bin/env bash
# =====================================================================
# 配置中心自动验证（第 10 周）
#
# 做四件事，任一步失败即退出非零：
#   1. 把 nacos-config/dev 与 nacos-config/test 下的配置发布到 Nacos
#   2. 列出 Data ID / Group / 内容摘要，证明配置可集中管理
#   3. 调 GET /api/config/effective，证明服务确实读到了配置中心的值
#      （不是本地 application.yml 的默认值）
#   4. 动态验证：把 max-advance-days 从 7 改成 60，不重启服务，
#      同一个「20 天后」的预约请求从 400 变成 201，且容器启动时间不变
#
# 用法：
#   bash scripts/config-test.sh              # 发布 + 验证（默认 test 组）
#   CONFIG_GROUP=PETCLINIC_DEV bash scripts/config-test.sh
#
# 依赖：curl、docker（仅用于证明进程没重启）、.env 里的 Nacos 账号。
# 可重复运行：脚本结束会把 max-advance-days 恢复成 7，并取消它创建的预约。
# =====================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

NACOS_BASE="${NACOS_BASE:-http://localhost:8848}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
APPT_DIRECT_URL="${APPT_DIRECT_URL:-http://127.0.0.1:8083}"
CONFIG_GROUP="${CONFIG_GROUP:-PETCLINIC_TEST}"
SEED_PASSWORD="${APP_SEED_PASSWORD:-}"

PASS=0
FAIL=0
TMP_BODY="$(mktemp)"
TMP_CFG="$(mktemp)"
trap 'rm -f "$TMP_BODY" "$TMP_CFG"' EXIT

green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*"; }
info()  { printf '\033[36m%s\033[0m\n' "$*"; }

ok()   { PASS=$((PASS + 1)); green "  PASS  $*"; }
bad()  { FAIL=$((FAIL + 1)); red   "  FAIL  $*"; }

# expect <名称> <期望> <实际>
expect() {
  if [ "$2" = "$3" ]; then ok "$1（$3）"; else bad "$1：期望 $2，实际 $3"; fi
}

env_value() {
  [ -f .env ] || return 0
  grep -E "^$1=" .env | head -1 | cut -d= -f2- | tr -d '\r'
}

NACOS_USER="$(env_value NACOS_USERNAME)"
NACOS_PASS="$(env_value NACOS_PASSWORD)"
NACOS_NS="$(env_value NACOS_NAMESPACE)"
if [ -z "$SEED_PASSWORD" ]; then SEED_PASSWORD="$(env_value APP_SEED_PASSWORD)"; fi

if [ -z "$NACOS_USER" ] || [ -z "$NACOS_PASS" ]; then
  red "缺少 Nacos 账号：请在 .env 里配置 NACOS_USERNAME / NACOS_PASSWORD"
  exit 2
fi
if [ -z "$SEED_PASSWORD" ]; then
  red "缺少 APP_SEED_PASSWORD（.env 或环境变量），登录测试账号需要它"
  exit 2
fi

http_code() { curl -s -o "$TMP_BODY" -w '%{http_code}' --max-time 15 "$@"; }

json_field() { sed -nE "s/.*\"$1\":\"?([^\",}]+)\"?.*/\1/p" "$TMP_BODY" | head -1; }

echo "=============================================================="
echo " 第 10 周 · Nacos Config 配置中心验证"
echo " Nacos   : $NACOS_BASE（namespace=${NACOS_NS:-public}）"
echo " Group   : $CONFIG_GROUP"
echo " 服务    : $APPT_DIRECT_URL"
echo "=============================================================="

# ---------------------------------------------------------------------
info "1. 登录 Nacos 获取 accessToken"
# Nacos 3.x 的登录接口；2.x 也保留 /v1/auth/login，这里以 3.x 为准
code=$(http_code -X POST "$NACOS_BASE/nacos/v3/auth/user/login" \
  -d "username=$NACOS_USER" -d "password=$NACOS_PASS")
NACOS_TOKEN="$(json_field accessToken)"
expect "Nacos 登录" 200 "$code"
if [ -z "$NACOS_TOKEN" ]; then
  red "  没拿到 accessToken，响应：$(head -c 300 "$TMP_BODY")"
  exit 1
fi

# ---------------------------------------------------------------------
# 发布一个 Data ID；失败时打印 Nacos 的原始响应，便于排错
publish() {
  local data_id="$1" group="$2" file="$3"
  local resp
  resp=$(curl -s --max-time 20 -X POST "$NACOS_BASE/nacos/v3/admin/cs/config" \
    -H "accessToken: $NACOS_TOKEN" \
    --data-urlencode "dataId=$data_id" \
    --data-urlencode "groupName=$group" \
    --data-urlencode "namespaceId=$NACOS_NS" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@$file")
  if printf '%s' "$resp" | grep -q '"data":true'; then
    ok "发布 $group / $data_id"
  else
    bad "发布 $group / $data_id：$resp"
    return 1
  fi
}

get_config() {
  curl -s --max-time 15 -G "$NACOS_BASE/nacos/v3/client/cs/config" \
    -H "accessToken: $NACOS_TOKEN" \
    --data-urlencode "dataId=$1" \
    --data-urlencode "groupName=$2" \
    --data-urlencode "namespaceId=$NACOS_NS"
}

info "2. 发布 dev / test 两套配置（8 个 Data ID）"
for env in dev test; do
  group="PETCLINIC_DEV"
  [ "$env" = "test" ] && group="PETCLINIC_TEST"
  for svc in pet-service doctor-service appointment-service gateway-service; do
    publish "$svc.yaml" "$group" "nacos-config/$env/$svc.yaml"
  done
done

info "3. 回读校验：Data ID / Group / 是否含关键配置项"
check_published() {
  local data_id="$1" group="$2" key="$3"
  local body
  body=$(get_config "$data_id" "$group")
  if printf '%s' "$body" | grep -q "$key"; then
    printf '  %-26s %-16s %s\n' "$data_id" "$group" "含 $key"
    PASS=$((PASS + 1))
  else
    bad "$group / $data_id 回读不到 $key"
  fi
}
check_published pet-service.yaml        PETCLINIC_DEV  "maximum-pool-size: 5"
check_published pet-service.yaml        PETCLINIC_TEST "maximum-pool-size: 10"
check_published doctor-service.yaml     PETCLINIC_DEV  "maximum-pool-size: 4"
check_published doctor-service.yaml     PETCLINIC_TEST "maximum-pool-size: 8"
check_published appointment-service.yaml PETCLINIC_DEV  "max-advance-days: 30"
check_published appointment-service.yaml PETCLINIC_TEST "max-advance-days: 7"
check_published gateway-service.yaml    PETCLINIC_DEV  "com.zjgsu.njb.gateway: debug"
check_published gateway-service.yaml    PETCLINIC_TEST "com.zjgsu.njb.gateway: info"

# ---------------------------------------------------------------------
effective() {
  curl -s --max-time 10 "$APPT_DIRECT_URL/api/config/effective"
}

info "4. 服务实际读取到的配置（GET /api/config/effective）"
EFF=$(effective)
if [ -z "$EFF" ]; then
  red "  接口无响应，确认 appointment-service 已启动且映射了 127.0.0.1:8083"
  EFF="{}"
fi
printf '%s\n' "$EFF" > "$TMP_CFG"
cat "$TMP_CFG"
echo

cfg_num() { sed -nE "s/.*\"$1\":\"?([0-9]+)\"?.*/\1/p" "$TMP_CFG" | head -1; }

expect "配置分组 = $CONFIG_GROUP" "$CONFIG_GROUP" "$(sed -nE 's/.*"configGroup":"([^"]+)".*/\1/p' "$TMP_CFG" | head -1)"
expect "环境标记 = test" "test" "$(sed -nE 's/.*"env":"([^"]+)".*/\1/p' "$TMP_CFG" | head -1)"
expect "连接池最大值 = 10（来自 Nacos）" "10" "$(cfg_num hikariMaxPoolSize)"
expect "Feign 读超时 = 4000（来自 Nacos）" "4000" "$(cfg_num feignReadTimeoutMs)"
expect "预约提前天数 = 7（来自 Nacos）" "7" "$(cfg_num maxAdvanceDays)"

if [ "$CONFIG_GROUP" != "PETCLINIC_TEST" ]; then
  info "（非 test 组，后面的动态验证仍按 test 组的数据进行，跳过）"
fi

# ---------------------------------------------------------------------
info "5. 动态验证：先证「20 天后」被规则拒绝（400）"
code=$(http_code -X POST "$GATEWAY_URL/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$SEED_PASSWORD\"}")
TOKEN="$(json_field accessToken)"
expect "admin 登录" 200 "$code"

code=$(http_code "$GATEWAY_URL/api/pets" -H "Authorization: Bearer $TOKEN")
PET_ID=$(grep -o '"id":[0-9]*' "$TMP_BODY" | head -1 | cut -d: -f2)
code=$(http_code "$GATEWAY_URL/api/doctors" -H "Authorization: Bearer $TOKEN")
DOCTOR_ID=$(grep -o '"id":[0-9]*' "$TMP_BODY" | head -1 | cut -d: -f2)
if [ -z "${PET_ID:-}" ] || [ -z "${DOCTOR_ID:-}" ]; then
  red "  拿不到宠物/医生 id（宠物列表或医生列表为空），无法做动态验证"
  echo "--------------------------------------------------------------"
  red "结果：$PASS 通过，$((FAIL + 1)) 失败"
  exit 1
fi
info "  使用 petId=$PET_ID doctorId=$DOCTOR_ID（取自真实接口）"

# 20 天后，时间 10:00-10:30（用 python 算日期，避免跨月手工加错）
SLOT_DATE=$(python - <<'PY'
import datetime
print((datetime.date.today() + datetime.timedelta(days=20)).isoformat())
PY
)
SLOT_START="${SLOT_DATE}T10:00:00"
SLOT_END="${SLOT_DATE}T10:30:00"
BODY="{\"petId\":$PET_ID,\"doctorId\":$DOCTOR_ID,\"slotStart\":\"$SLOT_START\",\"slotEnd\":\"$SLOT_END\",\"reason\":\"week10 动态配置验证\"}"

code=$(http_code -X POST "$APPT_DIRECT_URL/api/appointments" \
  -H 'Content-Type: application/json' -d "$BODY")
expect "改配置前创建 $SLOT_DATE 的预约" 400 "$code"
if grep -q "最多提前 7 天" "$TMP_BODY"; then
  ok "拒绝原因包含「最多提前 7 天」"
else
  bad "拒绝原因不是预期的提前天数限制：$(head -c 200 "$TMP_BODY")"
fi

info "6. 改 Nacos 配置（7 → 60），不重启任何服务"
STARTED_BEFORE=$(docker inspect -f '{{.State.StartedAt}}' petclinic-appointment-service 2>/dev/null || echo "unknown")
sed 's/max-advance-days: 7/max-advance-days: 60/' nacos-config/test/appointment-service.yaml > "$TMP_CFG"
publish "appointment-service.yaml" "PETCLINIC_TEST" "$TMP_CFG"

info "  等待客户端刷新（最多 30 秒，Nacos 长轮询推送）"
NEW_VALUE=""
for _ in $(seq 1 15); do
  sleep 2
  NEW_VALUE=$(effective | sed -nE 's/.*"maxAdvanceDays":([0-9]+).*/\1/p' | head -1)
  [ "$NEW_VALUE" = "60" ] && break
done
expect "不重启，maxAdvanceDays 变为 60" "60" "${NEW_VALUE:-未刷新}"

code=$(http_code -X POST "$APPT_DIRECT_URL/api/appointments" \
  -H 'Content-Type: application/json' -d "$BODY")
expect "改配置后同一请求创建成功" 201 "$code"
NEW_ID=$(json_field id)

STARTED_AFTER=$(docker inspect -f '{{.State.StartedAt}}' petclinic-appointment-service 2>/dev/null || echo "unknown")
expect "容器启动时间未变（没有重启）" "$STARTED_BEFORE" "$STARTED_AFTER"

# ---------------------------------------------------------------------
info "7. 还原：把配置改回 7，并取消第 6 步创建的预约"
publish "appointment-service.yaml" "PETCLINIC_TEST" "nacos-config/test/appointment-service.yaml"
RESTORED=""
for _ in $(seq 1 15); do
  sleep 2
  RESTORED=$(effective | sed -nE 's/.*"maxAdvanceDays":([0-9]+).*/\1/p' | head -1)
  [ "$RESTORED" = "7" ] && break
done
expect "配置恢复为 7" "7" "${RESTORED:-未恢复}"

if [ -n "${NEW_ID:-}" ]; then
  code=$(http_code -X POST "$APPT_DIRECT_URL/api/appointments/$NEW_ID/cancel")
  expect "取消验证用预约 id=$NEW_ID" 200 "$code"
fi

echo "--------------------------------------------------------------"
if [ "$FAIL" -eq 0 ]; then
  green "结果：$PASS 项全部通过"
  exit 0
else
  red "结果：$PASS 通过，$FAIL 失败"
  exit 1
fi
