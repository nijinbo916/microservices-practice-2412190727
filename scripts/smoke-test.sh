#!/bin/bash
# =====================================================================
# 萌宠诊所 · 微服务冒烟测试（第 07 周 · 接入 Nacos）
#
# 用法（从仓库根目录运行）：
#   bash scripts/smoke-test.sh
#
# 可选参数：
#   --with-dependency-down   额外执行「依赖服务下线/恢复」场景：
#                            停掉 pet-service → 等 Nacos 摘除实例 → 创建预约应得 503
#                            且本地不产生任何预约数据 → 重启 pet-service → 再次创建应成功
#
# 端口从 .env 读取（缺省 8084 / 8082 / 8083）。
# =====================================================================
set -u

cd "$(dirname "$0")/.."

# ---- 读取 .env 中的端口 ----
PET_PORT=8084; DOCTOR_PORT=8082; APPOINTMENT_PORT=8083; NACOS_CONSOLE_PORT=18080
if [ -f .env ]; then
  PET_PORT=$(grep -E '^PET_PORT=' .env | cut -d= -f2)
  DOCTOR_PORT=$(grep -E '^DOCTOR_PORT=' .env | cut -d= -f2)
  APPOINTMENT_PORT=$(grep -E '^APPOINTMENT_PORT=' .env | cut -d= -f2)
  NACOS_CONSOLE_PORT=$(grep -E '^NACOS_CONSOLE_PORT=' .env | cut -d= -f2)
fi
PET_PORT=${PET_PORT:-8084}
DOCTOR_PORT=${DOCTOR_PORT:-8082}
APPOINTMENT_PORT=${APPOINTMENT_PORT:-8083}
NACOS_CONSOLE_PORT=${NACOS_CONSOLE_PORT:-18080}

PET="http://localhost:${PET_PORT}"
DOCTOR="http://localhost:${DOCTOR_PORT}"
APPT="http://localhost:${APPOINTMENT_PORT}"
NACOS="http://localhost:${NACOS_CONSOLE_PORT}"

PASS=0; FAIL=0

ok()   { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }

check_contains() { # $1 描述  $2 haystack  $3 needle
  case "$2" in *"$3"*) ok "$1" ;; *) bad "$1（期望包含「$3」，实际：$2）" ;; esac
}

extract_id() { # 从 JSON 里取第一个 "id":N
  printf '%s' "$1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2
}

echo "=============================================="
echo " 0) Nacos 控制台可访问（${NACOS}，新版控制台不在 8848/nacos）"
echo "=============================================="
nacos_code=$(curl -s -o /dev/null -m 10 -w '%{http_code}' "$NACOS/" || true)
if [ "$nacos_code" = "200" ] || [ "$nacos_code" = "302" ] || [ "$nacos_code" = "307" ]; then
  ok "Nacos 控制台 HTTP 可访问（$nacos_code）"
else
  bad "Nacos 控制台不可访问：$NACOS（HTTP $nacos_code）"
fi

echo ""
echo "=============================================="
echo " 1) 健康检查：三个服务的 /actuator/health"
echo "=============================================="
for name_url in "pet-service|$PET" "doctor-service|$DOCTOR" "appointment-service|$APPT"; do
  name="${name_url%%|*}"; url="${name_url##*|}"
  resp=$(curl -s -m 10 "$url/actuator/health" || true)
  if [ -z "$resp" ]; then bad "$name 健康检查无响应"; else
    check_contains "$name 健康检查返回 UP" "$resp" '"status":"UP"'
  fi
done

echo ""
echo "=============================================="
echo " 2) doctor-service：医生主数据（3 名医生）"
echo "=============================================="
docs=$(curl -s -m 10 "$DOCTOR/api/doctors" || true)
check_contains "GET /api/doctors 返回李医生" "$docs" "李医生"
count=$(printf '%s' "$docs" | grep -o '"id"' | wc -l)
if [ "$count" -ge 3 ]; then ok "医生数量 >= 3（实际 $count）"; else bad "医生数量不足 3（实际 $count）"; fi

echo ""
echo "=============================================="
echo " 3) pet-service：创建宠物档案（201）"
echo "=============================================="
PET_NAME="冒烟测试宠物$(date +%H%M%S)"
pet_resp=$(curl -s -m 10 -X POST "$PET/api/pets" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"$PET_NAME\",\"species\":\"犬\",\"breed\":\"金毛\",\"ownerName\":\"倪先生\",\"ownerPhone\":\"13800000001\",\"birthday\":\"2021-05-20\",\"weightKg\":28.50,\"allergyHistory\":\"无\"}" || true)
PET_ID=$(extract_id "$pet_resp")
if [ -n "$PET_ID" ]; then ok "POST /api/pets 建档成功，id=$PET_ID"; else bad "POST /api/pets 失败：$pet_resp"; fi

