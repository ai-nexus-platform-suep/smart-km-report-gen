import {
  ChatDotRound,
  Collection,
  DataAnalysis,
  DocumentAdd,
  DocumentCopy,
  FolderOpened,
  Histogram,
  Management,
  Notebook,
  Search,
  Setting,
  SetUp,
  UserFilled,
} from '@element-plus/icons-vue'
import type { NavItem } from '@platform/ui/src/components/SideNav.vue'

export const platformNavItems: NavItem[] = [
  {
    path: '/dashboard',
    title: '平台首页',
    icon: DataAnalysis,
  },
  {
    path: '/km',
    title: '知识管理',
    icon: Collection,
    children: [
      { path: '/km/bases', title: '知识库管理' },
      { path: '/km/documents', title: '文档管理' },
      { path: '/km/search', title: '知识检索' },
    ],
  },
  {
    path: '/qa',
    title: '智能问答',
    icon: ChatDotRound,
    children: [
      { path: '/qa/chat', title: '智能对话' },
      { path: '/qa/conversations', title: '会话记录' },
      { path: '/qa/retrieval-test', title: '检索测试', admin: true },
      { path: '/qa/settings', title: '问答配置', admin: true },
    ],
  },
  {
    path: '/reports',
    title: '报告生成',
    icon: DocumentCopy,
    children: [
      { path: '/reports', title: '报告记录' },
      { path: '/reports/new', title: '新建报告' },
      { path: '/reports/dashboard', title: '趋势统计', admin: true },
      { path: '/reports/templates', title: '模板管理', admin: true },
      { path: '/reports/materials', title: '素材管理', admin: true },
    ],
  },
  {
    path: '/model-config',
    title: '模型配置',
    icon: SetUp,
    admin: true,
    children: [
      { path: '/km/settings', title: '知识模型配置', admin: true },
      { path: '/qa/llm', title: 'LLM / 报告模型配置', admin: true },
    ],
  },
  {
    path: '/admin',
    title: '系统管理',
    icon: Management,
    admin: true,
    children: [
      { path: '/admin/overview', title: '总览统计' },
      { path: '/admin/users', title: '用户管理' },
      { path: '/admin/roles', title: '角色权限' },
    ],
  },
]

export const quickAccessCards = [
  {
    title: '知识管理',
    description: '已接入知识库列表、文档管理、知识检索页面。',
    icon: FolderOpened,
    links: [
      { label: '知识库管理', to: '/km/bases' },
      { label: '文档管理', to: '/km/documents' },
      { label: '知识检索', to: '/km/search' },
    ],
  },
  {
    title: '智能问答',
    description: '已接入智能问答页、会话记录、检索测试和问答配置页面。',
    icon: ChatDotRound,
    links: [
      { label: '智能对话', to: '/qa/chat' },
      { label: '会话记录', to: '/qa/conversations' },
      { label: '问答配置', to: '/qa/settings' },
    ],
  },
  {
    title: '报告生成',
    description: '已接入报告记录、新建、大纲、工作台、导出、趋势统计和模板管理页面。',
    icon: DocumentCopy,
    links: [
      { label: '报告记录', to: '/reports' },
      { label: '新建报告', to: '/reports/new' },
      { label: '趋势统计', to: '/reports/dashboard' },
    ],
  },
  {
    title: '模型配置',
    description: '集中维护知识模型和统一 LLM 配置，报告生成复用同一套模型配置。',
    icon: SetUp,
    links: [
      { label: '知识模型配置', to: '/km/settings' },
      { label: 'LLM / 报告模型配置', to: '/qa/llm' },
    ],
  },
] as const

export const mergePhases = [
  {
    title: '第 1 次',
    subtitle: '统一入口壳',
    items: ['统一登录', '统一 Layout', '统一侧边栏', '统一路由聚合', '统一权限守卫'],
    icon: Notebook,
  },
  {
    title: '第 2 次',
    subtitle: '页面骨架合并',
    items: ['知识管理：知识库列表、文档管理、知识检索', '智能问答：问答页、会话列表、问答配置', '报告生成：新建报告、报告工作台、趋势统计'],
    icon: DocumentAdd,
  },
  {
    title: '第 3 次',
    subtitle: '业务接口合并',
    items: ['统一接口封装', '统一鉴权链路', '统一错误处理', '逐步删除旧入口'],
    icon: Setting,
  },
] as const

export const dashboardStats = [
  { label: '统一入口', value: '1 个', icon: Histogram },
  { label: '聚合模块', value: '3 个', icon: Collection },
  { label: '一级菜单', value: '6 项', icon: Search },
  { label: '权限角色', value: '3 类', icon: UserFilled },
] as const
