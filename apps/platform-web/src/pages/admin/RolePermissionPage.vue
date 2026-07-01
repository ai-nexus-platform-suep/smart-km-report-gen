<script setup lang="ts">
import { computed, ref } from 'vue'

const selectedRoleId = ref('admin')

const roles = [
  {
    id: 'admin',
    name: '管理员',
    desc: '维护系统配置、用户、角色和所有业务模块管理能力。',
    users: 6,
    tone: 'danger',
    modules: [
      { name: '平台首页', view: true, edit: true, config: true },
      { name: '知识管理', view: true, edit: true, config: true },
      { name: '智能问答', view: true, edit: true, config: true },
      { name: '报告生成', view: true, edit: true, config: true },
      { name: '系统管理', view: true, edit: true, config: true },
    ],
  },
  {
    id: 'user',
    name: '普通用户',
    desc: '使用知识检索、智能问答和报告生成，不开放系统配置。',
    users: 30,
    tone: 'info',
    modules: [
      { name: '平台首页', view: true, edit: false, config: false },
      { name: '知识管理', view: true, edit: false, config: false },
      { name: '智能问答', view: true, edit: false, config: false },
      { name: '报告生成', view: true, edit: true, config: false },
      { name: '系统管理', view: false, edit: false, config: false },
    ],
  },
  {
    id: 'reviewer',
    name: '报告审核员',
    desc: '聚焦报告查看、校验和导出，暂不开放用户与模型配置。',
    users: 4,
    tone: 'warning',
    modules: [
      { name: '平台首页', view: true, edit: false, config: false },
      { name: '知识管理', view: true, edit: false, config: false },
      { name: '智能问答', view: false, edit: false, config: false },
      { name: '报告生成', view: true, edit: true, config: false },
      { name: '系统管理', view: false, edit: false, config: false },
    ],
  },
] as const

const selectedRole = computed(() => roles.find((item) => item.id === selectedRoleId.value) || roles[0])

const guardRules = [
  '侧边栏按 admin 字段隐藏管理员菜单',
  '路由 meta.admin 会拦截非管理员访问',
  '第三次接口合并时再补后端能力点校验',
] as const
</script>

<template>
  <div class="page role-page">
    <section class="role-head">
      <div>
        <span class="eyebrow">PERMISSION MATRIX</span>
        <h1>角色权限</h1>
        <p>把菜单权限和能力开关放到同一张矩阵里，避免三个子系统各写一套权限判断。</p>
      </div>
      <el-button type="primary">新建角色</el-button>
    </section>

    <section class="role-layout">
      <aside class="surface role-list-panel">
        <span class="eyebrow">ROLES</span>
        <h2>角色组</h2>
        <button
          v-for="role in roles"
          :key="role.id"
          class="role-card"
          :class="{ active: selectedRoleId === role.id }"
          @click="selectedRoleId = role.id"
        >
          <div>
            <strong>{{ role.name }}</strong>
            <span>{{ role.users }} 人</span>
          </div>
          <p>{{ role.desc }}</p>
        </button>
      </aside>

      <div class="surface matrix-panel">
        <div class="matrix-title">
          <div>
            <span class="eyebrow">ACTIVE ROLE</span>
            <h2>{{ selectedRole.name }}</h2>
            <p>{{ selectedRole.desc }}</p>
          </div>
          <el-tag :type="selectedRole.tone" effect="plain">{{ selectedRole.users }} 个用户</el-tag>
        </div>

        <div class="permission-table">
          <div class="permission-row header">
            <span>模块</span>
            <span>可查看</span>
            <span>可编辑</span>
            <span>可配置</span>
          </div>
          <div v-for="item in selectedRole.modules" :key="item.name" class="permission-row">
            <strong>{{ item.name }}</strong>
            <span class="permission-dot" :class="{ on: item.view }">{{ item.view ? '允许' : '禁止' }}</span>
            <span class="permission-dot" :class="{ on: item.edit }">{{ item.edit ? '允许' : '禁止' }}</span>
            <span class="permission-dot" :class="{ on: item.config }">{{ item.config ? '允许' : '禁止' }}</span>
          </div>
        </div>
      </div>

      <aside class="surface guard-panel">
        <span class="eyebrow">GUARD RAILS</span>
        <h2>权限守卫说明</h2>
        <ul>
          <li v-for="rule in guardRules" :key="rule">{{ rule }}</li>
        </ul>
        <div class="guard-tip">
          <strong>建议</strong>
          <p>后续可以把这里的静态矩阵替换为后端返回的菜单树，然后仍然复用当前页面布局。</p>
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.role-page {
  display: grid;
  gap: 18px;
}

