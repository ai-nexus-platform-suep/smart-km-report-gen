#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Auth & RBAC 接口自动化测试

用法:
  python auth/src/test/python/test_auth_rbac_api.py
  python auth/src/test/python/test_auth_rbac_api.py --base-url http://localhost:8080
  python auth/src/test/python/test_auth_rbac_api.py --base-url http://localhost:8081 --direct-auth

环境变量:
  AUTH_BASE_URL   默认 http://localhost:8080 (Gateway)
  AUTH_PASSWORD   默认 admin123

前置条件:
  1. MySQL 已执行 auth/src/main/resources/db/init.sql
  2. auth 服务已启动 (8081)；走 Gateway 时需 gateway (8080) 已启动
  3. 走 Gateway 测 qa-chat 权限时需 qa-chat-service (8090) 已启动
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Optional


DEFAULT_PASSWORD = "admin123"
DEFAULT_BASE_URL = "http://localhost:8080"


# ---------------------------------------------------------------------------
# 日志输出
# ---------------------------------------------------------------------------

def log_section(title: str, *desc_lines: str) -> None:
    print(f"\n{'=' * 60}")
    print(f"  {title}")
    print(f"{'=' * 60}")
    for line in desc_lines:
        print(f"  · {line}")


def log_step(msg: str) -> None:
    print(f"  >> {msg}")


def log_detail(msg: str) -> None:
    print(f"     {msg}")


def format_list(items: list[Any], max_show: int = 15) -> str:
    if not items:
        return "(空)"
    text_items = [str(x) for x in items]
    if len(text_items) <= max_show:
        return ", ".join(text_items)
    return ", ".join(text_items[:max_show]) + f" … 共 {len(text_items)} 项"


def log_perm_summary(username: str, roles: list[str], perms: list[str]) -> None:
    log_detail(f"[{username}] 角色 ({len(roles)}): {format_list(roles)}")
    log_detail(f"[{username}] 权限 ({len(perms)}): {format_list(sorted(perms), max_show=20)}")


def log_perm_expectation(
    username: str,
    expected_role: str,
    must_perms: list[str],
    roles: list[str],
    perms: list[str],
    note: str = "",
) -> None:
    log_step(f"校验账号 [{username}] 登录返回的 JWT 角色与权限")
    if note:
        log_detail(f"说明: {note}")
    log_detail(f"期望角色: {expected_role}")
    log_detail(f"必需权限: {format_list(must_perms)}")
    log_perm_summary(username, roles, perms)
    missing = [p for p in must_perms if p not in perms]
    if missing:
        log_detail(f"缺失权限: {format_list(missing)}")
    else:
        log_detail("必需权限校验: 全部命中 ✓")


# ---------------------------------------------------------------------------
# 测试上下文
# ---------------------------------------------------------------------------

@dataclass
class TestResult:
    name: str
    passed: bool
    detail: str = ""


@dataclass
class TestContext:
    base_url: str
    direct_auth: bool
    password: str
    tokens: dict[str, str] = field(default_factory=dict)
    refresh_tokens: dict[str, str] = field(default_factory=dict)
    results: list[TestResult] = field(default_factory=list)

    def record(self, name: str, passed: bool, detail: str = "") -> None:
        self.results.append(TestResult(name, passed, detail))
        mark = "PASS" if passed else "FAIL"
        suffix = f" — {detail}" if detail else ""
        print(f"  [{mark}] {name}{suffix}")


# ---------------------------------------------------------------------------
# HTTP 工具
# ---------------------------------------------------------------------------

def http_request(
    method: str,
    url: str,
    body: Optional[dict] = None,
    token: Optional[str] = None,
    expect_status: Optional[int] = None,
) -> tuple[int, dict[str, Any]]:
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            status = resp.status
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        status = e.code
        raw = e.read().decode("utf-8") if e.fp else ""

    parsed: dict[str, Any] = {}
    if raw:
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = {"_raw": raw}

    if expect_status is not None and status != expect_status:
        raise AssertionError(f"HTTP {status}, expected {expect_status}, body={parsed}")

    return status, parsed


def api_ok(body: dict) -> bool:
    return body.get("code") == 200


