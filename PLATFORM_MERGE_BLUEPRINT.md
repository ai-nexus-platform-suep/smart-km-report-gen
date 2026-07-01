# 前端平台合并迁移蓝图

## 1. 目标

把当前并列开发的三个前端应用：

- `apps/km-web`
- `apps/qa-web`
- `apps/report-web`

收敛为：

- 一个统一入口应用 `apps/platform-web`
- 三个业务功能包 `packages/features-km`、`packages/features-qa`、`packages/features-report`
- 一套统一共享底座 `packages/core` + `packages/ui`

最终效果：

- 用户只访问一个前端入口
- 登录、权限、菜单、主题、管理后台统一
- 三个业务模块仍保留清晰模块边界
- 后续新增功能不再复制 `main.ts / App.vue / router / layout / auth`

---

## 2. 现状判断

当前仓库已经具备 monorepo 雏形：

- 根目录：`frontend/`
- 包管理：`pnpm workspace`
- 构建编排：`turbo`
- 共享包：`packages/core`、`packages/ui`、`packages/mock`

但仍存在以下问题：

1. 三个应用各自维护入口文件
2. 三个应用各自维护路由树
3. `report-web` 自带独立壳层，未复用共享布局
4. `qa-web` 通过全局样式覆写共享布局，后续容易互相污染
5. 共享层目前只统一了登录、鉴权、基础 request，没有统一业务模块容器
6. 管理端能力分散在三个 app 的 `/admin` 下，不符合总需求书的“总平台后台”形态

结论：

不要继续保留三个正式前端站点，应改为“一个平台站点 + 三个功能包”。

---

## 2.1 当前过渡期路由边界

在三个业务模块页面尚未完全迁入 `packages/features-*` 前，`apps/platform-web` 不再直接引用其他 app 的内部页面路径。

当前约定：

- `apps/km-web/src/platform-routes.ts` 只负责导出知识管理在统一入口中的路由。
- `apps/qa-web/src/platform-routes.ts` 只负责导出智能问答在统一入口中的路由。
- `apps/report-web/src/platform-routes.ts` 只负责导出报告生成在统一入口中的路由。
- `apps/platform-web/src/router/index.ts` 只聚合这些公开路由出口，以及平台自己拥有的首页、系统管理、占位页。

这样做的目的：

- 各模块维护者后续调整自己页面文件名、内部依赖、页面路径时，优先只改本 app 的 `platform-routes.ts`。
- 统一入口不再散落 `../../../qa-web/src/pages/...` 这类跨 app 内部路径。
- 后续迁移到 `packages/features-km`、`packages/features-qa`、`packages/features-report` 时，可以直接把 `platform-routes.ts` 的内容迁移为功能包的 `routes.ts`。

---

## 3. 目标目录骨架

建议把 `frontend/` 调整为：

```text
frontend/
  apps/
    platform-web/
      src/
        main.ts
        App.vue
        router/
          index.ts
        layout/
          nav.ts
  packages/
    core/
      src/
        auth/
        request/
        permissions/
        constants/
        types/
        utils/
    ui/
      src/
        components/
          layout/
          navigation/
          feedback/
          form/
          data-display/
        composables/
        styles/
        router/
    features-auth/
      src/
        api/
        pages/
        stores/
        routes.ts
    features-km/
      src/
        api/
        pages/
        components/
        stores/
        types/
        routes.ts
    features-qa/
      src/
        api/
        pages/
        components/
        stores/
        types/
        routes.ts
    features-report/
      src/
        api/
        pages/
        components/
        stores/
        types/
        utils/
        routes.ts
    mock/
```

---

## 4. 模块边界

### 4.1 `apps/platform-web`

职责只保留：

- 装配路由
- 装配全局布局
- 注册 Pinia / Element Plus / Mock
- 统一导航配置
- 统一权限守卫

不要在这里写业务页面。

### 4.2 `packages/core`

职责：

- token 管理
- 用户信息存取
- 请求封装
- SSE 基础封装
- 通用类型
- 通用常量
- RBAC 权限判断

建议新增子目录：

