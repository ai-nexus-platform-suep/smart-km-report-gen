<template>
  <div class="tag-editor">
    <div class="tag-list" v-if="Object.keys(tags).length > 0">
      <el-tag
        v-for="(val, key) in tags"
        :key="key"
        closable
        size="small"
        :type="tagType(key)"
        :disable-transitions="false"
        @close="removeTag(key)"
      >
        {{ key }}: {{ val }}
      </el-tag>
    </div>
    <div class="tag-empty" v-else>
      <span class="empty-text">暂无标签</span>
    </div>
    <el-button size="small" type="primary" link :icon="Plus" @click="showAdd = true" class="add-btn">
      添加标签
    </el-button>

    <el-dialog v-model="showAdd" title="添加标签" width="400px" append-to-body>
      <el-form :model="form" label-width="60px" size="small" :rules="rules" ref="formRef">
        <el-form-item label="键" prop="key">
          <el-input v-model="form.key" placeholder="标签键（如：设备）" />
        </el-form-item>
        <el-form-item label="值" prop="value">
          <el-input v-model="form.value" placeholder="标签值（如：汽轮机）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="showAdd = false">取消</el-button>
        <el-button size="small" type="primary" @click="confirmAdd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'

const props = defineProps<{
  tags: Record<string, string>
}>()

const emit = defineEmits<{
  update: [tags: Record<string, string>]
}>()

const showAdd = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ key: '', value: '' })
const rules = {
  key: [{ required: true, message: '请输入标签键', trigger: 'blur' }],
  value: [{ required: true, message: '请输入标签值', trigger: 'blur' }],
}

const TAG_COLORS = ['', 'success', 'warning', 'info', 'danger']
function tagType(key: string) {
  let hash = 0
  for (let i = 0; i < key.length; i++) hash = key.charCodeAt(i) + ((hash << 5) - hash)
  return TAG_COLORS[Math.abs(hash) % TAG_COLORS.length]
}

function removeTag(key: string) {
  const next = { ...props.tags }
  delete next[key]
  emit('update', next)
}

async function confirmAdd() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const next = { ...props.tags, [form.key]: form.value }
  emit('update', next)
  form.key = ''
  form.value = ''
  showAdd.value = false
}
</script>

<style scoped>
.tag-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag-empty .empty-text {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
.add-btn {
  margin-left: 4px;
}
</style>
