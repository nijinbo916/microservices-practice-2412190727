#!/usr/bin/env bash
# =====================================================================
# 期中汇报现场演示（约 60 秒，5 个镜头）
#
# 用法（从仓库根目录运行，前提：容器已提前起好）：
#   bash scripts/demo.sh
#
# 设计原则：
#   1. 每幕只输出 3-6 行，投屏看得清
#   2. 不 build、不重启、不改数据，纯读 + 一次写入
#   3. 任一幕失败不中断，继续往下走（现场不卡死）
# =====================================================================

set -u

PET="http://localhost:${PET_PORT:-8084}"
DOCTOR="http://localhost:${DOCTOR_PORT:-8082}"
APPT="http://localhost:${APPOINTMENT_PORT:-8083}"

# 颜色：Windows 的 cmd 窗口不认识 ANSI，设置 NO_COLOR=1 可关闭（bat 启动器会自动设）
if [ -n "${NO_COLOR:-}" ]; then
  B=""; DIM=""; G=""; R=""; Y=""; N=""
else
  B=$'\033[1m'; DIM=$'\033[2m'; G=$'\033[32m'; R=$'\033[31m'; Y=$'\033[33m'; N=$'\033[0m'
fi

say() { printf '\n%s▌ %s%s\n' "$B" "$1" "$N"; }
sub() { printf '%s   %s%s\n' "$DIM" "$1" "$N"; }
ok()  { printf '%s   ✓ %s%s\n' "$G" "$1" "$N"; }
bad() { printf '%s   ✗ %s%s\n' "$R" "$1" "$N"; }

# 每幕之间停一下，给讲解留时间
# 默认：按回车推进（现场最从容）；-y 自动模式（录屏用，每幕停 2 秒）
AUTO=0
[ "${1:-}" = "-y" ] && AUTO=1
next() {
  if [ "$AUTO" = "1" ]; then
    sleep 2
  else
    printf '%s   ── 按回车继续 ▸ %s' "$DIM" "$N"
    read -r _ || true
  fi
}

# 预热：确认服务在，不在就直接退出并提示（不要在现场刷一屏报错）
if ! curl -s -m 5 "$PET/actuator/health" >/dev/null 2>&1; then
  printf '%s  服务未就绪，请先执行：docker compose up -d && docker compose ps%s\n' "$R" "$N"
  exit 1
fi

# ---------------------------------------------------------------- 1
say "① 一条命令拉起整套环境：MySQL + Nacos + 3 个业务服务"
docker ps --filter "name=petclinic" --format '{{.Names}}|{{.Status}}' 2>/dev/null \
  | while IFS='|' read -r n s; do printf '   %-30s %s\n' "$n" "$s"; done
sub "5 个容器全部 healthy，端口 8084 / 8082 / 8083，控制台 18080"
next

# ---------------------------------------------------------------- 2
say "② 三个服务三个库，各管各的数据"
for pair in "pet-service|$PET|/api/pets" "doctor-service|$DOCTOR|/api/doctors" "appointment-service|$APPT|/api/appointments"; do
  name="${pair%%|*}"; rest="${pair#*|}"; url="${rest%%|*}"; path="${rest##*|}"
  code=$(curl -s -o /dev/null -m 8 -w '%{http_code}' "$url$path" || echo 000)
  if [ "$code" = "200" ]; then
    printf '   %s✓%s %-22s 健康 UP   GET %s → %s\n' "$G" "$N" "$name" "$path" "$code"
  else
    printf '   %s✗%s %-22s GET %s → %s\n' "$R" "$N" "$name" "$path" "$code"
  fi
done
sub "pet / doctor / appointment 三个库、三个账号，互不越界"
next

# ---------------------------------------------------------------- 3
say "③ 跨服务调用：appointment 只存 id，名称实时去问对方服务"
PET_NAME="演示宠物$(date +%H%M%S)"
pet=$(curl -s -m 10 -X POST "$PET/api/pets" -H "Content-Type: application/json" \
  -d "{\"name\":\"$PET_NAME\",\"species\":\"犬\",\"breed\":\"金毛\",\"ownerName\":\"倪先生\",\"ownerPhone\":\"13800000001\",\"birthday\":\"2021-05-20\",\"weightKg\":28.50,\"allergyHistory\":\"无\"}")
