<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { bindingVersionApi, type BindingVersionAsset } from '../api/bindingVersionApi'
import { canonicalAssetApi } from '../api/canonicalAssetApi'
import { integrationAssetApi } from '../api/integrationAssetApi'
import { mappingAssetApi } from '../api/mappingAssetApi'
import { policyAssetApi } from '../api/policyAssetApi'
import { providerContractVersionApi } from '../api/providerContractVersionApi'
import { accessChannelApi } from '../api/accessChannelApi'

const errorMessage = ref(''); const dialog = ref(false); const previewDialog = ref(false); const preview = ref<Record<string, unknown> | null>(null)
const selectedBindingId = ref(0); const requestContractId = ref(0); const responseContractId = ref(0)
const requestContractVersionId = ref(0); const responseContractVersionId = ref(0); const providerContractVersionId = ref(0); const endpointId = ref(0)
const accessChannelId = ref<number | null>(null)
const requestMappingId = ref(0); const responseMappingId = ref(0); const requestMappingVersionId = ref(0); const responseMappingVersionId = ref(0)
const policyId = ref<number | null>(null); const policyVersionId = ref<number | null>(null)
const complianceText = ref('{}'); const routingText = ref('{}')

const bindings = useQuery({ queryKey: ['integration-bindings'], queryFn: ({ signal }) => mappingAssetApi.bindings(signal) })
const operations = useQuery({ queryKey: ['canonical-operations'], queryFn: ({ signal }) => canonicalAssetApi.operations(undefined, signal) })
const canonicalContracts = useQuery({ queryKey: ['canonical-contracts'], queryFn: ({ signal }) => canonicalAssetApi.contracts(undefined, signal) })
const endpoints = useQuery({ queryKey: ['integration-endpoints'], queryFn: ({ signal }) => integrationAssetApi.endpoints(undefined, signal) })
const mappings = useQuery({ queryKey: ['integration-mappings'], queryFn: ({ signal }) => mappingAssetApi.mappings(undefined, signal) })
const policies = useQuery({ queryKey: ['integration-policies'], queryFn: ({ signal }) => policyAssetApi.policies(undefined, signal) })
const channels = useQuery({ queryKey: ['access-channels'], queryFn: ({ signal }) => accessChannelApi.channels(undefined, signal) })
const bindingItems = computed(() => bindings.data.value?.items ?? []); const operationItems = computed(() => operations.data.value?.items ?? [])
const contractItems = computed(() => canonicalContracts.data.value?.items ?? []); const endpointItems = computed(() => endpoints.data.value?.items ?? [])
const mappingItems = computed(() => mappings.data.value?.items ?? []); const policyItems = computed(() => policies.data.value?.items ?? [])
const currentBinding = computed(() => bindingItems.value.find(item => item.id === selectedBindingId.value) ?? null)
const requestContracts = computed(() => contractItems.value.filter(item => item.operationId === currentBinding.value?.operationId && item.contractKind === 'REQUEST'))
const responseContracts = computed(() => contractItems.value.filter(item => item.operationId === currentBinding.value?.operationId && item.contractKind === 'RESPONSE'))
const availableEndpoints = computed(() => endpointItems.value.filter(item => item.providerContractId === currentBinding.value?.providerContractId && item.lifecycleStatus === 'PUBLISHED'))
const selectedEndpoint = computed(() => availableEndpoints.value.find(item => item.id === endpointId.value))
const availableChannels = computed(() => (channels.data.value ?? []).filter(item => item.status === 'ACTIVE' && item.baseUrl === selectedEndpoint.value?.baseUrl))
const requestMappings = computed(() => mappingItems.value.filter(item => item.bindingId === selectedBindingId.value && item.direction === 'OUTBOUND_REQUEST'))
const responseMappings = computed(() => mappingItems.value.filter(item => item.bindingId === selectedBindingId.value && item.direction === 'INBOUND_RESPONSE'))
const availablePolicies = computed(() => policyItems.value.filter(item => item.bindingId === selectedBindingId.value && item.status === 'ACTIVE'))
const operationName = computed(() => operationItems.value.find(item => item.id === currentBinding.value?.operationId)?.operationName ?? '—')

