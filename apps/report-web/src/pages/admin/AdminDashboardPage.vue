<template>
  <AuthGuard require-admin>
    <div class="page dashboard-page" v-loading="loading">
      <PageHeader
        eyebrow="ADMIN TREND MONITOR"
        title="趋势监控"
        description="系统运行状态、报告生成质量和多业务活动趋势。"
      >
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :label="7">7 天</el-radio-button>
          <el-radio-button :label="14">14 天</el-radio-button>
          <el-radio-button :label="30">30 天</el-radio-button>
        </el-radio-group>
        <el-button :loading="loading" type="primary" @click="load">刷新</el-button>
      </PageHeader>

      <section class="terminal-band dashboard-band">
        <div>
          <span class="terminal-label">DATA SOURCE</span>
          <strong>{{ data?.source === 'api' ? 'BACKEND API' : 'LOCAL MOCK' }}</strong>
        </div>
        <div>
          <span class="terminal-label">LAST SYNC</span>
          <strong>{{ updatedText }}</strong>
        </div>
        <div>
          <span class="terminal-label">SUCCESS RATE</span>
          <strong>{{ successRate }}%</strong>
        </div>
        <div>
          <span class="terminal-label">ALERT LEVEL</span>
          <strong :class="hasDanger ? 'danger-text' : 'success-text'">{{ hasDanger ? 'ATTENTION' : 'STABLE' }}</strong>
        </div>
      </section>

      <section class="metric-grid">
        <div v-for="metric in metrics" :key="metric.key" class="admin-metric interactive-lift" :class="`metric-${metric.tone}`">
          <div class="metric-head">
            <span class="terminal-label">{{ metric.code }}</span>
            <span>{{ metric.delta }}</span>
          </div>
          <strong>{{ formatNumber(metric.value) }}</strong>
          <p>{{ metric.label }}{{ metric.unit ? ` / ${metric.unit}` : '' }}</p>
        </div>
      </section>

      <section class="trend-grid">
        <div v-for="trend in trends" :key="trend.key" class="surface trend-card">
          <div class="surface-title compact-title">
            <div>
              <span class="eyebrow">TIME DISTRIBUTION</span>
              <h2>{{ trend.title }}</h2>
            </div>
            <strong :style="{ color: trend.color }">{{ trendTotal(trend) }}</strong>
          </div>
          <svg class="trend-chart" viewBox="0 0 520 210" role="img" :aria-label="trend.title">
            <defs>
              <linearGradient :id="`area-${trend.key}`" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" :stop-color="trend.color" stop-opacity="0.32" />
                <stop offset="100%" :stop-color="trend.color" stop-opacity="0.02" />
              </linearGradient>
            </defs>
            <line v-for="line in chartGrid" :key="line" x1="24" x2="500" :y1="line" :y2="line" class="chart-grid-line" />
            <polyline :points="areaPoints(trend)" :fill="`url(#area-${trend.key})`" stroke="none" />
            <polyline :points="linePoints(trend)" fill="none" :stroke="trend.color" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
            <circle
              v-for="(point, index) in trend.points"
              :key="`${trend.key}-${point.date}`"
              :cx="pointX(index, trend.points.length)"
              :cy="pointY(point.count, trend)"
              r="3.5"
              :fill="trend.color"
              class="trend-point"
            />
            <text x="24" y="202" class="chart-label">{{ trend.points[0]?.date }}</text>
            <text x="448" y="202" class="chart-label">{{ trend.points[trend.points.length - 1]?.date }}</text>
          </svg>
        </div>
      </section>

      <div class="dashboard-main">
        <section class="surface">
          <div class="surface-title">
            <div>
              <span class="eyebrow">DISTRIBUTION</span>
              <h2>业务分布</h2>
            </div>
          </div>
          <div class="distribution-list">
            <div v-for="group in distributions" :key="group.key" class="distribution-group">
              <h3>{{ group.title }}</h3>
              <div class="donut-panel">
                <div class="donut-chart" :style="{ background: donutGradient(group.items) }">
                  <div class="donut-center">
                    <strong>{{ distributionTotal(group.items) }}</strong>
                    <span>总量</span>
                  </div>
                </div>
                <div class="distribution-legend">
                  <div v-for="item in group.items" :key="item.name" class="legend-row">
                    <i :style="{ background: item.color }" />
                    <span>{{ item.name }}</span>
                    <strong>{{ item.value }}</strong>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="surface">
          <div class="surface-title">
            <div>
              <span class="eyebrow">SERVICE HEALTH</span>
              <h2>接口健康</h2>
            </div>
          </div>
          <div class="health-list">
            <div v-for="item in health" :key="item.name" class="health-row">
              <span class="status-dot" :class="healthTone(item.status)" />
              <div>
                <strong>{{ item.name }}</strong>
                <small>{{ item.detail }}</small>
              </div>
              <em>{{ item.latencyMs }}ms</em>
            </div>
          </div>
        </section>
      </div>

      <div class="dashboard-main lower-grid">
        <section class="surface">
          <div class="surface-title">
            <div>
              <span class="eyebrow">ALERT QUEUE</span>
              <h2>异常队列</h2>
            </div>
          </div>
          <div class="alert-list">
            <div v-for="alert in alerts" :key="alert.id" class="alert-row" :class="`alert-${alert.level}`">
              <span class="mono">{{ alert.id }}</span>
              <div>
                <strong>{{ alert.title }}</strong>
                <p>{{ alert.description }}</p>
              </div>
              <time>{{ alert.time }}</time>
            </div>
          </div>
        </section>

        <section class="surface recent-surface">
          <div class="surface-title">
            <div>
              <span class="eyebrow">RECENT TASKS</span>
              <h2>最近报告任务</h2>
            </div>
          </div>
          <el-table :data="recentTasks" class="recent-table" empty-text="暂无报告任务">
            <el-table-column prop="id" label="任务编号" width="120" />
            <el-table-column label="报告名称" min-width="220">
              <template #default="{ row }">
                <strong>{{ row.name }}</strong>
                <small>{{ row.type }}</small>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="owner" label="负责人" width="100" />
            <el-table-column prop="duration" label="耗时" width="100" />
            <el-table-column prop="time" label="更新时间" min-width="180" />
          </el-table>
        </section>
      </div>
    </div>
  </AuthGuard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AuthGuard from '@platform/ui/src/components/AuthGuard.vue'
