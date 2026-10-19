#!/usr/bin/env bash
# =====================================================================
# 第 05 周容器化验证脚本
#
# 前置：docker compose up -d --build 已执行，两个服务均为 running/healthy
# 用法：bash scripts/docker-smoke-test.sh
#       默认访问 http://localhost:8080，可用 BASE_URL 覆盖
# =====================================================================
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

hr() { printf '\n===== %s =====\n' "$1"; }

hr "0. 容器状态"
docker compose ps

hr "1. 健康检查 GET ${BASE_URL}/actuator/health"
curl -s -w '\nHTTP %{http_code}\n' "${BASE_URL}/actuator/health"

hr "2. 创建宠物 POST /api/pets"
curl -s -w '\nHTTP %{http_code}\n' -X POST "${BASE_URL}/api/pets" \
  -H 'Content-Type: application/json' \
  -d '{"name":"豆豆","species":"犬","breed":"金毛","ownerName":"倪先生","ownerPhone":"13800000001","birthday":"2021-05-20","weightKg":28.50,"allergyHistory":"青霉素过敏"}'

hr "3. 医生列表 GET /api/doctors"
curl -s -w '\nHTTP %{http_code}\n' "${BASE_URL}/api/doctors"

hr "4. 创建预约 POST /api/appointments"
curl -s -w '\nHTTP %{http_code}\n' -X POST "${BASE_URL}/api/appointments" \
  -H 'Content-Type: application/json' \
  -d '{"petId":1,"doctorId":1,"slotStart":"2026-11-02T10:00:00","slotEnd":"2026-11-02T10:30:00","reason":"食欲不振，需要检查"}'

hr "5. 按宠物查询预约 GET /api/appointments?petId=1"
curl -s -w '\nHTTP %{http_code}\n' "${BASE_URL}/api/appointments?petId=1"

hr "6. 查询预约详情 GET /api/appointments/1"
curl -s -w '\nHTTP %{http_code}\n' "${BASE_URL}/api/appointments/1"

hr "7. 号源冲突（同医生同时段再约一次，期望 409）"
curl -s -w '\nHTTP %{http_code}\n' -X POST "${BASE_URL}/api/appointments" \
  -H 'Content-Type: application/json' \
  -d '{"petId":1,"doctorId":1,"slotStart":"2026-11-02T10:15:00","slotEnd":"2026-11-02T10:45:00","reason":"重复预约测试"}'