def login(ctx: TestContext, username: str) -> dict[str, Any]:
    log_step(f"POST /api/auth/login  username={username}")
    _, body = http_request(
        "POST",
        f"{ctx.base_url}/api/auth/login",
        {"username": username, "password": ctx.password},
        expect_status=200,
    )
    if not api_ok(body):
        raise AssertionError(f"login failed: {body}")
    data = body.get("data") or {}
    token = data.get("accessToken")
    refresh = data.get("refreshToken")
    if not token:
        raise AssertionError("missing accessToken")
    ctx.tokens[username] = token
    if refresh:
        ctx.refresh_tokens[username] = refresh
    log_detail(f"登录成功, accessToken 已缓存 (前 20 字符: {token[:20]}…)")
    return data


# ---------------------------------------------------------------------------
# 测试用例
# ---------------------------------------------------------------------------

def test_connectivity(ctx: TestContext) -> None:
    log_section(
        "1. 服务连通性",
        "验证 auth 服务可访问，superadmin 能完成登录并签发 Token",
    )
    name = "服务连通性 /api/auth/login (superadmin)"
    try:
        login(ctx, "superadmin")
        ctx.record(name, True)
    except Exception as e:
        ctx.record(name, False, str(e))


def test_public_register(ctx: TestContext) -> None:
    log_section(
        "2. 公开注册",
        "POST /api/auth/register 无需 Token，新用户默认绑定 ROLE_USER",
    )
    username = f"autotest_{int(time.time())}"
    name = "公开注册 POST /api/auth/register"
    log_step(f"注册新用户 username={username}, password=123456")
    try:
        status, body = http_request(
            "POST",
            f"{ctx.base_url}/api/auth/register",
            {"username": username, "password": "123456"},
        )
        ok = (status == 201 and api_ok(body)) or body.get("code") == 1002
        log_detail(f"HTTP {status}, code={body.get('code')}, message={body.get('message', '')}")
        ctx.record(name, ok, f"user={username}, status={status}, code={body.get('code')}")
    except Exception as e:
        ctx.record(name, False, str(e))


def test_login_roles_permissions(ctx: TestContext) -> None:
    log_section(
        "3. 登录 JWT 角色与权限（核心）",
        "登录响应 data.roles / data.permissions 应写入 JWT",
        "按 init.sql 三角色矩阵校验关键权限码",
    )
    cases = [
        {
            "username": "superadmin",
            "role": "ROLE_SUPER_ADMIN",
            "must_perms": ["auth:role:manage", "auth:permission:manage", "auth:menu:manage"],
            "note": "超管拥有系统底层配置权限（角色/菜单/权限 CRUD）",
        },
        {
            "username": "admin",
            "role": "ROLE_ADMIN",
            "must_perms": ["auth:user:list", "auth:user:create", "chat:model:manage", "chat:stats:view"],
            "must_not_have": ["auth:permission:manage"],
            "note": "管理员可管用户与业务模块，不可访问权限配置",
        },
        {
            "username": "user",
            "role": "ROLE_USER",
            "must_perms": ["chat:conversation:use", "chat:model:view", "chat:stats:view"],
            "must_not_have": ["auth:user:list", "chat:model:manage"],
            "note": "普通用户仅基础业务只读 + 会话，无管理类权限",
        },
    ]
    for case in cases:
        username = case["username"]
        name = f"登录 {username} — roles & permissions"
        try:
            data = login(ctx, username)
            roles = data.get("roles") or []
            perms = data.get("permissions") or []
            log_perm_expectation(
                username, case["role"], case["must_perms"], roles, perms, case.get("note", "")
            )
            ok = case["role"] in roles and all(p in perms for p in case["must_perms"])
            forbidden = case.get("must_not_have") or []
            leaked = [p for p in forbidden if p in perms]
            if leaked:
                log_detail(f"不应拥有的权限却出现了: {format_list(leaked)}")
                ok = False
            elif forbidden:
                log_detail(f"越权校验: 未包含禁止权限 {format_list(forbidden)} ✓")
            ctx.record(
                name,
                ok,
                f"roles={roles}, perm_count={len(perms)}"
                + (f", leaked={leaked}" if leaked else ""),
            )
        except Exception as e:
            ctx.record(name, False, str(e))


