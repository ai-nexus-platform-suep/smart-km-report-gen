<template>
  <div class="create-page">
    <div class="page-header">
      <el-button text @click="goBack" :icon="ArrowLeft">返回</el-button>
      <h2>新建知识库</h2>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="create-form" @submit.prevent="handleSubmit">
      <el-card shadow="never" class="form-section">
        <template #header><span class="section-title">基础信息</span></template>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="知识库名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入知识库名称，最多100个字符" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="文档类型" prop="docType">
              <el-select v-model="form.docType" placeholder="请选择文档类型" style="width:100%">
                <el-option label="规程规范" value="规程规范" />
                <el-option label="技术报告论文" value="技术报告论文" />
                <el-option label="术语条目" value="术语条目" />
                <el-option label="通用文档" value="通用文档" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入知识库描述（可选），最多500个字符" maxlength="500" show-word-limit />
        </el-form-item>
      </el-card>

      <el-card shadow="never" class="form-section">
        <template #header><span class="section-title">分段策略</span></template>
        <el-form-item label="分段方式" prop="chunkStrategy.type">
          <el-radio-group v-model="form.chunkStrategy.type">
            <el-radio-button value="heading"><el-icon><FolderOpened /></el-icon>标题分段</el-radio-button>
            <el-radio-button value="fixed_size"><el-icon><SetUp /></el-icon>固定大小分段</el-radio-button>
          </el-radio-group>
          <div class="strategy-desc">
            <template v-if="form.chunkStrategy.type === 'heading'">按文档标题层级自动分段，适合结构清晰的文档。</template>
            <template v-else>按固定字符数切分文档，适合无标题结构的纯文本。</template>
          </div>
        </el-form-item>
        <template v-if="form.chunkStrategy.type === 'heading'">
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="分隔符" prop="chunkStrategy.separator">
                <el-input v-model="form.chunkStrategy.separator" placeholder="例如：\n## " />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="递归合并" prop="chunkStrategy.recursiveMerge">
                <el-switch v-model="form.chunkStrategy.recursiveMerge" active-text="开启" inactive-text="关闭" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else>
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="分块大小（字符数）" prop="chunkStrategy.chunkSize">
                <el-input-number v-model="form.chunkStrategy.chunkSize" :min="128" :max="2048" :step="128" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="重叠字符数" prop="chunkStrategy.overlap">
                <el-input-number v-model="form.chunkStrategy.overlap" :min="0" :max="512" :step="16" style="width:100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </el-card>

      <el-card shadow="never" class="form-section">
        <template #header><span class="section-title">检索配置</span></template>
        <el-form-item label="检索方式" prop="searchStrategy">
          <el-select v-model="form.searchStrategy" style="width:320px">
            <el-option label="向量检索 + 重排序（推荐）" value="vector_rerank">
              <span>向量检索 + 重排序</span>
              <span class="option-desc">高精度，适合语义匹配场景</span>
            </el-option>
            <el-option label="仅向量检索" value="vector">
              <span>仅向量检索</span><span class="option-desc">速度快，适合简单查询</span>
            </el-option>
            <el-option label="关键词检索" value="keyword">
              <span>关键词检索</span><span class="option-desc">精确匹配，适合术语查询</span>
            </el-option>
            <el-option label="混合检索" value="hybrid">
              <span>混合检索</span><span class="option-desc">综合向量与关键词，兼顾精度与召回</span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-card>

      <div class="form-actions">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">创建知识库</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, FolderOpened, SetUp } from '@element-plus/icons-vue'
import { createKnowledgeBase, type CreateKnowledgeBaseRequest } from '../api/knowledge'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)

const form = reactive<CreateKnowledgeBaseRequest>({
  name: '',
  description: '',
  docType: '通用文档',
  chunkStrategy: {
    type: 'heading',
    separator: '\\n## ',
    recursiveMerge: true,
    chunkSize: 512,
    overlap: 64,
  },
  searchStrategy: 'vector_rerank',
})

const validateName = (_rule: any, value: string, callback: any) => {
  if (!value || !value.trim()) {
    callback(new Error('请输入知识库名称'))
  } else if (value.trim().length > 100) {
    callback(new Error('名称不能超过100个字符'))
  } else {
    callback()
  }
}

const rules = {
  name: [{ required: true, validator: validateName, trigger: 'blur' }],
  docType: [{ required: true, message: '请选择文档类型', trigger: 'change' }],
}

function goBack() { router.push('/knowledge') }

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const response = await createKnowledgeBase(form) as any
    if (response.data?.code === 200) {
      ElMessage.success('知识库创建成功')
      router.push('/knowledge')
    } else {
      ElMessage.error(response.data?.message || '创建失败')
    }
  } catch {
    ElMessage.error('网络错误，请稍后重试')
  } finally { submitting.value = false }
}
</script>

<style scoped>
.create-page { padding: 0; max-width: 900px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; }
.page-header h2 { font-size: 20px; font-weight: 600; color: var(--text-primary); margin: 0; }
.form-section { margin-bottom: 20px; border: 1px solid var(--border-color); border-radius: var(--border-radius); background: var(--bg-container); }
.form-section :deep(.el-card__header) { padding: 14px 20px; border-bottom: 1px solid var(--border-color); }
.section-title { font-size: 15px; font-weight: 600; color: var(--text-primary); }
.strategy-desc { margin-top: 8px; font-size: 13px; color: var(--text-secondary); line-height: 1.6; padding: 8px 12px; background: var(--bg-hover); border-radius: var(--border-radius-sm); }
.option-desc { display: block; font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.form-actions { display: flex; justify-content: flex-end; gap: 12px; padding: 16px 0; }

/* 深色模式 */
[data-theme='dark'] .form-section {
  background: var(--bg-container);
}
[data-theme='dark'] .strategy-desc {
  background: var(--bg-hover);
}

</style>