.role-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-end;
  padding: 26px;
  border: 1px solid rgba(30, 107, 255, 0.14);
  border-radius: 26px;
  background:
    radial-gradient(circle at 92% 0%, rgba(0, 184, 217, 0.16), transparent 28%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(238, 246, 255, 0.88));
  box-shadow: var(--platform-shadow-soft);
}

.role-head h1 {
  margin: 8px 0 8px;
  color: var(--platform-text-strong);
  font-family: var(--font-display);
  font-size: clamp(30px, 4vw, 44px);
}

.role-head p,
.matrix-title p,
.role-card p,
.guard-tip p {
  margin: 0;
  color: var(--platform-text-muted);
  line-height: 1.7;
}

.role-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 320px;
  gap: 16px;
}

.role-list-panel,
.matrix-panel,
.guard-panel {
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 20px;
}

.role-list-panel h2,
.matrix-title h2,
.guard-panel h2 {
  margin: 0;
  color: var(--platform-text-strong);
}

.role-card {
  display: grid;
  gap: 8px;
  width: 100%;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 16px;
  text-align: left;
  background: rgba(248, 250, 252, 0.72);
  transition:
    border-color 160ms var(--ease-standard),
    transform 160ms var(--ease-standard),
    background 160ms var(--ease-standard);
}

.role-card:hover,
.role-card.active {
  border-color: rgba(30, 107, 255, 0.36);
  background: rgba(232, 240, 255, 0.72);
  transform: translateY(-1px);
}

.role-card div,
.matrix-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.role-card strong,
.permission-row strong,
.guard-tip strong {
  color: var(--platform-text-strong);
}

.role-card span {
  color: var(--platform-text-muted);
  font-size: 12px;
}

.permission-table {
  display: grid;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.58);
}

.permission-row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) repeat(3, 96px);
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
}

.permission-row:first-child {
  border-top: 0;
}

.permission-row.header {
  color: var(--platform-text-muted);
  background: rgba(248, 250, 252, 0.86);
  font-size: 13px;
  font-weight: 800;
}

.permission-dot {
  width: max-content;
  padding: 5px 10px;
  border-radius: 999px;
  color: var(--platform-text-muted);
  background: rgba(100, 116, 139, 0.1);
  font-size: 12px;
  font-weight: 800;
}

.permission-dot.on {
  color: var(--state-success);
  background: rgba(22, 163, 74, 0.1);
}

.guard-panel ul {
  display: grid;
  gap: 10px;
}

.guard-panel li {
  position: relative;
  padding: 12px 12px 12px 34px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  color: var(--platform-text-muted);
  background: rgba(248, 250, 252, 0.68);
  line-height: 1.6;
}

.guard-panel li::before {
  position: absolute;
  top: 17px;
  left: 14px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--accent-blue);
  content: "";
}

.guard-tip {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-radius: 16px;
  background: rgba(30, 107, 255, 0.08);
}

@media (max-width: 1280px) {
  .role-layout {
    grid-template-columns: 260px minmax(0, 1fr);
  }

  .guard-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 900px) {
  .role-head,
  .role-layout,
  .permission-row {
    grid-template-columns: 1fr;
  }

  .role-head {
    display: grid;
    align-items: start;
  }
}
</style>
