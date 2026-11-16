#!/usr/bin/env bash
# =====================================================================
# 认证与鉴权自动测试（第 09 周）
#
# 覆盖作业要求的四个场景 + 三项加固验证：
#   1. 未登录访问受保护接口        -> 401
#   2. 登录获取 token              -> 200 + accessToken
#   3. 携带 token 访问受保护接口   -> 200
#   4. 低权限 token 访问管理接口   -> 403
#   5. 伪造 X-Auth-Roles 头        -> 仍然 403（网关把客户端头删掉）
#   6. 直连业务服务（绕过网关）    -> 401（下游校验网关凭证）
#   7. 错误密码 / 假 token         -> 401
#
# 用法：
#   bash scripts/auth-test.sh                    # 默认跑全套
#   GATEWAY_URL=http://localhost:8080 bash scripts/auth-test.sh
#
# 依赖：curl。不需要 jq，token 用 sed 提取。
# 可重复运行：不写任何数据，DELETE 用不存在的 id（期望 404 而不是 403）。
# =====================================================================
set -uo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
# 直连地址用于验证「绕过网关」会被下游拒绝；默认对齐 .env 的 PET_PORT=8084
PET_DIRECT_URL="${PET_DIRECT_URL:-http://127.0.0.1:8084}"

ADMIN_USER="${ADMIN_USER:-admin}"
DOCTOR_USER="${DOCTOR_USER:-doctor}"
OWNER_USER="${OWNER_USER:-owner}"
# 测试账号密码来自环境变量，与 .env 的 APP_SEED_PASSWORD 一致；
# 仓库里不写死可用口令，没设置就直接报错退出
SEED_PASSWORD="${APP_SEED_PASSWORD:-}"

PASS_COUNT=0
FAIL_COUNT=0
TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY"' EXIT

green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*"; }
info()  { printf '\033[36m%s\033[0m\n' "$*"; }

