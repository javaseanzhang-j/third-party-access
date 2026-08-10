<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { formatTime } from '@/features/global-impact/model/presentation'
import { driftReminderBatchApi, type BatchDetail, type BatchStatus, type ReminderBatch } from '../api/driftReminderBatchApi'
import { getDueGovernanceExecutions } from '../api/driftGovernanceEvaluationApi'
import { driftGovernanceCommandApi, type ReplacementResult } from '../api/driftGovernanceCommandApi'
import ReminderBatchCreatePanel from '../components/ReminderBatchCreatePanel.vue'
import ReminderBatchCommandDialog, { type ReminderBatchAction } from '../components/ReminderBatchCommandDialog.vue'
import { auditEventLabel, auditTimelineType } from '../model/reminderAuditPresentation'

const route = useRoute()
const router = useRouter()
const form = reactive({ workspaceId: route.query.workspaceId ? Number(route.query.workspaceId) : null as number | null })
const workspaceId = ref<number | null>(form.workspaceId)
const selected = ref<ReminderBatch | null>(null)
const batches = useQuery({
  queryKey: computed(() => ['drift-reminder-batches', workspaceId.value]),
  queryFn: ({ signal }) => driftReminderBatchApi.batches(workspaceId.value!, signal),
  enabled: computed(() => workspaceId.value !== null)
})
const metrics = useQuery({
  queryKey: computed(() => ['drift-reminder-metrics', workspaceId.value]),
  queryFn: ({ signal }) => driftReminderBatchApi.metrics(workspaceId.value!, signal),
  enabled: computed(() => workspaceId.value !== null)
})
const candidates = useQuery({
  queryKey: computed(() => ['drift-governance-due-executions', workspaceId.value]),
  queryFn: ({ signal }) => getDueGovernanceExecutions(workspaceId.value!, signal),
  enabled: computed(() => workspaceId.value !== null)
})
const detail = useQuery({
  queryKey: computed(() => ['drift-reminder-batch-detail', selected.value?.id]),
  queryFn: ({ signal }) => driftReminderBatchApi.detail(selected.value!.id, signal),
  enabled: computed(() => selected.value !== null)
})
const delivery = useQuery({
  queryKey: computed(() => ['drift-reminder-batch-delivery', selected.value?.id]),
  queryFn: ({ signal }) => driftReminderBatchApi.delivery(selected.value!.id, signal),
  enabled: computed(() => selected.value !== null)
})
const timeline = useQuery({
  queryKey: computed(() => ['drift-reminder-batch-timeline', selected.value?.id]),
  queryFn: ({ signal }) => driftReminderBatchApi.timeline(selected.value!.id, signal),
  enabled: computed(() => selected.value !== null)
})
const hasReplacement = computed(() => selected.value?.replacesBatchId != null || selected.value?.replacedByBatchId != null)
const diff = useQuery({
  queryKey: computed(() => ['drift-reminder-batch-diff', selected.value?.id]),
  queryFn: ({ signal }) => driftReminderBatchApi.diff(selected.value!.id, signal),
  enabled: computed(() => selected.value !== null && hasReplacement.value),
  retry: false
})
const commandNotice = ref<{ type: 'success' | 'error'; message: string } | null>(null)
const createPanelKey = ref(0)
const activeAction = ref<ReminderBatchAction | null>(null)
const actionVisible = computed({ get: () => activeAction.value !== null, set: value => { if (!value) activeAction.value = null } })
const createBatch = useMutation({
  mutationFn: (input: { environmentCode: string; executionIds: number[] }) =>
    driftGovernanceCommandApi.createBatch(workspaceId.value!, input.environmentCode, input.executionIds),
  onSuccess: result => {
    createPanelKey.value++
    selected.value = result.batch
    commandNotice.value = { type: 'success', message: `DRAFT 批次 #${result.batch.id} 已创建，尚未批准或提交。` }
    refreshAll()
  },
  onError: error => { commandNotice.value = { type: 'error', message: error instanceof Error ? error.message : 'DRAFT 批次创建失败' } }
})
type CommandInput = { action: ReminderBatchAction; reason: string; environmentCode: string; executionIds: number[] }
const batchCommand = useMutation<BatchDetail | ReplacementResult, Error, CommandInput>({
  mutationFn: (input: CommandInput) => {
    const batch = detail.data.value!.batch
    if (input.action === 'APPROVE') return driftGovernanceCommandApi.approveBatch(batch.id, batch.rowVersion)
    if (input.action === 'CANCEL') return driftGovernanceCommandApi.cancelBatch(batch.id, batch.rowVersion, input.reason)
    if (input.action === 'REPLACE') return driftGovernanceCommandApi.replaceBatch(batch.id, batch.rowVersion,
      input.reason, input.environmentCode, input.executionIds)
    return driftGovernanceCommandApi.dispatchBatch(batch.id, batch.rowVersion)
  },
  onSuccess: (result, input) => {
    activeAction.value = null
    const replacement = isReplacement(result) ? result.replacement : result
    selected.value = replacement.batch
    commandNotice.value = { type: 'success', message: ({ APPROVE: '批次已批准，尚未创建 Outbox。', CANCEL: '批次已取消，成员占用已释放。',
      REPLACE: `替代批次 #${replacement.batch.id} 已创建为 DRAFT。`, DISPATCH: `批次已提交到 Outbox #${replacement.batch.outboxId}。` })[input.action] }
    refreshAll()
  },
  onError: error => {
    commandNotice.value = { type: 'error', message: error instanceof Error ? error.message : '提醒批次操作失败' }
    refreshAll()
  }
})

