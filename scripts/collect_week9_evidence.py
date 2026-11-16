# -*- coding: utf-8 -*-
"""收集第 09 周真实运行证据：全部为当场请求的真实返回，写入 logs/*.txt。"""
import base64
import json
import os
import subprocess
import sys
from pathlib import Path
from urllib.request import Request, urlopen

REPO = Path(__file__).resolve().parents[1]
LOGS = REPO / "docs/homework/week-09/logs"
LOGS.mkdir(parents=True, exist_ok=True)

GW = "http://localhost:8080"
PET_DIRECT = "http://127.0.0.1:8084"
# 与 .env 的 APP_SEED_PASSWORD 一致；不写死在代码里
PASSWORD = os.environ.get("APP_SEED_PASSWORD", "")
if not PASSWORD:
    sys.exit("请先设置 APP_SEED_PASSWORD（与 .env 一致）")


def call(url, method="GET", headers=None, body=None):
    req = Request(url, method=method, headers=headers or {},
                  data=body.encode() if body else None)
    try:
        with urlopen(req, timeout=10) as r:
            return r.status, r.read().decode()
    except Exception as e:  # 4xx/5xx 也会抛
        code = getattr(e, "code", None)
        raw = e.read().decode() if hasattr(e, "read") else str(e)
        return code, raw


def save(name, text):
    (LOGS / name).write_text(text, encoding="utf-8")
    print("saved", name, len(text), "chars")


# 1. 网关路由
code, body = call(GW + "/actuator/gateway/routes")
routes = json.loads(body)
lines = [f"{r['route_id']}  ->  {r['uri']}  |  {r['predicate'].split('&&')[-1].strip()}"
         for r in routes]
lines.append(f"共 {len(routes)} 条路由")
save("routes.txt", "\n".join(lines))

# 2. 网关健康
code, body = call(GW + "/api/health")
save("gateway-health.txt", f"GET {GW}/api/health -> HTTP {code}\n{body}")

# 3. 三个角色登录
seg = []
tokens = {}
for user in ("admin", "owner", "doctor"):
    code, body = call(GW + "/api/auth/login", "POST",
                      {"Content-Type": "application/json"},
                      json.dumps({"username": user, "password": PASSWORD}))
    d = json.loads(body)
    tokens[user] = d.get("accessToken", "")
    seg.append(f"POST /api/auth/login  username={user}  ->  HTTP {code}")
    seg.append("  " + json.dumps({k: (v[:28] + "..." if k == "accessToken" else v)
                                  for k, v in d.items()}, ensure_ascii=False))
save("login-three-roles.txt", "\n".join(seg))

# 4. 错误密码 + 假 token
seg = ["# 场景 1：正确用户名 + 错误密码"]
code, body = call(GW + "/api/auth/login", "POST",
                  {"Content-Type": "application/json"},
                  json.dumps({"username": "admin", "password": "definitely-wrong"}))
seg.append(f"POST /api/auth/login  password=definitely-wrong  ->  HTTP {code}")
seg.append("  " + body)
seg.append("# 场景 2：伪造 token（签名不对）")
code, body = call(GW + "/api/pets",
                  headers={"Authorization": "Bearer not.a.real.token"})
seg.append(f"GET /api/pets  Authorization: Bearer not.a.real.token  ->  HTTP {code}")
seg.append("  " + body)
save("login-bad-creds.txt", "\n".join(seg))

# 5. JWT 解码
tok = tokens["admin"]
h, p = tok.split(".")[0], tok.split(".")[1]
h += "=" * (-len(h) % 4)
p += "=" * (-len(p) % 4)
header = json.loads(base64.urlsafe_b64decode(h))
payload = json.loads(base64.urlsafe_b64decode(p))
save("jwt-decoded.txt",
     "accessToken（前 40 字符）: " + tok[:40] + "..."
     + "\n\nHEADER:\n" + json.dumps(header, ensure_ascii=False, indent=2)
     + "\n\nPAYLOAD:\n" + json.dumps(payload, ensure_ascii=False, indent=2))

# 6. 未登录 401 / 带 token 200
seg = []
code, body = call(GW + "/api/pets")
seg.append(f"GET /api/pets  （无 Authorization 头）  ->  HTTP {code}")
seg.append("  " + body)
code, body = call(GW + "/api/pets",
                  headers={"Authorization": "Bearer " + tokens["admin"]})
seg.append(f"GET /api/pets  （admin token）  ->  HTTP {code}")
seg.append("  " + body[:220] + ("..." if len(body) > 220 else ""))
save("protected-401-200.txt", "\n".join(seg))

# 7. 越权 403 / 放行 200
seg = []
for user, note in (("owner", "OWNER 角色"), ("doctor", "DOCTOR 角色"), ("admin", "ADMIN 角色")):
    code, body = call(GW + "/api/admin/summary",
                      headers={"Authorization": "Bearer " + tokens[user]})
    seg.append(f"GET /api/admin/summary  （{user}，{note}）  ->  HTTP {code}")
    seg.append("  " + body)
save("role-403-200.txt", "\n".join(seg))

# 8. 伪造身份头 + 绕过网关直连
seg = []
code, body = call(GW + "/api/admin/summary",
                  headers={"Authorization": "Bearer " + tokens["owner"],
                           "X-Auth-Roles": "ADMIN", "X-Auth-User": "admin"})
seg.append(f"GET /api/admin/summary  owner token + 伪造 X-Auth-Roles: ADMIN  ->  HTTP {code}")
seg.append("  " + body)
code, body = call(PET_DIRECT + "/api/pets")
seg.append(f"GET {PET_DIRECT}/api/pets  （绕过网关，无凭证）  ->  HTTP {code}")
seg.append("  " + body)
code, body = call(PET_DIRECT + "/api/pets",
                  headers={"X-Auth-User": "admin", "X-Auth-Roles": "ADMIN"})
seg.append(f"GET {PET_DIRECT}/api/pets  （直连 + 伪造身份头）  ->  HTTP {code}")
seg.append("  " + body)
save("forged-header-direct.txt", "\n".join(seg))

# 9. docker ps 六容器
out = subprocess.run(["docker", "ps", "--format",
                      "{{.Names}}\t{{.Status}}\t{{.Ports}}"],
                     capture_output=True, text=True).stdout
save("docker-ps.txt", "PS> docker ps --format '{{.Names}}\\t{{.Status}}\\t{{.Ports}}'\n" + out)

print("done")
