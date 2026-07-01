<template>
  <div class="page">
    <PageHeader
      eyebrow="CREATE REPORT"
      title="新建报告"
      description="先选择已启用模板，再填写报告主题、专业、电厂与年份。大纲将按所选模板结构生成。"
    />

    <div class="split-grid create-layout">
      <section class="surface">
        <div class="surface-title">
          <div>
            <span class="eyebrow">TEMPLATE SELECT</span>
            <h2>已启用模板</h2>
          </div>
          <el-tag type="success" effect="plain">{{ enabledTemplates.length }} 个可用</el-tag>
        </div>

        <div class="template-grid" v-loading="templateLoading">
          <button
            v-for="template in enabledTemplates"
            :key="template.id"
            class="template-card interactive-lift"
            :class="{ active: sameId(form.templateId, template.id) }"
            type="button"
            @click="selectTemplate(template)"
          >
            <span class="mono">{{ template.version }}</span>
            <strong>{{ template.name }}</strong>
            <p>{{ reportTypeLabels[template.reportType] }}</p>
            <small>{{ template.enabled ? 'ENABLED' : 'DISABLED' }}</small>
          </button>

          <el-empty v-if="!templateLoading && enabledTemplates.length === 0" description="暂无已启用模板，请先在模板管理中启用模板" />
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
          <el-form-item label="报告模板" prop="templateId">
            <el-select v-model="form.templateId" placeholder="请选择已启用模板" filterable @change="syncTypeByTemplate">
              <el-option
                v-for="template in enabledTemplates"
                :key="template.id"
                :label="template.name"
                :value="template.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="报告类型" prop="type">
            <el-input :model-value="reportTypeLabels[form.type]" readonly />
          </el-form-item>

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

          <el-button type="primary" :disabled="enabledTemplates.length === 0" :loading="submitting" @click="submit">
            按模板生成大纲
          </el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { listTemplates } from '@/api/admin'
import { useReportStore } from '@/stores/reports'
import type { CreateReportPayload, EntityId, ReportType, TemplateRecord } from '@/types/domain'
import { reportTypeLabels } from '@/utils/labels'

const store = useReportStore()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const templateLoading = ref(false)
const templates = ref<TemplateRecord[]>([])
const sameId = (a?: EntityId, b?: EntityId) => String(a) === String(b)

const form = reactive<CreateReportPayload>({
  templateId: undefined,
  name: '',
  type: 'SUMMER_PEAK_CHECK',
  subject: '',
  specialty: '电气',
  powerPlant: '',
  reportYear: new Date().getFullYear(),
})

const enabledTemplates = computed(() => templates.value.filter((template) => template.enabled))
const specialtyOptions = ['电气', '锅炉', '燃料', '审计', '安全']

const rules: FormRules = {
  templateId: [{ required: true, message: '请选择已启用模板', trigger: 'change' }],
  name: [{ required: true, message: '请输入报告名称', trigger: 'blur' }],
  subject: [{ required: true, message: '请输入报告主题', trigger: 'blur' }],
  specialty: [{ required: true, message: '请选择专业', trigger: 'blur' }],
  powerPlant: [{ required: true, message: '请输入电厂', trigger: 'blur' }],
  reportYear: [{ required: true, type: 'number', message: '请输入年份', trigger: 'blur' }],
}

onMounted(loadTemplates)

async function loadTemplates() {
  templateLoading.value = true
  try {
    templates.value = await listTemplates({ enabled: true })
    if (!form.templateId && enabledTemplates.value[0]) selectTemplate(enabledTemplates.value[0])
  } finally {
    templateLoading.value = false
  }
}

function selectTemplate(template: TemplateRecord) {
  form.templateId = template.id
  form.type = template.reportType as ReportType
}

function syncTypeByTemplate() {
  const template = enabledTemplates.value.find((item) => sameId(item.id, form.templateId))
  if (template) form.type = template.reportType as ReportType
}

async function submit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const report = await store.create({ ...form })
    ElMessage.success('已按模板生成大纲，请确认章节结构')
    router.push(`/reports/${report.id}/outline`)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.create-layout {
  grid-template-columns: minmax(420px, 0.85fr) minmax(520px, 1.15fr);
  height: calc(100vh - 150px);
  min-height: 0;
}

.create-layout > .surface {
  min-height: 0;
}

.template-grid {
  display: grid;
  align-content: start;
  gap: 14px;
  height: calc(100vh - 235px);
  min-height: 0;
  padding: 16px 18px;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.template-card {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: 8px;
  min-height: 112px;
  padding: 16px 18px;
  border: 1px solid rgba(132, 151, 176, 0.3);
  border-radius: 18px;
  text-align: left;
  background:
    radial-gradient(circle at 100% 0, rgba(0, 184, 217, 0.13), transparent 34%),
    linear-gradient(135deg, #ffffff, #f4f8ff);
  box-shadow: 0 12px 30px rgba(29, 35, 43, 0.07);
  cursor: pointer;
}

.template-card::before {
  position: absolute;
  top: 0;
  left: 20px;
  width: 68px;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--accent-blue), var(--accent-cyan));
  content: "";
}

.template-card.active {
  border-color: rgba(30, 107, 255, 0.5);
  background:
    radial-gradient(circle at 100% 0, rgba(0, 184, 217, 0.2), transparent 36%),
    linear-gradient(135deg, #eef6ff, #ffffff);
  box-shadow: 0 18px 42px rgba(30, 107, 255, 0.14);
}

.template-card strong {
  position: relative;
  font-size: 18px;
}

.template-card p,
.template-card small {
  position: relative;
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.6;
}

.template-card small {
  color: var(--state-success);
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 800;
}

.report-form {
  display: grid;
  gap: 4px;
  max-height: calc(100vh - 235px);
  padding: 18px 22px;
  overflow-y: auto;
  scrollbar-gutter: stable;
}
</style>
