# Git 协作规范

## 分支树状结构

```
main ──────────────────── 保护分支 (仅总leader 操作)
│
└── develop ──────────── 保护分支 (仅总leader 合入)
    │
    ├── feat-a ───────── 第一组集成-知识管理 (组长管理, 7人)
    │   ├── feat/a-login
    │   ├── feat/a-dashboard
    │   └── ...
    │
    ├── feat-b ───────── 第二组集成-智能问答 (组长管理, 7人)
    │   ├── feat/b-payment
    │   ├── feat/b-order
    │   └── ...
    │
    ├── feat-c ───────── 第三组集成-报告生成 (组长管理, 7人)
    │   ├── feat/c-report
    │   ├── feat/c-admin
    │   └── ...
    │
    └── frontend ─────── 前端共享 (前端组长管理)
        ├── frontend/feat/theme
        ├── frontend/feat/layout
        └── ...
```

## 合入链路

```
组员:      feat/a-*  ──MR──▶  feat-a      (组长审批)
组长:      feat-a    ──MR──▶  develop     (总leader 审批)
前端:      frontend/feat/* ──MR──▶ frontend  (前端组长审批)
前端组长:  frontend  ──MR──▶  develop     (总leader 审批)
总leader:  develop   ──MR──▶  main        (打 tag 发布)
```

> 总leader 只需关注 4 条分支：`feat-a`、`feat-b`、`feat-c`、`frontend`，无需逐个处理组员 MR。

## 分支说明

| 分支 | 用途 | 从何处拉取 | 合入目标 | 谁审批 |
|------|------|------------|----------|--------|
| `main` | 生产代码 | - | - | 总leader |
| `develop` | 全局集成 | - | - | 总leader |
| `feat-a/b/c` | 各模块集成 | `develop` | `develop` | 总leader |
| `feat/<组>-<功能>` | 组员功能开发 | `feat-*` | `feat-*` | 组长 |
| `frontend` | 前端统一基线 | `develop` | `develop` | 总leader |
| `frontend/feat/*` | 前端功能开发 | `frontend` | `frontend` | 前端组长 |

## 规则

### 1. 分支命名

```
组集成分支:   feat-a, feat-b, feat-c
功能分支:     feat/<组>-<功能>       例: feat/a-login
前端分支:     frontend/feat/<功能>   例: frontend/feat/theme
修复分支:     fix/<组>-<描述>        例: fix/b-payment
```

### 2. Commit 规范

```
<type>(<scope>): <subject>
          │          └── 描述：动词开头，≤50 字，不加句号
          └── 范围：a / b / c / ui
```

#### type 定义

| type | 含义 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(a): 增加短信验证码登录` |
| `fix` | 修 bug | `fix(b): 修复金额溢出` |
| `refactor` | 重构（不改功能行为） | `refactor(c): 抽离导出工具函数` |
| `style` | 样式 / 格式化（不涉及逻辑） | `style(ui): 统一按钮圆角为 6px` |
| `docs` | 文档 | `docs(a): 补充鉴权流程注释` |
| `test` | 测试 | `test(b): 补充支付回调单测` |
| `chore` | 构建 / 依赖 / 脚本 | `chore: 升级 axios 到 1.x` |

#### 格式约束

- `scope` 必填：组代码用 `a / b / c`，前端通用代码用 `ui`，全局改动（如 `chore`）可省略
- `subject` 中文/英文均可，动词开头，不加句号
- 标题 ≤ 50 字符
- 如需要额外说明，空一行后写 `body`，不限字数

```
示例：[feat]: 接入支付宝扫码支付

支付流程：用户点击支付 → 生成二维码 → 轮询回调 → 更新订单状态
```

#### 常见反例

```
❌ feat: 提交           // 缺 scope，描述太模糊
❌ 随便改了改            // 无 type/scope
❌ feat(a): 登录和支付   // 一个 commit 包含两个功能，应拆开
❌ fix(ui): fixed bug.  // 不要过去式，不要句号
```

### 3. 前端协作

- 各组前端人员统一从 `frontend` 拉分支
- 通用组件 / 全局样式 / 布局 → 走 `frontend`
- 模块特定页面逻辑 → 走各自 `feat-*`
- 前端组长审查 `frontend` 所有 MR，确保风格统一

### 4. 禁令

- 禁止 push `main` / `develop`
- 禁止 force push 共享分支 (`develop`, `feat-*`, `frontend`)
- 禁止提交 `node_modules`、`.env`、构建产物
- 禁止一个 commit 跨多个 scope
- 禁止跳过 MR 直接合入

## 日常操作速查

```bash
# === 组员日常 ===
git checkout feat-a
git pull --rebase
git checkout -b feat/a-xxx
# 开发...
git add .
git commit -m "feat(a): 功能描述"
git push -u origin feat/a-xxx
# 创建 MR: feat/a-xxx → feat-a   (组长审批)

# === 组长日常 (合并组员 MR 后) ===
git checkout feat-a
git pull --rebase
# 向 develop 提 MR: feat-a → develop   (总leader 审批)

# === 前端开发 ===
git checkout frontend
git pull --rebase
git checkout -b frontend/feat/xxx
git commit -m "feat(ui): UI功能描述"
# MR: frontend/feat/xxx → frontend   (前端组长审批)

# === 前端组长 ===
# MR: frontend → develop   (总leader 审批)


#
```

## 发布流程

```
develop → main (打 tag)
tag: v<major>.<minor>.<patch>   例: v1.0.0
```
