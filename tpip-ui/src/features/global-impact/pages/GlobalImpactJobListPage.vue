<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { globalImpactApi, type JobFilters, type JobPriority, type JobStatus } from '../api/globalImpactApi'
import JobStatusTag from '../components/JobStatusTag.vue'
import { formatTime, jobStatusLabel, priorityLabel, shortId } from '../model/presentation'

const route = useRoute()
const router = useRouter()
const form = reactive({
  keyword: String(route.query.keyword ?? ''),
  status: String(route.query.status ?? ''),
  priority: String(route.query.priority ?? '')
})
const page = computed(() => Math.max(0, Number(route.query.page ?? 0)))
const filters = computed<JobFilters>(() => ({
  keyword: String(route.query.keyword ?? '') || undefined,
  status: String(route.query.status ?? '') || undefined,
  priority: String(route.query.priority ?? '') || undefined,
  page: page.value,
  size: 20
}))
const jobs = useQuery({
  queryKey: computed(() => ['global-impact-jobs', filters.value]),
  queryFn: ({ signal }) => globalImpactApi.jobs(filters.value, signal)
})
const counts = computed(() => {
  const items = jobs.data.value?.items ?? []
  return {
    total: jobs.data.value?.totalElements ?? 0,
    running: items.filter(item => item.status === 'RUNNING').length,
    attention: items.filter(item => item.status === 'FAILED' || item.status === 'READY').length,
    terminal: items.filter(item => ['SEALED', 'EXPIRED', 'CANCELLED'].includes(item.status)).length
  }
})

function applyFilters(): void {
  void router.replace({ query: {
    ...(form.keyword ? { keyword: form.keyword.trim() } : {}),
    ...(form.status ? { status: form.status } : {}),
    ...(form.priority ? { priority: form.priority } : {}),
    page: '0'
  } })
}
function resetFilters(): void {
  form.keyword = ''; form.status = ''; form.priority = ''
  void router.replace({ query: { page: '0' } })
}
function changePage(value: number): void {
  void router.replace({ query: { ...route.query, page: String(value - 1) } })
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div><h2>全局影响任务</h2><p>观察治理策略在全部 Workspace 上的计算、证据与封板状态。</p></div>
      <div class="action-row"><el-button :loading="jobs.isFetching.value" @click="jobs.refetch()">刷新数据</el-button><el-button type="primary" @click="router.push('/global-impact-jobs/new')">创建影响任务</el-button></div>
    </div>
    <div class="metric-strip">
      <div class="metric"><span>任务总数</span><strong>{{ counts.total }}</strong></div>
      <div class="metric"><span>本页执行中</span><strong>{{ counts.running }}</strong></div>
      <div class="metric"><span>本页待处理</span><strong>{{ counts.attention }}</strong></div>
      <div class="metric"><span>本页终态</span><strong>{{ counts.terminal }}</strong></div>
    </div>
    <div class="surface">
      <form class="filter-bar" aria-label="任务筛选" @submit.prevent="applyFilters">
        <el-input v-model="form.keyword" class="grow" clearable placeholder="任务 ID、策略编码或名称" />
        <el-select v-model="form.status" clearable placeholder="全部状态" style="width: 150px">
          <el-option v-for="(label, value) in jobStatusLabel" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="form.priority" clearable placeholder="全部优先级" style="width: 140px">
          <el-option v-for="(label, value) in priorityLabel" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button native-type="submit" type="primary">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </form>
      <AsyncStatePanel
        :loading="jobs.isPending.value"
        :error="jobs.isError.value"
        :empty="jobs.isSuccess.value && !jobs.data.value?.items.length"
        loading-label="正在加载全局影响任务"
        error-title="无法读取任务数据，请确认 Control Plane 已启动。"
        empty-description="没有符合当前条件的全局影响任务"
        @retry="jobs.refetch()"
      >
      <el-table :data="jobs.data.value?.items ?? []" row-key="jobId">
        <el-table-column label="任务 / 候选策略" min-width="290">
          <template #default="{ row }">
            <button class="link-button mono" @click="router.push(`/global-impact-jobs/${row.jobId}`)">{{ shortId(row.jobId) }}</button>
            <button class="link-button" style="margin-top:6px;font-size:12px" @click="router.push(`/governance-policies/${row.candidatePolicy.policyId}/versions/${row.candidatePolicy.versionId}`)">{{ row.candidatePolicy.policyName }}</button>
            <div class="subtle mono" style="margin-top:3px;font-size:10px">{{ row.candidatePolicy.policyCode }} · v{{ row.candidatePolicy.versionNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><JobStatusTag :status="row.status as JobStatus" /></template></el-table-column>
        <el-table-column label="优先级" width="90"><template #default="{ row }">{{ priorityLabel[row.priority as JobPriority] }}</template></el-table-column>
        <el-table-column label="处理进度" min-width="220">
          <template #default="{ row }">
            <div class="progress-cell"><div class="progress-caption"><span>{{ row.progress.processedCount }}/{{ row.progress.workspaceCount }} Workspace</span><b>{{ row.progress.progressPercent }}%</b></div>
            <el-progress :percentage="row.progress.progressPercent" :stroke-width="6" :show-text="false" /></div>
          </template>
        </el-table-column>
        <el-table-column label="成功 / 失败" width="120"><template #default="{ row }"><span style="color:#27835f">{{ row.progress.succeededCount }}</span> / <span style="color:#b64c4c">{{ row.progress.failedCount }}</span></template></el-table-column>
        <el-table-column label="最近进度" width="185"><template #default="{ row }"><span class="subtle">{{ formatTime(row.lastProgressAt) }}</span></template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="router.push(`/global-impact-jobs/${row.jobId}`)">详情</el-button></template></el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:18px;border-top:1px solid var(--line)">
        <el-pagination background layout="total, prev, pager, next" :total="jobs.data.value?.totalElements ?? 0" :page-size="20" :current-page="page + 1" @current-change="changePage" />
      </div>
      </AsyncStatePanel>
    </div>
  </section>
</template>
