<script setup lang="ts">
import { dashboardStats, mergePhases, quickAccessCards } from '../layout/nav'

const moduleHealth = [
  { name: '知识管理', status: '页面骨架已接入', tone: 'success', progress: '80%', note: '知识库、文档、检索入口已统一' },
  { name: '智能问答', status: '问答主链路已接入', tone: 'success', progress: '76%', note: '聊天、会话、配置页已进入平台路由' },
  { name: '报告生成', status: 'reports 页面已替换', tone: 'warning', progress: '72%', note: '报告记录、新建、工作台和模板页已收拢' },
] as const

const focusTasks = [
  '把空白模块补成静态页面骨架，先保证演示完整度',
  '保留各模块原接口路径，第三次集成再统一 request 封装',
  '逐步抽公共表格、筛选栏、状态标签，减少重复代码',
] as const
</script>

<template>
  <div class="page dashboard-page">
    <section class="hero-board">
      <div class="hero-copy">
        <span class="eyebrow">TECH SUPERVISION COMMAND CENTER</span>
        <h1>技术监督辅助平台统一入口</h1>
        <p>
          三个小任务已经收拢到同一个前端入口。当前重点是让知识管理、智能问答、报告生成在同一套导航、布局和权限体系下稳定展示。
        </p>
        <div class="hero-actions">
          <RouterLink to="/km/bases" class="hero-button primary">进入知识库</RouterLink>
          <RouterLink to="/qa/chat" class="hero-button">发起问答</RouterLink>
          <RouterLink to="/reports/new" class="hero-button">新建报告</RouterLink>
        </div>
      </div>

      <div class="hero-panel">
        <div class="orbit-card">
          <span>Integrated Apps</span>
          <strong>3</strong>
          <small>KM / QA / Report</small>
        </div>
        <div class="orbit-lines">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </div>
    </section>

    <section class="metric-grid">
      <article v-for="item in dashboardStats" :key="item.label" class="surface metric-card">
        <div class="metric-icon">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
        </div>
        <div>
          <strong>{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </article>
    </section>

    <section class="dashboard-layout">
      <div class="surface section-card status-board">
        <div class="surface-title">
          <div>
            <span class="eyebrow">MODULE STATUS</span>
            <h2>模块接入状态</h2>
          </div>
          <span class="board-badge">第二次页面骨架合并中</span>
        </div>

        <div class="health-list">
          <article v-for="item in moduleHealth" :key="item.name" class="health-card">
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.note }}</p>
            </div>
            <span class="health-status" :class="item.tone">{{ item.status }}</span>
            <div class="health-progress">
              <span :style="{ width: item.progress }"></span>
            </div>
          </article>
        </div>
      </div>

      <aside class="surface section-card focus-card">
        <div class="surface-title compact">
          <div>
            <span class="eyebrow">TODAY FOCUS</span>
            <h2>当前能补什么</h2>
          </div>
        </div>
        <ul class="focus-list">
          <li v-for="task in focusTasks" :key="task">{{ task }}</li>
        </ul>
      </aside>
    </section>

    <section class="surface section-card">
      <div class="surface-title">
        <div>
          <span class="eyebrow">QUICK ACCESS</span>
          <h2>模块入口</h2>
        </div>
      </div>
      <div class="card-grid">
        <article v-for="card in quickAccessCards" :key="card.title" class="module-card interactive-lift">
          <div class="module-head">
            <span class="module-icon">
              <el-icon :size="20"><component :is="card.icon" /></el-icon>
            </span>
            <div>
              <strong>{{ card.title }}</strong>
              <p>{{ card.description }}</p>
            </div>
          </div>
          <div class="link-row">
            <RouterLink v-for="link in card.links" :key="link.to" :to="link.to" class="module-link">
              {{ link.label }}
            </RouterLink>
          </div>
        </article>
      </div>
    </section>

    <section class="surface section-card">
      <div class="surface-title">
        <div>
          <span class="eyebrow">MERGE PLAN</span>
          <h2>合并节奏</h2>
        </div>
      </div>
      <div class="phase-grid">
        <article v-for="phase in mergePhases" :key="phase.title" class="phase-card">
          <div class="phase-head">
            <span class="phase-icon">
              <el-icon :size="18"><component :is="phase.icon" /></el-icon>
            </span>
            <div>
              <strong>{{ phase.title }}</strong>
              <p>{{ phase.subtitle }}</p>
            </div>
          </div>
          <ul class="phase-list">
            <li v-for="item in phase.items" :key="item">{{ item }}</li>
          </ul>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 18px;
}

.hero-board {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 24px;
  min-height: 280px;
  padding: 34px;
  border: 1px solid rgba(30, 107, 255, 0.14);
  border-radius: 30px;
  background:
    radial-gradient(circle at 78% 18%, rgba(0, 184, 217, 0.24), transparent 30%),
    radial-gradient(circle at 12% 20%, rgba(30, 107, 255, 0.16), transparent 28%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.97), rgba(238, 246, 255, 0.9));
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.1);
}

.hero-board::after {
  position: absolute;
  inset: auto -90px -130px auto;
  width: 320px;
  height: 320px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(30, 107, 255, 0.16), rgba(0, 184, 217, 0.12));
  content: "";
}

.hero-copy {
  position: relative;
  z-index: 1;
  display: grid;
  align-content: center;
  gap: 18px;
}

.hero-copy h1 {
  max-width: 780px;
  margin: 0;
  color: var(--platform-text-strong);
  font-family: var(--font-display);
  font-size: clamp(34px, 5vw, 58px);
  letter-spacing: -0.04em;
  line-height: 1.02;
}

