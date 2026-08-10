<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { globalImpactSnapshotApi } from '../api/globalImpactSnapshotApi'
import type { RiskLevel } from '../api/globalImpactApi'
import { formatTime, riskLabel, shortId } from '../model/presentation'

const route = useRoute(); const router = useRouter()
const snapshotId = computed(() => String(route.params.snapshotId))
const page = ref(0)
const detail = useQuery({
  queryKey: computed(() => ['global-impact-snapshot', snapshotId.value]),
  queryFn: ({ signal }) => globalImpactSnapshotApi.snapshot(snapshotId.value, signal)
})
const workspaces = useQuery({
  queryKey: computed(() => ['global-impact-snapshot-workspaces', snapshotId.value, page.value]),
  queryFn: ({ signal }) => globalImpactSnapshotApi.workspaces(snapshotId.value, page.value, 10, signal)
})
function refresh(): void { void Promise.all([detail.refetch(), workspaces.refetch()]) }
function signed(value: number): string { return `${value >= 0 ? '+' : ''}${value}` }
function riskName(value: RiskLevel): string { return riskLabel[value] }
</script>

<template>
  <section>
    <div class="page-heading">
      <div><el-button link @click="router.back()">← 返回上一页</el-button><h2 style="margin-top:10px">封板快照 {{ shortId(snapshotId) }}</h2><p>不可变治理证据、消费状态及 Workspace 影响明细。</p></div>
      <el-button :loading="detail.isFetching.value || workspaces.isFetching.value" @click="refresh">刷新快照</el-button>
    </div>
    <AsyncStatePanel :loading="detail.isPending.value" :error="detail.isError.value"
      loading-label="正在加载封板快照" error-title="封板快照读取失败" @retry="detail.refetch()">
      <template v-if="detail.data.value">
        <el-alert v-if="detail.data.value.expired" title="该快照已过期，仅保留为审计证据，不能用于后续策略发布或激活。"
          type="warning" :closable="false" show-icon />
        <div class="metric-strip">
          <div class="metric"><span>资产状态</span><strong>{{ detail.data.value.expired ? '已过期' : '有效' }}</strong></div>
          <div class="metric"><span>Workspace</span><strong>{{ detail.data.value.workspaceCount }}</strong></div>
          <div class="metric"><span>策略版本</span><strong>v{{ detail.data.value.candidatePolicy.versionNo }}</strong></div>
          <div class="metric"><span>创建人</span><strong style="font-size:16px">{{ detail.data.value.createdBy }}</strong></div>
        </div>
        <div class="detail-grid">
          <div class="surface detail-card"><h3>快照事实</h3><div class="fact-grid">
            <div class="fact"><label>候选策略</label><div><button class="link-button" @click="router.push(`/governance-policies/${detail.data.value.candidatePolicy.policyId}/versions/${detail.data.value.candidatePolicy.versionId}`)">{{ detail.data.value.candidatePolicy.policyName }} · {{ detail.data.value.candidatePolicy.policyCode }}</button></div></div>
            <div class="fact"><label>创建时间</label><div>{{ formatTime(detail.data.value.createdAt) }}</div></div>
            <div class="fact"><label>过期时间</label><div>{{ formatTime(detail.data.value.expiresAt) }}</div></div>
            <div class="fact"><label>Snapshot ID</label><div class="mono">{{ detail.data.value.snapshotId }}</div></div>
            <div class="fact"><label>Candidate Checksum</label><div class="mono">{{ detail.data.value.candidateChecksum }}</div></div>
            <div class="fact"><label>Coverage Checksum</label><div class="mono">{{ detail.data.value.coverageChecksum }}</div></div>
            <div class="fact"><label>Impact Checksum</label><div class="mono">{{ detail.data.value.impactChecksum }}</div></div>
          </div></div>
          <div class="surface detail-card"><h3>消费状态</h3><div class="fact-grid">
            <div class="fact"><label>发布消费</label><div>{{ detail.data.value.consumption.publishUsedAt ? `${detail.data.value.consumption.publishUsedBy} · ${formatTime(detail.data.value.consumption.publishUsedAt)}` : '未消费' }}</div></div>
            <div class="fact"><label>激活消费</label><div>{{ detail.data.value.consumption.activationUsedAt ? `${detail.data.value.consumption.activationUsedBy} · ${formatTime(detail.data.value.consumption.activationUsedAt)}` : '未消费' }}</div></div>
          </div><el-alert style="margin-top:18px" title="消费记录由发布/激活门禁写入，快照内容封板后不可修改。" type="info" :closable="false" /></div>
        </div>
      </template>
    </AsyncStatePanel>
    <div class="surface" style="margin-top:18px">
      <AsyncStatePanel :loading="workspaces.isPending.value" :error="workspaces.isError.value"
        :empty="workspaces.isSuccess.value && !workspaces.data.value?.items.length"
        loading-label="正在加载 Workspace 快照" error-title="Workspace 快照读取失败"
        empty-description="该快照没有 Workspace 证据" @retry="workspaces.refetch()">
        <el-table :data="workspaces.data.value?.items ?? []" row-key="snapshotId">
          <el-table-column label="Workspace" min-width="240"><template #default="{ row }"><button class="link-button" @click="router.push(`/workspaces/${row.workspaceId}`)">{{ row.workspaceName }}</button><div class="subtle mono" style="margin-top:4px;font-size:10px">{{ row.workspaceCode }}</div></template></el-table-column>
          <el-table-column prop="environmentCode" label="环境" width="90" />
          <el-table-column label="风险" width="90"><template #default="{ row }">{{ riskName(row.riskLevel) }}</template></el-table-column>
          <el-table-column label="变化报告" width="100"><template #default="{ row }">{{ row.impact.totalChangedReports }}</template></el-table-column>
          <el-table-column label="新增逾期" width="100"><template #default="{ row }">{{ row.impact.summary.newlyOverdueReports }}</template></el-table-column>
          <el-table-column label="新增提醒" width="100"><template #default="{ row }">{{ row.impact.summary.addedReminderCandidates }}</template></el-table-column>
          <el-table-column label="提醒间隔变化" width="130"><template #default="{ row }">{{ signed(row.impact.parameterChanges.reminderIntervalSecondsDelta) }}s</template></el-table-column>
          <el-table-column label="当前策略" min-width="180"><template #default="{ row }">{{ row.currentPolicy ? `${row.currentPolicy.policyName} · v${row.currentPolicy.versionNo}` : '未配置' }}</template></el-table-column>
        </el-table>
        <div style="display:flex;justify-content:flex-end;padding:16px"><el-pagination background layout="total,prev,pager,next"
          :total="workspaces.data.value?.totalElements ?? 0" :page-size="10" :current-page="page + 1"
          @current-change="(value: number) => page = value - 1" /></div>
      </AsyncStatePanel>
    </div>
  </section>
</template>