import PageHeader from '@/components/PageHeader.vue'
import { fetchDashboardData, type ActivityTrend, type DashboardData, type DistributionItem, type HealthStatus } from '@/api/admin'

const days = ref(30)
const loading = ref(false)
const data = ref<DashboardData>()
const chartGrid = [40, 82, 124, 166]

const metrics = computed(() => data.value?.metrics ?? [])
const trends = computed(() => data.value?.trends ?? [])
const distributions = computed(() => data.value?.distributions ?? [])
const health = computed(() => data.value?.health ?? [])
const alerts = computed(() => data.value?.alerts ?? [])
const recentTasks = computed(() => data.value?.recentTasks ?? [])
const updatedText = computed(() => (data.value ? new Date(data.value.updatedAt).toLocaleString() : '-'))
const hasDanger = computed(() => alerts.value.some((item) => item.level === 'danger'))
const successRate = computed(() => {
  const total = metricValue('reportGenerations')
  const failed = metricValue('failedTasks')
  if (!total) return 100
  return Math.max(0, Math.round(((total - failed) / total) * 100))
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    data.value = await fetchDashboardData(days.value)
  } finally {
    loading.value = false
  }
}

function metricValue(key: string) {
  return metrics.value.find((item) => item.key === key)?.value ?? 0
}

function formatNumber(value: number) {
  return value.toLocaleString()
}

function trendTotal(trend: ActivityTrend) {
  return trend.points.reduce((sum, point) => sum + point.count, 0)
}

function trendMax(trend: ActivityTrend) {
  return Math.max(1, ...trend.points.map((point) => point.count))
}

function pointX(index: number, total: number) {
  if (total <= 1) return 24
  return 24 + (index / (total - 1)) * 476
}

function pointY(count: number, trend: ActivityTrend) {
  return 176 - (count / trendMax(trend)) * 136
}

function linePoints(trend: ActivityTrend) {
  return trend.points.map((point, index) => `${pointX(index, trend.points.length)},${pointY(point.count, trend)}`).join(' ')
}

function areaPoints(trend: ActivityTrend) {
  return `24,176 ${linePoints(trend)} 500,176`
}

function distributionTotal(items: DistributionItem[]) {
  return items.reduce((sum, item) => sum + item.value, 0)
}

function donutGradient(items: DistributionItem[]) {
  const total = distributionTotal(items)
  if (!total) return 'conic-gradient(var(--bg-subtle) 0deg 360deg)'

  let cursor = 0
  const segments = items.map((item) => {
    const start = cursor
    const end = cursor + (item.value / total) * 360
    cursor = end
    return `${item.color} ${start.toFixed(2)}deg ${end.toFixed(2)}deg`
  })
  return `conic-gradient(${segments.join(', ')})`
}

function healthTone(status: HealthStatus) {
  if (status === 'ONLINE') return 'success'
  if (status === 'DEGRADED') return 'warning'
  return 'danger'
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    OUTLINE_READY: '大纲就绪',
    CONTENT_GENERATING: '正文生成中',
    CONTENT_INCOMPLETE: '正文待补全',
    CONTENT_READY: '正文就绪',
    EXPORTED: '已导出',
    FAILED: '生成失败'
  }
  return labels[status] || status
}

function statusTag(status: string) {
  if (status === 'FAILED') return 'danger'
  if (status === 'CONTENT_GENERATING' || status === 'CONTENT_INCOMPLETE') return 'warning'
  if (status === 'CONTENT_READY' || status === 'EXPORTED') return 'success'
  return 'info'
}
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 16px;
}

