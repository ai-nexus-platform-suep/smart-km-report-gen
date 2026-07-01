<template>
  <div class="platform-page">
    <section class="platform-hero">
      <p class="platform-eyebrow">Conversation Archive</p>
      <h1>会话记录</h1>
      <p>
        会话记录页先统一列表、筛选、复核状态和素材转化入口。真实历史会话接口后续再从原问答模块迁移到统一服务层。
      </p>
      <div class="platform-actions">
        <el-button type="primary">导出记录</el-button>
        <el-button>批量归档</el-button>
      </div>
    </section>

    <div class="platform-layout">
      <section class="platform-section">
        <div class="platform-toolbar">
          <el-input placeholder="搜索问题、答案摘要或引用来源" clearable />
          <el-select placeholder="会话状态" clearable>
            <el-option label="进行中" value="active" />
            <el-option label="已归档" value="archived" />
            <el-option label="待复核" value="review" />
          </el-select>
          <el-date-picker type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
        </div>

        <div class="platform-table">
          <div class="platform-table-row header">
            <span>会话主题</span>
            <span>来源知识库</span>
            <span>状态</span>
            <span>更新时间</span>
          </div>
          <div v-for="item in conversations" :key="item.title" class="platform-table-row">
            <strong>{{ item.title }}</strong>
            <span>{{ item.library }}</span>
            <span class="platform-chip" :class="item.statusClass">{{ item.status }}</span>
            <span>{{ item.updatedAt }}</span>
          </div>
        </div>
      </section>

      <aside class="platform-section">
        <div>
          <p class="platform-eyebrow">Review Flow</p>
          <h2>复核流程</h2>
        </div>
        <div class="platform-timeline">
          <div v-for="step in reviewFlow" :key="step.title" class="platform-timeline-item">
            <div>
              <strong>{{ step.title }}</strong>
              <span>{{ step.desc }}</span>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
const conversations = [
  { title: '迎峰度夏风险问答', library: '技术监督标准库', status: '进行中', statusClass: 'success', updatedAt: '今天 10:18' },
  { title: '锅炉辅机缺陷闭环', library: '设备缺陷案例库', status: '待复核', statusClass: 'warning', updatedAt: '昨天 17:04' },
  { title: '报告引用来源整理', library: '报告素材知识库', status: '已归档', statusClass: 'neutral', updatedAt: '昨天 14:22' },
  { title: '环保监督常见问题', library: '问答 FAQ 知识库', status: '已归档', statusClass: 'neutral', updatedAt: '前天 16:30' },
]

const reviewFlow = [
  { title: '会话沉淀', desc: '保留问题、答案、引用来源和用户反馈。' },
  { title: '人工复核', desc: '高价值会话可以进入素材或 FAQ 候选池。' },
  { title: '报告联动', desc: '第三阶段再接入报告素材映射接口。' },
]
</script>