def test_me_endpoints(ctx: TestContext) -> None:
    log_section(
        "4. /me 系列 — 从 Token 解析身份与权限",
        "GET /api/auth/me          → username + roles + permissions",
        "GET /api/auth/me/permissions → 权限码列表",
        "GET /api/auth/me/menus      → 按角色过滤的菜单树",
    )
    for username in ("superadmin", "admin", "user"):
        token = ctx.tokens.get(username)
        if not token:
            ctx.record(f"/me 系列 ({username})", False, "no token")
            continue
        log_step(f"--- 账号 [{username}] ---")
        for path in ("/api/auth/me", "/api/auth/me/permissions", "/api/auth/me/menus"):
            name = f"GET {path} ({username})"
            try:
                log_step(f"GET {path}")
                _, body = http_request("GET", f"{ctx.base_url}{path}", token=token, expect_status=200)
                ok = api_ok(body) and body.get("data") is not None
                data = body.get("data")
                if path.endswith("/me") and isinstance(data, dict):
                    log_detail(
                        f"username={data.get('username')}, "
                        f"roles={format_list(data.get('roles') or [])}, "
                        f"perm_count={len(data.get('permissions') or [])}"
                    )
                elif path.endswith("/permissions") and isinstance(data, list):
                    log_detail(f"权限列表 ({len(data)}): {format_list(sorted(data), max_show=20)}")
                elif path.endswith("/menus") and isinstance(data, list):
                    menu_names = _collect_menu_names(data)
                    log_detail(f"可见菜单 ({len(menu_names)}): {format_list(menu_names, max_show=12)}")
                    if username == "user":
                        ok = ok and len(data) >= 1
                ctx.record(name, ok, f"code={body.get('code')}")
            except Exception as e:
                ctx.record(name, False, str(e))


def _collect_menu_names(menus: list[Any]) -> list[str]:
    names: list[str] = []
    for item in menus:
        if not isinstance(item, dict):
            continue
        title = item.get("title") or item.get("name") or item.get("menuName") or str(item.get("id", "?"))
        names.append(str(title))
        children = item.get("children")
        if isinstance(children, list):
            names.extend(_collect_menu_names(children))
    return names


def test_refresh_token(ctx: TestContext) -> None:
    log_section(
        "5. Refresh Token 轮换",
        "POST /api/auth/refresh 用 refreshToken 换取新的 access + refresh",
    )
    name = "POST /api/auth/refresh"
    refresh = ctx.refresh_tokens.get("superadmin")
    if not refresh:
        ctx.record(name, False, "no refresh token")
        return
    log_step("使用 superadmin 的 refreshToken 刷新")
    try:
        _, body = http_request(
            "POST",
            f"{ctx.base_url}/api/auth/refresh",
            {"refreshToken": refresh},
            expect_status=200,
        )
        data = body.get("data") or {}
        ok = api_ok(body) and bool(data.get("accessToken")) and bool(data.get("refreshToken"))
        if ok:
            ctx.tokens["superadmin"] = data["accessToken"]
            log_detail("刷新成功，superadmin accessToken 已更新")
        ctx.record(name, ok)
    except Exception as e:
        ctx.record(name, False, str(e))


def test_superadmin_admin_apis(ctx: TestContext) -> None:
    log_section(
        "6. 超管管理 API — @RequirePermission 正向用例",
        "superadmin 应能访问全部 auth 管理接口",
    )
    token = ctx.tokens.get("superadmin")
    if not token:
        ctx.record("超管管理 API", False, "no token")
        return
    endpoints = [
        ("GET /api/auth/users", "GET", "/api/auth/users", 200, "auth:user:list"),
        ("GET /api/auth/roles", "GET", "/api/auth/roles", 200, "auth:role:list 或 auth:role:manage"),
        ("GET /api/auth/menus", "GET", "/api/auth/menus", 200, "auth:menu:list 或 auth:menu:manage"),
        ("GET /api/auth/permissions", "GET", "/api/auth/permissions", 200, "auth:permission:manage"),
        ("GET /api/auth/logs", "GET", "/api/auth/logs?pageNum=1&pageSize=10", 200, "auth:log:list"),
    ]
    for name, method, path, expect, required_perm in endpoints:
        try:
            log_step(f"{method} {path}  (需要权限: {required_perm})")
            _, body = http_request(method, f"{ctx.base_url}{path}", token=token, expect_status=expect)
            data = body.get("data")
            count = len(data) if isinstance(data, list) else (data.get("total") if isinstance(data, dict) else "?")
            log_detail(f"code={body.get('code')}, 返回条数/总量≈{count}")
            ctx.record(name, api_ok(body), f"code={body.get('code')}")
        except Exception as e:
            ctx.record(name, False, str(e))