const bindingVersions = useQuery({ queryKey: computed(() => ['binding-versions', selectedBindingId.value]), queryFn: ({ signal }) => bindingVersionApi.versions(selectedBindingId.value, signal), enabled: computed(() => selectedBindingId.value > 0) })
const requestContractVersions = useQuery({ queryKey: computed(() => ['canonical-contract-versions', requestContractId.value]), queryFn: ({ signal }) => canonicalAssetApi.versions(requestContractId.value, signal), enabled: computed(() => requestContractId.value > 0) })
const responseContractVersions = useQuery({ queryKey: computed(() => ['canonical-contract-versions', responseContractId.value]), queryFn: ({ signal }) => canonicalAssetApi.versions(responseContractId.value, signal), enabled: computed(() => responseContractId.value > 0) })
const providerVersions = useQuery({ queryKey: computed(() => ['provider-contract-versions', currentBinding.value?.providerContractId]), queryFn: ({ signal }) => providerContractVersionApi.versions(currentBinding.value!.providerContractId, signal), enabled: computed(() => Boolean(currentBinding.value?.providerContractId)) })
const requestMappingVersions = useQuery({ queryKey: computed(() => ['mapping-versions', requestMappingId.value]), queryFn: ({ signal }) => mappingAssetApi.versions(requestMappingId.value, signal), enabled: computed(() => requestMappingId.value > 0) })
const responseMappingVersions = useQuery({ queryKey: computed(() => ['mapping-versions', responseMappingId.value]), queryFn: ({ signal }) => mappingAssetApi.versions(responseMappingId.value, signal), enabled: computed(() => responseMappingId.value > 0) })
const policyVersions = useQuery({ queryKey: computed(() => ['policy-versions', policyId.value]), queryFn: ({ signal }) => policyAssetApi.versions(policyId.value!, signal), enabled: computed(() => Boolean(policyId.value)) })
const published = <T extends { lifecycleStatus: string }>(items: T[] | undefined): T[] => (items ?? []).filter(item => item.lifecycleStatus === 'PUBLISHED')

watch(bindingItems, items => { if (items.length && !items.some(item => item.id === selectedBindingId.value)) selectedBindingId.value = items[0]!.id }, { immediate: true })
watch([currentBinding, requestContracts, responseContracts, requestMappings, responseMappings, availableEndpoints, availablePolicies], () => {
  requestContractId.value = requestContracts.value[0]?.id ?? 0; responseContractId.value = responseContracts.value[0]?.id ?? 0
  requestMappingId.value = requestMappings.value[0]?.id ?? 0; responseMappingId.value = responseMappings.value[0]?.id ?? 0
  endpointId.value = availableEndpoints.value[0]?.id ?? 0; policyId.value = availablePolicies.value[0]?.id ?? null
})
watch(() => published(requestContractVersions.data.value), items => { requestContractVersionId.value = items[0]?.id ?? 0 })
watch(() => published(responseContractVersions.data.value), items => { responseContractVersionId.value = items[0]?.id ?? 0 })
watch(() => published(providerVersions.data.value), items => { providerContractVersionId.value = items[0]?.id ?? 0 })
watch(() => published(requestMappingVersions.data.value), items => { requestMappingVersionId.value = items[0]?.id ?? 0 })
watch(() => published(responseMappingVersions.data.value), items => { responseMappingVersionId.value = items[0]?.id ?? 0 })
watch(() => published(policyVersions.data.value), items => { policyVersionId.value = items[0]?.id ?? null })
watch(availableChannels, items => { if (!items.some(item => item.id === accessChannelId.value)) accessChannelId.value = items[0]?.id ?? null }, { immediate: true })