function search() {
  if (!form.workspaceId) return
  selected.value = null
  workspaceId.value = form.workspaceId
  void router.replace({ query: { workspaceId: String(form.workspaceId) } })
}
function selectBatch(row: unknown) { selected.value = row as ReminderBatch }
function openAction(action: ReminderBatchAction) { commandNotice.value = null; activeAction.value = action }
function refreshAll() {
  void Promise.all([batches.refetch(), metrics.refetch(), candidates.refetch()])
  if (selected.value) void Promise.all([detail.refetch(), delivery.refetch(), timeline.refetch()])
}
function isReplacement(value: unknown): value is ReplacementResult {
  return typeof value === 'object' && value !== null && 'replacement' in value
}
function statusType(status: BatchStatus) {
  return status === 'DISPATCHED' ? 'success' : status === 'CANCELLED' ? 'info' : status === 'APPROVED' ? 'warning' : 'primary'
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div><h2>提醒批次运营资产</h2><p>批次创建、受控状态转换、替代差异、Outbox 关联与渠道投递。</p></div>
      <div v-if="workspaceId" class="page-heading-actions"><el-button :loading="batches.isFetching.value" @click="refreshAll">刷新资产</el-button><el-button @click="router.push(`/drift-governance-evaluations?workspaceId=${workspaceId}`)">返回治理评估</el-button></div>
    </div>
    <div class="surface reminder-filter">
      <el-input-number v-model="form.workspaceId" :min="1" controls-position="right" placeholder="Workspace ID" />
      <el-button type="primary" :disabled="!form.workspaceId" @click="search">读取提醒资产</el-button>
      <span>所有命令使用服务端状态机、当前 Row Version 和本地审计 Operator</span>
    </div>
    <el-empty v-if="!workspaceId" description="请选择 Workspace 后读取提醒批次资产" />
    <el-alert v-if="commandNotice" class="command-notice" :title="commandNotice.message" :type="commandNotice.type" show-icon closable @close="commandNotice=null" />
    <AsyncStatePanel v-if="workspaceId" :loading="batches.isPending.value||metrics.isPending.value||candidates.isPending.value" :error="batches.isError.value||metrics.isError.value||candidates.isError.value"
      loading-label="正在读取提醒运营资产" error-title="提醒运营资产读取失败" @retry="batches.refetch();metrics.refetch();candidates.refetch()">
      <template v-if="batches.data.value&&metrics.data.value&&candidates.data.value">
        <ReminderBatchCreatePanel :key="createPanelKey" :candidates="candidates.data.value" :submitting="createBatch.isPending.value" @create="createBatch.mutate" />
        <div class="metric-strip">
          <div class="metric"><span>批次总数</span><strong>{{metrics.data.value.totalBatches}}</strong><small>D {{metrics.data.value.draftBatches}} / A {{metrics.data.value.approvedBatches}} / S {{metrics.data.value.dispatchedBatches}}</small></div>
          <div class="metric metric--warning"><span>到期待入批</span><strong>{{metrics.data.value.dueUnbatchedExecutions}}</strong><small>最早 {{formatTime(metrics.data.value.oldestDueAt)}}</small></div>
          <div class="metric"><span>活动占用</span><strong>{{metrics.data.value.activeReservedExecutions}}</strong></div>
          <div class="metric"><span>终态投递成功率</span><strong>{{metrics.data.value.terminalDeliverySuccessRate}}%</strong><small>{{metrics.data.value.deliveredDeliveries}} 成功 / {{metrics.data.value.deadLetterDeliveries}} 死信</small></div>
        </div>
        <div class="reminder-status-strip surface">
          <div><span>Outbox 路由</span><strong>{{metrics.data.value.unroutedOutboxes}} / {{metrics.data.value.routedOutboxes}} / {{metrics.data.value.noMatchOutboxes}} / {{metrics.data.value.renderFailedOutboxes}}</strong><small>UNROUTED / ROUTED / NO_MATCH / RENDER_FAILED</small></div>
          <div><span>渠道投递</span><strong>{{metrics.data.value.pendingDeliveries}} / {{metrics.data.value.claimedDeliveries}} / {{metrics.data.value.deliveredDeliveries}} / {{metrics.data.value.deadLetterDeliveries}}</strong><small>PENDING / CLAIMED / DELIVERED / DEAD_LETTER</small></div>
          <div><span>已取消批次</span><strong>{{metrics.data.value.cancelledBatches}}</strong></div>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>提醒批次</h3><span class="subtle">批次提交才创建 Outbox，DRAFT/APPROVED 不代表已发送</span></div>
          <el-empty v-if="!batches.data.value.length" description="当前没有提醒批次" />
          <el-table v-else :data="batches.data.value" row-key="id">
            <el-table-column label="批次" min-width="185"><template #default="{row}"><strong>#{{row.id}} · {{row.batchCode}}</strong><div class="subtle">{{row.environmentCode}} · {{row.creationSource}}</div></template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusType(row.status)">{{row.status}}</el-tag></template></el-table-column>
            <el-table-column prop="ownerCode" label="负责人" width="130" />
            <el-table-column prop="memberCount" label="成员" width="80" />
            <el-table-column label="Outbox" width="100"><template #default="{row}">{{row.outboxId??'未提交'}}</template></el-table-column>
            <el-table-column label="替代血缘" width="145"><template #default="{row}">{{row.replacesBatchId?`替代 #${row.replacesBatchId}`:row.replacedByBatchId?`被 #${row.replacedByBatchId} 替代`:'—'}}</template></el-table-column>
            <el-table-column label="创建证据" width="175"><template #default="{row}">{{row.createdBy}}<div class="subtle">{{formatTime(row.createdAt)}}</div></template></el-table-column>
            <el-table-column label="版本" width="80"><template #default="{row}">RV {{row.rowVersion}}</template></el-table-column>
            <el-table-column label="操作" width="95" fixed="right"><template #default="{row}"><el-button link type="primary" @click="selectBatch(row)">查看资产</el-button></template></el-table-column>
          </el-table>
        </div>
      </template>
    </AsyncStatePanel>

    <el-drawer :model-value="selected!==null" size="760px" title="提醒批次资产详情" @close="selected=null">
      <AsyncStatePanel :loading="detail.isPending.value||delivery.isPending.value||timeline.isPending.value" :error="detail.isError.value||delivery.isError.value||timeline.isError.value"
        loading-label="正在读取批次证据" error-title="批次证据读取失败" @retry="detail.refetch();delivery.refetch();timeline.refetch()">
        <template v-if="detail.data.value&&delivery.data.value&&timeline.data.value">
          <div class="batch-detail-header">
            <div><span>批次</span><strong>#{{detail.data.value.batch.id}} · {{detail.data.value.batch.batchCode}}</strong></div>
            <div class="batch-detail-actions"><el-tag :type="statusType(detail.data.value.batch.status)">{{detail.data.value.batch.status}}</el-tag><template v-if="detail.data.value.batch.status==='DRAFT'"><el-button size="small" @click="openAction('APPROVE')">批准</el-button><el-button size="small" @click="openAction('REPLACE')">替代</el-button><el-button size="small" type="danger" plain @click="openAction('CANCEL')">取消</el-button></template><template v-else-if="detail.data.value.batch.status==='APPROVED'"><el-button size="small" type="danger" @click="openAction('DISPATCH')">提交 Outbox</el-button><el-button size="small" @click="openAction('REPLACE')">替代</el-button><el-button size="small" type="danger" plain @click="openAction('CANCEL')">取消</el-button></template></div>
          </div>
          <div class="fact-grid batch-facts">
            <div class="fact"><label>Workspace / 环境</label><div>#{{detail.data.value.batch.workspaceId}} / {{detail.data.value.batch.environmentCode}}</div></div>
            <div class="fact"><label>负责人</label><div>{{detail.data.value.batch.ownerCode}}</div></div>
            <div class="fact"><label>成员 / Row Version</label><div>{{detail.data.value.batch.memberCount}} / {{detail.data.value.batch.rowVersion}}</div></div>
            <div class="fact"><label>创建来源</label><div>{{detail.data.value.batch.creationSource}}</div></div>
            <div class="fact"><label>Content Checksum</label><div class="mono">{{detail.data.value.batch.contentChecksum}}</div></div>
            <div class="fact"><label>Aggregation Key</label><div class="mono">{{detail.data.value.batch.aggregationKey}}</div></div>
          </div>

          <div class="drawer-section audit-timeline-section">
            <div class="section-title"><h3>不可变命令审计</h3><span class="subtle">当前 RV {{timeline.data.value.currentRowVersion}} · {{timeline.data.value.events.length}} 条证据</span></div>
            <el-empty v-if="!timeline.data.value.events.length" description="该批次没有可读取的不可变审计事件" :image-size="72" />
            <el-timeline v-else class="batch-audit-timeline">
              <el-timeline-item v-for="event in timeline.data.value.events" :key="event.eventId"
                :type="auditTimelineType(event.eventType)" :timestamp="formatTime(event.occurredAt)" placement="top">
                <div class="audit-event-card">
                  <div class="audit-event-heading"><strong>{{auditEventLabel(event.eventType)}}</strong><el-tag size="small" effect="plain">{{event.actorCode}}</el-tag></div>
                  <p>{{event.summary}}</p>
                  <div class="audit-event-facts">
                    <span v-if="event.status">状态 {{event.status}}</span><span v-if="event.rowVersion!==null">RV {{event.rowVersion}}</span>
                    <span v-if="event.replacesBatchId">替代 #{{event.replacesBatchId}}</span><span v-if="event.replacedByBatchId">被 #{{event.replacedByBatchId}} 替代</span>
                    <span v-if="event.outboxId">Outbox #{{event.outboxId}}</span>
                  </div>
                  <div v-if="event.reason" class="audit-event-reason">依据：{{event.reason}}</div>
                  <small class="mono audit-event-id">{{event.eventId}}</small>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>

          <div class="drawer-section"><h3>成员快照</h3><el-table :data="detail.data.value.members" size="small"><el-table-column prop="executionId" label="执行账本" width="110" /><el-table-column prop="reminderNo" label="提醒序号" width="90" /><el-table-column label="评估 Checksum" min-width="260"><template #default="{row}"><span class="mono ledger-hash">{{row.evaluationChecksum}}</span></template></el-table-column></el-table></div>

          <div class="drawer-section"><h3>Outbox 与渠道投递</h3><el-alert v-if="!delivery.data.value.submitted" title="批次尚未提交，没有 Outbox 或渠道投递。" type="info" :closable="false" /><template v-else-if="delivery.data.value.overview"><div class="delivery-overview"><span>Outbox #{{delivery.data.value.overview.outboxId}}</span><strong>{{delivery.data.value.overview.outboxStatus}} · {{delivery.data.value.overview.routingStatus}}</strong><small>路由 {{formatTime(delivery.data.value.overview.routingAttemptedAt)}} · 送达 {{formatTime(delivery.data.value.overview.deliveredAt)}}</small></div><el-table :data="delivery.data.value.overview.deliveries" size="small"><el-table-column prop="channelCode" label="渠道" /><el-table-column prop="status" label="状态" width="120" /><el-table-column prop="attemptCount" label="尝试" width="80" /><el-table-column label="终态时间" width="180"><template #default="{row}">{{formatTime(row.deliveredAt??row.deadLetteredAt)}}</template></el-table-column></el-table></template></div>

          <div v-if="hasReplacement" class="drawer-section"><h3>替代成员差异</h3><AsyncStatePanel :loading="diff.isPending.value" :error="diff.isError.value" loading-label="正在计算替代差异" error-title="替代差异读取失败" @retry="diff.refetch()"><template v-if="diff.data.value"><div class="diff-summary"><span>#{{diff.data.value.fromBatchId}} → #{{diff.data.value.toBatchId}}</span><strong>+{{diff.data.value.addedCount}} / -{{diff.data.value.removedCount}} / ={{diff.data.value.unchangedCount}} / ~{{diff.data.value.modifiedCount}}</strong></div><el-table :data="diff.data.value.members" size="small"><el-table-column prop="executionId" label="执行" width="90" /><el-table-column prop="changeKind" label="变化" width="110" /><el-table-column label="提醒序号" width="120"><template #default="{row}">{{row.previousReminderNo??'—'}} → {{row.currentReminderNo??'—'}}</template></el-table-column><el-table-column label="Checksum 变化" min-width="250"><template #default="{row}"><div class="mono ledger-hash">{{row.previousEvaluationChecksum??'—'}}</div><div class="mono ledger-hash">{{row.currentEvaluationChecksum??'—'}}</div></template></el-table-column></el-table></template></AsyncStatePanel></div>
        </template>
      </AsyncStatePanel>
    </el-drawer>
    <ReminderBatchCommandDialog v-if="activeAction&&detail.data.value" v-model="actionVisible" :action="activeAction"
      :batch="detail.data.value.batch" :members="detail.data.value.members" :submitting="batchCommand.isPending.value" @submit="batchCommand.mutate" />
  </section>
</template>