def test_rbac_forbidden(ctx: TestContext) -> None:
    log_section(
        "7. RBAC 越权拦截 — @RequirePermission 负向用例",
        "无对应权限的用户访问受保护接口应返回 code=403",
        "依赖 common 模块 PermissionAspect + UserContextHolder",
    )
    cases = [
        {
            "username": "user",
            "method": "GET",
            "path": "/api/auth/users",
            "desc": "普通用户访问用户列表应 403",
            "required_perm": "auth:user:list",
            "reason": "ROLE_USER 未分配 auth:user:list",
        },
        {
            "username": "admin",
            "method": "GET",
            "path": "/api/auth/permissions",
            "desc": "管理员访问权限配置应 403",
            "required_perm": "auth:permission:manage",
            "reason": "ROLE_ADMIN 故意不含 auth:permission:manage",
        },
    ]
    for case in cases:
        username = case["username"]
        desc = case["desc"]
        token = ctx.tokens.get(username)
        if not token:
            ctx.record(desc, False, "no token")
            continue
        log_step(f"[{username}] {case['method']} {case['path']}")
        log_detail(f"接口注解权限: {case['required_perm']}")
        log_detail(f"预期结果: 403 — {case['reason']}")
        try:
            status, body = http_request(
                case["method"], f"{ctx.base_url}{case['path']}", token=token
            )
            ok = status == 200 and body.get("code") == 403
            if not ok and status != 200:
                ok = body.get("code") == 403
            log_detail(f"实际: HTTP {status}, code={body.get('code')}, message={body.get('message', '')}")
            if ok:
                log_detail("越权拦截生效 ✓")
            else:
                log_detail("未按预期拒绝访问 ✗")
            ctx.record(desc, ok, f"status={status}, code={body.get('code')}")
        except Exception as e:
            ctx.record(desc, False, str(e))


def test_admin_can_manage_users(ctx: TestContext) -> None:
    log_section(
        "8. 管理员正向权限",
        "admin 拥有 auth:user:list，应能 GET /api/auth/users",
    )
    token = ctx.tokens.get("admin")
    name = "管理员 GET /api/auth/users"
    if not token:
        ctx.record(name, False, "no token")
        return
    log_step("GET /api/auth/users  (需要权限: auth:user:list)")
    try:
        _, body = http_request("GET", f"{ctx.base_url}/api/auth/users", token=token, expect_status=200)
        users = body.get("data") or []
        log_detail(f"code={body.get('code')}, 用户数量={len(users) if isinstance(users, list) else '?'}")
        ctx.record(name, api_ok(body))
    except Exception as e:
        ctx.record(name, False, str(e))


def test_gateway_protected_without_token(ctx: TestContext) -> None:
    log_section(
        "9. Gateway 网关鉴权",
        "未携带 Token 访问受保护业务路由应 401",
    )
    if ctx.direct_auth:
        log_step("直连 auth 模式，跳过 Gateway 401 测试")
        ctx.record("Gateway 无 Token 401 (跳过，直连 auth)", True, "skipped")
        return
    name = "Gateway 无 Token 访问 /api/model-configs → 401"
    log_step("GET /api/model-configs  (无 Authorization 头)")
    try:
        status, body = http_request("GET", f"{ctx.base_url}/api/model-configs")
        ok = status == 401 or body.get("code") == 401
        log_detail(f"HTTP {status}, code={body.get('code')}")
        ctx.record(name, ok, f"status={status}, code={body.get('code')}")
    except Exception as e:
        ctx.record(name, False, str(e))


