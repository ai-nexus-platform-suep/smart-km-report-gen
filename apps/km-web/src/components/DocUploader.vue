<template>
  <div
    class="doc-uploader"
    :class="{ 'is-dragover': dragover, 'is-disabled': uploading }"
    @dragenter.prevent="onDragEnter"
    @dragover.prevent="onDragOver"
    @dragleave.prevent="onDragLeave"
    @drop.prevent="onDrop"
    @click="inputRef?.click()"
  >
    <input
      ref="inputRef"
      type="file"
      multiple
      :accept="acceptStr"
      style="display: none"
      @change="onFileChange"
    />
    <div class="uploader-content">
      <el-icon class="upload-icon" :size="36"><UploadFilled /></el-icon>
      <p class="upload-text">{{ uploading ? '正在上传...' : '拖拽文件到此处或点击上传' }}</p>
      <p class="upload-hint">
        支持 {{ allowedExtensions.join(', ') }} 格式
        <el-tooltip :content="'单文件最大 ' + formatSize(maxFileSize)" placement="top">
          <span class="hint-link">（大小限制）</span>
        </el-tooltip>
      </p>
      <div v-if="uploading" class="upload-progress">
        <el-progress :percentage="uploadPercent" :striped="true" :duration="1" />
      </div>
      <div v-if="errorMsg" class="upload-error">
        <el-alert :title="errorMsg" type="error" show-icon :closable="true" @close="errorMsg = ''" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadDocument } from '../api/document'

const ALLOWED_TYPES: Record<string, string[]> = {
  'application/pdf': ['.pdf'],
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
  'application/msword': ['.doc'],
  'text/plain': ['.txt'],
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx'],
  'application/vnd.ms-excel': ['.xls'],
  'application/vnd.openxmlformats-officedocument.presentationml.presentation': ['.pptx'],
}

const ALLOWED_EXTENSIONS = ['.pdf', '.doc', '.docx', '.txt', '.xls', '.xlsx', '.ppt', '.pptx']

const props = withDefaults(defineProps<{
  kbId: number | string
  maxFileSize?: number
}>(), {
  maxFileSize: 50 * 1024 * 1024, // 50MB
})

const emit = defineEmits<{
  success: [result: { document: any; kbDocCount: number }]
}>()

const dragover = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)
const errorMsg = ref('')
const inputRef = ref<HTMLInputElement | null>(null)

const acceptStr = computed(() => ALLOWED_EXTENSIONS.join(','))

const allowedExtensions = ALLOWED_EXTENSIONS

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
  if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(0) + ' MB'
  return (bytes / 1024).toFixed(0) + ' KB'
}

function validateFile(file: File): string | null {
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    return '不支持的文件类型：' + ext + '，仅支持 ' + ALLOWED_EXTENSIONS.join(', ')
  }
  if (file.size > props.maxFileSize) {
    return '文件大小超出限制：' + formatSize(file.size) + '，最大允许 ' + formatSize(props.maxFileSize)
  }
  if (file.size === 0) {
    return '文件为空，请检查文件内容'
  }
  return null
}

async function handleFile(file: File) {
  errorMsg.value = ''
  const validationError = validateFile(file)
  if (validationError) {
    errorMsg.value = validationError
    return
  }
  uploading.value = true
  uploadPercent.value = 0
  try {
    const res = await uploadDocument(props.kbId, file, (pct) => { uploadPercent.value = pct })
    ElMessage.success('文件上传成功：' + file.name)
    emit('success', res.data.data)
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '上传失败，请重试'
  } finally {
    uploading.value = false
    uploadPercent.value = 0
  }
}

function onDragEnter(e: DragEvent) {
  dragover.value = true
}
function onDragOver(e: DragEvent) {
  dragover.value = true
}
function onDragLeave(e: DragEvent) {
  dragover.value = false
}
function onDrop(e: DragEvent) {
  dragover.value = false
  if (uploading.value) return
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    Array.from(files).forEach(f => handleFile(f))
  }
}
function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    Array.from(input.files).forEach(f => handleFile(f))
  }
  input.value = ''
}
</script>

<style scoped>
.doc-uploader {
  border: 2px dashed var(--border-color, #d9d9d9);
  border-radius: 8px;
  padding: 32px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  background: var(--el-fill-color-lighter, #fafafa);
}
.doc-uploader:hover,
.doc-uploader.is-dragover {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.doc-uploader.is-disabled {
  cursor: not-allowed;
  opacity: 0.7;
}
.upload-icon {
  color: var(--el-color-primary, #409eff);
  margin-bottom: 8px;
}
.upload-text {
  font-size: 15px;
  color: var(--el-text-color-primary, #303133);
  margin: 8px 0 4px;
}
.upload-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin: 0;
}
.hint-link {
  color: var(--el-color-primary, #409eff);
  cursor: pointer;
  border-bottom: 1px dashed;
}
.upload-progress {
  margin-top: 16px;
  max-width: 360px;
  margin-left: auto;
  margin-right: auto;
}
.upload-error {
  margin-top: 12px;
}
</style>
