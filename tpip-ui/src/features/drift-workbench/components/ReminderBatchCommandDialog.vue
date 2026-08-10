<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BatchMember, ReminderBatch } from '../api/driftReminderBatchApi'

export type ReminderBatchAction = 'APPROVE' | 'CANCEL' | 'REPLACE' | 'DISPATCH'
const props = defineProps<{
  modelValue: boolean
  action: ReminderBatchAction
  batch: ReminderBatch
  members: BatchMember[]
  submitting: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [value: { action: ReminderBatchAction; reason: string; environmentCode: string; executionIds: number[] }]
}>()
const reason = ref('')
const environmentCode = ref('local')
const executionIdsText = ref('')
const dispatchConfirmed = ref(false)
const validationError = ref('')
const title = computed(() => ({ APPROVE: '批准提醒批次', CANCEL: '取消提醒批次',
  REPLACE: '替代提醒批次', DISPATCH: '提交提醒批次到 Outbox' })[props.action])

watch(() => props.modelValue, visible => {
  if (!visible) return
  reason.value = ''
  environmentCode.value = props.batch.environmentCode
  executionIdsText.value = props.members.map(item => item.executionId).join(', ')
  dispatchConfirmed.value = false
  validationError.value = ''
}, { immediate: true })

function executionIds(): number[] | null {
  const values = executionIdsText.value.split(',').map(value => value.trim()).filter(Boolean)
  if (!values.length || values.length > 100 || values.some(value => !/^[1-9][0-9]*$/.test(value))) return null
  const ids = values.map(Number)
  return new Set(ids).size === ids.length ? ids : null
}
function confirm(): void {
  const normalizedReason = reason.value.trim()
  if ((props.action === 'CANCEL' || props.action === 'REPLACE') && !normalizedReason) {
    validationError.value = '请填写操作原因，原因会进入不可变审计证据。'; return
  }
  if (props.action === 'REPLACE' && !/^[a-z][a-z0-9_-]{0,31}$/.test(environmentCode.value.trim())) {
    validationError.value = '环境编码格式无效。'; return
  }
  const ids = props.action === 'REPLACE' ? executionIds() : props.members.map(item => item.executionId)
  if (!ids) { validationError.value = '执行账本 ID 必须是 1 至 100 个互不重复的正整数。'; return }
  if (props.action === 'DISPATCH' && !dispatchConfirmed.value) {
    validationError.value = '请确认已核对成员、环境和提醒预算。'; return
  }
  validationError.value = ''
  emit('submit', { action: props.action, reason: normalizedReason,
    environmentCode: environmentCode.value.trim(), executionIds: ids })
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="560px" destroy-on-close
    :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting"
    @update:model-value="$emit('update:modelValue',$event)">
    <el-alert v-if="action==='APPROVE'" title="批准只改变批次状态，不创建 Outbox，也不发送通知。" type="info" :closable="false" show-icon />
    <el-alert v-else-if="action==='CANCEL'" title="取消会释放成员占用，但保留批次和审计证据。" type="warning" :closable="false" show-icon />
    <el-alert v-else-if="action==='REPLACE'" title="原批次将被取消，新 DRAFT 批次与其建立双向替代血缘。" type="warning" :closable="false" show-icon />
    <el-alert v-else title="提交会在同一事务中创建 Outbox、推进提醒预算；通知 Worker 启用时可能产生真实外部投递。" type="error" :closable="false" show-icon />
    <div class="command-summary">
      <div><span>批次</span><strong>#{{batch.id}}</strong></div>
      <div><span>状态 / 版本</span><strong>{{batch.status}} / RV {{batch.rowVersion}}</strong></div>
      <div><span>环境 / 成员</span><strong>{{batch.environmentCode}} / {{members.length}}</strong></div>
    </div>
    <div v-if="action==='CANCEL'||action==='REPLACE'" class="command-field">
      <label>操作原因</label>
      <el-input v-model="reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明本次治理操作依据" />
    </div>
    <template v-if="action==='REPLACE'">
      <div class="command-field"><label>新批次环境</label><el-input v-model="environmentCode" maxlength="32" /></div>
      <div class="command-field"><label>执行账本 ID</label><el-input v-model="executionIdsText" placeholder="例如：11, 12" /><small>新成员必须保持原聚合键和负责人，最终由服务端重新校验。</small></div>
    </template>
    <el-checkbox v-if="action==='DISPATCH'" v-model="dispatchConfirmed" class="dispatch-confirmation">
      我已核对 Workspace、环境、成员、提醒序号和预算，确认提交到 Outbox
    </el-checkbox>
    <p v-if="validationError" class="command-validation" role="alert">{{validationError}}</p>
    <template #footer>
      <el-button :disabled="submitting" @click="$emit('update:modelValue',false)">返回检查</el-button>
      <el-button :type="action==='DISPATCH'||action==='CANCEL'?'danger':'primary'" :loading="submitting" @click="confirm">确认执行</el-button>
    </template>
  </el-dialog>
</template>
