<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

type Tone = 'success' | 'warning' | 'neutral'

interface StatItem {
  label: string
  value: string
  tone?: Tone
}

interface WorkbenchRow {
  name: string
  owner: string
  status: string
  statusTone: Tone
  updatedAt: string
}

interface ModuleBlueprint {
  eyebrow: string
  title: string
  description: string
  stats: StatItem[]
  actions: string[]
  tableTitle: string
  tableDescription: string
  rows: WorkbenchRow[]
  roadmap: string[]
  tips: string[]
}

const route = useRoute()

const moduleBlueprints: Record<string, ModuleBlueprint> = {
  '/reports/materials': {
    eyebrow: 'Report Material Mapping',
    title: '素材映射',
    description:
      '用于维护“素材字段 -> 报告模板占位符”的映射关系，让报告生成页能够复用知识库素材和系统基础信息。',
    stats: [
      { label: '映射规则', value: '42', tone: 'success' },
      { label: '待绑定字段', value: '8', tone: 'warning' },
      { label: '覆盖模板', value: '6 套', tone: 'neutral' },
    ],
    actions: ['新建映射', '导入规则', '检查缺口'],
    tableTitle: '映射关系预览',
    tableDescription: '后续第三次接口合并时，可把这里改成真实模板字段映射管理。',
    rows: [
      { name: '电厂基础信息 -> 报告封面', owner: '运行月报模板', status: '已启用', statusTone: 'success', updatedAt: '今天 11:05' },
      { name: '缺陷案例摘要 -> 风险分析章节', owner: '技术监督报告', status: '待校验', statusTone: 'warning', updatedAt: '今天 08:58' },
      { name: '专家建议 -> 整改建议章节', owner: '专项检查报告', status: '已启用', statusTone: 'success', updatedAt: '昨天 17:22' },
    ],
    roadmap: ['统一模板占位符命名', '接入报告模板字段接口', '生成前检查缺失素材'],
    tips: ['这个页面能连接知识管理和报告生成', '适合放字段映射、规则校验、导入导出', '暂时不影响已有 reports/* 主流程'],
  },
  '/admin/users': {
    eyebrow: 'System Identity',
    title: '用户管理',
    description:
      '统一维护平台用户、账号状态和所属角色，为后续统一登录、权限守卫和顶部用户信息提供管理入口。',
    stats: [
      { label: '平台用户', value: '36', tone: 'success' },
      { label: '待启用', value: '4', tone: 'warning' },
      { label: '角色类型', value: '3 类', tone: 'neutral' },
    ],
    actions: ['新建用户', '导入名单', '停用账号'],
    tableTitle: '用户列表预览',
    tableDescription: '这里先放管理员能理解的字段，后面接真实用户接口即可。',
    rows: [
      { name: '系统管理员', owner: 'ADMIN', status: '启用中', statusTone: 'success', updatedAt: '今天 09:10' },
      { name: '技术监督专责', owner: 'USER', status: '启用中', statusTone: 'success', updatedAt: '昨天 16:45' },
      { name: '报告审核员', owner: 'USER', status: '待启用', statusTone: 'warning', updatedAt: '昨天 11:20' },
    ],
    roadmap: ['接入统一账号列表', '支持角色批量分配', '补充登录日志与状态筛选'],
    tips: ['目前权限守卫已经按 ADMIN/USER 工作', '用户管理页先作为系统管理入口占位', '后续可与后端 JWT 用户模型对齐'],
  },
  '/admin/roles': {
    eyebrow: 'Permission Matrix',
    title: '角色权限',
    description:
      '统一维护管理员、普通用户等角色的菜单权限和能力开关，避免三个子系统各自判断权限导致行为不一致。',
    stats: [
      { label: '角色类型', value: '3 类', tone: 'success' },
      { label: '菜单权限', value: '21', tone: 'neutral' },
      { label: '待确认能力', value: '5', tone: 'warning' },
    ],
    actions: ['新建角色', '权限矩阵', '复制权限'],
    tableTitle: '权限矩阵预览',
    tableDescription: '静态阶段先把权限边界展示清楚，后面接菜单权限接口。',
    rows: [
      { name: 'ADMIN -> 系统管理', owner: '全量菜单', status: '已开放', statusTone: 'success', updatedAt: '今天 10:32' },
      { name: 'USER -> 报告生成', owner: '基础能力', status: '已开放', statusTone: 'success', updatedAt: '昨天 15:15' },
      { name: 'USER -> 模型配置', owner: '管理员能力', status: '已限制', statusTone: 'warning', updatedAt: '昨天 12:40' },
    ],
    roadmap: ['补充菜单权限树', '统一前端路由 meta.admin', '对接后端能力点校验'],
    tips: ['当前侧边栏已隐藏系统管理子菜单', '路由守卫会拦截非管理员访问', '这里后续适合做成可视化权限矩阵'],
  },
}