function message(error: Error): string { return error instanceof ApiError ? error.message : '请求失败，请确认 Control Plane 状态。' }
function parse(value: string, field: string): unknown { try { return JSON.parse(value) as unknown } catch { throw new Error(`${field} 不是合法 JSON。`) } }
const createVersion = useMutation({ mutationFn: (input: Parameters<typeof bindingVersionApi.create>[1]) => bindingVersionApi.create(selectedBindingId.value, input), onSuccess: () => { dialog.value = false; void bindingVersions.refetch(); ElMessage.success('BindingVersion 依赖闭包已冻结') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const publish = useMutation({ mutationFn: (row: BindingVersionAsset) => bindingVersionApi.publish(row.bindingId, row.id), onSuccess: () => { void bindingVersions.refetch(); ElMessage.success('BindingVersion 已发布') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const loadPreview = useMutation({ mutationFn: (row: BindingVersionAsset) => { const binding = bindingItems.value.find(item => item.id === row.bindingId)!; return bindingVersionApi.preview(row.bindingId, row.id, `preview.${binding.bindingCode}`, '0.0.1') }, onSuccess: result => { preview.value = result; previewDialog.value = true }, onError: (e: Error) => { errorMessage.value = message(e) } })
function submit(): void {
  errorMessage.value = ''
  if (![requestContractVersionId.value, responseContractVersionId.value, providerContractVersionId.value, endpointId.value, requestMappingVersionId.value, responseMappingVersionId.value].every(Boolean)) { errorMessage.value = 'Canonical、Provider、Endpoint 和双向 Mapping 都必须选择已发布版本。'; return }
  try { createVersion.mutate({ canonicalRequestContractVersionId: requestContractVersionId.value, canonicalResponseContractVersionId: responseContractVersionId.value, providerContractVersionId: providerContractVersionId.value, endpointId: endpointId.value, accessChannelId: accessChannelId.value, requestMappingVersionId: requestMappingVersionId.value, responseMappingVersionId: responseMappingVersionId.value, callbackMappingVersionId: null, policyVersionId: policyVersionId.value, errorMappingVersionId: null, complianceMetadata: parse(complianceText.value, 'Compliance Metadata'), routingAttributes: parse(routingText.value, 'Routing Attributes') }) } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'JSON 解析失败。' }
}
function requestPublish(row: unknown): void { publish.mutate(row as BindingVersionAsset) }
function requestPreview(row: unknown): void { loadPreview.mutate(row as BindingVersionAsset) }
function bindingName(id: number): string { const item = bindingItems.value.find(value => value.id === id); return item ? `${item.bindingName} · ${item.bindingCode}` : `Binding #${id}` }
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>BindingVersion 执行闭包</h2><p>冻结一次第三方调用所需的全部已发布依赖，形成可验证、可发布、可回滚的运行单元。</p></div><el-tag effect="plain">Dependency Closure</el-tag></div>
    <el-alert v-if="errorMessage" class="command-notice" type="error" :title="errorMessage" closable @close="errorMessage = ''" show-icon />
    <el-alert class="command-notice" type="info" :closable="false" title="服务端会再次检查归属、方向、Schema Reference 和生命周期；任一依赖不一致都拒绝冻结或发布。" show-icon />
    <div class="surface binding-version-panel"><div class="asset-toolbar"><div><strong>新版本使用的接入通道</strong><span>先选择 Endpoint，再选择 URL 一致的通道；冻结后会执行通道公共参数和接口覆盖参数。</span></div><el-select v-model="accessChannelId" clearable placeholder="兼容模式：不使用通道参数" style="width:320px"><el-option v-for="item in availableChannels" :key="item.id" :label="`${item.channelName} · ${item.channelCode}`" :value="item.id" /></el-select></div><el-alert v-if="selectedEndpoint && !availableChannels.length" type="warning" :closable="false" title="当前 Endpoint 没有 URL 一致的启用通道；可继续兼容模式，但不会装配公共参数。" /></div>
    <div class="surface binding-version-panel"><div class="asset-toolbar canonical-version-toolbar"><div><strong>BindingVersion</strong><span>当前 Operation：{{ operationName }}。Policy 可选，其余六项依赖必填。</span></div><div><el-select v-model="selectedBindingId" filterable><el-option v-for="item in bindingItems" :key="item.id" :label="bindingName(item.id)" :value="item.id" /></el-select><el-button type="primary" :disabled="!selectedBindingId" @click="dialog = true">冻结新版本</el-button></div></div>
      <AsyncStatePanel :loading="bindingVersions.isPending.value" :error="bindingVersions.isError.value" error-title="BindingVersion 读取失败" @retry="bindingVersions.refetch()"><el-empty v-if="!bindingVersions.data.value?.length" description="该 Binding 尚未冻结版本" /><el-table v-else :data="bindingVersions.data.value"><el-table-column label="Revision" width="100"><template #default="{ row }"><strong>{{ row.versionNo }}</strong></template></el-table-column><el-table-column label="Contract Versions" min-width="230"><template #default="{ row }"><div class="dependency-id">Canonical Req #{{ row.canonicalRequestContractVersionId }}</div><div class="dependency-id">Canonical Res #{{ row.canonicalResponseContractVersionId }}</div><div class="dependency-id">Provider #{{ row.providerContractVersionId }}</div></template></el-table-column><el-table-column label="Runtime Dependencies" min-width="230"><template #default="{ row }"><div class="dependency-id">Endpoint #{{ row.endpointId }}</div><div class="dependency-id">Mapping #{{ row.requestMappingVersionId }} / #{{ row.responseMappingVersionId }}</div><div class="dependency-id">Policy {{ row.policyVersionId ? `#${row.policyVersionId}` : '—' }}</div></template></el-table-column><el-table-column prop="idempotencyClass" label="幂等" width="150" /><el-table-column label="Checksum" min-width="280"><template #default="{ row }"><span class="mono checksum-cell">{{ row.contentChecksum }}</span></template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ row.lifecycleStatus }}</el-tag></template></el-table-column><el-table-column label="操作" width="160"><template #default="{ row }"><el-button v-if="row.lifecycleStatus === 'PUBLISHED'" link type="primary" @click="requestPreview(row)">Bundle 预览</el-button><el-button v-else link type="primary" @click="requestPublish(row)">发布</el-button></template></el-table-column></el-table></AsyncStatePanel>
    </div>
    <el-dialog v-model="dialog" title="冻结 BindingVersion" width="980px"><el-alert type="warning" :closable="false" title="创建后依赖 ID 和 checksum 不可修改；变更任何依赖都必须创建新 BindingVersion。" show-icon /><el-form class="dialog-form binding-closure-form" label-position="top"><h4>Canonical Contract</h4><div class="form-two-columns"><el-form-item label="Request Contract"><el-select v-model="requestContractId"><el-option v-for="item in requestContracts" :key="item.id" :label="item.contractName" :value="item.id" /></el-select></el-form-item><el-form-item label="Published Version" required><el-select v-model="requestContractVersionId"><el-option v-for="item in published(requestContractVersions.data.value)" :key="item.id" :label="`${item.semanticVersion} · #${item.id}`" :value="item.id" /></el-select></el-form-item><el-form-item label="Response Contract"><el-select v-model="responseContractId"><el-option v-for="item in responseContracts" :key="item.id" :label="item.contractName" :value="item.id" /></el-select></el-form-item><el-form-item label="Published Version" required><el-select v-model="responseContractVersionId"><el-option v-for="item in published(responseContractVersions.data.value)" :key="item.id" :label="`${item.semanticVersion} · #${item.id}`" :value="item.id" /></el-select></el-form-item></div><h4>Provider Runtime</h4><div class="form-two-columns"><el-form-item label="ProviderContract Published Version" required><el-select v-model="providerContractVersionId"><el-option v-for="item in published(providerVersions.data.value)" :key="item.id" :label="`${item.semanticVersion} · #${item.id}`" :value="item.id" /></el-select></el-form-item><el-form-item label="Published Endpoint" required><el-select v-model="endpointId"><el-option v-for="item in availableEndpoints" :key="item.id" :label="`${item.endpointCode} · ${item.environmentCode} · r${item.revisionNo}`" :value="item.id" /></el-select></el-form-item></div><h4>Mapping 与 Policy</h4><div class="form-two-columns"><el-form-item label="Outbound Mapping"><el-select v-model="requestMappingId"><el-option v-for="item in requestMappings" :key="item.id" :label="item.mappingName" :value="item.id" /></el-select></el-form-item><el-form-item label="Published Version" required><el-select v-model="requestMappingVersionId"><el-option v-for="item in published(requestMappingVersions.data.value)" :key="item.id" :label="`Revision ${item.versionNo} · #${item.id}`" :value="item.id" /></el-select></el-form-item><el-form-item label="Inbound Mapping"><el-select v-model="responseMappingId"><el-option v-for="item in responseMappings" :key="item.id" :label="item.mappingName" :value="item.id" /></el-select></el-form-item><el-form-item label="Published Version" required><el-select v-model="responseMappingVersionId"><el-option v-for="item in published(responseMappingVersions.data.value)" :key="item.id" :label="`Revision ${item.versionNo} · #${item.id}`" :value="item.id" /></el-select></el-form-item><el-form-item label="Policy（可选）"><el-select v-model="policyId" clearable><el-option v-for="item in availablePolicies" :key="item.id" :label="item.policyName" :value="item.id" /></el-select></el-form-item><el-form-item label="Compiled + Published Version"><el-select v-model="policyVersionId" clearable><el-option v-for="item in published(policyVersions.data.value).filter(v => v.compileStatus === 'COMPILED')" :key="item.id" :label="`Revision ${item.versionNo} · #${item.id}`" :value="item.id" /></el-select></el-form-item></div><h4>Metadata</h4><div class="form-two-columns"><el-form-item label="Compliance Metadata"><el-input v-model="complianceText" type="textarea" :rows="5" class="schema-editor" /></el-form-item><el-form-item label="Routing Attributes"><el-input v-model="routingText" type="textarea" :rows="5" class="schema-editor" /></el-form-item></div></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="createVersion.isPending.value" @click="submit">校验并冻结 DRAFT</el-button></template></el-dialog>
    <el-dialog v-model="previewDialog" title="Bundle Manifest 预览" width="920px"><pre class="plan-preview">{{ JSON.stringify(preview, null, 2) }}</pre><template #footer><el-button @click="previewDialog = false">关闭</el-button></template></el-dialog>
  </section>
</template>
