<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import JobStatusTag from '../components/JobStatusTag.vue'
import { globalImpactOperationsApi, type StallSeverity } from '../api/globalImpactOperationsApi'
import type { JobPriority, JobStatus } from '../api/globalImpactApi'
import { formatTime, priorityLabel, recoveryLabel, shortId } from '../model/presentation'

const router = useRouter()
const filters = reactive<{ severity: StallSeverity | ''; priority: JobPriority | ''; recovery: string }>({
  severity: '', priority: '', recovery: ''
})
const overview = useQuery({
  queryKey: ['global-impact-operations-overview'],
  queryFn: ({ signal }) => globalImpactOperationsApi.overview(100, signal),
  refetchInterval: 30_000
})
const visibleItems = computed(() => (overview.data.value?.items ?? []).filter(item =>
  (!filters.severity || item.severity === filters.severity)
  && (!filters.priority || item.priority === filters.priority)
  && (!filters.recovery || item.recoveryRecommendation === filters.recovery)))
const recoveryOptions = computed(() => overview.data.value?.recoveryRecommendations ?? [])
function duration(seconds: number): string {
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟`
  return `${Math.floor(seconds / 3600)} 小时 ${Math.floor(seconds % 3600 / 60)} 分钟`
}
function recoveryName(value: string): string { return recoveryLabel[value] ?? value }
function reset(): void { filters.severity = ''; filters.priority = ''; filters.recovery = '' }
</script>

<template>
  <section>
    <div class="page-heading">
      <div><h2>全局运营态势</h2><p>关注超过服务端 SLO 阈值仍未推进的影响任务，并进入对应任务完成处置。</p></div>
      <el-button :loading="overview.isFetching.value" @click="overview.refetch()">刷新态势</el-button>
    </div>
    <AsyncStatePanel :loading="overview.isPending.value" :error="overview.isError.value"
      loading-label="正在加载运营态势" error-title="运营态势读取失败" @retry="overview.refetch()">
      <template v-if="overview.data.value">
        <div class="metric-strip">
          <div class="metric"><span>停滞任务</span><strong>{{ overview.data.value.stalledCount }}</strong></div>
          <div class="metric metric--critical"><span>Critical</span><strong>{{ overview.data.value.criticalCount }}</strong></div>
          <div class="metric metric--warning"><span>Warning</span><strong>{{ overview.data.value.warningCount }}</strong></div>
          <div class="metric"><span>Critical 阈值</span><strong style="font-size:17px">{{ duration(overview.data.value.criticalThresholdSeconds) }}</strong></div>
        </div>
        <el-alert v-if="overview.data.value.limitReached" class="command-notice"
          title="结果已达到 100 条查询上限；请优先处置 Critical 任务，完整统计以监控指标为准。"
          type="warning" :closable="false" show-icon />
        <div class="operations-layout">
          <div class="surface operations-main">
            <div class="operations-filter">
              <el-select v-model="filters.severity" clearable placeholder="全部 SLO 等级">
                <el-option label="Critical" value="CRITICAL" /><el-option label="Warning" value="WARNING" />
              </el-select>
              <el-select v-model="filters.priority" clearable placeholder="全部优先级">
                <el-option v-for="(label, value) in priorityLabel" :key="value" :label="label" :value="value" />
              </el-select>
              <el-select v-model="filters.recovery" clearable placeholder="全部恢复建议">
                <el-option v-for="item in recoveryOptions" :key="item.recommendation"
                  :label="`${recoveryName(item.recommendation)} (${item.count})`" :value="item.recommendation" />
              </el-select>
              <el-button @click="reset">重置</el-button>
              <span>当前显示 {{ visibleItems.length }} / {{ overview.data.value.stalledCount }}</span>
            </div>
            <el-empty v-if="!visibleItems.length" description="当前范围内没有停滞任务" />
            <el-table v-else :data="visibleItems" row-key="jobId">
              <el-table-column label="SLO" width="100"><template #default="{ row }"><el-tag :type="row.severity === 'CRITICAL' ? 'danger' : 'warning'" effect="dark">{{ row.severity }}</el-tag></template></el-table-column>
              <el-table-column label="任务 / 策略" min-width="260"><template #default="{ row }"><button class="link-button mono" @click="router.push(`/global-impact-jobs/${row.jobId}`)">{{ shortId(row.jobId) }}</button><div style="margin-top:5px"><button class="link-button" style="font-size:12px" @click="router.push(`/governance-policies/${row.candidatePolicy.policyId}/versions/${row.candidatePolicy.versionId}`)">{{ row.candidatePolicy.policyName }} · v{{ row.candidatePolicy.versionNo }}</button></div></template></el-table-column>
              <el-table-column label="状态" width="100"><template #default="{ row }"><JobStatusTag :status="row.status as JobStatus" /></template></el-table-column>
              <el-table-column label="优先级" width="90"><template #default="{ row }">{{ priorityLabel[row.priority as JobPriority] }}</template></el-table-column>
              <el-table-column label="进度" width="150"><template #default="{ row }"><div class="progress-caption"><span>{{ row.progress.succeededCount + row.progress.failedCount }}/{{ row.progress.workspaceCount }}</span><b>{{ row.progress.progressPercent }}%</b></div><el-progress :percentage="row.progress.progressPercent" :stroke-width="5" :show-text="false" /></template></el-table-column>
              <el-table-column label="停滞" width="120"><template #default="{ row }"><strong>{{ duration(row.stalledSeconds) }}</strong><div class="subtle" style="margin-top:4px;font-size:9px">{{ formatTime(row.lastProgressAt) }}</div></template></el-table-column>
              <el-table-column label="恢复建议" min-width="180"><template #default="{ row }">{{ recoveryName(row.recoveryRecommendation) }}</template></el-table-column>
              <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="router.push(`/global-impact-jobs/${row.jobId}`)">处置</el-button></template></el-table-column>
            </el-table>
          </div>
          <aside class="surface operations-aside">
            <h3>恢复建议分布</h3>
            <div v-if="!recoveryOptions.length" class="empty-copy">当前无恢复建议</div>
            <button v-for="item in recoveryOptions" :key="item.recommendation"
              :class="{ active: filters.recovery === item.recommendation }" @click="filters.recovery = item.recommendation">
              <span>{{ recoveryName(item.recommendation) }}</span><strong>{{ item.count }}</strong>
            </button>
            <p>Warning 阈值 {{ duration(overview.data.value.stallThresholdSeconds) }}；页面每 30 秒刷新，阈值由 Control Plane 配置决定。</p>
          </aside>
        </div>
      </template>
    </AsyncStatePanel>
  </section>
</template>
