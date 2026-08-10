<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { formatTime } from '@/features/global-impact/model/presentation'
import { getDriftGroups, getGovernanceMetrics, type MetricsQuery } from '../api/driftGovernanceMetricsApi'

const route = useRoute()
const router = useRouter()
const form = reactive({
  workspaceId: route.query.workspaceId ? Number(route.query.workspaceId) : null as number | null,
  windowDays: 30,
  overrideEnabled: false,
  slaHours: 72
})
const applied = ref<MetricsQuery | null>(form.workspaceId ? { workspaceId: form.workspaceId, windowDays: 30 } : null)
const metrics = useQuery({
  queryKey: computed(() => ['drift-governance-metrics', applied.value]),
  queryFn: ({ signal }) => getGovernanceMetrics(applied.value!, signal),
  enabled: computed(() => applied.value !== null)
})
const groups = useQuery({
  queryKey: computed(() => ['drift-groups', applied.value?.workspaceId, metrics.data.value?.slaHours]),
  queryFn: ({ signal }) => getDriftGroups({
    workspaceId: applied.value!.workspaceId,
    scope: 'ALL',
    overdueAfterHours: metrics.data.value!.slaHours,
    limit: 20
  }, signal),
  enabled: computed(() => applied.value !== null && metrics.data.value !== undefined)
})
const dailyMaximum = computed(() => Math.max(1, ...(metrics.data.value?.daily.map(day =>
  day.createdReports + day.resolvedReports + day.previewedCommands + day.appliedCommands + day.rejectedCommands) ?? [1])))

function search() {
  if (!form.workspaceId) return
  applied.value = {
    workspaceId: form.workspaceId,
    windowDays: form.windowDays,
    ...(form.overrideEnabled ? { slaHours: form.slaHours } : {})
  }
  void router.replace({ query: { workspaceId: String(form.workspaceId) } })
}
function drill(checkCode: string, driftKind: string) {
  void router.push({ path: '/drift-workbench', query: {
    workspaceId: String(applied.value!.workspaceId), checkCode, driftKind, scope: 'ALL'
  } })
}
function bar(value: number) { return `${Math.max(value ? 3 : 0, value / dailyMaximum.value * 100)}%` }
</script>

