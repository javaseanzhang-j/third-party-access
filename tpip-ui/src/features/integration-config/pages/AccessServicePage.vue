<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { accessServiceApi, type AccessServiceReadiness, type AccessServiceReadinessCheck,
  type CreateAccessServiceInput, type ProductAccessService, type ReadinessStatus } from '../api/accessServiceApi'
import { integrationAssetApi } from '../api/integrationAssetApi'
import { serviceRouteApi, type DryRunResult, type RoutePolicyView, type RouteTargetConfig } from '../api/serviceRouteApi'
import { accessChannelApi } from '../api/accessChannelApi'
import { providerContractVersionApi, type ProviderContractVersionAsset } from '../api/providerContractVersionApi'
import { businessIntegrationApi, type InterfaceTransportVersion } from '../api/businessIntegrationApi'
import { canonicalAssetApi, type CanonicalContractVersionAsset } from '../api/canonicalAssetApi'
import BusinessFieldMappingEditor from '../components/BusinessFieldMappingEditor.vue'
import BusinessContractFieldEditor from '../components/BusinessContractFieldEditor.vue'
import { autoMatchFields, exampleFromSchema, executeMappingPreview, extractSchemaFields,
  type BusinessMappingRow } from '../model/businessFieldMapping'
import { buildBusinessMessageExample, buildBusinessMessageSchema,
  type BusinessMessageField } from '../model/businessMessageSchema'

