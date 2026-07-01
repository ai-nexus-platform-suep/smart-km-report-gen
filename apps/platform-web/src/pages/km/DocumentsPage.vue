<template>
  <div class="platform-page">
    <section class="platform-hero">
      <p class="platform-eyebrow">Knowledge Management / Document Flow</p>
      <h1>文档管理</h1>
      <p>
        文档页先沉淀统一上传、解析、索引状态的页面骨架，后续可以把文档列表、批量上传、解析任务直接迁移到这些区域里。
      </p>
      <div class="platform-actions">
        <el-button type="primary">上传文档</el-button>
        <el-button>批量导入</el-button>
        <el-button text>解析任务队列</el-button>
      </div>
    </section>

    <div class="platform-layout">
      <section class="platform-section">
        <div>
          <p class="platform-eyebrow">Document Queue</p>
          <h2>文档列表</h2>
        </div>
        <div class="platform-toolbar">
          <el-input placeholder="搜索文件名 / 标签 / 来源" clearable />
          <el-select placeholder="所属知识库" clearable>
            <el-option label="技术监督标准库" value="standard" />
            <el-option label="设备缺陷案例库" value="defect" />
          </el-select>
          <el-select placeholder="处理状态" clearable>
            <el-option label="已索引" value="indexed" />
            <el-option label="解析中" value="parsing" />
            <el-option label="待处理" value="pending" />
          </el-select>
        </div>

        <div class="platform-table">
          <div class="platform-table-row header">
            <span>文件</span>
            <span>知识库</span>
            <span>状态</span>
            <span>更新时间</span>
          </div>
          <div v-for="item in documents" :key="item.name" class="platform-table-row">
            <strong>{{ item.name }}</strong>
            <span>{{ item.library }}</span>
            <span class="platform-chip" :class="item.statusClass">{{ item.status }}</span>
            <span>{{ item.updatedAt }}</span>
          </div>
        </div>
      </section>

      <aside class="platform-section">
        <div>
          <p class="platform-eyebrow">Pipeline</p>
          <h2>处理流程</h2>
        </div>
        <div class="platform-timeline">
          <div v-for="step in pipeline" :key="step.title" class="platform-timeline-item">
            <div>
              <strong>{{ step.title }}</strong>
              <span>{{ step.desc }}</span>
            </div>
          </div>
        </div>
        <div class="platform-card">
          <strong>接口合并提示</strong>
          <p>上传、解析、向量化先保持原模块接口；第三次集成时再统一请求封装和错误处理。</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
const documents = [
  { name: '2026 年迎峰度夏监督检查要求.docx', library: '技术监督标准库', status: '已索引', statusClass: 'success', updatedAt: '今天 10:12' },
  { name: '汽轮机振动异常案例汇编.pdf', library: '设备缺陷案例库', status: '解析中', statusClass: 'warning', updatedAt: '今天 09:38' },
  { name: '火电厂环保监督模板.xlsx', library: '报告素材知识库', status: '待处理', statusClass: 'neutral', updatedAt: '昨天 19:24' },
  { name: '技术监督问答 FAQ 第一批.md', library: '问答 FAQ 知识库', status: '已索引', statusClass: 'success', updatedAt: '昨天 15:48' },
]

const pipeline = [
  { title: '上传入库', desc: '保留来源、标签、知识库归属等元数据。' },
  { title: '解析切片', desc: '后续接入原文档解析和切片结果预览。' },
  { title: '索引同步', desc: '向量化状态与知识检索页共享。' },
  { title: '异常处理', desc: '失败文件统一进入任务队列，不散落在各模块。' },
]
</script>