```text
packages/core/src/
  auth/
  permissions/
  request/
  constants/
  types/
  utils/
```

### 4.3 `packages/ui`

职责：

- 平台统一布局
- 统一 Header / SideNav / Breadcrumb / PageContainer
- 登录注册通用页
- 通用筛选栏、表格壳、空态、状态标签、统计卡片
- 平台主题 token
- 平台动效和基础 reset

原则：

- 业务模块可以传配置，不要各自复制壳层
- 主题变量只能在这里定义，业务模块只能用，不要重写根变量

### 4.4 `packages/features-auth`

职责：

- 登录页
- 注册页
- 当前用户资料
- 退出登录
- 用户基础信息 store

### 4.5 `packages/features-km`

对应总需求书中的：

- 知识库管理
- 文档管理
- 前台知识检索
- 素材管理
- 嵌入模型配置
- 重排序模型配置
- 解析器配置

### 4.6 `packages/features-qa`

对应总需求书中的：

- 智能对话
- 会话列表
- 多轮上下文
- 引用溯源
- 思考过程展示
- 检索测试
- 问答配置
- LLM 配置

### 4.7 `packages/features-report`

对应总需求书中的：

- 报告记录
- 新建报告
- 大纲编辑
- 内容工作台
- 报告导出
- 模板管理
- 素材映射
- 报告后台统计

---

## 5. 统一路由树

建议统一成下面这棵路由树：

```text
/
  /login
  /register
  /dashboard
  /km
    /bases
    /documents
    /search
    /materials
    /settings/embedding
    /settings/rerank
    /settings/parser
  /qa
    /chat
    /conversations
    /citations
    /retrieval-test
    /settings/runtime
    /settings/llm
  /reports
    /list
    /new
    /:id/outline
    /:id/workspace
    /:id/export
    /templates
    /materials
  /admin
    /overview
    /users
    /roles
    /permissions
```

### 路由原则

1. 业务域前缀固定
2. 所有管理类页面收口到平台导航中，不再按 app 分裂
3. 登录注册继续走公共基础路由
4. 后续如果加“数据分析”，直接新增 `features-analytics`

---

## 6. 左侧导航建议

一级菜单建议固定为：

- `平台首页`
- `知识管理`
- `智能问答`
- `报告生成`
- `系统管理`

二级菜单建议：

### 知识管理

- `知识库管理`
- `文档管理`
- `知识检索`
- `素材管理`
- `模型配置`

### 智能问答

- `智能对话`
- `会话记录`
- `检索测试`
- `问答配置`
- `LLM 配置`

### 报告生成

- `报告记录`
- `新建报告`
- `模板管理`
- `素材映射`

### 系统管理

- `总览统计`
- `用户管理`
- `角色权限`

---

## 7. 文件迁移映射

下面按“原路径 -> 新路径”的方式给出第一版迁移建议。

### 7.1 平台入口层

#### 现有入口文件

- `apps/km-web/src/main.ts`
- `apps/qa-web/src/main.ts`
- `apps/report-web/src/main.ts`

#### 目标

- 仅保留 `apps/platform-web/src/main.ts`

#### 处理方式

- 以现有三个 `main.ts` 的共同部分为基础整合
- 把 `report-web` 中额外的样式注册迁移到共享层
- `platform-web` 中统一启动 mock、router、pinia、element-plus

---

### 7.2 根组件

#### 原文件

- `apps/km-web/src/App.vue`
- `apps/qa-web/src/App.vue`
- `apps/report-web/src/App.vue`

#### 新目标

- `apps/platform-web/src/App.vue`

#### 处理方式

- 统一成：
  - 登录页不渲染平台壳
  - 业务页统一渲染 `@platform/ui` 的平台布局
- 不允许业务模块再单独写自己的应用壳

---

### 7.3 路由文件

#### 原文件

- `apps/km-web/src/router/index.ts`
- `apps/qa-web/src/router/index.ts`
- `apps/report-web/src/router/index.ts`

#### 新目标

- `apps/platform-web/src/router/index.ts`
- `packages/features-auth/src/routes.ts`
- `packages/features-km/src/routes.ts`
- `packages/features-qa/src/routes.ts`
- `packages/features-report/src/routes.ts`

