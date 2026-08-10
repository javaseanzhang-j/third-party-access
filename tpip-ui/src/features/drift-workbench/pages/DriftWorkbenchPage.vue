<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { formatTime } from '@/features/global-impact/model/presentation'
import {
  driftWorkbenchApi,
  type BulkResult,
  type DriftKind,
  type Filters,
  type Operation,
  type Scope,
  type WorkItem
} from '../api/driftWorkbenchApi'

const route = useRoute()
const router = useRouter()
const client = useQueryClient()
const routeScope = ['ACTIONABLE', 'RESOLVED', 'ALL'].includes(String(route.query.scope))
  ? route.query.scope as Scope : 'ACTIONABLE'
const routeDriftKind = ['NEW_CHECK', 'MISSING_CHECK', 'STATUS_CHANGED', 'RESULT_CHANGED', 'EVIDENCE_CHANGED']
  .includes(String(route.query.driftKind)) ? route.query.driftKind as DriftKind : ''
const form = reactive<{
  workspaceId: number | null
  scope: Scope
  driftKind: DriftKind | ''
  checkCode: string
  assigneeCode: string
  overdueOnly: boolean
}>({
  workspaceId: route.query.workspaceId ? Number(route.query.workspaceId) : null,
  scope: routeScope,
  driftKind: routeDriftKind,
  checkCode: typeof route.query.checkCode === 'string' ? route.query.checkCode : '',
  assigneeCode: typeof route.query.assigneeCode === 'string' ? route.query.assigneeCode : '',
  overdueOnly: false
})
const applied = ref<Filters>({
  workspaceId: form.workspaceId ?? undefined,
  scope: form.scope,
  driftKind: form.driftKind,
  checkCode: form.checkCode,
  assigneeCode: form.assigneeCode,
  page: 0,
  size: 20
})
const selected = ref<WorkItem[]>([])
const reports = useQuery({
  queryKey: computed(() => ['drift-workbench-reports', applied.value]),
  queryFn: ({ signal }) => driftWorkbenchApi.reports(applied.value, signal)
})
const summary = useQuery({
  queryKey: computed(() => ['drift-workbench-summary', applied.value.workspaceId]),
  queryFn: ({ signal }) => driftWorkbenchApi.summary({ workspaceId: applied.value.workspaceId, scope: 'ALL' }, signal)
})

function search() {
  applied.value = {
    workspaceId: form.workspaceId ?? undefined,
    scope: form.scope,
    driftKind: form.driftKind,
    checkCode: form.checkCode,
    assigneeCode: form.assigneeCode,
    overdueOnly: form.overdueOnly,
    page: 0,
    size: 20
  }
  void router.replace({ query: {
    ...(form.workspaceId ? { workspaceId: String(form.workspaceId) } : {}),
    ...(form.scope !== 'ACTIONABLE' ? { scope: form.scope } : {}),
    ...(form.driftKind ? { driftKind: form.driftKind } : {}),
    ...(form.checkCode.trim() ? { checkCode: form.checkCode.trim() } : {}),
    ...(form.assigneeCode.trim() ? { assigneeCode: form.assigneeCode.trim() } : {})
  } })
}
function page(value: number) { applied.value = { ...applied.value, page: value - 1 } }

watch(() => reports.data.value, () => { selected.value = [] })
const sameWorkspace = computed(() => selected.value.length > 0
  && selected.value.every(item => item.workspaceId === selected.value[0]!.workspaceId))
const dialog = reactive<{
  visible: boolean
  operation: Operation
  reason: string
  assignee: string
  preview: BulkResult | null
}>({ visible: false, operation: 'ASSIGN', reason: '', assignee: '', preview: null })

