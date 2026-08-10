<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { globalImpactApi, type JobStatus, type ItemStatus, type RiskLevel } from '../api/globalImpactApi'
import JobStatusTag from '../components/JobStatusTag.vue'
import ImpactSummaryPanel from '../components/ImpactSummaryPanel.vue'
import JobCommandDialog from '../components/JobCommandDialog.vue'
import SealPreflightPanel from '../components/SealPreflightPanel.vue'
import { globalImpactCommandApi } from '../api/globalImpactCommandApi'
import { commandErrorMessage, commandSuccess, type JobCommandAction } from '../model/commandPresentation'
import { ApiError } from '@/api/http'
import { actionLabel, formatTime, itemStatusLabel, priorityLabel, recoveryLabel, riskLabel, shortId } from '../model/presentation'
import { expiryInsight, recoveryGuidance } from '../model/jobOperations'

const route = useRoute(); const router = useRouter()
const jobId = computed(() => String(route.params.jobId))
const impactPage = ref(0)
const impactFilterForm = reactive<{ keyword: string; status: ItemStatus | ''; riskLevel: RiskLevel | '' }>({ keyword: '', status: '', riskLevel: '' })
const appliedImpactFilters = ref<{ keyword?: string; status?: ItemStatus; riskLevel?: RiskLevel }>({})
const detail = useQuery({ queryKey: computed(() => ['global-impact-job', jobId.value]), queryFn: ({ signal }) => globalImpactApi.job(jobId.value, signal) })
const impacts = useQuery({
  queryKey: computed(() => ['global-impact-job-impacts', jobId.value, appliedImpactFilters.value, impactPage.value]),
  queryFn: ({ signal }) => globalImpactApi.impacts(jobId.value, {
    ...appliedImpactFilters.value, page: impactPage.value, size: 10
  }, signal)
})
const impactSummary = useQuery({ queryKey: computed(() => ['global-impact-job-summary', jobId.value]), queryFn: ({ signal }) => globalImpactApi.impactSummary(jobId.value, signal) })
const timeline = useQuery({ queryKey: computed(() => ['global-impact-job-timeline', jobId.value]), queryFn: ({ signal }) => globalImpactApi.timeline(jobId.value, signal) })
const activeTab = ref('impacts')
const nowMillis = ref(Date.now())
let clock: ReturnType<typeof setInterval> | undefined
onMounted(() => { clock = setInterval(() => { nowMillis.value = Date.now() }, 30_000) })
onUnmounted(() => { if (clock) clearInterval(clock) })
const expiry = computed(() => detail.data.value
  ? expiryInsight(detail.data.value.summary.expiresAt, detail.data.value.summary.status, nowMillis.value)
  : null)
const guidance = computed(() => detail.data.value
  ? recoveryGuidance[detail.data.value.recoveryRecommendation] : undefined)
const auditFocusEventType = ref<string | null>(null)
const focusedEventId = computed(() => {
  const type = auditFocusEventType.value
  if (!type) return null
  return [...(timeline.data.value?.items ?? [])].reverse().find(event => event.eventType === type)?.eventId ?? null
})
const activeAction = ref<JobCommandAction | null>(null)
const commandNotice = ref<{ type: 'success' | 'error' | 'info'; message: string } | null>(null)
const dialogVisible = computed({
  get: () => activeAction.value !== null,
  set: value => { if (!value) activeAction.value = null }
})