#### 处理方式

- 每个 `features-*` 只导出自己的 `RouteRecordRaw[]`
- 平台入口统一 import 并合并
- 鉴权守卫统一挂在 `platform-web`

---

### 7.4 登录注册

#### 原来源

- `packages/ui/src/components/LoginPage.vue`
- `packages/ui/src/components/RegisterPage.vue`

#### 新目标

- `packages/features-auth/src/pages/LoginPage.vue`
- `packages/features-auth/src/pages/RegisterPage.vue`

#### 说明

这两页虽然目前在 `ui` 包里，但更适合归到功能域 `features-auth`。
`ui` 只保留纯展示组件，不保留领域页面。

---

### 7.5 KM 模块迁移

#### 原文件

- `apps/km-web/src/pages/KnowledgeList.vue`
- `apps/km-web/src/pages/SearchPage.vue`
- `apps/km-web/src/api/knowledge.ts`

#### 新目标

- `packages/features-km/src/pages/KnowledgeBaseListPage.vue`
- `packages/features-km/src/pages/KnowledgeSearchPage.vue`
- `packages/features-km/src/api/knowledge.ts`

#### 第二阶段建议补齐

- `packages/features-km/src/pages/DocumentListPage.vue`
- `packages/features-km/src/pages/MaterialListPage.vue`
- `packages/features-km/src/pages/settings/EmbeddingConfigPage.vue`
- `packages/features-km/src/pages/settings/RerankConfigPage.vue`
- `packages/features-km/src/pages/settings/ParserConfigPage.vue`

#### 备注

目前 `km-web` 页面还比较少，说明这块更像壳子原型。迁移时重点不是搬很多文件，而是先把路由和页面命名标准建立起来。

---

### 7.6 QA 模块迁移

#### 原文件

- `apps/qa-web/src/pages/ChatView.vue`
- `apps/qa-web/src/pages/Conversations.vue`
- `apps/qa-web/src/api/qa.ts`
- `apps/qa-web/src/api/index.ts`

#### 新目标

- `packages/features-qa/src/pages/ChatPage.vue`
- `packages/features-qa/src/pages/ConversationListPage.vue`
- `packages/features-qa/src/api/qa.ts`
- `packages/features-qa/src/api/index.ts`

#### 第二阶段建议补齐

- `packages/features-qa/src/pages/RetrievalTestPage.vue`
- `packages/features-qa/src/pages/QaRuntimeConfigPage.vue`
- `packages/features-qa/src/pages/LlmConfigPage.vue`
- `packages/features-qa/src/components/CitationPreview.vue`
- `packages/features-qa/src/components/ThinkingSteps.vue`

#### 备注

`qa-web` 目前的视觉覆盖不要直接搬到根样式，应先拆解成：

- 平台可复用部分 -> `packages/ui`
- 问答专属视觉部分 -> `packages/features-qa/src/styles/`

---

### 7.7 Report 模块迁移

#### 原文件

- `apps/report-web/src/pages/reports/ReportListPage.vue`
- `apps/report-web/src/pages/reports/NewReportPage.vue`
- `apps/report-web/src/pages/reports/OutlinePage.vue`
- `apps/report-web/src/pages/reports/WorkspacePage.vue`
- `apps/report-web/src/pages/reports/ExportPage.vue`
- `apps/report-web/src/pages/admin/AdminDashboardPage.vue`
- `apps/report-web/src/api/reports.ts`
- `apps/report-web/src/api/admin.ts`
- `apps/report-web/src/stores/reports.ts`
- `apps/report-web/src/types/domain.ts`
- `apps/report-web/src/utils/docx.ts`
- `apps/report-web/src/utils/outline.ts`
- `apps/report-web/src/utils/markdown.ts`
- `apps/report-web/src/utils/labels.ts`

#### 新目标