const fallbackBlueprint: ModuleBlueprint = {
  eyebrow: 'Module Workbench',
  title: computed(() => (route.meta.title as string) || '页面建设中').value,
  description:
    (route.meta.description as string) ||
    '该页面已经接入统一入口，当前先提供静态工作台骨架，后续可逐步替换为真实业务数据。',
  stats: [
    { label: '路由状态', value: '已接入', tone: 'success' },
    { label: '页面骨架', value: '待补全', tone: 'warning' },
    { label: '接口状态', value: '未合并', tone: 'neutral' },
  ],
  actions: ['补页面骨架', '确认字段', '接入接口'],
  tableTitle: '模块内容预览',
  tableDescription: '先保留展示结构，避免菜单点击后出现空白页。',
  rows: [
    { name: '页面结构确认', owner: '平台入口', status: '进行中', statusTone: 'warning', updatedAt: '今天' },
    { name: '接口路径梳理', owner: '业务模块', status: '待合并', statusTone: 'neutral', updatedAt: '后续' },
    { name: '权限边界校验', owner: '系统管理', status: '已预留', statusTone: 'success', updatedAt: '后续' },
  ],
  roadmap: ['确认静态 UI', '抽取公共组件', '接入真实接口'],
  tips: ['优先保证导航完整', '再逐步替换静态示例数据', '不要一次性重构业务接口'],
}

const blueprint = computed(() => moduleBlueprints[route.path] || fallbackBlueprint)
</script>

<template>
  <div class="page placeholder-page">
    <section class="placeholder-hero">
      <div>
        <span class="eyebrow">{{ blueprint.eyebrow }}</span>
        <h1>{{ blueprint.title }}</h1>
        <p>{{ blueprint.description }}</p>
      </div>
      <div class="placeholder-actions">
        <el-button v-for="action in blueprint.actions" :key="action" type="primary" plain>
          {{ action }}
        </el-button>
      </div>
    </section>

    <section class="placeholder-stats">
      <article v-for="item in blueprint.stats" :key="item.label" class="placeholder-stat">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small :class="item.tone">静态预览</small>
      </article>
    </section>

    <section class="placeholder-layout">
      <div class="surface placeholder-main">
        <div class="surface-title">
          <div>
            <span class="eyebrow">Preview Table</span>
            <h2>{{ blueprint.tableTitle }}</h2>
            <p>{{ blueprint.tableDescription }}</p>
          </div>
        </div>

        <div class="platform-table placeholder-table">
          <div class="platform-table-row header">
            <span>名称</span>
            <span>归属</span>
            <span>状态</span>
            <span>更新时间</span>
          </div>
          <div v-for="row in blueprint.rows" :key="row.name" class="platform-table-row">
            <strong>{{ row.name }}</strong>
            <span>{{ row.owner }}</span>
            <span class="platform-chip" :class="row.statusTone">{{ row.status }}</span>
            <span>{{ row.updatedAt }}</span>
          </div>
        </div>
      </div>

      <aside class="placeholder-side">
        <section class="surface placeholder-panel">
          <span class="eyebrow">Next Steps</span>
          <h2>后续接入清单</h2>
          <div class="platform-timeline">
            <div v-for="item in blueprint.roadmap" :key="item" class="platform-timeline-item">
              <div>
                <strong>{{ item }}</strong>
                <span>第三次业务接口合并时逐步替换静态数据。</span>
              </div>
            </div>
          </div>
        </section>

        <section class="surface placeholder-panel">
          <span class="eyebrow">Integration Notes</span>
          <h2>合并提示</h2>
          <ul class="placeholder-tips">
            <li v-for="tip in blueprint.tips" :key="tip">{{ tip }}</li>
          </ul>
        </section>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.placeholder-page {
  display: grid;
  gap: 18px;
}

