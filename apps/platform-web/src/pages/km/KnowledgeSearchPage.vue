<template>
  <div class="platform-page">
    <section class="platform-hero">
      <p class="platform-eyebrow">Knowledge Retrieval</p>
      <h1>知识检索</h1>
      <p>
        这个页面先统一检索入口、筛选器、召回结果和引用预览的骨架。后续智能问答的检索测试也可以复用同一套视觉结构。
      </p>
      <div class="platform-input-bar">
        <el-input placeholder="输入制度条款、设备问题或报告素材关键词" size="large" />
        <el-button type="primary" size="large">开始检索</el-button>
      </div>
    </section>

    <div class="platform-layout">
      <section class="platform-section">
        <div class="platform-row">
          <div>
            <p class="platform-eyebrow">Search Result</p>
            <h2>召回结果</h2>
          </div>
          <div class="platform-tags">
            <span class="platform-chip">标准库</span>
            <span class="platform-chip">案例库</span>
            <span class="platform-chip">报告素材</span>
          </div>
        </div>

        <div class="platform-grid">
          <article v-for="item in results" :key="item.title" class="platform-card">
            <div class="platform-row">
              <strong>{{ item.title }}</strong>
              <span class="platform-chip" :class="item.statusClass">相似度 {{ item.score }}</span>
            </div>
            <p>{{ item.excerpt }}</p>
            <div class="platform-tags">
              <span v-for="tag in item.tags" :key="tag" class="platform-chip neutral">{{ tag }}</span>
            </div>
          </article>
        </div>
      </section>

      <aside class="platform-section">
        <div>
          <p class="platform-eyebrow">Retrieval Config</p>
          <h2>检索参数</h2>
        </div>
        <el-form label-position="top">
          <el-form-item label="召回数量 Top K">
            <el-slider :model-value="8" :min="3" :max="20" />
          </el-form-item>
          <el-form-item label="相似度阈值">
            <el-slider :model-value="72" :min="0" :max="100" />
          </el-form-item>
          <el-form-item label="重排序模型">
            <el-select model-value="bge-reranker-large">
              <el-option label="bge-reranker-large" value="bge-reranker-large" />
              <el-option label="none" value="none" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="platform-card">
          <strong>当前阶段</strong>
          <p>这里只合并静态检索页骨架；真实 embedding、rerank、权限过滤在业务接口阶段接入。</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
const results = [
  {
    title: '迎峰度夏期间设备巡视要求',
    score: '0.92',
    statusClass: 'success',
    excerpt: '高温高负荷期间应强化主变、锅炉辅机、输煤系统等关键设备巡视，形成问题清单并闭环整改。',
    tags: ['制度标准', '设备巡视', '迎峰度夏'],
  },
  {
    title: '缺陷整改闭环案例：循环水泵振动异常',
    score: '0.86',
    statusClass: 'warning',
    excerpt: '案例记录了振动升高、轴承温度异常、检修复测和报告引用素材，可用于问答与报告生成。',
    tags: ['缺陷案例', '振动', '闭环整改'],
  },
  {
    title: '报告段落模板：监督检查总体情况',
    score: '0.81',
    statusClass: '',
    excerpt: '用于报告生成的总体情况段落，可根据电厂、专业、年份和检查主题进行参数化填充。',
    tags: ['报告素材', '段落模板'],
  },
]
</script>