- `packages/features-report/src/pages/ReportListPage.vue`
- `packages/features-report/src/pages/NewReportPage.vue`
- `packages/features-report/src/pages/OutlinePage.vue`
- `packages/features-report/src/pages/WorkspacePage.vue`
- `packages/features-report/src/pages/ExportPage.vue`
- `packages/features-report/src/pages/AdminDashboardPage.vue`
- `packages/features-report/src/api/reports.ts`
- `packages/features-report/src/api/admin.ts`
- `packages/features-report/src/stores/reports.ts`
- `packages/features-report/src/types/domain.ts`
- `packages/features-report/src/utils/docx.ts`
- `packages/features-report/src/utils/outline.ts`
- `packages/features-report/src/utils/markdown.ts`
- `packages/features-report/src/utils/labels.ts`

#### 组件迁移

- `apps/report-web/src/components/StatusBadge.vue`
  -> `packages/ui/src/components/data-display/StatusBadge.vue`

- `apps/report-web/src/components/PageHeader.vue`
  -> `packages/ui/src/components/layout/PageHeader.vue`

- `apps/report-web/src/components/MetricCard.vue`
  -> `packages/ui/src/components/data-display/MetricCard.vue`

- `apps/report-web/src/components/BrandLogo.vue`
  -> `packages/ui/src/components/navigation/BrandLogo.vue`

- `apps/report-web/src/components/OutlineTree.vue`
  -> `packages/features-report/src/components/OutlineTree.vue`

#### 特别说明

`report-web` 当前是三个应用中完成度最高的一个，建议以它的页面成熟度为参考，但不要把它的 `AppShell.vue` 作为最终平台壳。

---

### 7.8 报告应用壳处理

#### 原文件

- `apps/report-web/src/layouts/AppShell.vue`

#### 处理结论

不迁入 `features-report`，不保留为业务壳。

#### 替代方式

把其中可复用能力拆分：

- Logo 结构 -> `packages/ui`
- 顶部用户区 -> `packages/ui`
- 侧边栏样式思路 -> 吸收进 `packages/ui` 的平台主题
- 报告专属导航项 -> 放到 `apps/platform-web/src/layout/nav.ts`

---

### 7.9 样式 token 迁移

#### 原文件

- `packages/ui/src/styles/tokens.css`
- `apps/report-web/src/styles/tokens.css`
- `apps/report-web/src/styles/base.css`
- `apps/report-web/src/styles/motion.css`

#### 新目标

- `packages/ui/src/styles/tokens.css`
- `packages/ui/src/styles/base.css`
- `packages/ui/src/styles/motion.css`

#### 处理原则

1. 只保留一套全局 token
2. 平台设计语言以共享层为准
3. 把 `report-web` 中成熟的视觉细节抽进共享层
4. 禁止 `features-*` 直接覆写 `:root`

---

## 8. 建议新建的关键文件

### 8.1 `apps/platform-web/src/layout/nav.ts`

负责导出统一菜单配置：

```ts
export const platformNav = [
  {
    key: 'dashboard',
    title: '平台首页',
    path: '/dashboard',
  },
  {
    key: 'km',
    title: '知识管理',
    children: [
      { title: '知识库管理', path: '/km/bases' },
      { title: '文档管理', path: '/km/documents' },
      { title: '知识检索', path: '/km/search' },
      { title: '素材管理', path: '/km/materials' },
    ],
  },
]
```

### 8.2 `packages/core/src/permissions/index.ts`

封装角色和能力判断：

```ts
export function canAccessQa(user: UserInfo): boolean
export function canAccessReport(user: UserInfo): boolean
export function isAdmin(user: UserInfo): boolean
export function isSuperAdmin(user: UserInfo): boolean
```

### 8.3 `packages/ui/src/components/layout/PlatformShell.vue`

替代当前多个 `App.vue + AppShell + AppLayout` 的组合，成为平台唯一壳层。

---

## 9. 推荐实施阶段

### Phase 1：统一壳层

目标：

- 新建 `apps/platform-web`
- 新建统一 `PlatformShell`
- 跑通登录页和一个空的业务首页

产出：

- `platform-web` 可运行
- 平台导航可见
- 原三个 app 暂时保留

### Phase 2：统一路由和认证

目标：

- 登录、注册、鉴权、守卫全部从 `platform-web` 接管
- `baseRoutes` 从 `ui` 中迁出或瘦身

