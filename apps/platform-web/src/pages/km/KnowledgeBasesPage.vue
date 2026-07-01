<template>
  <div class="platform-page">
    <section class="platform-hero">
      <p class="platform-eyebrow">Knowledge Management / A Group</p>
      <h1>知识库管理</h1>
      <p>
        这里先统一知识库列表的页面骨架，保留后续接入真实知识库接口的位置。页面只负责平台入口风格、筛选区、列表区和运维侧栏，不改现有后端协议。
      </p>
      <div class="platform-actions">
        <el-button type="primary">新建知识库</el-button>
        <el-button>导入知识库</el-button>
        <el-button text>查看合并说明</el-button>
      </div>
    </section>

    <section class="platform-grid three">
      <div v-for="item in stats" :key="item.label" class="platform-card platform-stat">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <p>{{ item.hint }}</p>
      </div>
    </section>

    <div class="platform-layout">
      <section class="platform-section">
        <div class="platform-row">
          <div>
            <p class="platform-eyebrow">Library Index</p>
            <h2>知识库列表</h2>
          </div>
          <div class="platform-toolbar">
            <el-input placeholder="搜索知识库名称 / 负责人" clearable />
            <el-select placeholder="状态" clearable>
              <el-option label="运行中" value="running" />
              <el-option label="同步中" value="syncing" />
              <el-option label="待配置" value="pending" />
            </el-select>
          </div>
        </div>

        <div class="platform-table">
          <div class="platform-table-row header">
            <span>知识库</span>
            <span>覆盖范围</span>
            <span>状态</span>
            <span>最近同步</span>
          </div>
          <div v-for="item in libraries" :key="item.name" class="platform-table-row">
            <strong>{{ item.name }}</strong>
            <span>{{ item.scope }}</span>
            <span class="platform-chip" :class="item.statusClass">{{ item.status }}</span>
            <span>{{ item.updatedAt }}</span>
          </div>
        </div>
      </section>

      <aside class="platform-section">
        <div>
          <p class="platform-eyebrow">Merge Guard</p>
          <h2>合并边界</h2>
        </div>
        <div class="platform-timeline">
          <div v-for="item in mergeNotes" :key="item.title" class="platform-timeline-item">
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.desc }}</span>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
const stats = [
  { label: '知识库总数', value: '12', hint: '先用静态骨架占位，接口第三阶段统一接入。' },
  { label: '文档索引', value: '4,286', hint: '后续与文档管理列表共用索引状态。' },
  { label: '待处理任务', value: '7', hint: '解析、切片、向量化任务统一展示。' },
]

const libraries = [
  { name: '技术监督标准库', scope: '制度、标准、规范', status: '运行中', statusClass: 'success', updatedAt: '今天 09:20' },
  { name: '设备缺陷案例库', scope: '缺陷记录、整改闭环', status: '同步中', statusClass: 'warning', updatedAt: '今天 08:42' },
  { name: '报告素材知识库', scope: '报告段落、图表素材', status: '待配置', statusClass: 'neutral', updatedAt: '昨天 18:10' },
  { name: '问答 FAQ 知识库', scope: '常见问题、答案模板', status: '运行中', statusClass: 'success', updatedAt: '昨天 16:36' },
]

const mergeNotes = [
  { title: '统一入口', desc: '由 platform-web 承担登录、Layout、侧栏和权限守卫。' },
  { title: '页面骨架', desc: '当前页只合并静态 UI 和路由，不移动接口实现。' },
  { title: '接口后移', desc: '后续再把知识库 API、状态码、鉴权头统一到 feature 包。' },
]
</script>