<template>
  <section>
    <div class="page-heading">
      <div><h2>治理运营度量</h2><p>Workspace SLA、负责人负载、处置效率、命令质量和漂移聚合簇。</p></div>
      <el-button v-if="applied" @click="router.push(`/drift-workbench?workspaceId=${applied.workspaceId}`)">进入治理工作台</el-button>
    </div>
    <div class="surface metrics-filter">
      <el-input-number v-model="form.workspaceId" :min="1" controls-position="right" placeholder="Workspace ID" />
      <el-select v-model="form.windowDays"><el-option v-for="value in [7,14,30,60,90]" :key="value" :label="`${value} 天`" :value="value" /></el-select>
      <el-checkbox v-model="form.overrideEnabled">分析覆盖 SLA</el-checkbox>
      <el-input-number v-model="form.slaHours" :disabled="!form.overrideEnabled" :min="1" :max="8760" controls-position="right" />
      <span>小时</span>
      <el-button type="primary" :disabled="!form.workspaceId" @click="search">刷新度量</el-button>
    </div>
    <el-empty v-if="!applied" description="请选择 Workspace 后读取治理运营度量" />
    <AsyncStatePanel v-else :loading="metrics.isPending.value" :error="metrics.isError.value"
      loading-label="正在计算治理度量" error-title="治理运营度量读取失败" @retry="metrics.refetch()">
      <template v-if="metrics.data.value">
        <div class="sla-banner surface">
          <div><span>有效治理 SLA</span><strong>{{metrics.data.value.slaHours}} 小时</strong></div>
          <div><span>解析来源</span><strong>{{metrics.data.value.slaPolicy.source}} · {{metrics.data.value.slaPolicy.mode}}</strong></div>
          <div><span>策略负责人</span><strong>{{metrics.data.value.slaPolicy.ownerCode??'系统默认'}}</strong></div>
          <div class="sla-banner-action"><small>快照 {{formatTime(metrics.data.value.snapshotAt)}}</small><el-button v-if="metrics.data.value.slaPolicy.policyId&&metrics.data.value.slaPolicy.policyVersionId" link type="primary" @click="router.push(`/governance-policies/${metrics.data.value.slaPolicy.policyId}/versions/${metrics.data.value.slaPolicy.policyVersionId}`)">查看策略版本</el-button></div>
        </div>
        <div class="metric-strip">
          <div class="metric"><span>当前待处置</span><strong>{{metrics.data.value.backlog.actionableReports}}</strong></div>
          <div class="metric metric--critical"><span>已突破 SLA</span><strong>{{metrics.data.value.backlog.breachedReports}}</strong><small>{{metrics.data.value.backlog.breachRatePercent}}%</small></div>
          <div class="metric"><span>分派覆盖率</span><strong>{{metrics.data.value.backlog.assignmentCoveragePercent}}%</strong><small>{{metrics.data.value.backlog.assignedReports}} 已分派</small></div>
          <div class="metric metric--warning"><span>未分派且超期</span><strong>{{metrics.data.value.backlog.unassignedBreachedReports}}</strong></div>
        </div>

        <div class="metrics-summary-grid">
          <div class="surface metrics-card">
            <div class="section-title"><h3>处置效率</h3><span class="subtle">最近 {{metrics.data.value.windowDays}} 天</span></div>
            <div class="metrics-facts"><div><span>已解决</span><strong>{{metrics.data.value.resolution.resolvedReports}}</strong></div><div><span>SLA 达标率</span><strong>{{metrics.data.value.resolution.slaCompliancePercent}}%</strong></div><div><span>接受 / 驳回</span><strong>{{metrics.data.value.resolution.acceptedReports}} / {{metrics.data.value.resolution.dismissedReports}}</strong></div><div><span>平均 / 最大耗时</span><strong>{{metrics.data.value.resolution.averageResolutionHours}}h / {{metrics.data.value.resolution.maximumResolutionHours}}h</strong></div></div>
          </div>
          <div class="surface metrics-card">
            <div class="section-title"><h3>治理命令质量</h3><span class="subtle">Dry Run 不进入成功率分母</span></div>
            <div class="metrics-facts"><div><span>正式成功率</span><strong>{{metrics.data.value.commands.executionSuccessPercent}}%</strong></div><div><span>预检 / 应用 / 拒绝</span><strong>{{metrics.data.value.commands.previewedCommands}} / {{metrics.data.value.commands.appliedCommands}} / {{metrics.data.value.commands.rejectedCommands}}</strong></div><div><span>请求成员</span><strong>{{metrics.data.value.commands.requestedItems}}</strong></div><div><span>应用 / 拒绝成员</span><strong>{{metrics.data.value.commands.appliedItems}} / {{metrics.data.value.commands.rejectedItems}}</strong></div></div>
          </div>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>每日事实趋势</h3><span class="subtle">UTC 日历日 · 缺失日由服务端补零</span></div>
          <div class="daily-trend">
            <div v-for="day in metrics.data.value.daily" :key="day.date" class="daily-column" :title="`${day.date} 创建 ${day.createdReports} / 解决 ${day.resolvedReports} / 预检 ${day.previewedCommands} / 应用 ${day.appliedCommands} / 拒绝 ${day.rejectedCommands}`">
              <div class="daily-bars"><i class="daily-created" :style="{height:bar(day.createdReports)}" /><i class="daily-resolved" :style="{height:bar(day.resolvedReports)}" /><i class="daily-command" :style="{height:bar(day.previewedCommands+day.appliedCommands+day.rejectedCommands)}" /></div><span>{{day.date.slice(5)}}</span>
            </div>
          </div>
          <div class="trend-legend"><span><i class="daily-created" />创建报告</span><span><i class="daily-resolved" />解决报告</span><span><i class="daily-command" />治理命令</span></div>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>负责人负载</h3><span class="subtle">只统计当前可行动报告</span></div>
          <el-empty v-if="!metrics.data.value.assignees.length" description="当前没有负责人负载" />
          <el-table v-else :data="metrics.data.value.assignees">
            <el-table-column label="负责人" min-width="180"><template #default="{row}"><strong>{{row.assigned?row.assigneeCode:'未分派'}}</strong></template></el-table-column>
            <el-table-column prop="actionableReports" label="待处置" width="110" />
            <el-table-column prop="overdueReports" label="已超期" width="110" />
            <el-table-column label="最老待办" width="200"><template #default="{row}">{{row.oldestAgeHours}} 小时<div class="subtle">{{formatTime(row.oldestActionableAt)}}</div></template></el-table-column>
            <el-table-column label="操作" width="110"><template #default="{row}"><el-button link type="primary" :disabled="!row.assigned" @click="router.push({path:'/drift-workbench',query:{workspaceId:String(applied!.workspaceId),assigneeCode:row.assigneeCode}})">{{row.assigned?'查看待办':'尚未分派'}}</el-button></template></el-table-column>
          </el-table>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>漂移聚合簇</h3><span class="subtle">Check Code + Drift Kind · 有效 SLA {{metrics.data.value.slaHours}}h</span></div>
          <AsyncStatePanel :loading="groups.isPending.value" :error="groups.isError.value" loading-label="正在聚合漂移簇" error-title="漂移聚合读取失败" @retry="groups.refetch()">
            <el-empty v-if="groups.data.value&&!groups.data.value.length" description="当前没有漂移聚合簇" />
            <el-table v-else-if="groups.data.value" :data="groups.data.value" row-key="checkCode">
              <el-table-column prop="checkCode" label="检查项" min-width="230" />
              <el-table-column prop="driftKind" label="漂移类型" width="180" />
              <el-table-column prop="reportCount" label="报告" width="90" />
              <el-table-column prop="actionableReportCount" label="待处置" width="90" />
              <el-table-column prop="overdueReportCount" label="超期" width="90" />
              <el-table-column label="最近发生" width="180"><template #default="{row}">{{formatTime(row.latestReportAt)}}</template></el-table-column>
              <el-table-column label="操作" width="100"><template #default="{row}"><el-button link type="primary" @click="drill(row.checkCode,row.driftKind)">钻取报告</el-button></template></el-table-column>
            </el-table>
          </AsyncStatePanel>
        </div>
      </template>
    </AsyncStatePanel>
  </section>
</template>
