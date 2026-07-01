<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'

const keyword = ref('')
const roleFilter = ref('')
const statusFilter = ref('')
const activeUserId = ref('u-001')

const users = [
  {
    id: 'u-001',
    name: '系统管理员',
    account: 'admin',
    department: '平台运维组',
    role: 'ADMIN',
    status: 'active',
    lastActive: '今天 11:20',
    modules: ['系统管理', '知识管理', '智能问答', '报告生成'],
  },
  {
    id: 'u-002',
    name: '技术监督专责',
    account: 'supervisor',
    department: '技术监督部',
    role: 'USER',
    status: 'active',
    lastActive: '今天 10:46',
    modules: ['知识管理', '智能问答', '报告生成'],
  },
  {
    id: 'u-003',
    name: '报告审核员',
    account: 'report-review',
    department: '生产技术部',
    role: 'USER',
    status: 'pending',
    lastActive: '昨天 18:12',
    modules: ['报告生成'],
  },
  {
    id: 'u-004',
    name: '问答测试员',
    account: 'qa-tester',
    department: '信息中心',
    role: 'USER',
    status: 'active',
    lastActive: '昨天 15:38',
    modules: ['智能问答', '知识管理'],
  },
] as const

const filteredUsers = computed(() => {
  const word = keyword.value.trim().toLowerCase()
  return users.filter((user) => {
    const hitKeyword = !word || `${user.name}${user.account}${user.department}`.toLowerCase().includes(word)
    const hitRole = !roleFilter.value || user.role === roleFilter.value
    const hitStatus = !statusFilter.value || user.status === statusFilter.value
    return hitKeyword && hitRole && hitStatus
  })
})

const activeUser = computed(() => users.find((item) => item.id === activeUserId.value) || users[0])

const activeCount = computed(() => users.filter((item) => item.status === 'active').length)
const pendingCount = computed(() => users.filter((item) => item.status === 'pending').length)

function roleLabel(role: string) {
  return role === 'ADMIN' ? '管理员' : '普通用户'
}

function statusLabel(status: string) {
  return status === 'active' ? '启用中' : '待启用'
}

function statusType(status: string) {
  return status === 'active' ? 'success' : 'warning'
}
</script>

<template>
  <div class="page admin-users-page">
    <section class="users-head">
      <div>
        <span class="eyebrow">IDENTITY CENTER</span>
        <h1>用户管理</h1>
        <p>用更轻的列表完成账号维护：搜索、筛选、查看详情都在一页完成，避免把说明文案和操作入口堆在一起。</p>
      </div>
      <div class="users-actions">
        <el-button>导入名单</el-button>
        <el-button type="primary">新建用户</el-button>
      </div>
    </section>

    <section class="users-metrics">
      <article>
        <span>总用户</span>
        <strong>{{ users.length }}</strong>
      </article>
      <article>
        <span>启用中</span>
        <strong>{{ activeCount }}</strong>
      </article>
      <article>
        <span>待启用</span>
        <strong>{{ pendingCount }}</strong>
      </article>
    </section>

    <section class="users-layout">
      <div class="surface users-table-panel">
        <div class="users-toolbar">
          <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索姓名、账号、部门" />
          <el-select v-model="roleFilter" clearable placeholder="角色">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
          <el-select v-model="statusFilter" clearable placeholder="状态">
            <el-option label="启用中" value="active" />
            <el-option label="待启用" value="pending" />
          </el-select>
        </div>

        <el-table
          :data="filteredUsers"
          class="users-table"
          row-key="id"
          highlight-current-row
          empty-text="没有匹配用户"
          @row-click="(row) => (activeUserId = row.id)"
        >
          <el-table-column label="用户" min-width="220">
            <template #default="{ row }">
              <div class="user-cell">
                <el-avatar :size="34">{{ row.name.slice(0, 1) }}</el-avatar>
                <div>
                  <strong>{{ row.name }}</strong>
                  <small>@{{ row.account }}</small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="department" label="部门" min-width="150" />
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : 'info'">{{ roleLabel(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastActive" label="最近活跃" width="130" />
          <el-table-column label="操作" width="132" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click.stop="activeUserId = row.id">查看</el-button>
              <el-button size="small" text>编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <aside class="surface user-detail-panel">
        <span class="eyebrow">PROFILE</span>
        <div class="detail-user">
          <el-avatar :size="52">{{ activeUser.name.slice(0, 1) }}</el-avatar>
          <div>
            <h2>{{ activeUser.name }}</h2>
            <p>@{{ activeUser.account }} · {{ activeUser.department }}</p>
          </div>
        </div>

        <div class="detail-list">
          <div>
            <span>当前角色</span>
            <strong>{{ roleLabel(activeUser.role) }}</strong>
          </div>
          <div>
            <span>账号状态</span>
            <strong>{{ statusLabel(activeUser.status) }}</strong>
          </div>
          <div>
            <span>最近活跃</span>
            <strong>{{ activeUser.lastActive }}</strong>
          </div>
        </div>

        <div class="module-tags">
          <span v-for="item in activeUser.modules" :key="item">{{ item }}</span>
        </div>

        <div class="detail-actions">
          <el-button type="primary" plain>分配角色</el-button>
          <el-button plain>重置密码</el-button>
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.admin-users-page {
  display: grid;
  gap: 18px;
}

.users-head {
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

.users-head h1 {
  margin: 8px 0 8px;
  color: var(--platform-text-strong);
  font-family: var(--font-display);
  font-size: clamp(30px, 4vw, 44px);
}

.users-head p {
  max-width: 720px;
  margin: 0;
  color: var(--platform-text-muted);
  line-height: 1.75;
}

.users-actions,
.users-toolbar,
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.users-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.users-metrics article {
  display: grid;
  gap: 6px;
  padding: 16px 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--platform-shadow-soft);
}

.users-metrics span,
.detail-list span {
  color: var(--platform-text-muted);
}

.users-metrics strong {
  color: var(--platform-text-strong);
  font-family: var(--font-display);
  font-size: 32px;
}

.users-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
}

.users-table-panel {
  padding: 18px;
}

.users-toolbar {
  margin-bottom: 16px;
}

.users-toolbar .el-input {
  flex: 1 1 260px;
}

.users-toolbar .el-select {
  width: 150px;
}

.users-table {
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.user-cell,
.detail-user {
  display: flex;
  gap: 12px;
  align-items: center;
}

.user-cell strong {
  display: block;
  color: var(--platform-text-strong);
}

.user-cell small,
.detail-user p {
  color: var(--platform-text-muted);
}

.user-detail-panel {
  display: grid;
  gap: 18px;
  align-content: start;
  padding: 22px;
}

.detail-user h2 {
  margin: 0;
  color: var(--platform-text-strong);
}

.detail-user p {
  margin: 4px 0 0;
}

.detail-list {
  display: grid;
  gap: 10px;
}

.detail-list div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.68);
}

.detail-list strong {
  color: var(--platform-text-strong);
}

.module-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.module-tags span {
  padding: 6px 10px;
  border-radius: 999px;
  color: var(--accent-blue);
  background: rgba(30, 107, 255, 0.1);
  font-size: 12px;
  font-weight: 800;
}

@media (max-width: 1180px) {
  .users-head,
  .users-layout {
    grid-template-columns: 1fr;
  }

  .users-head {
    display: grid;
    align-items: start;
  }
}

@media (max-width: 760px) {
  .users-head,
  .users-table-panel,
  .user-detail-panel {
    padding: 16px;
    border-radius: 18px;
  }

  .users-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