PID=$(printf '%s' "$pet" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
sub "pet-service 建档 → id=$PID  名称=$PET_NAME"

D=$((3 + RANDOM % 5)); H=$((8 + RANDOM % 9)); M=$(( (RANDOM % 6) * 10 ))
# 结束时间必须用 date 加 30 分钟，直接 M+30 会算出 15:70 这种非法值
field() { printf '%s' "$appt" | grep -o "\"$1\":\"\?[^,\"}]*" | head -1 | sed 's/^[^:]*://; s/^"//'; }

# 同一医生同一时段唯一，撞上历史数据会 409；最多换 3 个时段重试，现场不会因此卡住
appt=""; AID=""
for attempt in 1 2 3; do
  [ "$attempt" -gt 1 ] && { D=$((3 + RANDOM % 5)); H=$((8 + RANDOM % 9)); M=$(( (RANDOM % 6) * 10 )); }
  D_DATE=$(date -d "+${D} day" +%Y-%m-%d)
  S="${D_DATE}T$(printf %02d $H):$(printf %02d $M):00"
  S_EPOCH=$(date -d "${D_DATE} $(printf %02d $H):$(printf %02d $M):00" +%s)
  E=$(date -d "@$((S_EPOCH + 1800))" +%Y-%m-%dT%H:%M:00)
  appt=$(curl -s -m 15 -X POST "$APPT/api/appointments" -H "Content-Type: application/json" \
    -d "{\"petId\":$PID,\"doctorId\":1,\"slotStart\":\"$S\",\"slotEnd\":\"$E\",\"reason\":\"期中汇报演示\"}")
  AID=$(field id)
  [ -n "$AID" ] && break
  sub "时段 $(printf %s "$S" | cut -c12-16) 已被占用，换下一个（第 $attempt 次）"
done

# 只挑关键字段展示，长 JSON 投屏看不清
ANAME=$(field petName); AOWNER=$(field ownerName)
ADOC=$(field doctorName); ASPEC=$(field doctorSpecialty); AST=$(field status)

if [ -z "$AID" ]; then
  bad "创建预约失败，已换 3 个时段（最后返回：$(printf '%s' "$appt" | tr -d '\n' | cut -c1-90)）"
else
  printf '   → id=%s  petName=%s  ownerName=%s\n' "$AID" "$ANAME" "$AOWNER"
  printf '   → doctorName=%s（%s）  status=%s\n' "$ADOC" "$ASPEC" "$AST"
  case "$appt" in
    *"$PET_NAME"*) ok "响应里带着远端宠物名「$PET_NAME」—— 是实时 HTTP 调用，不是复制字段" ;;
    *) bad "未取到宠物名" ;;
  esac
  case "$appt" in
    *"李医生"*) ok "也带着远端医生名「李医生」—— 一次预约校验了两个服务" ;;
    *) bad "未取到医生名" ;;
  esac
fi
next

# ---------------------------------------------------------------- 4
say "④ 三个服务已注册到 Nacos（分组 PERSONAL_GROUP）"
for s in pet doctor appointment; do
  line=$(docker logs "petclinic-$s-service" 2>&1 | grep -o "nacos registry.*register finished" | tail -1)
  [ -n "$line" ] && ok "$line" || sub "$s-service 无注册日志（可能已滚动）"
done
sub "appointment 每次调用前调 DiscoveryClient.getInstances()，拿的是当下实例列表"
next

# ---------------------------------------------------------------- 5
say "⑤ 失败场景：不存在的宠物 → 404，且库里不留脏数据"
before=$(curl -s -m 10 "$APPT/api/appointments" | grep -o '"id"' | wc -l | tr -d ' ')
code=$(curl -s -o /dev/null -m 15 -w '%{http_code}' -X POST "$APPT/api/appointments" \
  -H "Content-Type: application/json" \
  -d "{\"petId\":999999,\"doctorId\":1,\"slotStart\":\"$S\",\"slotEnd\":\"$E\",\"reason\":\"非法宠物\"}")
after=$(curl -s -m 10 "$APPT/api/appointments" | grep -o '"id"' | wc -l | tr -d ' ')
if [ "$code" = "404" ] && [ "$before" = "$after" ]; then
  ok "petId=999999 → HTTP $code，预约条数 $before → $after（零写入）"
else
  bad "HTTP $code，条数 $before → $after"
fi

printf '\n%s  演示结束：5 个容器、3 个服务、跨服务校验、服务发现、失败零写入%s\n\n' "$B" "$N"