interface CommandInput { action: JobCommandAction; reason: string; priority?: import('../api/globalImpactApi').JobPriority }
const command = useMutation({
  mutationFn: async (input: CommandInput) => {
    const rowVersion = detail.data.value?.summary.rowVersion
    if (rowVersion === undefined) throw new Error('Job detail is not loaded')
    switch (input.action) {
      case 'REPRIORITIZE':
        if (!input.priority) throw new Error('Priority is required')
        return globalImpactCommandApi.reprioritize(jobId.value, rowVersion, input.priority, input.reason)
      case 'CANCEL': return globalImpactCommandApi.cancel(jobId.value, rowVersion, input.reason)
      case 'RETRY_FAILED': return globalImpactCommandApi.retryFailed(jobId.value, rowVersion, input.reason)
      case 'SEAL': return globalImpactCommandApi.seal(jobId.value, rowVersion)
    }
  },
  onSuccess: (_result, input) => {
    activeAction.value = null
    commandNotice.value = { type: 'success', message: commandSuccess[input.action] }
    auditFocusEventType.value = ({
      REPRIORITIZE: 'GLOBAL_DRIFT_POLICY_IMPACT_JOB_REPRIORITIZED',
      CANCEL: 'GLOBAL_DRIFT_POLICY_IMPACT_JOB_CANCELLED',
      RETRY_FAILED: 'GLOBAL_DRIFT_POLICY_IMPACT_JOB_RETRIED',
      SEAL: 'GLOBAL_DRIFT_POLICY_IMPACT_JOB_SEALED'
    } as const)[input.action]
    activeTab.value = 'timeline'
    refreshAll()
  },
  onError: error => {
    commandNotice.value = { type: 'error', message: commandErrorMessage(error) }
    if (error instanceof ApiError && error.code === 'TPIP_GLOBAL_IMPACT_JOB_COMMAND_CONFLICT') {
      activeAction.value = null
      refreshAll()
    }
  }
})

function refreshAll(): void {
  void Promise.all([detail.refetch(), impacts.refetch(), impactSummary.refetch(), timeline.refetch()])
}

function applyImpactFilters(): void {
  appliedImpactFilters.value = {
    ...(impactFilterForm.keyword.trim() ? { keyword: impactFilterForm.keyword.trim() } : {}),
    ...(impactFilterForm.status ? { status: impactFilterForm.status } : {}),
    ...(impactFilterForm.riskLevel ? { riskLevel: impactFilterForm.riskLevel } : {})
  }
  impactPage.value = 0
}

function resetImpactFilters(): void {
  impactFilterForm.keyword = ''; impactFilterForm.status = ''; impactFilterForm.riskLevel = ''
  appliedImpactFilters.value = {}; impactPage.value = 0
}

function showFailedImpacts(): void {
  impactFilterForm.status = 'FAILED'; appliedImpactFilters.value = { status: 'FAILED' }
  impactPage.value = 0; activeTab.value = 'impacts'
}

function isCommandAction(action: string): action is JobCommandAction {
  return ['REPRIORITIZE', 'CANCEL', 'RETRY_FAILED', 'SEAL'].includes(action)
}

function openAction(action: string): void {
  commandNotice.value = null
  if (isCommandAction(action)) activeAction.value = action
  else if (action === 'VIEW_SEALED_SNAPSHOT' && detail.data.value?.sealedSnapshotId) {
    void router.push(`/global-impact-snapshots/${encodeURIComponent(detail.data.value.sealedSnapshotId)}`)
  } else commandNotice.value = { type: 'info', message: '当前任务尚未生成封板快照。' }
}

function submitCommand(input: CommandInput): void {
  command.mutate(input)
}

