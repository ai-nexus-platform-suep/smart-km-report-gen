<template>
  <div class="platform-page">
    <section class="platform-hero">
      <p class="platform-eyebrow">Model Configuration</p>
      <h1>问答与 LLM 配置</h1>
      <p>
        这里先把问答参数、检索参数和大模型连接信息收成统一配置页骨架。当前只做前端表单结构，不保存到后端。
      </p>
      <div class="platform-actions">
        <el-button type="primary">保存配置</el-button>
        <el-button>测试连接</el-button>
        <el-button text>恢复默认</el-button>
      </div>
    </section>

    <section class="platform-grid two">
      <div class="platform-section">
        <div>
          <p class="platform-eyebrow">QA Runtime</p>
          <h2>问答参数</h2>
        </div>
        <el-form label-position="top">
          <el-form-item label="默认知识库">
            <el-select model-value="standard">
              <el-option label="技术监督标准库" value="standard" />
              <el-option label="问答 FAQ 知识库" value="faq" />
            </el-select>
          </el-form-item>
          <el-form-item label="上下文轮数">
            <el-slider :model-value="6" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="回答温度">
            <el-slider :model-value="35" :min="0" :max="100" />
          </el-form-item>
        </el-form>
      </div>

      <div class="platform-section">
        <div>
          <p class="platform-eyebrow">Retrieval</p>
          <h2>检索配置</h2>
        </div>
        <el-form label-position="top">
          <el-form-item label="召回 Top K">
            <el-input-number :model-value="8" :min="1" :max="50" />
          </el-form-item>
          <el-form-item label="重排序模型">
            <el-select model-value="bge-reranker-large">
              <el-option label="bge-reranker-large" value="bge-reranker-large" />
              <el-option label="关闭重排序" value="none" />
            </el-select>
          </el-form-item>
          <el-form-item label="引用来源展示">
            <el-switch :model-value="true" />
          </el-form-item>
        </el-form>
      </div>

      <div class="platform-section">
        <div>
          <p class="platform-eyebrow">LLM Endpoint</p>
          <h2>大模型连接</h2>
        </div>
        <el-form label-position="top">
          <el-form-item label="模型服务地址">
            <el-input model-value="https://api.example.local/v1" />
          </el-form-item>
          <el-form-item label="默认模型">
            <el-input model-value="qwen-plus" />
          </el-form-item>
          <el-form-item label="超时时间">
            <el-input-number :model-value="60" :min="5" :max="300" />
          </el-form-item>
        </el-form>
      </div>

      <div class="platform-section">
        <div>
          <p class="platform-eyebrow">Access Control</p>
          <h2>权限与审计</h2>
        </div>
        <div class="platform-timeline">
          <div v-for="item in guardrails" :key="item.title" class="platform-timeline-item">
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.desc }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const guardrails = [
  { title: '管理员可见', desc: '菜单层已经通过统一 SideNav 和路由守卫控制 admin 入口。' },
  { title: '敏感字段', desc: '密钥类字段第三阶段接入后端加密存储，不在前端明文持久化。' },
  { title: '审计记录', desc: '配置变更后续统一写入系统管理审计日志。' },
]
</script>