.dashboard-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  padding: 18px 20px;
}

.dashboard-band strong {
  display: block;
  margin-top: 8px;
  font-family: var(--font-display);
  font-size: 28px;
  line-height: 1;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.admin-metric {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: 10px;
  min-height: 128px;
  padding: 16px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background:
    linear-gradient(135deg, rgba(30, 107, 255, 0.08), transparent 38%),
    var(--bg-surface);
}

.admin-metric::after {
  position: absolute;
  right: -22px;
  bottom: -22px;
  width: 72px;
  height: 72px;
  border: 1px solid currentColor;
  content: "";
  opacity: 0.14;
  transform: rotate(45deg);
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.metric-head span:last-child {
  color: var(--text-muted);
  font-size: 12px;
}

.admin-metric .terminal-label {
  color: currentColor;
}

.admin-metric strong {
  font-family: var(--font-display);
  font-size: 40px;
  line-height: 1;
}

.admin-metric p {
  margin: 0;
  color: var(--text-secondary);
}

.metric-blue {
  color: var(--accent-blue);
}

.metric-cyan {
  color: var(--accent-cyan);
}

.metric-green {
  color: var(--state-success);
}

.metric-orange {
  color: var(--state-warning);
}

.metric-pink {
  color: #ec5da5;
}

.metric-red {
  color: var(--state-danger);
}

.metric-purple {
  color: #8b5cf6;
}

.trend-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.compact-title {
  align-items: flex-start;
}

.compact-title > strong {
  font-family: var(--font-display);
  font-size: 32px;
}

.trend-chart {
  display: block;
  width: 100%;
  height: 230px;
  padding: 10px 14px 14px;
}

.chart-grid-line {
  stroke: var(--grid-line);
  stroke-dasharray: 6 6;
}

.chart-label {
  fill: var(--text-muted);
  font-family: var(--font-display);
  font-size: 18px;
}

.trend-point {
  animation: deltaIn 240ms var(--ease-standard) both;
}

.dashboard-main {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 16px;
}

.lower-grid {
  grid-template-columns: minmax(360px, 0.7fr) minmax(0, 1.3fr);
}

.distribution-list,
.health-list,
.alert-list {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.distribution-group {
  display: grid;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-default);
}

.distribution-group:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.distribution-group h3 {
  margin: 0;
  font-size: 15px;
}

.donut-panel {
  display: grid;
  grid-template-columns: 148px minmax(0, 1fr);
  align-items: center;
  gap: 18px;
}

.donut-chart {
  position: relative;
  display: grid;
  place-items: center;
  width: 148px;
  aspect-ratio: 1;
  border-radius: 50%;
  box-shadow:
    inset 0 0 0 1px rgba(30, 107, 255, 0.12),
    0 14px 28px rgba(29, 35, 43, 0.08);
  animation: deltaIn 220ms var(--ease-standard) both;
}

.donut-chart::before {
  position: absolute;
  inset: 14px;
  border-radius: inherit;
  background:
    linear-gradient(135deg, rgba(30, 107, 255, 0.035), transparent 42%),
    var(--bg-surface);
  box-shadow: inset 0 0 0 1px var(--border-default);
  content: "";
}

.donut-chart::after {
  position: absolute;
  inset: -5px;
  border: 1px solid rgba(0, 184, 217, 0.18);
  border-radius: inherit;
  content: "";
}

.donut-center {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 4px;
  text-align: center;
}

.donut-center strong {
  font-family: var(--font-display);
  font-size: 34px;
  line-height: 1;
}

.donut-center span {
  color: var(--text-muted);
  font-size: 12px;
}

.distribution-legend {
  display: grid;
  gap: 10px;
}

.legend-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.legend-row i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.legend-row span {
  color: var(--text-secondary);
}

.legend-row strong {
  font-family: var(--font-display);
  font-size: 20px;
}

.health-row {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.health-row strong,
.health-row small {
  display: block;
}

.health-row small {
  margin-top: 3px;
  color: var(--text-muted);
}

.health-row em {
  color: var(--text-secondary);
  font-family: var(--font-display);
  font-size: 18px;
  font-style: normal;
}

.alert-row {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr) 150px;
  gap: 12px;
  padding: 13px;
  border: 1px solid var(--border-default);
  border-left-width: 3px;
  border-radius: var(--radius-md);
}

.alert-row p {
  margin: 4px 0 0;
  color: var(--text-secondary);
}

.alert-row time {
  color: var(--text-muted);
  font-size: 12px;
  text-align: right;
}

.alert-danger {
  border-left-color: var(--state-danger);
}

.alert-warning {
  border-left-color: var(--state-warning);
}

.alert-info {
  border-left-color: var(--accent-blue);
}

.recent-surface {
  min-width: 0;
}

.recent-table :deep(strong),
.recent-table :deep(small) {
  display: block;
}

.recent-table :deep(small) {
  margin-top: 4px;
  color: var(--text-muted);
}
</style>
