<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { formatTime } from '@/features/global-impact/model/presentation'
import { getGovernanceEvaluations, getGovernanceExecutions, type ReportEvaluation } from '../api/driftGovernanceEvaluationApi'
import { driftGovernanceCommandApi } from '../api/driftGovernanceCommandApi'

const route = useRoute()
const router = useRouter()
const form = reactive({ workspaceId: route.query.workspaceId ? Number(route.query.workspaceId) : null as number | null })
const workspaceId = ref<number | null>(form.workspaceId)
const currentPage = ref(0)
const evaluations = useQuery({
  queryKey: computed(() => ['drift-governance-evaluations', workspaceId.value, currentPage.value]),
  queryFn: ({ signal }) => getGovernanceEvaluations(workspaceId.value!, currentPage.value, 20, signal),
  enabled: computed(() => workspaceId.value !== null)
})
const executions = useQuery({
  queryKey: computed(() => ['drift-governance-executions', workspaceId.value]),
  queryFn: ({ signal }) => getGovernanceExecutions(workspaceId.value!, signal),
  enabled: computed(() => workspaceId.value !== null)
})
const pageCandidates = computed(() => evaluations.data.value?.items.filter(item => item.reminderCandidate).length ?? 0)
const pageSuppressed = computed(() => evaluations.data.value?.items.filter(item => item.fullySuppressed).length ?? 0)
const materializedReports = computed(() => new Set(executions.data.value?.map(item => item.driftReportId) ?? []))
const materializeTarget = ref<ReportEvaluation | null>(null)
const commandNotice = ref<{ type: 'success' | 'error'; message: string } | null>(null)
const materialize = useMutation({
  mutationFn: () => driftGovernanceCommandApi.materialize(workspaceId.value!, materializeTarget.value!.reportId),
  onSuccess: result => {
    materializeTarget.value = null
    commandNotice.value = { type: 'success', message: `报告 #${result.driftReportId} 已冻结为执行账本 #${result.id}，尚未创建提醒批次。` }
    void Promise.all([evaluations.refetch(), executions.refetch()])
  },
  onError: error => { commandNotice.value = { type: 'error', message: error instanceof Error ? error.message : '执行账本物化失败' } }
})