def test_chat_permission_via_gateway(ctx: TestContext) -> None:
    log_section(
        "10. 跨服务业务权限 — qa-chat-service",
        "Gateway 透传 permissions 请求头，下游 @RequirePermission 校验",
        "admin → chat:model:view/manage 可访问模型配置",
        "user  → chat:stats:view 可访问 QA 统计，无 chat:model:manage",
    )
    if ctx.direct_auth:
        log_step("直连 auth 模式，跳过 qa-chat 业务权限测试")
        ctx.record("业务接口权限 (跳过，直连 auth)", True, "skipped")
        return
    admin_token = ctx.tokens.get("admin")
    user_token = ctx.tokens.get("user")
    if not admin_token or not user_token:
        ctx.record("qa-chat 权限", False, "missing tokens")
        return

    # admin 正向
    log_step("[admin] GET /api/model-configs")
    log_detail("需要权限: chat:model:view 或 chat:model:manage (ModelConfigController)")
    try:
        _, admin_body = http_request(
            "GET", f"{ctx.base_url}/api/model-configs", token=admin_token, expect_status=200
        )
        log_detail(f"code={admin_body.get('code')}, message={admin_body.get('message', '')}")
        ctx.record("管理员 GET /api/model-configs", api_ok(admin_body))
    except Exception as e:
        ctx.record("管理员 GET /api/model-configs", False, str(e))

    # user 正向 — stats
    log_step("[user] GET /api/stats/qa/overview")
    log_detail("需要权限: chat:stats:view (QaStatsController)")
    try:
        status, body = http_request(
            "GET", f"{ctx.base_url}/api/stats/qa/overview", token=user_token
        )
        ok = status == 200 and api_ok(body)
        log_detail(f"HTTP {status}, code={body.get('code')}")
        ctx.record("普通用户 GET /api/stats/qa/overview", ok, f"status={status}, code={body.get('code')}")
    except Exception as e:
        ctx.record("普通用户 GET /api/stats/qa/overview", False, str(e))


def run_all(ctx: TestContext) -> int:
    print(f"\n{'#' * 60}")
    print(f"  Auth RBAC API 自动化测试")
    print(f"{'#' * 60}")
    print(f"  Base URL : {ctx.base_url}")
    print(f"  模式     : {'直连 auth (8081)' if ctx.direct_auth else '经 Gateway (8080)'}")
    print(f"  测试密码 : {'*' * len(ctx.password)}")

    test_connectivity(ctx)
    test_public_register(ctx)
    test_login_roles_permissions(ctx)
    test_me_endpoints(ctx)
    test_refresh_token(ctx)
    test_superadmin_admin_apis(ctx)
    test_admin_can_manage_users(ctx)
    test_rbac_forbidden(ctx)
    test_gateway_protected_without_token(ctx)
    test_chat_permission_via_gateway(ctx)

    passed = sum(1 for r in ctx.results if r.passed)
    total = len(ctx.results)
    failed = [r for r in ctx.results if not r.passed]

    print(f"\n{'=' * 60}")
    print(f"  结果: {passed}/{total} 通过")
    print(f"{'=' * 60}")
    if failed:
        print("\n失败项:")
        for r in failed:
            print(f"  ✗ {r.name}: {r.detail}")
        return 1
    print("\n全部通过。")
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(description="Auth RBAC API 自动化测试")
    parser.add_argument(
        "--base-url",
        default=__import__("os").environ.get("AUTH_BASE_URL", DEFAULT_BASE_URL),
        help="API 基址，默认 Gateway http://localhost:8080",
    )
    parser.add_argument(
        "--direct-auth",
        action="store_true",
        help="直连 auth 服务 (默认 base-url 应设为 http://localhost:8081)",
    )
    parser.add_argument(
        "--password",
        default=__import__("os").environ.get("AUTH_PASSWORD", DEFAULT_PASSWORD),
        help="测试账号密码，默认 admin123",
    )
    args = parser.parse_args()

    ctx = TestContext(
        base_url=args.base_url.rstrip("/"),
        direct_auth=args.direct_auth,
        password=args.password,
    )
    sys.exit(run_all(ctx))


if __name__ == "__main__":
    main()
