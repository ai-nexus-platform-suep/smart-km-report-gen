<template>
  <div class="platform-page">
    <section class="platform-hero">
      <p class="platform-eyebrow">Intelligent QA / B Group</p>
      <h1>智能对话</h1>
      <p>
        问答页先统一成平台工作台骨架：左侧会话、中央问答、下方输入、右侧引用信息后续可以按需扩展。当前不替换任何问答接口。
      </p>
    </section>

    <section class="platform-chat-shell">
      <aside class="platform-chat-sidebar">
        <div class="platform-row">
          <strong>会话列表</strong>
          <el-button size="small" type="primary">新建</el-button>
        </div>
        <article v-for="item in sessions" :key="item.title" class="platform-card">
          <strong>{{ item.title }}</strong>
          <p>{{ item.time }}</p>
          <span class="platform-chip" :class="item.statusClass">{{ item.status }}</span>
        </article>
      </aside>

      <main class="platform-chat-main">
        <div class="platform-row">
          <div>
            <p class="platform-eyebrow">Conversation Workspace</p>
            <h2>技术监督问答</h2>
          </div>
          <div class="platform-tags">
            <span class="platform-chip">知识库增强</span>
            <span class="platform-chip">引用可追溯</span>
            <span class="platform-chip neutral">接口待合并</span>
          </div>
        </div>

        <div class="platform-grid">
          <article class="platform-message user">
            <p>迎峰度夏检查报告里，设备巡视部分应该关注哪些风险？</p>
          </article>
          <article class="platform-message">
            <p>
              建议围绕主变温升、锅炉辅机、输煤系统、保护定值和缺陷闭环五类风险组织内容，并引用最近一次巡视记录和整改台账。
            </p>
            <div class="platform-tags">
              <span class="platform-chip">引用 3 条</span>
              <span class="platform-chip success">可信度高</span>
            </div>
          </article>
          <article class="platform-message user">
            <p>后续能直接把答案转成报告素材吗？</p>
          </article>
          <article class="platform-message">
            <p>可以在第三阶段把问答输出与报告素材映射接口打通，当前骨架先预留“转为素材”和“加入报告草稿”的操作位。</p>
          </article>
        </div>

        <div class="platform-input-bar">
          <el-input placeholder="输入你的问题，后续接入智能问答接口" />
          <el-button type="primary">发送</el-button>
        </div>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
const sessions = [
  { title: '迎峰度夏检查风险梳理', time: '今天 10:18', status: '进行中', statusClass: 'success' },
  { title: '输煤系统缺陷整改问答', time: '昨天 17:04', status: '已归档', statusClass: 'neutral' },
  { title: '报告模板引用来源说明', time: '昨天 14:22', status: '待复核', statusClass: 'warning' },
]
</script>
