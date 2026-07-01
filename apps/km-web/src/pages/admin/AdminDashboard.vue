<template>
  <div class="dashboard-page">
    <h2>KM 概览</h2>
    <div class="stat-cards">
      <el-card shadow="never" class="stat-card">
        <div class="stat-inner">
          <div class="stat-icon blue"><el-icon><FolderOpened /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalKb ?? '-' }}</div>
            <div class="stat-label">知识库总数</div>
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-inner">
          <div class="stat-icon green"><el-icon><Document /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalDocs ?? '-' }}</div>
            <div class="stat-label">文档总数</div>
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-inner">
          <div class="stat-icon orange"><el-icon><Grid /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalChunks ?? '-' }}</div>
            <div class="stat-label">分块总数</div>
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-inner">
          <div class="stat-icon purple"><el-icon><TrendCharts /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.processedToday ?? '-' }}</div>
            <div class="stat-label">今日处理</div>
          </div>
        </div>
      </el-card>
    </div>
    <el-card shadow="never" class="trend-card">
      <template #header><span class="section-title">近 30 天上传趋势</span></template>
      <div ref="chartRef" style="height: 320px"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { FolderOpened, Document, Grid, TrendCharts } from '@element-plus/icons-vue'
import { getStatsOverview, getKmTrend, type StatsOverview } from '../../api/admin'
import * as echarts from 'echarts'

const stats = reactive<StatsOverview>({ totalKb: 0, totalDocs: 0, totalChunks: 0, processedToday: 0 })
const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

async function fetchStats() {
  try {
    const res = await getStatsOverview()
    const d = res.data?.data
    if (d) {
      // 兼容后端返回字段名（knowledgeBaseCount / documentCount / chunkCount / readyDocumentCount）
      Object.assign(stats, {
        totalKb: d.totalKb ?? d.knowledgeBaseCount ?? d.kbCount ?? 0,
        totalDocs: d.totalDocs ?? d.documentCount ?? 0,
        totalChunks: d.totalChunks ?? d.chunkCount ?? 0,
        processedToday: d.processedToday ?? d.todayProcessed ?? d.readyDocumentCount ?? 0,
      })
    }
  } catch { ElMessage.error('获取统计数据失败') }
}

async function renderTrend() {
  try {
    const res = await getKmTrend()
    const trend = res.data?.data ?? []
    const dates = trend.map((t: any) => t.date?.slice(5) || t.date)
    const counts = trend.map((t: any) => t.count ?? 0)
    await nextTick()
    if (!chartRef.value) return
    if (!chartInstance) chartInstance = echarts.init(chartRef.value)
    chartInstance.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 30, bottom: 30, top: 20 },
      xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 12 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        data: counts, type: 'line', smooth: true,
        lineStyle: { width: 3, color: '#409EFF' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(64,158,255,0.3)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }]) },
        symbol: 'circle', symbolSize: 6, itemStyle: { color: '#409EFF' },
      }],
    })
  } catch { /* ignore */ }
}

function handleResize() { chartInstance?.resize() }

onMounted(() => { fetchStats(); renderTrend(); window.addEventListener('resize', handleResize) })
onUnmounted(() => { window.removeEventListener('resize', handleResize); chartInstance?.dispose(); chartInstance = null })
</script>

<style scoped>
.dashboard-page { padding: 0; }
.dashboard-page h2 { font-size: 20px; font-weight: 600; margin: 0 0 20px; }
.stat-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
.stat-card { border: 1px solid var(--border-color); border-radius: var(--border-radius); background: var(--bg-container); }
.stat-inner { display: flex; align-items: center; gap: 16px; padding: 8px 0; }
.stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 24px; flex-shrink: 0; }
.stat-icon.blue { background: #ecf5ff; color: #409eff; }
.stat-icon.green { background: #f0f9eb; color: #67c23a; }
.stat-icon.orange { background: #fdf6ec; color: #e6a23c; }
.stat-icon.purple { background: #f5f0ff; color: #7c3aed; }
.stat-value { font-size: 28px; font-weight: 700; color: var(--text-primary); line-height: 1.2; }
.stat-label { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }
.trend-card { border: 1px solid var(--border-color); border-radius: var(--border-radius); background: var(--bg-container); }
.trend-card :deep(.el-card__header) { padding: 14px 20px; border-bottom: 1px solid var(--border-color); }
.section-title { font-size: 15px; font-weight: 600; color: var(--text-primary); }
</style>