.hero-copy p {
  max-width: 760px;
  margin: 0;
  color: var(--platform-text-muted);
  font-size: 16px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.hero-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid rgba(30, 107, 255, 0.18);
  border-radius: 999px;
  color: var(--accent-blue);
  background: rgba(255, 255, 255, 0.68);
  font-weight: 800;
  box-shadow: var(--shadow-xs);
  transition:
    transform 180ms var(--ease-standard),
    box-shadow 180ms var(--ease-standard),
    background 180ms var(--ease-standard);
}

.hero-button.primary {
  color: #fff;
  background: linear-gradient(135deg, var(--accent-blue), #00a7d8);
  box-shadow: 0 14px 30px rgba(30, 107, 255, 0.24);
}

.hero-button:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.hero-panel {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  min-height: 220px;
}

.orbit-card {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 190px;
  height: 190px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 44px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.58)),
    linear-gradient(135deg, rgba(30, 107, 255, 0.1), rgba(0, 184, 217, 0.1));
  box-shadow: 0 24px 70px rgba(30, 107, 255, 0.16);
}

.orbit-card span,
.orbit-card small {
  color: var(--platform-text-muted);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.orbit-card strong {
  color: var(--accent-blue);
  font-family: var(--font-display);
  font-size: 72px;
  line-height: 0.9;
}

.orbit-lines,
.orbit-lines span {
  position: absolute;
  border: 1px solid rgba(30, 107, 255, 0.12);
  border-radius: 999px;
}

.orbit-lines {
  inset: 18px;
}

.orbit-lines span:nth-child(1) {
  inset: 28px;
}

.orbit-lines span:nth-child(2) {
  inset: 56px;
  border-color: rgba(0, 184, 217, 0.16);
}

.orbit-lines span:nth-child(3) {
  inset: 84px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(255, 255, 255, 0.76));
  box-shadow: var(--platform-shadow-soft);
}

.metric-card strong,
.module-card strong,
.phase-card strong {
  display: block;
  color: var(--text-primary);
}

.metric-card strong {
  font-size: 24px;
}

.metric-card span {
  color: var(--text-secondary);
}

.metric-icon,
.module-icon,
.phase-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}

.metric-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  color: var(--accent-blue);
  background: var(--accent-blue-soft);
}

.section-card {
  padding-bottom: 18px;
}

.dashboard-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
}

.status-board,
.focus-card {
  overflow: hidden;
}

.board-badge {
  align-self: start;
  padding: 6px 12px;
  border-radius: 999px;
  color: var(--state-success);
  background: rgba(22, 163, 74, 0.1);
  font-size: 12px;
  font-weight: 800;
}

.health-list {
  display: grid;
  gap: 12px;
  padding: 0 20px 20px;
}

.health-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.72);
}

.health-card strong {
  color: var(--platform-text-strong);
  font-size: 16px;
}

.health-card p {
  margin: 4px 0 0;
  color: var(--platform-text-muted);
}

.health-status {
  padding: 5px 10px;
  border-radius: 999px;
  color: var(--accent-blue);
  background: rgba(30, 107, 255, 0.1);
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.health-status.success {
  color: var(--state-success);
  background: rgba(22, 163, 74, 0.1);
}

.health-status.warning {
  color: var(--state-warning);
  background: rgba(245, 158, 11, 0.12);
}

.health-progress {
  grid-column: 1 / -1;
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
}

.health-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent-blue), var(--accent-cyan));
}

.surface-title.compact {
  padding-bottom: 10px;
}

.focus-list {
  display: grid;
  gap: 12px;
  padding: 0 20px 20px;
}

.focus-list li {
  position: relative;
  padding: 13px 14px 13px 38px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 16px;
  color: var(--platform-text-muted);
  background: rgba(248, 250, 252, 0.72);
  line-height: 1.6;
}

.focus-list li::before {
  position: absolute;
  top: 17px;
  left: 16px;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--accent-cyan);
  box-shadow: 0 0 0 5px rgba(0, 184, 217, 0.12);
  content: "";
}

.card-grid,
.phase-grid {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.card-grid,
.phase-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.module-card,
.phase-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.86));
}

.module-head,
.phase-head {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.module-icon,
.phase-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  color: var(--accent-cyan);
  background: var(--accent-cyan-soft);
}

.module-head p,
.phase-head p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  line-height: 1.6;
}

.link-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.module-link {
  padding: 7px 12px;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  color: var(--accent-blue);
  background: var(--bg-surface);
  transition:
    border-color 160ms var(--ease-standard),
    transform 160ms var(--ease-standard);
}

.module-link:hover {
  border-color: var(--accent-blue);
  transform: translateY(-1px);
}

.phase-list {
  display: grid;
  gap: 8px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.phase-list li {
  position: relative;
  padding-left: 14px;
}

.phase-list li::before {
  position: absolute;
  top: 9px;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-blue);
  content: "";
}

@media (max-width: 1180px) {
  .hero-board,
  .dashboard-layout {
    grid-template-columns: 1fr;
  }

  .metric-grid,
  .card-grid,
  .phase-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .hero-board {
    padding: 22px;
    border-radius: 22px;
  }

  .hero-panel {
    display: none;
  }

  .metric-grid,
  .card-grid,
  .phase-grid {
    grid-template-columns: 1fr;
  }

  .health-card {
    grid-template-columns: 1fr;
  }
}
</style>