const queryClient = useQueryClient()
const servicesQuery = useQuery({ queryKey: ['product-services'], queryFn: ({ signal }) => accessServiceApi.list(signal) })
const interfacesQuery = useQuery({ queryKey: ['provider-contracts'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const providersQuery = useQuery({ queryKey: ['providers'], queryFn: ({ signal }) => integrationAssetApi.providers('', signal) })
const channelsQuery = useQuery({ queryKey: ['product-channels'], queryFn: ({ signal }) => accessChannelApi.channels(undefined, signal) })
const productsQuery = useQuery({ queryKey: ['provider-products'], queryFn: ({ signal }) => accessChannelApi.products(undefined, signal) })
const services = computed(() => servicesQuery.data.value ?? [])
const createDialog = ref(false)
const detailService = ref<ProductAccessService | null>(null)
const readiness = ref<AccessServiceReadiness | null>(null)
const readinessLoading = ref(false)
const targetDialog = ref(false)
const routeDialog = ref(false)
const routeView = ref<RoutePolicyView | null>(null)
const routeRows = ref<Array<RouteTargetConfig & { targetName: string; conditionText: string; health: 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN' }>>([])
const routeSettings = reactive({ healthFilterEnabled: true, fallbackMode: 'ONLY_NOT_SENT' as 'ONLY_NOT_SENT' | 'DISABLED' })
const dryRunForm = reactive({ requestId: '', routingKey: '', attributesText: '{}' })
const dryRunResult = ref<DryRunResult | null>(null)
const errorMessage = ref('')
const defaultSchema = JSON.stringify({ type: 'object', additionalProperties: false, properties: {} }, null, 2)
const standardContractMode = ref<'FORM' | 'JSON'>('FORM')
const standardRequestFields = ref<BusinessMessageField[]>([]); const standardResponseFields = ref<BusinessMessageField[]>([])
const form = reactive({ serviceCode: '', serviceName: '', description: '', invocationMode: 'SYNC' as 'SYNC' | 'ASYNC' | 'CALLBACK',
  idempotencyClass: 'UNKNOWN' as 'UNKNOWN' | 'IDEMPOTENT' | 'IDEMPOTENT_WITH_KEY' | 'NON_IDEMPOTENT',
  dataClassification: 'INTERNAL' as 'INTERNAL' | 'PUBLIC' | 'CONFIDENTIAL' | 'RESTRICTED', ownerCode: 'local',
  requestSchemaText: defaultSchema, requestExampleText: '{}', responseSchemaText: defaultSchema, responseExampleText: '{}' })
const targetForm = reactive({ providerId: undefined as number | undefined, providerProductId: undefined as number | undefined,
  providerContractId: undefined as number | undefined, providerContractVersionId: undefined as number | undefined,
  accessChannelId: undefined as number | undefined, transportVersionId: undefined as number | undefined,
  targetName: '', ownerCode: 'local' })
const channelInterfaces = ref<Record<number, number[]>>({})
const providerVersions = ref<ProviderContractVersionAsset[]>([])
const transportVersions = ref<InterfaceTransportVersion[]>([])
const canonicalRequestVersion = ref<CanonicalContractVersionAsset | null>(null)
const canonicalResponseVersion = ref<CanonicalContractVersionAsset | null>(null)
const requestMappingRows = ref<BusinessMappingRow[]>([]); const responseMappingRows = ref<BusinessMappingRow[]>([])
const requestSampleText = ref('{}'); const providerResponseSampleText = ref('{}')
const requestPreview = ref<unknown | null>(null); const responsePreview = ref<unknown | null>(null)
const productInterfaceIds = ref<number[]>([])
const eligibleProducts = computed(() => (productsQuery.data.value ?? []).filter(item =>
  item.providerId === targetForm.providerId && item.status === 'ACTIVE'))
const eligibleInterfaces = computed(() => (interfacesQuery.data.value?.items ?? []).filter(item =>
  item.status === 'ACTIVE' && item.providerId === targetForm.providerId && productInterfaceIds.value.includes(item.id)))
const selectedInterface = computed(() => interfacesQuery.data.value?.items.find(item => item.id === targetForm.providerContractId) ?? null)
const eligibleChannels = computed(() => (channelsQuery.data.value ?? []).filter(item => item.status === 'ACTIVE'
  && item.providerId === selectedInterface.value?.providerId
  && item.providerProductId === targetForm.providerProductId
  && (channelInterfaces.value[item.id] ?? []).includes(targetForm.providerContractId ?? 0)))
const selectedProviderVersion = computed(() => providerVersions.value.find(item => item.id === targetForm.providerContractVersionId) ?? null)
const businessRequestFields = computed(() => extractSchemaFields(canonicalRequestVersion.value?.schemaDocument))
const businessResponseFields = computed(() => extractSchemaFields(canonicalResponseVersion.value?.schemaDocument))
const providerRequestFields = computed(() => extractSchemaFields(selectedProviderVersion.value?.requestSchema))
const providerResponseFields = computed(() => extractSchemaFields(selectedProviderVersion.value?.responseSchema))

function parseJson(text: string, label: string, optional = false): unknown {
  if (optional && !text.trim()) return null
  try { return JSON.parse(text) } catch { throw new Error(`${label}不是合法 JSON`) }
}
function providerName(providerId: number): string {
  return providersQuery.data.value?.items.find(item => item.id === providerId)?.providerName ?? `第三方 #${providerId}`
}
function emptyStandardField(): BusinessMessageField {
  return { path: '$.', name: '', type: 'STRING', required: false, description: '', example: '' }
}
function openCreate(): void {
  errorMessage.value = ''; standardContractMode.value = 'FORM'
  if (!standardRequestFields.value.length) standardRequestFields.value = [emptyStandardField()]
  if (!standardResponseFields.value.length) standardResponseFields.value = [emptyStandardField()]
  createDialog.value = true
}
async function loadReadiness(): Promise<void> {
  if (!detailService.value) return
  readinessLoading.value = true
  try { readiness.value = await accessServiceApi.readiness(detailService.value.id) }
  catch { readiness.value = null; ElMessage.error('接入就绪检查读取失败') }
  finally { readinessLoading.value = false }
}
function openDetail(service: unknown): void {
  detailService.value = service as ProductAccessService
  readiness.value = null
  void loadReadiness()
}
function readinessLabel(status: ReadinessStatus): string {
  if (status === 'READY') return '可以验证'
  if (status === 'READY_WITH_WARNINGS') return '可以验证，有提示'
  return '暂不能验证'
}
function readinessTag(status: ReadinessStatus): 'success' | 'warning' | 'danger' {
  return status === 'READY' ? 'success' : status === 'READY_WITH_WARNINGS' ? 'warning' : 'danger'
}
function checkIcon(check: AccessServiceReadinessCheck): string {
  return check.status === 'PASS' ? '✓' : check.status === 'WARN' ? '!' : '×'
}
async function openTarget(): Promise<void> {
  errorMessage.value = ''; targetForm.providerId = undefined; targetForm.providerProductId = undefined
  targetForm.providerContractId = undefined; targetForm.providerContractVersionId = undefined
  targetForm.accessChannelId = undefined; targetForm.transportVersionId = undefined; targetForm.targetName = ''
  providerVersions.value = []; transportVersions.value = []; requestMappingRows.value = []; responseMappingRows.value = []
  requestPreview.value = null; responsePreview.value = null
  targetDialog.value = true
  if (!channelsQuery.data.value) await channelsQuery.refetch()
  const channels = channelsQuery.data.value ?? []
  channelInterfaces.value = Object.fromEntries(await Promise.all(channels.map(async item => [item.id, await accessChannelApi.interfaceIds(item.id)])))
  const requestContract = detailService.value?.contracts.find(item => item.kind === 'REQUEST')
  const responseContract = detailService.value?.contracts.find(item => item.kind === 'RESPONSE')
  const [requestVersions, responseVersions] = await Promise.all([
    requestContract ? canonicalAssetApi.versions(requestContract.contractId) : Promise.resolve([]),
    responseContract ? canonicalAssetApi.versions(responseContract.contractId) : Promise.resolve([])
  ])
  canonicalRequestVersion.value = requestVersions.find(item => item.id === requestContract?.versionId) ?? null
  canonicalResponseVersion.value = responseVersions.find(item => item.id === responseContract?.versionId) ?? null
  requestSampleText.value = pretty(canonicalRequestVersion.value?.exampleDocument
    ?? exampleFromSchema(canonicalRequestVersion.value?.schemaDocument))
}
function selectTargetProvider(providerId: number): void {
  targetForm.providerId = providerId; targetForm.providerProductId = undefined; targetForm.providerContractId = undefined
  targetForm.providerContractVersionId = undefined; targetForm.accessChannelId = undefined
  targetForm.transportVersionId = undefined; providerVersions.value = []; transportVersions.value = []; productInterfaceIds.value = []
}
async function selectTargetProduct(productId: number): Promise<void> {
  targetForm.providerProductId = productId; targetForm.providerContractId = undefined
  targetForm.providerContractVersionId = undefined; targetForm.accessChannelId = undefined
  targetForm.transportVersionId = undefined; providerVersions.value = []; transportVersions.value = []
  productInterfaceIds.value = await accessChannelApi.productInterfaceIds(productId)
}
async function selectTargetInterface(contractId: number): Promise<void> {
  targetForm.providerContractId = contractId; targetForm.providerContractVersionId = undefined
  const [protocols, transports] = await Promise.all([
    providerContractVersionApi.versions(contractId), businessIntegrationApi.transportVersions(contractId)
  ])
  providerVersions.value = protocols.filter(item => item.lifecycleStatus === 'PUBLISHED')
  transportVersions.value = transports.filter(item => item.lifecycleStatus === 'PUBLISHED')
  targetForm.providerContractVersionId = providerVersions.value[0]?.id
  targetForm.accessChannelId = eligibleChannels.value[0]?.id
  targetForm.transportVersionId = transportVersions.value[0]?.id
  configureMappings()
  const item = selectedInterface.value
  if (item && !targetForm.targetName) targetForm.targetName = `${providerName(item.providerId)}${item.contractName}实现`
}
function selectProtocolVersion(): void { configureMappings() }
function configureMappings(): void {
  requestMappingRows.value = autoMatchFields(businessRequestFields.value, providerRequestFields.value)
  responseMappingRows.value = autoMatchFields(providerResponseFields.value, businessResponseFields.value)
  const examples = selectedProviderVersion.value?.examples
  const responseExample = examples && typeof examples === 'object' && !Array.isArray(examples)
    ? (examples as Record<string, unknown>).response : null
  providerResponseSampleText.value = pretty(responseExample ?? exampleFromSchema(selectedProviderVersion.value?.responseSchema))
  requestPreview.value = null; responsePreview.value = null
}
function runMappingPreview(): void {
  errorMessage.value = ''
  try {
    requestPreview.value = executeMappingPreview(parseJson(requestSampleText.value, '业务请求样例'), requestMappingRows.value)
    responsePreview.value = executeMappingPreview(parseJson(providerResponseSampleText.value, '第三方返回样例'), responseMappingRows.value)
    ElMessage.success('请求和返回映射预览通过')
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '映射预览失败' }
}
function pretty(value: unknown): string { return JSON.stringify(value ?? {}, null, 2) }
const createService = useMutation({
  mutationFn: (input: CreateAccessServiceInput) => accessServiceApi.create(input),
  onSuccess: async created => { createDialog.value = false; await queryClient.invalidateQueries({ queryKey: ['product-services'] }); openDetail(created); ElMessage.success('接入服务与标准契约已创建') },
  onError: error => { errorMessage.value = error instanceof Error ? error.message : '创建失败' }
})
function submitCreate(): void {
  errorMessage.value = ''
  if (!form.serviceCode.trim() || !form.serviceName.trim() || !form.ownerCode.trim()) { errorMessage.value = '请填写服务编码、名称和负责人'; return }
  try {
    const requestFields = standardRequestFields.value.filter(item => item.path.trim() && item.path.trim() !== '$.')
    const responseFields = standardResponseFields.value.filter(item => item.path.trim() && item.path.trim() !== '$.')
    const emptyObject = { type: 'object', additionalProperties: false, properties: {} }
    const requestSchema = standardContractMode.value === 'FORM'
      ? buildBusinessMessageSchema(requestFields) ?? emptyObject : parseJson(form.requestSchemaText, '标准请求结构') as Record<string, unknown>
    const responseSchema = standardContractMode.value === 'FORM'
      ? buildBusinessMessageSchema(responseFields) ?? emptyObject : parseJson(form.responseSchemaText, '标准返回结构') as Record<string, unknown>
    const requestExample = standardContractMode.value === 'FORM'
      ? buildBusinessMessageExample(requestFields) ?? {} : parseJson(form.requestExampleText, '请求样例', true)
    const responseExample = standardContractMode.value === 'FORM'
      ? buildBusinessMessageExample(responseFields) ?? {} : parseJson(form.responseExampleText, '返回样例', true)
    createService.mutate({ serviceCode: form.serviceCode.trim(), serviceName: form.serviceName.trim(), description: form.description.trim() || null,
      invocationMode: form.invocationMode, idempotencyClass: form.idempotencyClass, dataClassification: form.dataClassification,
      ownerCode: form.ownerCode.trim(), requestSchema, requestExample, responseSchema, responseExample })
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'JSON 格式错误' }
}
const addTarget = useMutation({
  mutationFn: () => accessServiceApi.provisionBusinessTarget(detailService.value!.id, { providerContractId: targetForm.providerContractId!,
    providerContractVersionId: targetForm.providerContractVersionId!, accessChannelId: targetForm.accessChannelId!,
    transportVersionId: targetForm.transportVersionId!,
    targetName: targetForm.targetName.trim(), ownerCode: targetForm.ownerCode.trim(),
    requestMappings: requestMappingRows.value, responseMappings: responseMappingRows.value }),
  onSuccess: async () => { targetDialog.value = false; await queryClient.invalidateQueries({ queryKey: ['product-services'] }); detailService.value = await accessServiceApi.get(detailService.value!.id); await loadReadiness(); ElMessage.success('第三方实现、字段映射和可执行版本已发布') },
  onError: error => { errorMessage.value = error instanceof Error ? error.message : '添加失败' }
})
function submitTarget(): void {
  errorMessage.value = ''
  if (!targetForm.providerContractId || !targetForm.providerContractVersionId || !targetForm.accessChannelId
    || !targetForm.transportVersionId || !targetForm.targetName.trim() || !targetForm.ownerCode.trim()) {
    errorMessage.value = '请完成接口、通道、报文结构版本、调用版本和实现名称配置'; return
  }
  if (!requestMappingRows.value.length || !responseMappingRows.value.length) {
    errorMessage.value = '请求和返回至少各配置一条字段映射'; return
  }
  try { runMappingPreview(); if (!errorMessage.value) addTarget.mutate() }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '字段映射配置错误' }
}
async function openRoute(): Promise<void> {
  if (!detailService.value) return
  errorMessage.value = ''; dryRunResult.value = null
  routeView.value = await serviceRouteApi.get(detailService.value.id)
  const latest = routeView.value.versions[0]
  routeSettings.healthFilterEnabled = latest?.healthFilterEnabled ?? true
  routeSettings.fallbackMode = latest?.fallbackMode ?? 'ONLY_NOT_SENT'
  const configured = new Map((latest?.targets ?? []).map(item => [item.bindingId, item]))
  routeRows.value = detailService.value.targets.map(target => {
    const value = configured.get(target.bindingId)
    return { bindingId: target.bindingId, enabled: value?.enabled ?? true, priority: value?.priority ?? 100,
      weight: value?.weight ?? 100, healthRequirement: value?.healthRequirement ?? 'HEALTHY_OR_UNKNOWN',
      manualStatus: value?.manualStatus ?? 'AVAILABLE', conditions: value?.conditions ?? {}, targetName: target.targetName,
      conditionText: JSON.stringify(value?.conditions ?? {}), health: 'UNKNOWN' }
  })
  dryRunForm.requestId = `dry-${Date.now()}`; dryRunForm.routingKey = dryRunForm.requestId; dryRunForm.attributesText = '{}'; routeDialog.value = true
}
const saveRoute = useMutation({ mutationFn: () => serviceRouteApi.saveDraft(detailService.value!.id, {
  healthFilterEnabled: routeSettings.healthFilterEnabled, fallbackMode: routeSettings.fallbackMode,
  targets: routeRows.value.map(row => ({ bindingId: row.bindingId, enabled: row.enabled, priority: row.priority,
    weight: row.weight, healthRequirement: row.healthRequirement, manualStatus: row.manualStatus,
    conditions: parseJson(row.conditionText, `${row.targetName}的匹配条件`) as Record<string, unknown> })) }),
  onSuccess: async () => { routeView.value = await serviceRouteApi.get(detailService.value!.id); ElMessage.success('已保存新的路由草稿版本') },
  onError: error => { errorMessage.value = error instanceof Error ? error.message : '路由保存失败' } })
const publishRoute = useMutation({ mutationFn: (versionId: number) => serviceRouteApi.publish(detailService.value!.id, versionId),
  onSuccess: async () => { routeView.value = await serviceRouteApi.get(detailService.value!.id); await loadReadiness(); ElMessage.success('路由版本已发布，可以返回详情查看就绪状态') },
  onError: error => { errorMessage.value = error instanceof Error ? error.message : '发布失败' } })
const runDryRoute = useMutation({ mutationFn: () => {
  const published = routeView.value?.versions.find(item => item.lifecycleStatus === 'PUBLISHED')
  if (!published) throw new Error('请先发布一个路由版本')
  return serviceRouteApi.dryRun(detailService.value!.id, { versionId: published.id, requestId: dryRunForm.requestId.trim(),
    routingKey: dryRunForm.routingKey.trim(), attributes: parseJson(dryRunForm.attributesText, '调用属性') as Record<string, unknown>,
    healthByBinding: Object.fromEntries(routeRows.value.map(row => [row.bindingId, row.health])) }) },
  onSuccess: result => { dryRunResult.value = result }, onError: error => { errorMessage.value = error instanceof Error ? error.message : 'Dry Run 失败' } })
</script>

<template>
  <section>
    <div class="product-page-heading"><div><span class="eyebrow">接入服务</span><h2>接入服务</h2><p>业务系统只需要记住服务编码；标准请求、标准返回和多个第三方实现都在一个服务中管理。</p></div><el-button type="primary" @click="openCreate">＋ 新增接入服务</el-button></div>
    <el-alert type="info" :closable="false" show-icon class="command-notice" title="创建服务时会自动建立并发布首个标准请求、返回契约；技术目录由平台内部维护。" />
    <el-alert v-if="servicesQuery.isError.value" type="error" :closable="false" show-icon class="command-notice" title="接入服务读取失败，请确认 Control Plane 已启动。" />
    <div v-loading="servicesQuery.isPending.value" class="surface product-table-card">
      <el-empty v-if="!servicesQuery.isPending.value && !services.length" description="还没有可供业务系统调用的服务"><el-button type="primary" @click="openCreate">＋ 定义第一个服务</el-button></el-empty>
      <el-table v-else :data="services"><el-table-column label="服务名称" prop="serviceName" min-width="180" /><el-table-column label="业务调用编码" min-width="210"><template #default="{ row }"><code>{{ row.serviceCode }}</code></template></el-table-column><el-table-column label="标准报文" width="150"><template #default="{ row }">{{ row.contracts.filter((item: any) => item.lifecycleStatus === 'PUBLISHED').length }}/2 已发布</template></el-table-column><el-table-column label="第三方实现" width="145"><template #default="{ row }">{{ row.targets.filter((item: any) => item.status === 'ACTIVE').length }}/{{ row.targets.length }} 已启用</template></el-table-column><el-table-column label="调用选择" min-width="155"><template #default="{ row }"><span v-if="row.targets.length > 1">优先级 + 权重路由</span><span v-else-if="row.targets.length === 1">固定使用该实现</span><span v-else class="muted-text">需要添加实现</span></template></el-table-column><el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">查看与管理</el-button></template></el-table-column></el-table>
    </div>

    <el-dialog v-model="createDialog" title="新增接入服务" width="940px" :close-on-click-modal="false">
      <el-alert type="success" :closable="false" show-icon title="这里定义的是业务系统看到的稳定接口，不需要选择第三方厂商。" />
      <el-form class="dialog-form" label-position="top"><div class="form-two-columns"><el-form-item label="业务调用编码（serviceCode）" required><el-input v-model="form.serviceCode" placeholder="例如：sms.send" /><small class="form-hint">业务系统调用平台时传递的固定编码。</small></el-form-item><el-form-item label="服务名称" required><el-input v-model="form.serviceName" placeholder="例如：发送短信" /></el-form-item><el-form-item label="调用方式"><el-select v-model="form.invocationMode" style="width:100%"><el-option label="同步等待结果" value="SYNC" /><el-option label="异步受理" value="ASYNC" /><el-option label="回调返回" value="CALLBACK" /></el-select></el-form-item><el-form-item label="重复请求处理"><el-select v-model="form.idempotencyClass" style="width:100%"><el-option label="暂未确定" value="UNKNOWN" /><el-option label="重复调用结果相同" value="IDEMPOTENT" /><el-option label="调用方传幂等键" value="IDEMPOTENT_WITH_KEY" /><el-option label="不可重复调用" value="NON_IDEMPOTENT" /></el-select></el-form-item><el-form-item label="数据敏感级别"><el-select v-model="form.dataClassification" style="width:100%"><el-option label="内部数据" value="INTERNAL" /><el-option label="公开数据" value="PUBLIC" /><el-option label="敏感数据" value="CONFIDENTIAL" /><el-option label="严格受限数据" value="RESTRICTED" /></el-select></el-form-item><el-form-item label="负责人" required><el-input v-model="form.ownerCode" /></el-form-item></div><el-form-item label="用途说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      <div class="standard-contract-heading"><div><strong>业务标准报文</strong><small>这是所有第三方实现共同遵守的稳定业务字段，第三方差异由后续字段映射吸收。</small></div><el-radio-group v-model="standardContractMode"><el-radio-button value="FORM">业务字段表单</el-radio-button><el-radio-button value="JSON">高级 JSON Schema</el-radio-button></el-radio-group></div>
      <template v-if="standardContractMode === 'FORM'"><BusinessContractFieldEditor v-model="standardRequestFields" title="业务标准请求" description="业务系统调用 serviceCode 时提交的字段" path-example="例如 $.mobile" /><BusinessContractFieldEditor v-model="standardResponseFields" title="业务标准返回" description="TPIP 向业务系统返回的稳定字段" path-example="例如 $.accepted" /></template>
      <div v-else class="schema-two-columns"><el-form-item label="业务标准请求结构（JSON Schema）" required><el-input v-model="form.requestSchemaText" type="textarea" :rows="10" class="schema-editor" /></el-form-item><el-form-item label="业务标准返回结构（JSON Schema）" required><el-input v-model="form.responseSchemaText" type="textarea" :rows="10" class="schema-editor" /></el-form-item><el-form-item label="请求样例 JSON"><el-input v-model="form.requestExampleText" type="textarea" :rows="5" class="schema-editor" /></el-form-item><el-form-item label="返回样例 JSON"><el-input v-model="form.responseExampleText" type="textarea" :rows="5" class="schema-editor" /></el-form-item></div><p v-if="errorMessage" class="command-validation">{{ errorMessage }}</p></el-form>
      <template #footer><el-button @click="createDialog = false">取消</el-button><el-button type="primary" :loading="createService.isPending.value" @click="submitCreate">创建服务和标准报文</el-button></template>
    </el-dialog>

    <el-drawer :model-value="detailService !== null" size="820px" title="接入服务详情" @close="detailService = null">
      <template v-if="detailService">
        <div class="product-detail-title"><span>业务调用编码</span><strong class="mono">{{ detailService.serviceCode }}</strong><p>{{ detailService.serviceName }} · {{ detailService.description || '暂无说明' }}</p></div>
        <div v-loading="readinessLoading" class="drawer-section readiness-panel">
          <div class="readiness-heading"><div><h3>接入就绪检查</h3><p>平台自动检查是否已经具备端到端验证条件。</p></div><el-button link type="primary" @click="loadReadiness">重新检查</el-button></div>
          <template v-if="readiness">
            <div class="readiness-summary" :class="`is-${readiness.status.toLowerCase()}`">
              <div><el-tag :type="readinessTag(readiness.status)" effect="dark">{{ readinessLabel(readiness.status) }}</el-tag><strong>{{ readiness.summary }}</strong></div>
              <router-link v-if="readiness.status !== 'BLOCKED'" :to="readiness.verificationPath"><el-button type="success">进入验证与发布</el-button></router-link>
            </div>
            <div class="readiness-check-list">
              <div v-for="item in readiness.checks" :key="item.code" class="readiness-check" :class="`is-${item.status.toLowerCase()}`">
                <span class="readiness-check-icon">{{ checkIcon(item) }}</span><div><strong>{{ item.name }}</strong><small>{{ item.detail }}</small></div>
                <router-link v-if="item.status !== 'PASS'" :to="item.actionPath">去补齐</router-link>
              </div>
            </div>
            <el-collapse v-if="readiness.targets.length" class="target-readiness">
              <el-collapse-item v-for="target in readiness.targets" :key="target.bindingId" :name="target.bindingId">
                <template #title><div class="target-readiness-title"><strong>{{ target.targetName }}</strong><span>{{ target.providerName }} · {{ target.interfaceName }}</span><el-tag size="small" :type="readinessTag(target.status)">{{ readinessLabel(target.status) }}</el-tag></div></template>
                <div v-for="item in target.checks" :key="item.code" class="readiness-check compact" :class="`is-${item.status.toLowerCase()}`"><span class="readiness-check-icon">{{ checkIcon(item) }}</span><div><strong>{{ item.name }}</strong><small>{{ item.detail }}</small></div><router-link v-if="item.status !== 'PASS'" :to="item.actionPath">去补齐</router-link></div>
              </el-collapse-item>
            </el-collapse>
          </template>
        </div>
        <div class="drawer-section"><h3>业务标准报文</h3><el-table :data="detailService.contracts" size="small"><el-table-column label="方向"><template #default="{ row }">{{ row.kind === 'REQUEST' ? '业务请求' : '业务返回' }}</template></el-table-column><el-table-column prop="contractName" label="名称" /><el-table-column label="版本"><template #default="{ row }"><el-tag type="success">{{ row.semanticVersion }} · 已发布</el-tag></template></el-table-column></el-table></div>
        <div class="drawer-section"><div class="asset-toolbar"><div><strong>第三方实现</strong><span>同一个业务调用编码可以绑定多家厂商接口。</span></div><el-button type="primary" @click="openTarget">＋ 添加第三方实现</el-button></div><el-empty v-if="!detailService.targets.length" description="尚未添加第三方实现" /><el-table v-else :data="detailService.targets" size="small"><el-table-column prop="targetName" label="实现名称" /><el-table-column prop="providerName" label="提供方" /><el-table-column prop="interfaceName" label="第三方接口" /><el-table-column label="状态" width="90"><template #default="{ row }">{{ row.status === 'ACTIVE' ? '已启用' : '已停用' }}</template></el-table-column></el-table></div>
        <div class="drawer-section"><div class="asset-toolbar"><div><strong>多目标调用选择</strong><span>先匹配条件和健康状态，再选最小优先级组，最后按权重选择。</span></div><el-button type="primary" :disabled="!detailService.targets.length" @click="openRoute">配置调用选择</el-button></div></div>
      </template>
    </el-drawer>

    <el-dialog v-model="targetDialog" class="target-provision-dialog" title="添加可执行的第三方实现" width="1180px" :close-on-click-modal="false">
      <el-alert type="success" :closable="false" show-icon title="选择通道和接口调用版本后，平台自动生成执行地址并继承通道认证；提交后自动发布双向映射和可执行配置。" />
      <el-form class="dialog-form" label-position="top">
        <h3>1. 选择第三方接口与通道</h3>
        <div class="form-two-columns">
          <el-form-item label="第三方提供方" required><el-select v-model="targetForm.providerId" filterable style="width:100%" placeholder="先选择提供方" @change="selectTargetProvider"><el-option v-for="item in providersQuery.data.value?.items ?? []" :key="item.id" :value="item.id" :label="item.providerName" /></el-select></el-form-item>
          <el-form-item label="产品/服务" required><el-select v-model="targetForm.providerProductId" filterable :disabled="!targetForm.providerId" style="width:100%" placeholder="例如：短信服务" @change="selectTargetProduct"><el-option v-for="item in eligibleProducts" :key="item.id" :value="item.id" :label="item.productName" /></el-select></el-form-item>
          <el-form-item label="第三方接口" required><el-select v-model="targetForm.providerContractId" filterable :disabled="!targetForm.providerProductId" style="width:100%" placeholder="只显示当前产品服务的接口" @change="selectTargetInterface"><el-option v-for="item in eligibleInterfaces" :key="item.id" :value="item.id" :label="item.contractName" /></el-select></el-form-item>
          <el-form-item label="报文结构版本" required><el-select v-model="targetForm.providerContractVersionId" style="width:100%" placeholder="选择已发布版本" @change="selectProtocolVersion"><el-option v-for="item in providerVersions" :key="item.id" :value="item.id" :label="`${item.semanticVersion} · 内部修订 ${item.versionNo}`" /></el-select><small class="form-hint">选择后自动读取第三方请求与返回字段。</small></el-form-item>
          <el-form-item label="接入通道" required><el-select v-model="targetForm.accessChannelId" style="width:100%" placeholder="选择已关联该接口的通道"><el-option v-for="item in eligibleChannels" :key="item.id" :value="item.id" :label="`${item.channelName} · ${item.baseUrl}`" /></el-select><small class="form-hint">自动继承该通道已发布的账号认证、公共参数和接口覆盖。</small></el-form-item>
          <el-form-item label="接口调用版本" required><el-select v-model="targetForm.transportVersionId" style="width:100%" placeholder="选择已发布调用版本"><el-option v-for="item in transportVersions" :key="item.id" :value="item.id" :label="`${typeof item.semanticVersion === 'string' ? item.semanticVersion : `${item.semanticVersion.major}.${item.semanticVersion.minor}.${item.semanticVersion.patch}`} · ${item.httpMethod} ${item.resourcePath}`" /></el-select><small class="form-hint">最终地址由通道 Base URL 和这里的接口 Path 自动组合。</small></el-form-item>
        </div>
        <el-alert v-if="targetForm.providerContractId && !eligibleChannels.length" type="warning" :closable="false" title="该接口还没有可用通道，请先在“接入通道”中关联接口并配置公共参数。" />
        <el-alert v-else-if="targetForm.providerContractId && !transportVersions.length" type="warning" :closable="false" title="该接口还没有已发布调用版本，请先在“第三方接入”中配置并发布 Method 与 Path。" />
        <h3>2. 配置业务字段与第三方字段</h3>
        <el-alert v-if="targetForm.providerContractVersionId && (!businessRequestFields.length || !providerRequestFields.length || !businessResponseFields.length || !providerResponseFields.length)" type="warning" :closable="false" title="业务标准报文或第三方报文缺少可选择字段；请先补充 JSON Schema properties。" />
        <div class="mapping-direction-card"><div><strong>请求转换</strong><small>业务系统请求 → 第三方请求</small></div><BusinessFieldMappingEditor v-model="requestMappingRows" :source-fields="businessRequestFields" :target-fields="providerRequestFields" source-label="业务请求字段" target-label="第三方请求字段" /></div>
        <div class="mapping-direction-card"><div><strong>返回转换</strong><small>第三方返回 → 业务标准返回</small></div><BusinessFieldMappingEditor v-model="responseMappingRows" :source-fields="providerResponseFields" :target-fields="businessResponseFields" source-label="第三方返回字段" target-label="业务返回字段" /></div>
        <div class="mapping-preview-panel"><div class="mapping-preview-heading"><div><strong>映射样例预览</strong><small>使用契约中保存的样例检查字段路径、必填和类型转换。</small></div><el-button type="primary" @click="runMappingPreview">执行双向预览</el-button></div><div class="mapping-preview-grid"><label><span>业务请求样例</span><el-input v-model="requestSampleText" type="textarea" :rows="7" class="schema-editor" /></label><label><span>转换后的第三方请求</span><pre>{{ pretty(requestPreview) }}</pre></label><label><span>第三方返回样例</span><el-input v-model="providerResponseSampleText" type="textarea" :rows="7" class="schema-editor" /></label><label><span>转换后的业务返回</span><pre>{{ pretty(responsePreview) }}</pre></label></div></div>
        <h3>3. 实现信息</h3>
        <div class="form-two-columns"><el-form-item label="实现名称" required><el-input v-model="targetForm.targetName" placeholder="例如：阿里云短信实现" /></el-form-item><el-form-item label="负责人"><el-input v-model="targetForm.ownerCode" /></el-form-item></div>
        <p v-if="errorMessage" class="command-validation">{{ errorMessage }}</p>
      </el-form>
      <template #footer><el-button @click="targetDialog = false">取消</el-button><el-button type="primary" :loading="addTarget.isPending.value" @click="submitTarget">校验、生成并发布实现</el-button></template>
    </el-dialog>

    <el-dialog v-model="routeDialog" title="多目标路由配置" width="1120px" :close-on-click-modal="false"><el-alert type="warning" :closable="false" show-icon title="权重只在同一优先级的可用目标中生效；手工摘除和不健康目标不会参与选择。超时结果未知时禁止跨厂商重试。" /><div class="route-settings"><el-switch v-model="routeSettings.healthFilterEnabled" active-text="过滤不健康目标" /><el-select v-model="routeSettings.fallbackMode"><el-option label="仅确认未发送时故障切换" value="ONLY_NOT_SENT" /><el-option label="禁止自动故障切换" value="DISABLED" /></el-select></div>
      <el-table :data="routeRows" size="small"><el-table-column prop="targetName" label="第三方实现" min-width="170" /><el-table-column label="启用" width="70"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column><el-table-column label="优先级" width="110"><template #default="{ row }"><el-input-number v-model="row.priority" :min="0" :max="10000" controls-position="right" /></template></el-table-column><el-table-column label="权重" width="110"><template #default="{ row }"><el-input-number v-model="row.weight" :min="1" :max="10000" controls-position="right" /></template></el-table-column><el-table-column label="健康要求" width="160"><template #default="{ row }"><el-select v-model="row.healthRequirement"><el-option label="健康或未知" value="HEALTHY_OR_UNKNOWN" /><el-option label="必须健康" value="HEALTHY_ONLY" /></el-select></template></el-table-column><el-table-column label="人工状态" width="130"><template #default="{ row }"><el-select v-model="row.manualStatus"><el-option label="可用" value="AVAILABLE" /><el-option label="已摘除" value="DRAINED" /></el-select></template></el-table-column><el-table-column label="匹配条件 JSON" min-width="190"><template #default="{ row }"><el-input v-model="row.conditionText" class="mono" placeholder='{"tenant":"vip"}' /></template></el-table-column></el-table>
      <div class="route-version-bar"><strong>路由版本</strong><el-tag v-for="version in routeView?.versions ?? []" :key="version.id" :type="version.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">v{{ version.versionNo }} · {{ version.lifecycleStatus }}</el-tag><el-button v-if="routeView?.versions[0]?.lifecycleStatus === 'DRAFT'" type="success" :loading="publishRoute.isPending.value" @click="publishRoute.mutate(routeView!.versions[0]!.id)">发布最新草稿</el-button></div>
      <div class="drawer-section"><h3>路由 Dry Run</h3><div class="route-dry-grid"><el-input v-model="dryRunForm.requestId" placeholder="requestId" /><el-input v-model="dryRunForm.routingKey" placeholder="稳定路由键，如 memberId" /><el-input v-model="dryRunForm.attributesText" class="mono" placeholder='调用属性 JSON，如 {"tenant":"vip"}' /><el-button type="primary" :loading="runDryRoute.isPending.value" @click="runDryRoute.mutate()">执行 Dry Run</el-button></div><div class="route-health-grid"><div v-for="row in routeRows" :key="row.bindingId"><span>{{ row.targetName }}</span><el-select v-model="row.health"><el-option label="未知" value="UNKNOWN" /><el-option label="健康" value="HEALTHY" /><el-option label="不健康" value="UNHEALTHY" /></el-select></div></div><el-result v-if="dryRunResult" :icon="dryRunResult.outcome === 'SELECTED' ? 'success' : 'warning'" :title="dryRunResult.outcome === 'SELECTED' ? `选择目标 #${dryRunResult.selectedBindingId}` : '没有可用目标'" :sub-title="`决策审计 #${dryRunResult.decisionId}`" /></div><p v-if="errorMessage" class="command-validation">{{ errorMessage }}</p>
      <template #footer><el-button @click="routeDialog = false">关闭</el-button><el-button type="primary" :loading="saveRoute.isPending.value" @click="saveRoute.mutate()">保存新草稿版本</el-button></template></el-dialog>
  </section>
</template>

<style scoped>
.readiness-panel { min-height: 132px; }
.readiness-heading,.readiness-summary,.target-readiness-title { display:flex; align-items:center; justify-content:space-between; gap:16px; }
.readiness-heading h3 { margin:0; }
.readiness-heading p { margin:5px 0 0; color:var(--el-text-color-secondary); font-size:13px; }
.readiness-summary { margin:14px 0; padding:14px 16px; border-radius:8px; background:#f1f8f5; border:1px solid #bfe3d2; }
.readiness-summary.is-blocked { background:#fff4f3; border-color:#f3c6c2; }
.readiness-summary.is-ready_with_warnings { background:#fff8eb; border-color:#efdaa9; }
.readiness-summary > div { display:flex; align-items:center; gap:10px; }
.readiness-check-list { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
.readiness-check { display:grid; grid-template-columns:24px 1fr auto; gap:8px; align-items:center; padding:10px; border:1px solid var(--el-border-color-lighter); border-radius:6px; }
.readiness-check.compact { margin:6px 0; }
.readiness-check-icon { display:grid; place-items:center; width:22px; height:22px; border-radius:50%; color:#fff; background:#35a777; font-weight:700; }
.readiness-check.is-warn .readiness-check-icon { background:#d99a2b; }
.readiness-check.is-block .readiness-check-icon { background:#d9534f; }
.readiness-check div { min-width:0; }
.readiness-check strong,.readiness-check small { display:block; }
.readiness-check small { margin-top:3px; color:var(--el-text-color-secondary); line-height:1.4; }
.readiness-check a { color:var(--el-color-primary); white-space:nowrap; font-size:13px; }
.target-readiness { margin-top:12px; }
.target-readiness-title { width:100%; padding-right:10px; justify-content:flex-start; }
.target-readiness-title span { flex:1; color:var(--el-text-color-secondary); font-size:13px; }
@media (max-width: 760px) { .readiness-check-list { grid-template-columns:1fr; } .readiness-summary { align-items:flex-start; flex-direction:column; } }
</style>
