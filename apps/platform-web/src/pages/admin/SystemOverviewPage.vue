<script setup lang="ts">
const summaryCards = [
  { label: '平台用户', value: '36', delta: '+4 本周', tone: 'blue' },
  { label: '管理员', value: '6', delta: '2 个值班', tone: 'cyan' },
  { label: '角色组', value: '4', delta: '权限已收口', tone: 'green' },
  { label: '受控菜单', value: '21', delta: '3 个模块', tone: 'orange' },
] as const

const moduleCards = [
  { name: '知识管理', owner: 'A 组', status: '已接入', users: 18, permission: '普通用户可访问，模型配置管理员可见' },
  { name: '智能问答', owner: 'B 组', status: '已接入', users: 24, permission: '对话开放，检索测试和配置管理员可见' },
  { name: '报告生成', owner: 'C 组', status: '已接入', users: 20, permission: '报告记录开放，模板和素材映射管理员可见' },
] as const

const securityItems = [
  { label: '统一登录', value: '已启用', tone: 'success' },
  { label: '路由守卫', value: 'ADMIN / USER', tone: 'success' },
  { label: '菜单隐藏', value: '按角色过滤', tone: 'success' },
  { label: '接口鉴权', value: '第三次合并', tone: 'warning' },
] as const

const recentEvents = [
  { time: '今天 11:30', title: '统一入口完成系统管理页优化', detail: '总览、用户、角色三个页面替换为平台专用骨架。' },
  { time: '今天 10:40', title: '报告模块接入 reports/* 页面', detail: '报告记录、新建报告、模板管理已聚合。' },
  { time: '昨天 18:20', title: '问答与知识管理路由收口', detail: '保留原模块路径兼容，统一走平台 Layout。' },
] as const
</script>

<template>
  <div class="page admin-page">
    <section class="admin-hero">
      <div>
        <span class="eyebrow">SYSTEM CONTROL</span>
        <h1>系统管理总览</h1>
        <p>这里聚合用户、角色、菜单权限和三个业务模块的接入状态，作为管理员进入平台后的第一张系统视图。</p>
      </div>
      <div class="admin-hero-actions">
        <RouterLink to="/admin/users" class="admin-link primary">管理用户</RouterLink>
        <RouterLink to="/admin/roles" class="admin-link">配置角色</RouterLink>
      </div>
    </section>

    <section class="admin-stat-grid">
      <article v-for="item in summaryCards" :key="item.label" class="admin-stat-card" :class="item.tone">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.delta }}</small>
      </article>
    </section>

    <section class="admin-overview-layout">
      <div class="surface admin-main-panel">
        <div class="surface-title">
          <div>
            <span class="eyebrow">MODULE ACCESS</span>
            <h2>模块接入与权限边界</h2>
          </div>
        </div>

        <div class="module-access-list">
          <article v-for="item in moduleCards" :key="item.name" class="module-access-card">
            <div class="module-mark">{{ item.name.slice(0, 2) }}</div>
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.permission }}</p>
            </div>
            <span>{{ item.owner }}</span>
            <em>{{ item.users }} 人</em>
            <b>{{ item.status }}</b>
          </article>
        </div>
      </div>

      <aside class="admin-side-stack">
        <section class="surface admin-side-panel">
          <span class="eyebrow">SECURITY</span>
          <h2>安全状态</h2>
          <div class="security-list">
            <div v-for="item in securityItems" :key="item.label" class="security-row">
              <span class="status-dot" :class="item.tone"></span>
              <div>
                <strong>{{ item.label }}</strong>
                <small>{{ item.value }}</small>
              </div>
            </div>
          </div>
        </section>

        <section class="surface admin-side-panel">
          <span class="eyebrow">RECENT</span>
          <h2>最近管理动作</h2>
          <div class="event-list">
            <div v-for="item in recentEvents" :key="item.title" class="event-row">
              <time>{{ item.time }}</time>
              <strong>{{ item.title }}</strong>
              <p>{{ item.detail }}</p>
            </div>
          </div>
        </section>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.admin-page {
  display: grid;
  gap: 18px;
}

