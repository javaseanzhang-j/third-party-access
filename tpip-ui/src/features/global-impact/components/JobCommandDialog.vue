<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { JobPriority } from '../api/globalImpactApi'
import { priorityLabel } from '../model/presentation'
import { commandTitle, type JobCommandAction } from '../model/commandPresentation'

const props = defineProps<{
  modelValue: boolean
  action: JobCommandAction
  currentPriority: JobPriority
  submitting: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [value: { action: JobCommandAction; reason: string; priority?: JobPriority }]
}>()
const reason = ref('')
const priority = ref<JobPriority>(props.currentPriority)
const validationError = ref('')
const requiresReason = computed(() => props.action !== 'SEAL')

watch(() => props.modelValue, visible => {
  if (visible) {
    reason.value = ''
    priority.value = props.currentPriority
    validationError.value = ''
  }
})

function confirm(): void {
  const normalized = reason.value.trim()
  if (requiresReason.value && !normalized) {
    validationError.value = '请填写操作原因，原因会进入审计记录。'
    return
  }
  if (props.action === 'REPRIORITIZE' && priority.value === props.currentPriority) {
    validationError.value = '请选择不同于当前值的新优先级。'
    return
  }
  validationError.value = ''
  emit('submit', { action: props.action, reason: normalized,
    ...(props.action === 'REPRIORITIZE' ? { priority: priority.value } : {}) })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="commandTitle[action]"
    width="520px"
    destroy-on-close
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert
      v-if="action === 'SEAL'"
      title="封板后生成不可变快照，不能再次修改或重算。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-alert
      v-else-if="action === 'CANCEL'"
      title="取消会终止该任务尚未完成的治理计算。"
      type="warning"
      :closable="false"
      show-icon
    />
    <div v-if="action === 'REPRIORITIZE'" class="command-field">
      <label>新优先级</label>
      <el-select v-model="priority" style="width:100%">
        <el-option v-for="(label, value) in priorityLabel" :key="value" :label="label" :value="value" />
      </el-select>
    </div>
    <div v-if="requiresReason" class="command-field">
      <label>操作原因</label>
      <el-input v-model="reason" type="textarea" :rows="4" maxlength="500" show-word-limit
        placeholder="说明本次治理操作的依据" />
    </div>
    <p v-if="validationError" class="command-validation" role="alert">{{ validationError }}</p>
    <template #footer>
      <el-button :disabled="submitting" @click="$emit('update:modelValue', false)">返回检查</el-button>
      <el-button :type="action === 'CANCEL' ? 'danger' : 'primary'" :loading="submitting" @click="confirm">
        确认执行
      </el-button>
    </template>
  </el-dialog>
</template>
