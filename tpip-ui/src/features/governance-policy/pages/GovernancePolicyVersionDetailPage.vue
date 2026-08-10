<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { formatTime } from '@/features/global-impact/model/presentation'
import { governancePolicyAssetApi } from '../api/governancePolicyAssetApi'
import PolicyImpactJobsTable from '../components/PolicyImpactJobsTable.vue'

const route = useRoute(); const router = useRouter()
const policyId = computed(() => Number(route.params.policyId)); const versionId = computed(() => Number(route.params.versionId))
const detail = useQuery({
  queryKey: computed(() => ['governance-policy-version-asset', policyId.value, versionId.value]),
  queryFn: ({ signal }) => governancePolicyAssetApi.version(policyId.value, versionId.value, signal)
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
      <div><el-button link @click="router.push(`/governance-policies/${policyId}`)">← 返回策略详情</el-button><h2 style="margin-top:10px">策略版本详情</h2><p>不可变版本参数、抑制规则和影响任务使用证据。</p></div>
      <el-button :loading="detail.isFetching.value" @click="detail.refetch()">刷新版本</el-button>
    </div>
    <AsyncStatePanel :loading="detail.isPending.value" :error="detail.isError.value"
      loading-label="正在加载策略版本" error-title="策略版本读取失败" @retry="detail.refetch()">
      <template v-if="detail.data.value">
        <el-alert v-if="detail.data.value.version.currentlySelected" class="command-notice"
          title="这是策略当前选择的版本；版本内容不可原地修改。" type="success" :closable="false" show-icon />
        <div class="metric-strip">
          <div class="metric"><span>版本</span><strong>v{{ detail.data.value.version.versionNo }}</strong></div>
          <div class="metric"><span>生命周期</span><strong style="font-size:17px">{{ detail.data.value.version.lifecycleStatus }}</strong></div>
          <div class="metric"><span>影响任务</span><strong>{{ detail.data.value.version.impactJobCount }}</strong></div>
          <div class="metric"><span>负责人</span><strong style="font-size:16px">{{ detail.data.value.version.ownerCode }}</strong></div>
        </div>
        <div class="detail-grid">
          <div class="surface detail-card"><h3>治理参数</h3><div class="fact-grid">
            <div class="fact"><label>所属策略</label><div><button class="link-button" @click="router.push(`/governance-policies/${policyId}`)">{{ detail.data.value.policy.policyName }}</button><small class="mono"> · {{ detail.data.value.policy.policyCode }}</small></div></div>
            <div class="fact"><label>逾期阈值</label><div>{{ duration(detail.data.value.version.overdueAfterSeconds) }}</div></div>
            <div class="fact"><label>聚合窗口</label><div>{{ duration(detail.data.value.version.aggregationWindowSeconds) }}</div></div>
            <div class="fact"><label>提醒间隔</label><div>{{ duration(detail.data.value.version.reminderIntervalSeconds) }}</div></div>
            <div class="fact"><label>最大提醒次数</label><div>{{ detail.data.value.version.maximumReminders }}</div></div>
            <div class="fact"><label>发布时间</label><div>{{ formatTime(detail.data.value.version.publishedAt) }}</div></div>
            <div class="fact"><label>创建时间</label><div>{{ formatTime(detail.data.value.version.createdAt) }}</div></div>
            <div class="fact"><label>Content Checksum</label><div class="mono">{{ detail.data.value.version.contentChecksum }}</div></div>
          </div></div>
          <div class="surface detail-card"><h3>抑制规则</h3>
            <div class="rule-group"><label>漂移类型</label><div><el-tag v-for="kind in detail.data.value.version.suppressedDriftKinds" :key="kind" effect="plain">{{ kind }}</el-tag><span v-if="!detail.data.value.version.suppressedDriftKinds.length" class="subtle">无</span></div></div>
            <div class="rule-group"><label>检查项编码</label><div><el-tag v-for="code in detail.data.value.version.suppressedCheckCodes" :key="code" effect="plain">{{ code }}</el-tag><span v-if="!detail.data.value.version.suppressedCheckCodes.length" class="subtle">无</span></div></div>
            <el-alert style="margin-top:20px" title="抑制规则只影响治理提醒与聚合，不删除原始漂移事实。" type="info" :closable="false" />
          </div>
        </div>
        <div class="surface"><div class="section-title"><h3>使用该版本的影响任务</h3><span class="subtle">最多展示 20 条</span></div><PolicyImpactJobsTable :jobs="detail.data.value.recentImpactJobs" /></div>
      </template>
    </AsyncStatePanel>
  </section>
</template>
