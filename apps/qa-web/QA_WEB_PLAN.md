# B组智能问答前端开发清单

## 1. 目标

我们只负责 `qa-web`，在现有 Vue 3 monorepo 框架上完成智能问答前端。

参考方向：

- FastGPT 数据集管理页：偏后台工作台，信息密度高，列表和配置区清楚。
- Dify：偏 AI 产品，留白更舒服，聊天区、调试区、引用区层次清晰。

我们的做法不是照搬，而是组合两者：

- 管理页、列表页学 FastGPT。
- 聊天页、思考过程、引用展示学 Dify。

## 2. 当前项目里我们应该改哪里

主要开发区：

- `frontend/apps/qa-web/src/pages`
- `frontend/apps/qa-web/src/router`
- `frontend/apps/qa-web/src/components`（后续可新增）

尽量不要主动改的共享层：

- `frontend/packages/core`
- `frontend/packages/ui`
- `frontend/packages/mock`
- `frontend/package.json`
- `frontend/pnpm-workspace.yaml`
- `frontend/turbo.json`

说明：

- `packages/core` 是公共接口协议、类型、请求工具。
- `packages/ui` 是公共布局、登录注册、路由守卫。
- `packages/mock` 是三组共用的开发期假接口。
- 共享层如果要改，必须先同步另外两组。

## 3. 页面结构建议

### 3.1 `/chat` 智能问答主页面

参考 Dify 的主交互区，建议做成三栏：

- 左栏：会话列表
  - 新建对话按钮
  - 搜索会话
  - 最近会话分组
  - 当前会话高亮
- 中栏：聊天主区域
  - 顶部显示会话标题、当前知识库、模型信息
  - 消息流区域
  - 用户消息 / 助手消息气泡
  - 输入框、发送按钮、停止生成按钮
- 右栏：辅助信息区
  - 思考过程 `thinkingSteps`
  - 引用来源 `citations`
  - 当前检索命中数
  - 会话参数摘要

### 3.2 `/conversations` 会话管理页

参考 FastGPT 数据集列表页，做成“工作台列表页”：

- 顶部：页面标题 + 新建对话按钮
- 工具栏：搜索、状态筛选、时间排序
- 主体：会话表格或卡片列表
  - 标题
  - 最后更新时间
  - 消息数
  - 关联知识库
  - 操作：查看、删除
- 右侧或抽屉：会话详情
  - 基本信息
  - 最近消息预览
  - 检索记录摘要

### 3.3 `/admin/qa/dashboard`

做成轻量统计页：

- 总会话数
- 总消息数
- 最近 30 天趋势
- 热门问题关键词

### 3.4 `/admin/qa/config`

做成参数配置页：

- TopK
- 相似度阈值
- 重排阈值
- 默认知识库

### 3.5 `/admin/qa/retrieval-test`

做成“检索调试页”：

- 输入测试问题
- 选择知识库
- 返回候选片段列表
- 展示 score、文档名、片段内容

### 3.6 `/admin/qa/llm`

做成模型配置页：

- API Base
- API Key
- 模型名
- 超时时间

## 4. 组件拆分建议

建议在 `frontend/apps/qa-web/src/components` 新增这些组件：

- `ConversationSidebar.vue`
- `ConversationSearchBar.vue`
- `ConversationList.vue`
- `ConversationListItem.vue`
- `ChatHeader.vue`
- `MessageList.vue`
- `MessageBubble.vue`
- `ChatComposer.vue`
- `ThinkingPanel.vue`
- `CitationPanel.vue`
- `RetrievalResultCard.vue`
- `QaStatCard.vue`

## 5. 接口对接顺序

先接现有 mock，再换真实后端。

优先顺序：

1. `API_QA.CHAT.LIST`
2. `API_QA.CHAT.CREATE`
3. `API_QA.CHAT.HISTORY`
4. `API_QA.SEARCH.RETRIEVE`
5. `API_QA.ADMIN.QA_CONFIG`
6. `API_QA.ADMIN.RETRIEVAL_TEST`
7. `API_QA.ADMIN.LLM_CONFIG`
8. `API_QA.ADMIN.STATS`
9. `API_QA.ADMIN.QA_TREND`

注意：

- 现阶段 `STREAM` 可以先留接口位，先做假流式交互。
- 真正接 SSE 时，再补消息增量渲染和停止生成。

## 6. 开发优先级

### P0：先搭骨架

- 完成 `ChatView.vue` 的三栏布局
- 完成 `Conversations.vue` 的列表页骨架
- 补 `qa-web/src/components`

### P1：先跑通静态交互

- 假数据展示消息气泡
- 假数据展示思考过程和引用来源
- 假数据展示会话列表切换

### P2：接 mock 接口

- 接入会话列表
- 接入历史消息
- 接入创建会话
- 接入检索测试和配置页

### P3：做管理后台页

- 问答统计
- 问答配置
- 检索测试
- LLM 配置

### P4：最后做细节

- SSE 流式输出
- 加载态、空态、错误态
- 移动端适配
- 滚动体验
- 删除确认、消息复制、引用展开

## 7. 视觉风格约束

整体风格建议：

- 不做花哨官网风，做“专业 AI 工作台”
- 主色用蓝绿或蓝灰，不要大面积紫色
- 页面背景保持浅色，卡片白底，层次靠边框和阴影拉开
- 圆角统一，按钮风格统一
- 聊天页留白比列表页更大
- 管理页信息密度可以更高

具体取法：

- FastGPT 学“列表管理感”和“右侧配置区”
- Dify 学“聊天布局”“调试区”“AI 产品感”

## 8. 我们当前不要做的事

- 不重写 `packages/ui` 的整体布局
- 不私自改共享接口命名
- 不改 A 组和 C 组页面
- 不在 `frontend copy`、`_repo_tmp`、`dist` 里开发
- 不先做复杂动效，先把主流程跑通

## 9. 第一周最值得做的具体任务

1. 把 `ChatView.vue` 从占位页改成三栏页面骨架。
2. 把 `Conversations.vue` 从占位页改成列表管理页骨架。
3. 新建 `components` 目录，把聊天页组件拆开。
4. 用现有 mock 数据先把会话列表、历史消息、引用来源渲染出来。
5. 再补管理后台四个页面的基础表单和统计卡片。

## 10. 参考链接

- FastGPT 数据集管理参考：<https://cloud.fastgpt.cn/dataset/list>
- FastGPT 产品站：<https://fastgpt.cn/>
- Dify：<https://dify.ai/>
