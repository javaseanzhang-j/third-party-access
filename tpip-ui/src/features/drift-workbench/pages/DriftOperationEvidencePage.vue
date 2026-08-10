<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { formatTime } from '@/features/global-impact/model/presentation'
import { getDriftOperationEvidence } from '../api/driftWorkbenchApi'

const route = useRoute()
const router = useRouter()
const routeKey = computed(() => typeof route.params.commandKey === 'string' ? route.params.commandKey : '')
const inputKey = ref(routeKey.value)
const evidence = useQuery({
  queryKey: computed(() => ['drift-operation-evidence', routeKey.value]),
  queryFn: ({ signal }) => getDriftOperationEvidence(routeKey.value, signal),
  enabled: computed(() => routeKey.value.length > 0),
  retry: false
})

watch(routeKey, value => { inputKey.value = value })
function lookup() {
  const key = inputKey.value.trim()
  if (key) void router.push(`/drift-operations/${encodeURIComponent(key)}`)
}
function statusType(status: string) {
  return status === 'APPLIED' ? 'success' : status === 'REJECTED' ? 'danger' : 'info'
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <el-button link @click="router.push('/drift-workbench')">← 返回漂移治理工作台</el-button>
        <h2 style="margin-top:10px">治理操作证据</h2>
        <p>按 Command Key 回查请求快照、逐项结果、并发版本与幂等证据。</p>
      </div>
    </div>

    <div class="surface operation-lookup">
      <el-input v-model="inputKey" clearable placeholder="输入 Command Key" @keyup.enter="lookup" />
      <el-button type="primary" :disabled="!inputKey.trim()" @click="lookup">查询证据</el-button>
    </div>

    <el-empty v-if="!routeKey" description="请输入治理命令的 Command Key" />
    <AsyncStatePanel v-else :loading="evidence.isPending.value" :error="evidence.isError.value"
      loading-label="正在读取治理证据" error-title="治理操作证据读取失败" @retry="evidence.refetch()">
      <template v-if="evidence.data.value">
        <div class="metric-strip">
          <div class="metric"><span>状态</span><strong style="font-size:17px">{{evidence.data.value.status}}</strong></div>
          <div class="metric"><span>目标项</span><strong>{{evidence.data.value.result.itemCount}}</strong></div>
          <div class="metric"><span>已应用</span><strong>{{evidence.data.value.result.appliedCount}}</strong></div>
          <div class="metric metric--critical"><span>已拒绝</span><strong>{{evidence.data.value.result.rejectedCount}}</strong></div>
        </div>

        <div class="detail-grid">
          <div class="surface detail-card">
            <h3>命令身份</h3>
            <div class="fact-grid">
              <div class="fact"><label>Command Key</label><div class="mono">{{evidence.data.value.commandKey}}</div></div>
              <div class="fact"><label>操作类型</label><div>{{evidence.data.value.operationType}}</div></div>
              <div class="fact"><label>Workspace</label><div><button class="link-button" @click="router.push(`/workspaces/${evidence.data.value.workspaceId}`)">#{{evidence.data.value.workspaceId}}</button></div></div>
              <div class="fact"><label>操作者</label><div>{{evidence.data.value.actorCode}}</div></div>
              <div class="fact"><label>创建时间</label><div>{{formatTime(evidence.data.value.createdAt)}}</div></div>
              <div class="fact"><label>执行属性</label><div>{{evidence.data.value.dryRun?'Dry Run':'实际执行'}}</div></div>
            </div>
          </div>
          <div class="surface detail-card">
            <h3>请求完整性</h3>
            <div class="current-version-callout">
              <span>SHA-256 Request Checksum</span>
              <small class="mono operation-checksum">{{evidence.data.value.requestChecksum}}</small>
            </div>
            <div class="operation-tags">
              <el-tag :type="statusType(evidence.data.value.status)">{{evidence.data.value.status}}</el-tag>
              <el-tag v-if="evidence.data.value.result.idempotentReplay" type="warning">IDEMPOTENT REPLAY</el-tag>
              <el-tag v-if="evidence.data.value.dryRun" type="info">PREVIEW ONLY</el-tag>
            </div>
          </div>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>请求快照</h3><span class="subtle">保存规范化治理意图，不包含原始第三方报文</span></div>
          <div class="operation-request">
            <div><label>负责人</label><strong>{{evidence.data.value.request.assigneeCode??'不适用'}}</strong></div>
            <div><label>操作理由</label><strong>{{evidence.data.value.request.reason}}</strong></div>
            <div><label>完成时间</label><strong>{{formatTime(evidence.data.value.result.completedAt)}}</strong></div>
          </div>
        </div>

        <div class="surface trace-section">
          <div class="section-title"><h3>逐项执行证据</h3><span class="subtle">Expected → Previous → Resulting Row Version</span></div>
          <el-table :data="evidence.data.value.result.items" row-key="reportId">
            <el-table-column label="报告" width="90"><template #default="{row}">#{{row.reportId}}</template></el-table-column>
            <el-table-column label="资格" width="105"><template #default="{row}"><el-tag :type="row.eligible?'success':'danger'">{{row.eligible?'ELIGIBLE':'REJECTED'}}</el-tag></template></el-table-column>
            <el-table-column label="状态变化" width="190"><template #default="{row}">{{row.previousStatus??'—'}} → {{row.targetStatus??row.previousStatus??'—'}}</template></el-table-column>
            <el-table-column label="版本变化" width="190"><template #default="{row}">{{row.expectedRowVersion}} → {{row.previousRowVersion??'—'}} → {{row.resultingRowVersion??'—'}}</template></el-table-column>
            <el-table-column label="原因" min-width="230"><template #default="{row}"><strong class="mono">{{row.reasonCode}}</strong><div v-if="row.reasonMessage" class="subtle">{{row.reasonMessage}}</div></template></el-table-column>
            <el-table-column label="后继基线" width="110"><template #default="{row}">{{row.successorBaselineId??'—'}}</template></el-table-column>
          </el-table>
        </div>
      </template>
    </AsyncStatePanel>
  </section>
</template>