.admin-hero {
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
    radial-gradient(circle at 92% 8%, rgba(0, 184, 217, 0.18), transparent 30%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(238, 246, 255, 0.9));
  box-shadow: var(--platform-shadow-soft);
}

.admin-hero h1 {
  margin: 8px 0 10px;
  color: var(--platform-text-strong);
  font-family: var(--font-display);
  font-size: clamp(32px, 5vw, 48px);
  letter-spacing: -0.03em;
}

.admin-hero p {
  max-width: 740px;
  margin: 0;
  color: var(--platform-text-muted);
  line-height: 1.8;
}

.admin-hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.admin-link {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  border: 1px solid rgba(30, 107, 255, 0.18);
  border-radius: 999px;
  color: var(--accent-blue);
  background: rgba(255, 255, 255, 0.7);
  font-weight: 800;
}

.admin-link.primary {
  color: #fff;
  background: linear-gradient(135deg, var(--accent-blue), var(--accent-cyan));
  box-shadow: 0 14px 30px rgba(30, 107, 255, 0.22);
}

.admin-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.admin-stat-card {
  display: grid;
  gap: 8px;
  padding: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--platform-shadow-soft);
}

.admin-stat-card span,
.admin-stat-card small {
  color: var(--platform-text-muted);
}

.admin-stat-card strong {
  color: var(--platform-text-strong);
  font-family: var(--font-display);
  font-size: 34px;
  line-height: 1;
}

.admin-stat-card.blue {
  border-color: rgba(30, 107, 255, 0.2);
}

.admin-stat-card.cyan {
  border-color: rgba(0, 184, 217, 0.22);
}

.admin-stat-card.green {
  border-color: rgba(22, 163, 74, 0.22);
}

.admin-stat-card.orange {
  border-color: rgba(245, 158, 11, 0.24);
}

.admin-overview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
}

.admin-main-panel,
.admin-side-panel {
  padding-bottom: 20px;
}

.module-access-list {
  display: grid;
  gap: 12px;
  padding: 0 20px;
}

.module-access-card {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) 80px 70px 74px;
  gap: 14px;
  align-items: center;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.72);
}

.module-mark {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(135deg, var(--accent-blue), var(--accent-cyan));
  font-weight: 900;
}

.module-access-card strong,
.module-access-card b,
.security-row strong,
.event-row strong {
  color: var(--platform-text-strong);
}

.module-access-card p {
  margin: 4px 0 0;
  color: var(--platform-text-muted);
  line-height: 1.6;
}

.module-access-card span,
.module-access-card em {
  color: var(--platform-text-muted);
  font-style: normal;
}

.module-access-card b {
  width: max-content;
  padding: 5px 10px;
  border-radius: 999px;
  color: var(--state-success);
  background: rgba(22, 163, 74, 0.1);
  font-size: 12px;
}

.admin-side-stack {
  display: grid;
  gap: 16px;
  align-content: start;
}

.admin-side-panel {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.admin-side-panel h2 {
  margin: 0;
  color: var(--platform-text-strong);
}

.security-list,
.event-list {
  display: grid;
  gap: 12px;
}

.security-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--platform-text-muted);
}

.status-dot.success {
  background: var(--state-success);
  box-shadow: 0 0 0 5px rgba(22, 163, 74, 0.1);
}

.status-dot.warning {
  background: var(--state-warning);
  box-shadow: 0 0 0 5px rgba(245, 158, 11, 0.12);
}

.security-row small,
.event-row time,
.event-row p {
  color: var(--platform-text-muted);
}

.event-row {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.68);
}

.event-row p {
  margin: 0;
  line-height: 1.6;
}

@media (max-width: 1180px) {
  .admin-hero,
  .admin-overview-layout {
    grid-template-columns: 1fr;
  }

  .admin-hero {
    display: grid;
    align-items: start;
  }

  .admin-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .admin-hero,
  .admin-side-panel {
    padding: 18px;
    border-radius: 20px;
  }

  .admin-stat-grid,
  .module-access-card {
    grid-template-columns: 1fr;
  }
}
</style>
