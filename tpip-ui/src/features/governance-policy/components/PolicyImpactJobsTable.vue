<script setup lang="ts">
import { useRouter } from 'vue-router'
import JobStatusTag from '@/features/global-impact/components/JobStatusTag.vue'
import type { JobPriority, JobStatus } from '@/features/global-impact/api/globalImpactApi'
import { formatTime, priorityLabel, shortId } from '@/features/global-impact/model/presentation'
import type { PolicyImpactJob } from '../api/governancePolicyAssetApi'

defineProps<{ jobs: PolicyImpactJob[] }>()
const router = useRouter()
</script>

<template>
  <el-empty v-if="!jobs.length" description="该范围内暂无影响任务" />
  <el-table v-else :data="jobs" row-key="jobId">
    <el-table-column label="任务" min-width="160"><template #default="{ row }">
      <button class="link-button mono" @click="router.push(`/global-impact-jobs/${row.jobId}`)">{{ shortId(row.jobId) }}</button>
      <div class="subtle" style="margin-top:4px;font-size:10px">{{ formatTime(row.createdAt) }}</div>
    </template></el-table-column>
    <el-table-column label="版本" width="80"><template #default="{ row }">v{{ row.versionNo }}</template></el-table-column>
    <el-table-column label="状态" width="105"><template #default="{ row }"><JobStatusTag :status="row.status as JobStatus" /></template></el-table-column>
    <el-table-column label="优先级" width="85"><template #default="{ row }">{{ priorityLabel[row.priority as JobPriority] }}</template></el-table-column>
    <el-table-column label="进度" min-width="145"><template #default="{ row }">
      <div class="progress-caption"><span>{{ row.succeededCount + row.failedCount }}/{{ row.workspaceCount }}</span><b>{{ row.progressPercent }}%</b></div>
      <el-progress :percentage="row.progressPercent" :stroke-width="5" :show-text="false" />
    </template></el-table-column>
    <el-table-column label="操作" width="85" fixed="right"><template #default="{ row }">
      <el-button link type="primary" @click="router.push(`/global-impact-jobs/${row.jobId}`)">查看任务</el-button>
    </template></el-table-column>
  </el-table>
</template>
