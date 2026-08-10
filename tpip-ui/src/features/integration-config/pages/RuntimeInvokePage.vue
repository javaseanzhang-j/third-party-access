<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { canonicalAssetApi } from '../api/canonicalAssetApi'
import { deploymentApi } from '../api/deploymentApi'
import { runtimeInvokeApi, type RuntimeInvocationResult } from '../api/runtimeInvokeApi'

const selectedOperationId = ref(0); const environmentCode = ref('test'); const requestId = ref('')
const caller = ref('local-ui'); const tenantId = ref(''); const idempotencyKey = ref(''); const deadline = ref('')
const attributesText = ref('{\n  "traceId": "trace-local-001"\n}'); const payloadText = ref('{\n  "customerId": "C1001"\n}')
const errorMessage = ref(''); const result = ref<RuntimeInvocationResult | null>(null)

const operations = useQuery({ queryKey: ['canonical-operations'], queryFn: ({ signal }) => canonicalAssetApi.operations(undefined, signal) })
const operationItems = computed(() => operations.data.value?.items.filter(item => item.status === 'ACTIVE' && item.invocationMode === 'SYNC') ?? [])
const currentOperation = computed(() => operationItems.value.find(item => item.id === selectedOperationId.value) ?? null)
const activeRoute = useQuery({ queryKey: computed(() => ['active-route', currentOperation.value?.operationCode, environmentCode.value]), queryFn: ({ signal }) => deploymentApi.activeRoute(currentOperation.value!.operationCode, environmentCode.value, signal), enabled: computed(() => Boolean(currentOperation.value?.operationCode && environmentCode.value)), retry: false })
const invocationSuccess = computed(() => Boolean((result.value?.document.result as Record<string, unknown> | undefined)?.success))

watch(operationItems, items => {
  if (items.length && !items.some(item => item.id === selectedOperationId.value)) {
    selectedOperationId.value = items.find(item => item.operationCode.includes('customer.lookup'))?.id ?? items[0]!.id
  }
}, { immediate: true })

function resetRequestId(): void { requestId.value = `ui-${Date.now()}` }
resetRequestId()
function parseObject(text: string, field: string): Record<string, unknown> {
  let value: unknown
  try { value = JSON.parse(text) as unknown } catch { throw new Error(`${field} 不是合法 JSON。`) }
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error(`${field} 必须是 JSON Object。`)
  return value as Record<string, unknown>
}
const invoke = useMutation({ mutationFn: () => {
  const rawAttributes = parseObject(attributesText.value, 'Attributes')
  const attributes: Record<string, string> = {}
  Object.entries(rawAttributes).forEach(([key, value]) => { if (typeof value !== 'string') throw new Error(`Attributes.${key} 必须是字符串。`); attributes[key] = value })
  return runtimeInvokeApi.invoke(currentOperation.value!.operationCode, { meta: { requestId: requestId.value.trim(), caller: caller.value.trim(), tenantId: tenantId.value.trim() || null, idempotencyKey: idempotencyKey.value.trim() || null, deadline: deadline.value.trim() || null, attributes }, payload: parseObject(payloadText.value, 'Payload') })
}, onSuccess: value => { result.value = value; ElMessage.success(`Runtime 已返回 HTTP ${value.httpStatus}`) }, onError: (e: Error) => { errorMessage.value = e.message || 'Runtime 调用失败，请确认 18081 服务状态。' } })
function submit(): void {
  errorMessage.value = ''; result.value = null
  if (!currentOperation.value || !requestId.value.trim() || !caller.value.trim()) { errorMessage.value = 'Operation、Request ID 和 Caller 均为必填项。'; return }
  if (!activeRoute.data.value) { errorMessage.value = '当前 Operation + Environment 没有 ACTIVE Route，不能调用。'; return }
  invoke.mutate()
}
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>Runtime 调用控制台</h2><p>以业务系统视角只提交 Canonical Request，通过 ACTIVE Route 执行完整第三方调用管道。</p></div><el-tag effect="plain">POST :invoke</el-tag></div>
    <el-alert v-if="errorMessage" class="command-notice" type="error" :title="errorMessage" closable @close="errorMessage = ''" show-icon />
    <el-alert class="command-notice" type="info" :closable="false" title="HTTP 200 不代表业务成功，必须检查 result.success 与 result.code。业务系统不应依赖 Provider、BindingVersion、Bundle 或 Deployment ID。" show-icon />

    <div class="runtime-invoke-grid">
      <div class="surface runtime-request-panel"><div class="section-title"><h3>Canonical Invocation Request</h3><el-button @click="resetRequestId">生成 Request ID</el-button></div><el-form class="runtime-request-form" label-position="top"><div class="form-two-columns"><el-form-item label="Operation" required><el-select v-model="selectedOperationId" filterable><el-option v-for="item in operationItems" :key="item.id" :label="`${item.operationName} · ${item.operationCode}`" :value="item.id" /></el-select></el-form-item><el-form-item label="Runtime Environment"><el-input v-model="environmentCode" /></el-form-item><el-form-item label="Request ID" required><el-input v-model="requestId" /></el-form-item><el-form-item label="Caller" required><el-input v-model="caller" /></el-form-item><el-form-item label="Tenant ID（可选）"><el-input v-model="tenantId" clearable /></el-form-item><el-form-item label="Idempotency Key（可选）"><el-input v-model="idempotencyKey" clearable /></el-form-item><el-form-item label="Deadline ISO-8601（可选）"><el-input v-model="deadline" placeholder="2026-08-09T12:00:00Z" clearable /></el-form-item></div><div class="runtime-json-editors"><el-form-item label="Attributes（仅字符串值，不得包含 Secret）"><el-input v-model="attributesText" type="textarea" :rows="8" class="schema-editor" /></el-form-item><el-form-item label="Canonical Payload"><el-input v-model="payloadText" type="textarea" :rows="12" class="schema-editor" /></el-form-item></div><el-button type="primary" :disabled="!activeRoute.data.value" :loading="invoke.isPending.value" @click="submit">调用 Runtime</el-button></el-form></div>

      <div class="runtime-result-column"><div class="surface runtime-route-card"><div class="section-title"><h3>Active Route</h3><el-button :loading="activeRoute.isFetching.value" @click="activeRoute.refetch()">刷新</el-button></div><template v-if="activeRoute.data.value"><div class="active-route-revision"><span>Revision</span><strong class="mono">{{ activeRoute.data.value.revision }}</strong></div><div class="runtime-route-target" v-for="target in activeRoute.data.value.targets" :key="target.deploymentId"><strong>{{ target.trafficPercentage }}% · {{ target.deploymentCode }}</strong><small>{{ target.bundleCode }}@{{ target.bundleVersion }}</small></div></template><el-empty v-else description="无 ACTIVE Route" :image-size="55" /></div>
        <div class="surface runtime-response-card"><div class="section-title"><h3>Invocation Response</h3><el-tag v-if="result" :type="invocationSuccess ? 'success' : 'danger'">HTTP {{ result.httpStatus }}</el-tag></div><el-empty v-if="!result" description="尚未调用" :image-size="60" /><template v-else><div class="runtime-result-summary"><span>业务结果</span><strong>{{ invocationSuccess ? 'SUCCESS' : 'FAILED' }}</strong><small>{{ (result.document.result as Record<string, unknown> | undefined)?.code ?? result.document.code ?? 'UNKNOWN' }}</small></div><pre class="plan-preview runtime-result-json">{{ JSON.stringify(result.document, null, 2) }}</pre></template></div>
      </div>
    </div>
  </section>
</template>