function open(operation: Operation) {
  dialog.operation = operation
  dialog.reason = ''
  dialog.assignee = ''
  dialog.preview = null
  dialog.visible = true
}
const command = useMutation({
  mutationFn: (dryRun: boolean) => driftWorkbenchApi.bulk(dialog.operation, {
    workspaceId: selected.value[0]!.workspaceId,
    dryRun,
    reason: dialog.reason,
    assigneeCode: dialog.operation === 'ASSIGN' ? dialog.assignee : undefined,
    items: selected.value.map(item => ({ reportId: item.reportId, rowVersion: item.rowVersion }))
  }),
  onSuccess: (result, dryRun) => {
    if (dryRun) dialog.preview = result
    else dialog.visible = false
  }
})
const valid = computed(() => dialog.reason.trim().length > 0
  && (dialog.operation !== 'ASSIGN' || dialog.assignee.trim().length > 0))
const canApply = computed(() => valid.value && dialog.preview?.rejectedCount === 0
  && dialog.preview?.eligibleCount === selected.value.length)

watch(() => command.data.value, value => {
  if (value && command.variables.value === false) {
    void Promise.all([
      client.invalidateQueries({ queryKey: ['drift-workbench-reports'] }),
      client.invalidateQueries({ queryKey: ['drift-workbench-summary'] })
    ])
    void router.push(`/drift-operations/${encodeURIComponent(value.commandKey)}`)
  }
})
watch([
  () => dialog.reason,
  () => dialog.assignee,
  () => selected.value.map(item => `${item.reportId}:${item.rowVersion}`).join(',')
], () => { dialog.preview = null })