function search() {
  if (!form.workspaceId) return
  currentPage.value = 0
  workspaceId.value = form.workspaceId
  void router.replace({ query: { workspaceId: String(form.workspaceId) } })
}
function page(value: number) { currentPage.value = value - 1 }
function openMaterialize(row: ReportEvaluation) { commandNotice.value = null; materializeTarget.value = row }
function seconds(value: number) {
  if (value % 86400 === 0) return `${value / 86400} 天`
  if (value % 3600 === 0) return `${value / 3600} 小时`
  return `${value} 秒`
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div><h2>治理评估与执行资产</h2><p>有效策略、逐项抑制证据、提醒候选和冻结执行账本。</p></div>
      <div v-if="workspaceId" class="page-heading-actions">
        <el-button @click="router.push(`/drift-governance-metrics?workspaceId=${workspaceId}`)">查看运营度量</el-button>
        <el-button type="primary" plain @click="router.push(`/drift-workbench?workspaceId=${workspaceId}`)">进入治理工作台</el-button>
      </div>
    </div>
    <div class="surface evaluation-filter">
      <el-input-number v-model="form.workspaceId" :min="1" controls-position="right" placeholder="Workspace ID" />
      <el-button type="primary" :disabled="!form.workspaceId" @click="search">读取治理资产</el-button>
      <span>物化只冻结策略与评估快照，不创建批次或通知</span>
    </div>
    <el-empty v-if="!workspaceId" description="请选择 Workspace 后解析有效治理策略" />
    <el-alert v-if="commandNotice" class="command-notice" :title="commandNotice.message" :type="commandNotice.type" show-icon closable @close="commandNotice=null" />

    <AsyncStatePanel v-else :loading="evaluations.isPending.value" :error="evaluations.isError.value"
      loading-label="正在解析治理策略并评估报告" error-title="治理评估读取失败" @retry="evaluations.refetch()">
      <template v-if="evaluations.data.value">
        <div class="policy-evaluation-banner surface">
          <div><span>策略来源</span><strong>{{evaluations.data.value.policy.source}}</strong></div>
          <div><span>负责人</span><strong>{{evaluations.data.value.policy.ownerCode}}</strong></div>
          <div><span>超期 / 聚合窗口</span><strong>{{seconds(evaluations.data.value.policy.overdueAfterSeconds)}} / {{seconds(evaluations.data.value.policy.aggregationWindowSeconds)}}</strong></div>
          <div><span>提醒间隔 / 上限</span><strong>{{seconds(evaluations.data.value.policy.reminderIntervalSeconds)}} / {{evaluations.data.value.policy.maximumReminders}} 次</strong></div>
          <el-button v-if="evaluations.data.value.policy.policyId&&evaluations.data.value.policy.policyVersionId" link type="primary" @click="router.push(`/governance-policies/${evaluations.data.value.policy.policyId}/versions/${evaluations.data.value.policy.policyVersionId}`)">查看不可变策略版本</el-button>
        </div>
        <div class="metric-strip">
          <div class="metric"><span>可行动报告</span><strong>{{evaluations.data.value.totalElements}}</strong></div>
          <div class="metric metric--warning"><span>本页提醒候选</span><strong>{{pageCandidates}}</strong></div>
          <div class="metric"><span>本页完全抑制</span><strong>{{pageSuppressed}}</strong></div>
          <div class="metric"><span>已冻结执行账本</span><strong>{{executions.data.value?.length??0}}</strong></div>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>治理评估</h3><span class="subtle">候选 = 可行动 + 已超期 + 未完全抑制</span></div>
          <el-empty v-if="!evaluations.data.value.items.length" description="当前没有可行动漂移报告" />
          <el-table v-else :data="evaluations.data.value.items" row-key="reportId">
            <el-table-column type="expand">
              <template #default="{row}">
                <div class="suppression-evidence">
                  <div class="suppression-policy"><strong>策略抑制清单</strong><span>类别：{{evaluations.data.value.policy.suppressedDriftKinds.join(', ')||'无'}}</span><span>检查项：{{evaluations.data.value.policy.suppressedCheckCodes.join(', ')||'无'}}</span></div>
                  <el-table :data="row.drifts" size="small">
                    <el-table-column prop="itemNo" label="#" width="55" />
                    <el-table-column prop="checkCode" label="检查项" min-width="200" />
                    <el-table-column prop="driftKind" label="漂移类型" width="180" />
                    <el-table-column label="类别命中" width="100"><template #default="scope">{{scope.row.kindSuppressed?'是':'否'}}</template></el-table-column>
                    <el-table-column label="检查项命中" width="110"><template #default="scope">{{scope.row.checkSuppressed?'是':'否'}}</template></el-table-column>
                    <el-table-column label="结论" width="100"><template #default="scope"><el-tag :type="scope.row.suppressed?'info':'success'">{{scope.row.suppressed?'SUPPRESSED':'ACTIVE'}}</el-tag></template></el-table-column>
                  </el-table>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="报告" width="100"><template #default="{row}"><strong>#{{row.reportId}}</strong></template></el-table-column>
            <el-table-column prop="reviewStatus" label="评审状态" width="130" />
            <el-table-column label="到期证据" min-width="210"><template #default="{row}">{{formatTime(row.dueAt)}}<div class="subtle">{{row.overdue?'已超期':'SLA 内'}}</div></template></el-table-column>
            <el-table-column label="抑制结论" width="130"><template #default="{row}"><el-tag :type="row.fullySuppressed?'info':'success'">{{row.fullySuppressed?'完全抑制':'存在有效漂移'}}</el-tag></template></el-table-column>
            <el-table-column label="提醒候选" width="120"><template #default="{row}"><el-tag :type="row.reminderCandidate?'warning':'info'">{{row.reminderCandidate?'CANDIDATE':'NO'}}</el-tag></template></el-table-column>
            <el-table-column label="版本" width="90"><template #default="{row}">RV {{row.rowVersion}}</template></el-table-column>
            <el-table-column label="操作" width="105" fixed="right"><template #default="{row}">
              <el-button v-if="row.reminderCandidate&&!materializedReports.has(row.reportId)" link type="primary" @click="openMaterialize(row as ReportEvaluation)">冻结账本</el-button>
              <span v-else-if="materializedReports.has(row.reportId)" class="subtle">已物化</span><span v-else class="subtle">不可物化</span>
            </template></el-table-column>
          </el-table>
          <div class="policy-pagination"><span>共 {{evaluations.data.value.totalElements}} 条可行动报告</span><el-pagination background layout="prev,pager,next" :page-size="evaluations.data.value.size" :total="evaluations.data.value.totalElements" :current-page="evaluations.data.value.page+1" @current-change="page" /></div>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>冻结执行账本</h3><span class="subtle">物化不等于发送提醒 · 内部快照不对 UI 暴露</span></div>
          <AsyncStatePanel :loading="executions.isPending.value" :error="executions.isError.value" loading-label="正在读取执行账本" error-title="执行账本读取失败" @retry="executions.refetch()">
            <el-empty v-if="executions.data.value&&!executions.data.value.length" description="当前没有已冻结执行账本" />
            <el-table v-else-if="executions.data.value" :data="executions.data.value" row-key="id">
              <el-table-column label="执行 / 报告" width="130"><template #default="{row}"><strong>#{{row.id}}</strong><div class="subtle">Report #{{row.driftReportId}}</div></template></el-table-column>
              <el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="row.status==='READY'?'warning':'info'">{{row.status}}</el-tag></template></el-table-column>
              <el-table-column prop="ownerCode" label="负责人" width="140" />
              <el-table-column label="提醒预算" width="125"><template #default="{row}">{{row.reminderCount}} / {{row.maximumReminders}}</template></el-table-column>
              <el-table-column label="下次允许" width="185"><template #default="{row}">{{formatTime(row.nextReminderAt)}}</template></el-table-column>
              <el-table-column label="聚合键 / Checksum" min-width="250"><template #default="{row}"><div class="mono ledger-hash">{{row.aggregationKey}}</div><div class="mono ledger-hash subtle">{{row.evaluationChecksum}}</div></template></el-table-column>
              <el-table-column label="物化证据" width="170"><template #default="{row}">{{row.materializedBy}}<div class="subtle">{{formatTime(row.materializedAt)}}</div></template></el-table-column>
              <el-table-column label="版本" width="80"><template #default="{row}">RV {{row.rowVersion}}</template></el-table-column>
            </el-table>
          </AsyncStatePanel>
        </div>
      </template>
    </AsyncStatePanel>
    <el-dialog :model-value="materializeTarget!==null" title="确认冻结治理执行账本" width="540px"
      :close-on-click-modal="false" :show-close="!materialize.isPending.value" @update:model-value="(value: boolean)=>{if(!value)materializeTarget=null}">
      <el-alert title="系统会重新评估候选资格，并冻结当前策略版本、逐项评估、负责人、聚合键和提醒预算。该操作不会创建 Outbox 或发送通知。" type="info" :closable="false" show-icon />
      <div v-if="materializeTarget" class="command-summary">
        <div><span>Workspace</span><strong>#{{workspaceId}}</strong></div>
        <div><span>漂移报告</span><strong>#{{materializeTarget.reportId}}</strong></div>
        <div><span>候选结论</span><strong>CANDIDATE</strong></div>
      </div>
      <template #footer><el-button :disabled="materialize.isPending.value" @click="materializeTarget=null">返回检查</el-button><el-button type="primary" :loading="materialize.isPending.value" @click="materialize.mutate()">确认冻结账本</el-button></template>
    </el-dialog>
  </section>
</template>
