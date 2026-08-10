<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { GovernanceExecution } from '../api/driftGovernanceEvaluationApi'

const props = defineProps<{ candidates: GovernanceExecution[]; submitting: boolean }>()
const emit = defineEmits<{
  create: [value: { environmentCode: string; executionIds: number[] }]
}>()
const selectedIds = ref<number[]>([])
const environmentCode = ref('local')
const confirming = ref(false)
const selected = computed(() => props.candidates.filter(item => selectedIds.value.includes(item.id)))
const compatible = computed(() => selected.value.length > 0 && selected.value.every(item =>
  item.aggregationKey === selected.value[0]!.aggregationKey && item.ownerCode === selected.value[0]!.ownerCode))
const validEnvironment = computed(() => /^[a-z][a-z0-9_-]{0,31}$/.test(environmentCode.value.trim()))

watch(() => props.candidates.map(item => item.id).join(','), () => {
  const available = new Set(props.candidates.map(item => item.id))
  selectedIds.value = selectedIds.value.filter(id => available.has(id))
})

function openConfirmation(): void {
  if (compatible.value && validEnvironment.value) confirming.value = true
}
function confirm(): void {
  emit('create', { environmentCode: environmentCode.value.trim(), executionIds: [...selectedIds.value] })
}
</script>

<template>
  <div class="surface reminder-create-panel">
    <div class="section-title">
      <div><h3>创建 DRAFT 提醒批次</h3><span class="subtle">仅展示已到期、预算未耗尽且没有活动批次占用的执行账本</span></div>
      <el-input v-model="environmentCode" maxlength="32" placeholder="环境编码" style="width:150px" />
    </div>
    <el-empty v-if="!candidates.length" description="当前没有可入批的到期执行账本" />
    <template v-else>
      <el-checkbox-group v-model="selectedIds" class="reminder-candidate-grid">
        <el-checkbox v-for="item in candidates" :key="item.id" :value="item.id" border>
          <strong>#{{item.id}} · Report #{{item.driftReportId}}</strong>
          <span>{{item.ownerCode}} · 提醒 {{item.reminderCount + 1}} / {{item.maximumReminders}}</span>
          <small class="mono">{{item.aggregationKey}}</small>
        </el-checkbox>
      </el-checkbox-group>
      <el-alert v-if="selected.length&&!compatible" title="同一批次的执行账本必须具有相同负责人和聚合键。" type="warning" :closable="false" />
      <el-alert v-if="!validEnvironment" title="环境编码必须以小写字母开头，只能包含小写字母、数字、下划线和连字符。" type="error" :closable="false" />
      <div class="reminder-create-actions">
        <span>已选择 {{selected.length}} / {{candidates.length}}</span>
        <el-button type="primary" :disabled="!compatible||!validEnvironment" @click="openConfirmation">检查并创建</el-button>
      </div>
    </template>
    <el-dialog v-model="confirming" title="确认创建 DRAFT 批次" width="540px" :close-on-click-modal="false" :show-close="!submitting">
      <el-alert title="创建只冻结成员预览，不会批准、创建 Outbox、消耗提醒预算或发送通知。" type="info" :closable="false" show-icon />
      <div class="command-summary">
        <div><span>环境</span><strong>{{environmentCode}}</strong></div>
        <div><span>成员数</span><strong>{{selected.length}}</strong></div>
        <div><span>负责人</span><strong>{{selected[0]?.ownerCode}}</strong></div>
      </div>
      <template #footer>
        <el-button :disabled="submitting" @click="confirming=false">返回检查</el-button>
        <el-button type="primary" :loading="submitting" @click="confirm">确认创建 DRAFT</el-button>
      </template>
    </el-dialog>
  </div>
</template>