function actionLabel(operation: Operation) {
  return { ASSIGN: '分派负责人', ACKNOWLEDGE: '批量确认', ACCEPT: '接受并建立后继基线', DISMISS: '驳回漂移' }[operation]
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div><h2>漂移治理工作台</h2><p>基于 Row Version 先 Dry Run，再执行负责人分派、确认与处置。</p></div>
      <el-button :loading="reports.isFetching.value" @click="reports.refetch()">刷新工作台</el-button>
    </div>
    <div v-if="summary.data.value" class="metric-strip">
      <div class="metric"><span>全部报告</span><strong>{{summary.data.value.totalReports}}</strong></div>
      <div class="metric"><span>待处置</span><strong>{{summary.data.value.actionableReports}}</strong></div>
      <div class="metric metric--critical"><span>已超期</span><strong>{{summary.data.value.overdueReports}}</strong></div>
      <div class="metric"><span>变更项</span><strong>{{summary.data.value.changedItems}}</strong></div>
    </div>

    <div class="surface">
      <div class="drift-filter">
        <el-input-number v-model="form.workspaceId" :min="1" controls-position="right" placeholder="Workspace ID" />
        <el-select v-model="form.scope"><el-option label="待处置" value="ACTIONABLE" /><el-option label="已处置" value="RESOLVED" /><el-option label="全部" value="ALL" /></el-select>
        <el-select v-model="form.driftKind" clearable placeholder="全部漂移类型"><el-option v-for="value in ['NEW_CHECK','MISSING_CHECK','STATUS_CHANGED','RESULT_CHANGED','EVIDENCE_CHANGED']" :key="value" :label="value" :value="value" /></el-select>
        <el-input v-model="form.checkCode" clearable placeholder="检查项编码" />
        <el-input v-model="form.assigneeCode" clearable placeholder="负责人" />
        <el-checkbox v-model="form.overdueOnly">仅超期</el-checkbox>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
      <div class="bulk-toolbar">
        <span>已选择 {{selected.length}} 项</span>
        <el-button :disabled="!sameWorkspace" @click="open('ASSIGN')">分派</el-button>
        <el-button :disabled="!sameWorkspace" @click="open('ACKNOWLEDGE')">确认</el-button>
        <el-button :disabled="!sameWorkspace" type="success" plain @click="open('ACCEPT')">接受</el-button>
        <el-button :disabled="!sameWorkspace" type="danger" plain @click="open('DISMISS')">驳回</el-button>
        <small v-if="selected.length&&!sameWorkspace">批量操作必须属于同一 Workspace</small>
      </div>
      <AsyncStatePanel :loading="reports.isPending.value" :error="reports.isError.value"
        loading-label="正在加载漂移报告" error-title="漂移工作台读取失败" @retry="reports.refetch()">
        <template v-if="reports.data.value">
          <el-empty v-if="!reports.data.value.items.length" description="当前范围内没有漂移报告" />
          <el-table v-else :data="reports.data.value.items" row-key="reportId" @selection-change="selected=$event">
            <el-table-column type="selection" width="46" />
            <el-table-column label="报告 / Workspace" width="150"><template #default="{row}"><strong>#{{row.reportId}}</strong><div><button class="link-button" @click="router.push(`/workspaces/${row.workspaceId}`)">Workspace #{{row.workspaceId}}</button></div></template></el-table-column>
            <el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="row.overdue?'danger':row.reviewStatus==='OPEN'?'warning':'info'">{{row.reviewStatus}}</el-tag><div v-if="row.overdue" class="subtle" style="font-size:10px;margin-top:4px">已超期</div></template></el-table-column>
            <el-table-column label="漂移签名" min-width="260"><template #default="{row}"><div class="signature-list"><span v-for="drift in row.drifts.slice(0,3)" :key="drift.itemNo">{{drift.driftKind}} · {{drift.checkCode}}</span><small v-if="row.drifts.length>3">另 {{row.drifts.length-3}} 项</small></div></template></el-table-column>
            <el-table-column label="负责人" width="130"><template #default="{row}">{{row.assigneeCode??'未分派'}}</template></el-table-column>
            <el-table-column label="年龄 / 到期" width="180"><template #default="{row}">{{row.ageHours}} 小时<div class="subtle" style="font-size:10px">{{formatTime(row.dueAt)}}</div></template></el-table-column>
            <el-table-column label="版本" width="80"><template #default="{row}">RV {{row.rowVersion}}</template></el-table-column>
          </el-table>
          <div class="policy-pagination"><span>共 {{reports.data.value.totalElements}} 条报告</span><el-pagination background layout="prev,pager,next" :page-size="reports.data.value.size" :total="reports.data.value.totalElements" :current-page="reports.data.value.page+1" @current-change="page" /></div>
        </template>
      </AsyncStatePanel>
    </div>

    <el-dialog v-model="dialog.visible" :title="actionLabel(dialog.operation)" width="660px">
      <el-alert title="必须先执行 Dry Run；预检全部通过后才能实际执行。实际执行使用新的幂等键和当前 Row Version。" type="info" :closable="false" />
      <el-form label-position="top" style="margin-top:18px">
        <el-form-item v-if="dialog.operation==='ASSIGN'" label="负责人"><el-input v-model="dialog.assignee" maxlength="100" /></el-form-item>
        <el-form-item label="操作理由"><el-input v-model="dialog.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <el-alert v-if="command.isError.value" title="治理命令执行失败，请刷新事实后重新决策。" type="error" show-icon />
      <div v-if="dialog.preview" class="dry-run-result">
        <strong>Dry Run：{{dialog.preview.eligibleCount}} 可执行 / {{dialog.preview.rejectedCount}} 拒绝</strong>
        <div class="dry-run-command-key"><span class="mono">{{dialog.preview.commandKey}}</span><el-button link type="primary" @click="router.push(`/drift-operations/${encodeURIComponent(dialog.preview.commandKey)}`)">查看预检证据</el-button></div>
        <div v-for="item in dialog.preview.items.filter(value=>!value.eligible)" :key="item.reportId">#{{item.reportId}} · {{item.reasonCode}} · {{item.reasonMessage}}</div>
      </div>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button :disabled="!valid" :loading="command.isPending.value" @click="command.mutate(true)">执行 Dry Run</el-button>
        <el-button type="primary" :disabled="!canApply" :loading="command.isPending.value" @click="command.mutate(false)">确认实际执行</el-button>
      </template>
    </el-dialog>
  </section>
</template>