echo ""
echo "=============================================="
echo " 4) appointment-service：跨服务创建预约（201）"
echo "    创建前自动调用 pet-service / doctor-service 校验"
echo "=============================================="
FUTURE_START=$(date -d "+3 day" +%Y-%m-%dT10:00:00 2>/dev/null || date -v+3d +%Y-%m-%dT10:00:00)
FUTURE_END=$(date -d "+3 day" +%Y-%m-%dT10:30:00 2>/dev/null || date -v+3d +%Y-%m-%dT10:30:00)
appt_resp=$(curl -s -m 15 -X POST "$APPT/api/appointments" \
  -H "Content-Type: application/json" \
  -d "{\"petId\":$PET_ID,\"doctorId\":1,\"slotStart\":\"$FUTURE_START\",\"slotEnd\":\"$FUTURE_END\",\"reason\":\"冒烟测试预约\"}" || true)
APPT_ID=$(extract_id "$appt_resp")
if [ -n "$APPT_ID" ]; then
  ok "POST /api/appointments 创建成功，id=$APPT_ID"
  check_contains "响应包含远端取得的宠物名" "$appt_resp" "$PET_NAME"
  check_contains "响应包含远端取得的医生名" "$appt_resp" "李医生"
else
  bad "POST /api/appointments 失败：$appt_resp"
fi

echo ""
echo "=============================================="
echo " 5) 查询预约详情（200）并核对数据"
echo "=============================================="
if [ -n "$APPT_ID" ]; then
  detail=$(curl -s -m 15 "$APPT/api/appointments/$APPT_ID" || true)
  check_contains "GET /api/appointments/$APPT_ID 详情" "$detail" "\"id\":$APPT_ID"
  check_contains "详情中的宠物名与建档一致" "$detail" "$PET_NAME"
fi

echo ""
echo "=============================================="
echo " 6) 失败场景 A：宠物不存在（pet-service 404 -> 404，不写库）"
echo "=============================================="
miss_resp=$(curl -s -m 15 -X POST "$APPT/api/appointments" \
  -H "Content-Type: application/json" \
  -d "{\"petId\":999999,\"doctorId\":1,\"slotStart\":\"$FUTURE_START\",\"slotEnd\":\"$FUTURE_END\",\"reason\":\"不存在的宠物\"}" || true)
check_contains "预约依赖的宠物不存在返回 404" "$miss_resp" '"status":404'

echo ""
if [ "${1:-}" = "--with-dependency-down" ]; then
  echo "=============================================="
  echo " 7) 失败场景 B：pet-service 停机（应 503，不写库）"
  echo "=============================================="
  docker compose stop pet-service >/dev/null 2>&1
  echo "  已停止 pet-service，等待 Nacos 摘除实例（约 20s）..."
  sleep 20
  down_resp=$(curl -s -m 15 -X POST "$APPT/api/appointments" \
    -H "Content-Type: application/json" \
    -d "{\"petId\":$PET_ID,\"doctorId\":1,\"slotStart\":\"$FUTURE_START\",\"slotEnd\":\"$FUTURE_END\",\"reason\":\"依赖停机测试\"}" || true)
  check_contains "pet-service 停机时创建预约返回 503" "$down_resp" '"status":503'
  docker compose start pet-service >/dev/null 2>&1
  echo "  正在重启 pet-service，等待重新注册并可访问..."
  for i in $(seq 1 24); do
    if curl -s -m 3 "http://localhost:${PET_PORT}/actuator/health" | grep -q 'UP'; then
      echo "  pet-service 恢复（约 ${i}0s）"
      break
    fi
    sleep 10
  done
  # 恢复后用新的有效参数再创建一次，应重新成功（证明不是永久性错误）
  RECOVER_PET=$(curl -s -m 10 -X POST "http://localhost:${PET_PORT}/api/pets" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"恢复验证宠物$(date +%H%M%S)\",\"species\":\"猫\",\"breed\":\"英短\",\"ownerName\":\"倪先生\",\"ownerPhone\":\"13800000001\",\"weightKg\":4.20}" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
  RECOVER_START=$(date -d "+5 day" +%Y-%m-%dT11:00:00 2>/dev/null || date -v+5d +%Y-%m-%dT11:00:00)
  RECOVER_END=$(date -d "+5 day" +%Y-%m-%dT11:30:00 2>/dev/null || date -v+5d +%Y-%m-%dT11:30:00)
  rec_resp=$(curl -s -m 15 -X POST "$APPT/api/appointments" \
    -H "Content-Type: application/json" \
    -d "{\"petId\":${RECOVER_PET:-0},\"doctorId\":1,\"slotStart\":\"$RECOVER_START\",\"slotEnd\":\"$RECOVER_END\",\"reason\":\"恢复后验证\"}" || true)
  if printf '%s' "$rec_resp" | grep -q '"id"'; then
    ok "服务恢复后重新发现实例并创建预约成功"
  else
    bad "服务恢复后创建预约仍失败：$rec_resp"
  fi
  echo ""
fi

echo "=============================================="
echo " 结果：PASS=$PASS FAIL=$FAIL"
echo "=============================================="
[ "$FAIL" -eq 0 ]
