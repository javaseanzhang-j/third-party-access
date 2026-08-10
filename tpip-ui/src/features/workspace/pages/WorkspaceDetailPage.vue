<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import JobStatusTag from '@/features/global-impact/components/JobStatusTag.vue'
import { formatTime, riskLabel, shortId } from '@/features/global-impact/model/presentation'
import type { JobStatus, RiskLevel } from '@/features/global-impact/api/globalImpactApi'
import { workspaceAssetApi } from '../api/workspaceAssetApi'

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.workspaceId))
const detail = useQuery({
  queryKey: computed(() => ['workspace-asset', id.value]),
  queryFn: ({ signal }) => workspaceAssetApi.workspace(id.value, signal)
})
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <el-button link @click="router.push('/workspaces')">← 返回 Workspace 资产</el-button>
        <h2 style="margin-top:10px">Workspace 资产详情</h2>
        <p>有效策略解析、基线谱系、漂移报告和全局影响证据。</p>
      </div>
      <div class="page-heading-actions">
        <el-button @click="router.push(`/drift-reminder-batches?workspaceId=${id}`)">查看提醒批次</el-button>
        <el-button @click="router.push(`/drift-governance-evaluations?workspaceId=${id}`)">查看治理评估</el-button>
        <el-button @click="router.push(`/drift-governance-metrics?workspaceId=${id}`)">查看运营度量</el-button>
        <el-button type="primary" plain @click="router.push(`/drift-workbench?workspaceId=${id}`)">进入漂移治理</el-button>
        <el-button :loading="detail.isFetching.value" @click="detail.refetch()">刷新详情</el-button>
      </div>
    </div>

    <AsyncStatePanel :loading="detail.isPending.value" :error="detail.isError.value"
      loading-label="正在加载 Workspace" error-title="Workspace 详情读取失败" @retry="detail.refetch()">
      <template v-if="detail.data.value">
        <div class="metric-strip">
          <div class="metric"><span>生命周期</span><strong style="font-size:17px">{{detail.data.value.workspace.lifecycleStatus}}</strong></div>
          <div class="metric"><span>风险等级</span><strong>{{riskLabel[detail.data.value.workspace.riskLevel as RiskLevel]}}</strong></div>
          <div class="metric"><span>基线</span><strong>{{detail.data.value.workspace.baselineCount}}</strong></div>
          <div class="metric"><span>待处置漂移</span><strong>{{detail.data.value.workspace.driftSummary.actionableCount}}</strong></div>
        </div>

        <div class="detail-grid">
          <div class="surface detail-card">
            <h3>Workspace 身份</h3>
            <div class="fact-grid">
              <div class="fact"><label>名称</label><div>{{detail.data.value.workspace.workspaceName}}</div></div>
              <div class="fact"><label>编码</label><div class="mono">{{detail.data.value.workspace.workspaceCode}}</div></div>
              <div class="fact"><label>环境</label><div>{{detail.data.value.workspace.environmentCode}}</div></div>
              <div class="fact"><label>负责人</label><div>{{detail.data.value.workspace.ownerCode}}</div></div>
              <div class="fact"><label>Base Bundle</label><div>{{detail.data.value.workspace.baseBundleId??'未绑定'}}</div></div>
              <div class="fact"><label>更新时间</label><div>{{formatTime(detail.data.value.workspace.updatedAt)}}</div></div>
            </div>
          </div>
          <div class="surface detail-card">
            <h3>有效治理策略</h3>
            <template v-if="detail.data.value.workspace.effectivePolicy">
              <div class="current-version-callout">
                <span>{{detail.data.value.workspace.effectivePolicy.resolutionSource}} 来源</span>
                <strong style="font-size:18px">{{detail.data.value.workspace.effectivePolicy.policyName}}</strong>
                <small>{{detail.data.value.workspace.effectivePolicy.policyCode}} · v{{detail.data.value.workspace.effectivePolicy.versionNo}}</small>
              </div>
              <el-button type="primary" plain style="margin-top:18px"
                @click="router.push(`/governance-policies/${detail.data.value.workspace.effectivePolicy.policyId}/versions/${detail.data.value.workspace.effectivePolicy.versionId}`)">
                查看策略版本
              </el-button>
            </template>
            <el-empty v-else description="当前使用系统默认治理参数" :image-size="60" />
          </div>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>验证基线谱系</h3><span class="subtle">最近 20 条</span></div>
          <el-empty v-if="!detail.data.value.baselines.length" description="暂无验证基线" />
          <el-table v-else :data="detail.data.value.baselines" row-key="baselineId">
            <el-table-column label="基线" width="90"><template #default="{row}">#{{row.baselineId}}</template></el-table-column>
            <el-table-column prop="fixtureSuiteVersionId" label="Fixture 版本" width="120" />
            <el-table-column prop="sourceVerificationRunId" label="来源验证" width="110" />
            <el-table-column label="前序 / 接受报告" width="150"><template #default="{row}">{{row.predecessorBaselineId??'—'}} / {{row.acceptedDriftReportId??'—'}}</template></el-table-column>
            <el-table-column label="Checksum" min-width="260"><template #default="{row}"><span class="mono">{{row.baselineChecksum}}</span></template></el-table-column>
            <el-table-column label="创建时间" width="175"><template #default="{row}">{{formatTime(row.createdAt)}}</template></el-table-column>
          </el-table>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>最近漂移报告</h3><span class="subtle">原始事实不因处置而删除</span></div>
          <el-empty v-if="!detail.data.value.recentDriftReports.length" description="暂无漂移报告" />
          <el-table v-else :data="detail.data.value.recentDriftReports" row-key="reportId">
            <el-table-column label="报告" width="90"><template #default="{row}">#{{row.reportId}}</template></el-table-column>
            <el-table-column prop="driftStatus" label="结果" width="105" />
            <el-table-column label="变更 / 比较" width="110"><template #default="{row}">{{row.driftCount}} / {{row.comparedCheckCount}}</template></el-table-column>
            <el-table-column prop="reviewStatus" label="评审状态" width="120" />
            <el-table-column label="负责人" min-width="120"><template #default="{row}">{{row.assigneeCode??'未分派'}}</template></el-table-column>
            <el-table-column label="创建时间" width="175"><template #default="{row}">{{formatTime(row.createdAt)}}</template></el-table-column>
          </el-table>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>全局影响证据</h3><span class="subtle">最近 20 条</span></div>
          <el-empty v-if="!detail.data.value.recentImpactEvidence.length" description="暂无全局影响证据" />
          <el-table v-else :data="detail.data.value.recentImpactEvidence" row-key="jobId">
            <el-table-column label="任务" width="160"><template #default="{row}"><button class="link-button mono" @click="router.push(`/global-impact-jobs/${row.jobId}`)">{{shortId(row.jobId)}}</button></template></el-table-column>
            <el-table-column label="候选策略" min-width="200"><template #default="{row}"><button class="link-button" @click="router.push(`/governance-policies/${row.candidatePolicyId}/versions/${row.candidateVersionId}`)">{{row.candidatePolicyName}} · v{{row.candidateVersionNo}}</button></template></el-table-column>
            <el-table-column label="任务状态" width="105"><template #default="{row}"><JobStatusTag :status="row.jobStatus as JobStatus" /></template></el-table-column>
            <el-table-column prop="itemStatus" label="Workspace 结果" width="130" />
            <el-table-column prop="attemptCount" label="尝试" width="75" />
            <el-table-column label="操作" width="85" fixed="right"><template #default="{row}"><el-button link type="primary" @click="router.push(`/global-impact-jobs/${row.jobId}`)">查看证据</el-button></template></el-table-column>
          </el-table>
        </div>
      </template>
    </AsyncStatePanel>
  </section>
</template>