.placeholder-hero {
  position: relative;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  padding: 30px;
  border: 1px solid rgba(30, 107, 255, 0.14);
  border-radius: 28px;
  background:
    radial-gradient(circle at 92% 8%, rgba(0, 184, 217, 0.18), transparent 28%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(238, 246, 255, 0.9));
  box-shadow: var(--platform-shadow-soft);
}

.placeholder-hero::after {
  position: absolute;
  right: -80px;
  bottom: -110px;
  width: 250px;
  height: 250px;
  border-radius: 999px;
  background: rgba(30, 107, 255, 0.1);
  content: "";
}

.placeholder-hero > * {
  position: relative;
  z-index: 1;
}

.placeholder-hero h1 {
  margin: 8px 0 10px;
  color: var(--platform-text-strong);
  font-family: var(--font-display);
  font-size: clamp(32px, 5vw, 48px);
  letter-spacing: -0.03em;
}

.placeholder-hero p {
  max-width: 780px;
  margin: 0;
  color: var(--platform-text-muted);
  font-size: 15px;
  line-height: 1.8;
}

.placeholder-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  min-width: 260px;
}

.placeholder-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.placeholder-stat {
  display: grid;
  gap: 8px;
  padding: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--platform-shadow-soft);
}

.placeholder-stat span {
  color: var(--platform-text-muted);
}

.placeholder-stat strong {
  color: var(--platform-text-strong);
  font-family: var(--font-display);
  font-size: 34px;
  line-height: 1;
}

.placeholder-stat small {
  width: max-content;
  padding: 4px 10px;
  border-radius: 999px;
  color: var(--platform-text-muted);
  background: rgba(100, 116, 139, 0.1);
  font-weight: 800;
}

.placeholder-stat small.success {
  color: var(--state-success);
  background: rgba(22, 163, 74, 0.1);
}

.placeholder-stat small.warning {
  color: var(--state-warning);
  background: rgba(245, 158, 11, 0.12);
}

.placeholder-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
}

.placeholder-main,
.placeholder-panel {
  padding: 20px;
}

.placeholder-main {
  display: grid;
  gap: 16px;
}

.placeholder-main .surface-title {
  padding: 0;
}

.placeholder-main .surface-title p {
  margin: 6px 0 0;
  color: var(--platform-text-muted);
  line-height: 1.7;
}

.placeholder-side {
  display: grid;
  gap: 16px;
  align-content: start;
}

.placeholder-panel {
  display: grid;
  gap: 16px;
}

.placeholder-panel h2 {
  margin: 0;
  color: var(--platform-text-strong);
}

.placeholder-tips {
  display: grid;
  gap: 10px;
}

.placeholder-tips li {
  position: relative;
  padding: 12px 12px 12px 34px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 14px;
  color: var(--platform-text-muted);
  background: rgba(248, 250, 252, 0.72);
  line-height: 1.6;
}

.placeholder-tips li::before {
  position: absolute;
  top: 17px;
  left: 14px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--accent-blue);
  content: "";
}

@media (max-width: 1180px) {
  .placeholder-hero,
  .placeholder-layout {
    grid-template-columns: 1fr;
  }

  .placeholder-hero {
    display: grid;
    align-items: start;
  }

  .placeholder-actions {
    justify-content: flex-start;
    min-width: 0;
  }
}

@media (max-width: 760px) {
  .placeholder-hero,
  .placeholder-main,
  .placeholder-panel {
    padding: 16px;
    border-radius: 18px;
  }

  .placeholder-stats {
    grid-template-columns: 1fr;
  }
}
</style>
