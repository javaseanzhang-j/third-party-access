<script setup lang="ts">
import { computed } from 'vue'
import type { WorkspaceImpactSummary } from '../api/globalImpactApi'
import { distributionPercent } from '../model/summaryPresentation'

const props = defineProps<{ summary: WorkspaceImpactSummary }>()
const riskSegments = computed(() => [
  { key: 'low', label: '低风险', count: props.summary.risks.low, color: '#57aa84' },
  { key: 'medium', label: '中风险', count: props.summary.risks.medium, color: '#d5a445' },
  { key: 'high', label: '高风险', count: props.summary.risks.high, color: '#d7764f' },
  { key: 'critical', label: '严重', count: props.summary.risks.critical, color: '#a73e46' }
].map(item => ({ ...item, percent: distributionPercent(item.count, props.summary.workspaceCount) })))
</script>

<template>
  <div class="impact-summary-grid">
    <div class="impact-risk-panel">
      <div class="summary-heading"><div><span>全量风险分布</span><strong>{{ summary.workspaceCount }} Workspace</strong></div><small>非当前分页统计</small></div>
      <div class="risk-bar" aria-label="Workspace 风险分布">
        <span v-for="item in riskSegments" :key="item.key" :style="{ width: `${item.percent}%`, background: item.color }" :title="`${item.label} ${item.count}`" />
      </div>
      <div class="risk-legend">
        <div v-for="item in riskSegments" :key="item.key"><i :style="{ background: item.color }" /><span>{{ item.label }}</span><strong>{{ item.count }}</strong></div>
      </div>
    </div>
    <div class="impact-kpis">
      <div><span>变化报告</span><strong>{{ summary.impactTotals.changedReports }}</strong></div>
      <div><span>新增超期</span><strong>{{ summary.impactTotals.newlyOverdueReports }}</strong></div>
      <div><span>新增提醒候选</span><strong>{{ summary.impactTotals.addedReminderCandidates }}</strong></div>
    </div>
    <div class="status-distribution">
      <span>执行状态</span>
      <div><b>{{ summary.statuses.succeeded }}</b> 成功</div>
      <div><b>{{ summary.statuses.failed }}</b> 失败</div>
      <div><b>{{ summary.statuses.running }}</b> 执行中</div>
      <div><b>{{ summary.statuses.pending }}</b> 待执行</div>
    </div>
  </div>
</template>