function duration(seconds: number): string {
  if (seconds < 60) return `${seconds} 秒`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟`
  return `${Math.floor(seconds / 3600)} 小时 ${Math.floor(seconds % 3600 / 60)} 分钟`
}
function riskName(value: RiskLevel): string { return riskLabel[value] }
</script>

<template>
  <section>
    <div class="page-heading">
      <div><el-button link @click="router.push('/global-impact-jobs')">← 返回任务列表</el-button><h2 style="margin-top:10px">任务 {{ shortId(jobId) }}</h2><p>运行状态、Workspace 影响证据与完整审计时间线。</p></div>
      <el-button :loading="detail.isFetching.value || impactSummary.isFetching.value" @click="refreshAll">刷新详情</el-button>
    </div>
    <AsyncStatePanel
      :loading="detail.isPending.value"
      :error="detail.isError.value"
      loading-label="正在加载任务详情"
      error-title="任务详情读取失败"
      @retry="detail.refetch()"
    >
    <template v-if="detail.data.value">
      <el-alert v-if="commandNotice" class="command-notice" :title="commandNotice.message"
        :type="commandNotice.type" :closable="true" show-icon @close="commandNotice = null" />
      <el-alert v-if="expiry?.visible" class="command-notice" :title="expiry.title"
        :type="expiry.severity" :closable="false" show-icon />
      <div class="metric-strip">
        <div class="metric"><span>当前状态</span><div style="margin-top:9px"><JobStatusTag :status="detail.data.value.summary.status as JobStatus" /></div></div>
        <div class="metric"><span>处理进度</span><strong>{{ detail.data.value.summary.progress.progressPercent }}%</strong></div>
        <div class="metric"><span>调度次数</span><strong>{{ detail.data.value.dispatchCount }}</strong></div>
        <div class="metric"><span>停滞时长</span><strong style="font-size:17px">{{ duration(detail.data.value.stalledSeconds) }}</strong></div>
      </div>
      <div class="detail-grid">
        <div class="surface detail-card">
          <h3>任务事实</h3>
          <div class="fact-grid">
            <div class="fact"><label>候选策略</label><div><button class="link-button" @click="router.push(`/governance-policies/${detail.data.value.summary.candidatePolicy.policyId}/versions/${detail.data.value.summary.candidatePolicy.versionId}`)">{{ detail.data.value.summary.candidatePolicy.policyName }} · v{{ detail.data.value.summary.candidatePolicy.versionNo }}</button></div></div>
            <div class="fact"><label>优先级</label><div>{{ priorityLabel[detail.data.value.summary.priority] }}</div></div>
            <div class="fact"><label>覆盖 Workspace</label><div>{{ detail.data.value.summary.progress.workspaceCount }}</div></div>
            <div class="fact"><label>恢复建议</label><div>{{ recoveryLabel[detail.data.value.recoveryRecommendation] ?? detail.data.value.recoveryRecommendation }}</div></div>
            <div class="fact"><label>快照时间</label><div>{{ formatTime(detail.data.value.snapshotAt) }}</div></div>
            <div class="fact"><label>过期时间</label><div>{{ formatTime(detail.data.value.summary.expiresAt) }}</div></div>
            <div class="fact"><label>Coverage Checksum</label><div class="mono">{{ detail.data.value.coverageChecksum }}</div></div>
            <div class="fact"><label>Row Version</label><div class="mono">{{ detail.data.value.summary.rowVersion }}</div></div>
          </div>
        </div>
        <div class="surface detail-card">
          <h3>可执行操作</h3>
          <div class="action-row">
            <el-tooltip v-for="action in detail.data.value.summary.allowedActions" :key="action.action" :content="action.disabledReasonCode ?? '当前可执行'">
              <span><el-button :disabled="!action.enabled || command.isPending.value" @click="openAction(action.action)">{{ actionLabel[action.action] ?? action.action }}</el-button></span>
            </el-tooltip>
          </div>
          <div v-if="guidance" class="recovery-guidance">
            <span>运行引导</span><strong>{{ guidance.title }}</strong><p>{{ guidance.description }}</p>
            <el-button v-if="detail.data.value.summary.progress.failedCount > 0" link type="danger" @click="showFailedImpacts">查看失败 Workspace →</el-button>
          </div>
          <el-alert style="margin-top:18px" title="操作提交时携带当前 Row Version；冲突不会自动重放。" type="info" :closable="false" />
        </div>
      </div>
      <div class="surface impact-summary-card">
        <AsyncStatePanel
          :loading="impactSummary.isPending.value"
          :error="impactSummary.isError.value"
          loading-label="正在加载全量影响摘要"
          error-title="全量影响摘要读取失败"
          @retry="impactSummary.refetch()"
        >
          <ImpactSummaryPanel v-if="impactSummary.data.value" :summary="impactSummary.data.value" />
        </AsyncStatePanel>
      </div>
      <SealPreflightPanel v-if="detail.data.value.summary.status === 'READY' && impactSummary.data.value"
        :detail="detail.data.value" :summary="impactSummary.data.value" :submitting="command.isPending.value"
        @seal="openAction('SEAL')" />
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="Workspace 影响" name="impacts">
          <form class="impact-filter-bar" aria-label="Workspace 影响筛选" @submit.prevent="applyImpactFilters">
            <el-input v-model="impactFilterForm.keyword" clearable placeholder="Workspace 编码、名称或环境" />
            <el-select v-model="impactFilterForm.status" clearable placeholder="全部执行状态">
              <el-option v-for="(label, value) in itemStatusLabel" :key="value" :label="label" :value="value" />
            </el-select>
            <el-select v-model="impactFilterForm.riskLevel" clearable placeholder="全部风险等级">
              <el-option v-for="(label, value) in riskLabel" :key="value" :label="label" :value="value" />
            </el-select>
            <el-button native-type="submit" type="primary">筛选</el-button><el-button @click="resetImpactFilters">重置</el-button>
          </form>
          <AsyncStatePanel
            :loading="impacts.isPending.value"
            :error="impacts.isError.value"
            :empty="impacts.isSuccess.value && !impacts.data.value?.items.length"
            loading-label="正在加载 Workspace 影响"
            error-title="Workspace 影响读取失败"
            empty-description="该任务没有 Workspace 影响记录"
            @retry="impacts.refetch()"
          >
          <el-table :data="impacts.data.value?.items ?? []" row-key="workspaceId">
            <el-table-column label="Workspace" min-width="260"><template #default="{ row }"><strong>{{ row.workspaceName }}</strong><div class="subtle mono" style="margin-top:4px;font-size:10px">{{ row.workspaceCode }}</div></template></el-table-column>
            <el-table-column prop="environmentCode" label="环境" width="90" />
            <el-table-column label="风险" width="100"><template #default="{ row }">{{ riskName(row.riskLevel) }}</template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><JobStatusTag :status="row.status as ItemStatus" /></template></el-table-column>
            <el-table-column label="变化报告" width="100"><template #default="{ row }">{{ row.impactComparison?.totalChangedReports ?? '—' }}</template></el-table-column>
            <el-table-column label="新增提醒候选" width="120"><template #default="{ row }">{{ row.impactComparison?.summary.addedReminderCandidates ?? '—' }}</template></el-table-column>
            <el-table-column label="参数变化" min-width="190"><template #default="{ row }"><span v-if="row.impactComparison">提醒间隔 {{ row.impactComparison.parameterChanges.reminderIntervalSecondsDelta >= 0 ? '+' : '' }}{{ row.impactComparison.parameterChanges.reminderIntervalSecondsDelta }}s</span><span v-else class="subtle">暂无成功快照</span></template></el-table-column>
            <el-table-column label="执行诊断" min-width="230"><template #default="{ row }"><div v-if="row.failureCode" class="failure-diagnostic"><strong>{{ row.failureCode }}</strong><span>{{ row.failureMessage }}</span><small>尝试 {{ row.attemptCount }} 次</small></div><span v-else class="subtle">—</span></template></el-table-column>
          </el-table>
          <div style="display:flex;justify-content:flex-end;padding:16px"><el-pagination background layout="total,prev,pager,next" :total="impacts.data.value?.totalElements ?? 0" :page-size="10" :current-page="impactPage + 1" @current-change="(value: number) => impactPage = value - 1" /></div>
          </AsyncStatePanel>
        </el-tab-pane>
        <el-tab-pane label="审计时间线" name="timeline">
          <AsyncStatePanel
            :loading="timeline.isPending.value"
            :error="timeline.isError.value"
            :empty="timeline.isSuccess.value && !timeline.data.value?.items.length"
            loading-label="正在加载审计时间线"
            error-title="审计时间线读取失败"
            empty-description="暂无审计事件"
            @retry="timeline.refetch()"
          >
          <el-timeline style="padding:22px 30px 8px">
            <el-timeline-item v-for="event in timeline.data.value?.items ?? []" :key="event.eventId"
              :timestamp="formatTime(event.occurredAt)" placement="top" :type="event.eventId === focusedEventId ? 'primary' : undefined">
              <strong>{{ event.summary }}</strong><el-tag v-if="event.eventId === focusedEventId" size="small" style="margin-left:8px">本次操作</el-tag><div class="subtle" style="margin-top:5px">{{ event.actorCode }}<span v-if="event.detail"> · {{ event.detail }}</span></div>
            </el-timeline-item>
          </el-timeline>
          </AsyncStatePanel>
        </el-tab-pane>
      </el-tabs>
    </template>
    </AsyncStatePanel>
    <JobCommandDialog v-if="activeAction && detail.data.value" v-model="dialogVisible"
      :action="activeAction" :current-priority="detail.data.value.summary.priority"
      :submitting="command.isPending.value" @submit="submitCommand" />
  </section>
</template>