# expect <用例名> <期望状态码> <实际状态码> [额外说明]
expect() {
  local name="$1" want="$2" got="$3" extra="${4:-}"
  if [ "$want" = "$got" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    green "  PASS  $name  (HTTP $got)${extra:+  $extra}"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    red   "  FAIL  $name  期望 HTTP $want，实际 $got${extra:+  $extra}"
    if [ -s "$TMP_BODY" ]; then
      printf '        响应: %s\n' "$(head -c 300 "$TMP_BODY")"
    fi
  fi
}

# 发请求：状态码写 stdout，响应体写 $TMP_BODY
http_code() {
  curl -s -o "$TMP_BODY" -w '%{http_code}' --max-time 10 "$@"
}

# 从登录响应里取字段（不依赖 jq）
json_field() {
  sed -nE "s/.*\"$1\":\"?([^\",}]+)\"?.*/\1/p" "$TMP_BODY" | head -1
}

login() {
  local username="$1" password="$2"
  http_code -X POST "$GATEWAY_URL/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\"}"
}

if [ -z "$SEED_PASSWORD" ]; then
  red "APP_SEED_PASSWORD 未设置。先执行：export APP_SEED_PASSWORD=<.env 里的值>"
  exit 2
fi

echo "=============================================================="
echo " 认证与鉴权自动测试"
echo " 网关: $GATEWAY_URL"
echo " 直连: $PET_DIRECT_URL"
echo "=============================================================="

# ---------------------------------------------------------------------
info "0. 网关健康检查（公开入口，不需要 token）"
code=$(http_code "$GATEWAY_URL/api/health")
expect "GET /api/health 无需认证" 200 "$code"

# ---------------------------------------------------------------------
info "1. 未登录访问受保护接口，应失败（401）"
code=$(http_code "$GATEWAY_URL/api/pets")
expect "无 token 访问 /api/pets" 401 "$code"

info "1b. 伪造身份头但没有 token，应失败（401）"
code=$(http_code "$GATEWAY_URL/api/pets" -H 'X-Auth-User: admin' -H 'X-Auth-Roles: ADMIN')
expect "只有 X-Auth-Roles 没有 token" 401 "$code"

# ---------------------------------------------------------------------
info "2. 登录获取 Token"
code=$(login "$ADMIN_USER" "$SEED_PASSWORD")
ADMIN_TOKEN=$(json_field accessToken)
ADMIN_ROLE=$(json_field role)
expect "admin 登录" 200 "$code" "role=$ADMIN_ROLE"
if [ -z "$ADMIN_TOKEN" ]; then
  red "登录没拿到 accessToken，后续用例无法继续"
  echo "--------------------------------------------------------------"
  red "结果：$PASS_COUNT 通过，$((FAIL_COUNT + 1)) 失败"
  exit 1
fi

code=$(login "$OWNER_USER" "$SEED_PASSWORD")
OWNER_TOKEN=$(json_field accessToken)
expect "owner 登录" 200 "$code"

code=$(login "$DOCTOR_USER" "$SEED_PASSWORD")
DOCTOR_TOKEN=$(json_field accessToken)
expect "doctor 登录" 200 "$code"

info "2b. 错误密码应失败（401）"
code=$(login "$ADMIN_USER" "definitely-wrong-password")
expect "admin 用错密码登录" 401 "$code"

info "2c. 假 token 应失败（401）"
code=$(http_code "$GATEWAY_URL/api/pets" -H 'Authorization: Bearer not.a.real.token')
expect "伪造 token 访问 /api/pets" 401 "$code"

# ---------------------------------------------------------------------
info "3. 携带 Token 访问受保护接口，应成功（200）"
code=$(http_code "$GATEWAY_URL/api/pets" -H "Authorization: Bearer $ADMIN_TOKEN")
expect "admin token 访问 /api/pets" 200 "$code"

code=$(http_code "$GATEWAY_URL/api/auth/me" -H "Authorization: Bearer $ADMIN_TOKEN")
expect "admin token 访问 /api/auth/me" 200 "$code"
printf '        身份: %s\n' "$(head -c 200 "$TMP_BODY")"

code=$(http_code "$GATEWAY_URL/api/doctors" -H "Authorization: Bearer $ADMIN_TOKEN")
expect "admin token 访问 /api/doctors（路由 2）" 200 "$code"

code=$(http_code "$GATEWAY_URL/api/appointments" -H "Authorization: Bearer $ADMIN_TOKEN")
expect "admin token 访问 /api/appointments（路由 3）" 200 "$code"

# ---------------------------------------------------------------------
info "4. 低权限 Token 访问管理接口，应失败（403）"
code=$(http_code "$GATEWAY_URL/api/admin/summary" -H "Authorization: Bearer $OWNER_TOKEN")
expect "owner token 访问 /api/admin/summary" 403 "$code" "越权被拦"

code=$(http_code "$GATEWAY_URL/api/admin/summary" -H "Authorization: Bearer $DOCTOR_TOKEN")
expect "doctor token 访问 /api/admin/summary" 403 "$code" "越权被拦"

code=$(http_code "$GATEWAY_URL/api/admin/summary" -H "Authorization: Bearer $ADMIN_TOKEN")
expect "admin token 访问 /api/admin/summary" 200 "$code" "角色放行"
printf '        汇总: %s\n' "$(head -c 200 "$TMP_BODY")"

# ---------------------------------------------------------------------
info "5. 客户端伪造 X-Auth-Roles 头，应无效（网关先删再验签）"
code=$(http_code "$GATEWAY_URL/api/admin/summary" \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'X-Auth-Roles: ADMIN' \
  -H 'X-Auth-User: admin')
expect "owner token + 伪造 ADMIN 头" 403 "$code" "伪造头被剥离"

# ---------------------------------------------------------------------
info "6. 绕过网关直连 pet-service，应失败（401）"
code=$(http_code "$PET_DIRECT_URL/api/pets")
expect "直连 8084 无凭证" 401 "$code"

code=$(http_code "$PET_DIRECT_URL/api/pets" \
  -H 'X-Auth-User: admin' -H 'X-Auth-Roles: ADMIN')
expect "直连 + 伪造身份头" 401 "$code" "下游校验网关凭证"

# ---------------------------------------------------------------------
info "7. 删除接口只有 ADMIN：owner 403，admin 通过鉴权（404 说明过了鉴权这一关）"
code=$(http_code -X DELETE "$GATEWAY_URL/api/pets/999999" -H "Authorization: Bearer $OWNER_TOKEN")
expect "owner 删宠物" 403 "$code"

code=$(http_code -X DELETE "$GATEWAY_URL/api/pets/999999" -H "Authorization: Bearer $ADMIN_TOKEN")
expect "admin 删不存在的宠物（鉴权通过，业务 404）" 404 "$code"

# ---------------------------------------------------------------------
echo "=============================================================="
if [ "$FAIL_COUNT" -eq 0 ]; then
  green " 全部通过：$PASS_COUNT 项"
  echo "=============================================================="
  exit 0
else
  red   " 通过 $PASS_COUNT 项，失败 $FAIL_COUNT 项"
  echo "=============================================================="
  exit 1
fi
