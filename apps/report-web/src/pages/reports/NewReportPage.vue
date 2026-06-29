<template>
  <div class="page">
    <PageHeader
      eyebrow="CREATE REPORT"
      title="新建报告"
      description="选择固定报告类型并填写主题、专业、电厂、年份等核心输入，创建后进入大纲生成和编辑。"
    />

    <div class="split-grid">
      <section class="surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">TYPE SELECT</span>
            <h2>报告类型</h2>
          </div>
        </div>
        <div class="type-grid">
          <button
            v-for="item in typeCards"
            :key="item.value"
            class="type-card interactive-lift"
            :class="{ active: form.type === item.value }"
            type="button"
            @click="form.type = item.value"
          >
            <span class="mono">{{ item.code }}</span>
            <strong>{{ item.title }}</strong>
            <p>{{ item.desc }}</p>
          </button>
        </div>
      </section>

      <section class="surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">META DATA</span>
            <h2>报告信息</h2>
          </div>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="report-form">
          <el-form-item label="报告名称" prop="name">
            <el-input v-model="form.name" placeholder="如：2026 年迎峰度夏检查报告" />
          </el-form-item>
          <el-form-item label="报告主题" prop="subject">
            <el-input v-model="form.subject" type="textarea" placeholder="描述本次报告生成的业务主题" />
          </el-form-item>
          <div class="field-grid">
            <el-form-item label="专业" prop="specialty">
              <el-select v-model="form.specialty" filterable allow-create default-first-option>
                <el-option v-for="item in specialtyOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="电厂" prop="powerPlant">
              <el-input v-model="form.powerPlant" />
            </el-form-item>
            <el-form-item label="年份" prop="reportYear">
              <el-input-number v-model="form.reportYear" :controls="false" />
            </el-form-item>
          </div>
          <el-button type="primary" :loading="submitting" @click="submit">创建并进入大纲</el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { useReportStore } from '@/stores/reports'
import type { CreateReportPayload, ReportType } from '@/types/domain'

const store = useReportStore()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<CreateReportPayload>({
  name: '',
  type: 'SUMMER_PEAK_CHECK',
  subject: '',
  specialty: '电气',
  powerPlant: '',
  reportYear: new Date().getFullYear(),
})

const typeCards: Array<{ value: ReportType; code: string; title: string; desc: string }> = [
  {
    value: 'SUMMER_PEAK_CHECK',
    code: 'TYPE 01',
    title: '迎峰度夏检查报告',
    desc: '面向高温负荷、设备运行、隐患整改和保障措施。',
  },
  {
    value: 'COAL_INVENTORY_AUDIT',
    code: 'TYPE 02',
    title: '煤库存审计报告',
    desc: '面向库存盘点、账实核对、采购消耗和审计建议。',
  },
]

const specialtyOptions = ['电气', '锅炉', '燃料', '审计', '安全']

const rules: FormRules = {
  name: [{ required: true, message: '请输入报告名称', trigger: 'blur' }],
  subject: [{ required: true, message: '请输入报告主题', trigger: 'blur' }],
  specialty: [{ required: true, message: '请选择专业', trigger: 'blur' }],
  powerPlant: [{ required: true, message: '请输入电厂', trigger: 'blur' }],
  reportYear: [{ required: true, type: 'number', message: '请输入年份', trigger: 'blur' }],
}

async function submit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const report = await store.create(form)
    ElMessage.success('报告记录已创建')
    router.push(`/reports/${report.id}/outline`)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.type-grid {
  display: grid;
  gap: 14px;
  padding: 20px;
}

.type-card {
  display: grid;
  gap: 8px;
  min-height: 144px;
  padding: 18px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  text-align: left;
  background: var(--bg-surface);
  cursor: pointer;
}

.type-card.active {
  border-color: var(--accent-blue);
  background: var(--accent-blue-soft);
}

.type-card strong {
  font-size: 18px;
}

.type-card p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.report-form {
  display: grid;
  gap: 4px;
  padding: 20px;
}
</style>