产出：

- 只有一套路由入口
- 不再分别维护三个 app 的鉴权逻辑

### Phase 3：迁移 Report

目标：

- 先迁完成度最高的 `report-web`
- 页面、store、api、utils 迁入 `features-report`

原因：

- 页面多，能最先验证新骨架是否可用
- 还能倒逼 `ui` 抽象出真正通用的壳和组件

### Phase 4：迁移 QA

目标：

- 迁 `ChatView`、`Conversations`
- 抽问答专属组件
- 收掉全局覆写样式

### Phase 5：迁移 KM

目标：

- 迁知识库、检索页面
- 为后续文档管理、素材管理、配置页预留固定目录

### Phase 6：删除旧 app

删除条件：

- `platform-web` 已承载三大模块
- 原 `km-web / qa-web / report-web` 无新增访问入口

---

## 10. 团队拆活建议

### 平台基座

负责：

- `apps/platform-web`
- `packages/ui`
- `packages/core`
- 统一菜单、主题、权限

### 知识管理模块

负责：

- `packages/features-km`

### 智能问答模块

负责：

- `packages/features-qa`

### 报告生成模块

负责：

- `packages/features-report`

### 合并规则

1. 共享层变更先合到平台基座分支
2. 业务模块维护者从统一基座分支拉取
3. 功能包不直接改共享壳，避免互相踩样式

---

## 11. 风险点

### 风险 1：样式合并时互相污染

处理：

- 禁止模块直接改全局 `:root`
- 统一由 `packages/ui/src/styles/*` 承担全局样式

### 风险 2：路由迁移后菜单错乱

处理：

- 菜单配置单独放 `nav.ts`
- 不要在页面里写死导航项

### 风险 3：功能还没做完就强行删除旧 app

处理：

- 先迁完一个模块再删对应旧入口
- 保留阶段性回退能力

### 风险 4：管理后台重复建设

处理：

- 后台页面统一进平台管理域
- 不再允许三个模块各自再造一套后台壳

---

## 12. 第一周可执行清单

### Day 1

- 新建 `apps/platform-web`
- 新建 `PlatformShell`
- 新建统一 `router/index.ts`

### Day 2

- 把 `report-web` 的壳层视觉收敛进 `packages/ui`
- 合并 token、base、motion 样式

### Day 3

- 抽出 `features-auth`
- 登录注册改为平台统一入口

### Day 4

- 迁移 `features-report`
- 跑通 `/reports/list`、`/reports/new`

### Day 5

- 迁移 `features-qa`
- 跑通 `/qa/chat`、`/qa/conversations`

### Day 6

- 迁移 `features-km`
- 跑通 `/km/bases`、`/km/search`

### Day 7

- 联调
- 统一权限
- 清理旧入口和遗留样式

---

## 13. 最小验收标准

满足以下条件即可认为第一阶段合并完成：

1. 用户只需启动一个前端应用
2. 登录、注册、退出只有一套
3. 左侧导航只保留一个平台菜单
4. 三个业务域都能进入自己的主页面
5. 全局主题一致，没有明显三套风格并存
6. 管理员和普通用户菜单能正确区分

---

## 14. 推荐实际落地顺序

如果只选一个最稳的顺序，建议按这个做：

1. 先建 `platform-web`
2. 先统一 `ui` 主题和平台壳
3. 先迁 `report-web`
4. 再迁 `qa-web`
5. 最后迁 `km-web`
6. 全部迁完后删旧 app

原因：

- `report-web` 最成熟，最适合拿来验证新骨架
- `qa-web` 对交互要求高，放第二能尽快暴露壳层问题
- `km-web` 当前页面较少，最后迁成本最低

---

## 15. 直接结论

你们现在不该做的是：

- 继续让三个 app 长期并存
- 每个模块继续各改各的布局和样式
- 在每个模块里重复维护 `/admin`

你们应该做的是：

- 建一个 `platform-web`
- 把三个小任务收成三个 `features-*`
- 把共享能力真正沉到 `core` 和 `ui`

这样后面不管接后台、联调、验收还是答辩，都会比三套前端并列稳很多。
