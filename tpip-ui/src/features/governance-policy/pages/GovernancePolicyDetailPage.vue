<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { formatTime } from '@/features/global-impact/model/presentation'
import { governancePolicyAssetApi } from '../api/governancePolicyAssetApi'
import PolicyImpactJobsTable from '../components/PolicyImpactJobsTable.vue'

const route = useRoute(); const router = useRouter()
const policyId = computed(() => Number(route.params.policyId))
const detail = useQuery({
  queryKey: computed(() => ['governance-policy-asset', policyId.value]),
  queryFn: ({ signal }) => governancePolicyAssetApi.policy(policyId.value, signal)
})
function duration(seconds: number): string {
  if (seconds % 86400 === 0) return `${seconds / 86400} 天`
  if (seconds % 3600 === 0) return `${seconds / 3600} 小时`
  return `${seconds} 秒`
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div><el-button link @click="router.push('/governance-policies')">← 返回策略资产</el-button><h2 style="margin-top:10px">策略资产详情</h2><p>查看策略身份、版本谱系和最近影响任务。</p></div>
      <el-button :loading="detail.isFetching.value" @click="detail.refetch()">刷新详情</el-button>
    </div>
    <AsyncStatePanel :loading="detail.isPending.value" :error="detail.isError.value"
      loading-label="正在加载策略资产" error-title="策略资产详情读取失败" @retry="detail.refetch()">
      <template v-if="detail.data.value">
        <div class="metric-strip">
          <div class="metric"><span>状态</span><strong>{{ detail.data.value.policy.status }}</strong></div>
          <div class="metric"><span>作用域</span><strong>{{ detail.data.value.policy.scope }}</strong></div>
          <div class="metric"><span>版本数</span><strong>{{ detail.data.value.policy.versionCount }}</strong></div>
          <div class="metric"><span>影响任务</span><strong>{{ detail.data.value.policy.impactJobCount }}</strong></div>
        </div>
        <div class="detail-grid">
          <div class="surface detail-card"><h3>资产身份</h3><div class="fact-grid">
            <div class="fact"><label>策略名称</label><div>{{ detail.data.value.policy.policyName }}</div></div>
            <div class="fact"><label>策略编码</label><div class="mono">{{ detail.data.value.policy.policyCode }}</div></div>
            <div class="fact"><label>Workspace</label><div>{{ detail.data.value.policy.workspace ? `${detail.data.value.policy.workspace.workspaceName} · ${detail.data.value.policy.workspace.environmentCode}` : 'Global 默认策略' }}</div></div>
            <div class="fact"><label>Row Version</label><div class="mono">{{ detail.data.value.policy.rowVersion }}</div></div>
            <div class="fact"><label>创建时间</label><div>{{ formatTime(detail.data.value.policy.createdAt) }}</div></div>
            <div class="fact"><label>更新时间</label><div>{{ formatTime(detail.data.value.policy.updatedAt) }}</div></div>
          </div></div>
          <div class="surface detail-card"><h3>当前选择</h3>
            <template v-if="detail.data.value.policy.currentVersion">
              <div class="current-version-callout"><span>当前版本</span><strong>v{{ detail.data.value.policy.currentVersion.versionNo }}</strong><small>{{ detail.data.value.policy.currentVersion.lifecycleStatus }}</small></div>
              <el-button type="primary" plain style="margin-top:18px" @click="router.push(`/governance-policies/${policyId}/versions/${detail.data.value.policy.currentVersion.versionId}`)">查看版本定义</el-button>
            </template><el-empty v-else description="尚未选择已发布版本" :image-size="64" />
          </div>
        </div>
        <div class="surface" style="margin-bottom:16px"><div class="section-title"><h3>不可变版本</h3><span class="subtle">点击版本查看完整参数</span></div>
          <el-empty v-if="!detail.data.value.versions.length" description="暂无版本" />
          <el-table v-else :data="detail.data.value.versions" row-key="versionId">
            <el-table-column label="版本" width="100"><template #default="{ row }"><button class="link-button" @click="router.push(`/governance-policies/${policyId}/versions/${row.versionId}`)">v{{ row.versionNo }}</button><el-tag v-if="row.currentlySelected" size="small" type="success" style="margin-left:7px">当前</el-tag></template></el-table-column>
            <el-table-column prop="lifecycleStatus" label="生命周期" width="115" />
            <el-table-column label="逾期阈值" width="110"><template #default="{ row }">{{ duration(row.overdueAfterSeconds) }}</template></el-table-column>
            <el-table-column label="聚合窗口" width="110"><template #default="{ row }">{{ duration(row.aggregationWindowSeconds) }}</template></el-table-column>
            <el-table-column prop="ownerCode" label="负责人" min-width="130" />
            <el-table-column prop="impactJobCount" label="影响任务" width="95" />
            <el-table-column label="创建时间" width="175"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
            <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="router.push(`/governance-policies/${policyId}/versions/${row.versionId}`)">查看定义</el-button></template></el-table-column>
          </el-table>
        </div>
        <div class="surface"><div class="section-title"><h3>最近影响任务</h3><span class="subtle">最多展示 20 条</span></div><PolicyImpactJobsTable :jobs="detail.data.value.recentImpactJobs" /></div>
      </template>
    </AsyncStatePanel>
  </section>
</template>
