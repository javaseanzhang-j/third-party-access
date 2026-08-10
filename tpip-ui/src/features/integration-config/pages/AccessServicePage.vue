<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { accessServiceApi, type CreateAccessServiceInput, type ProductAccessService } from '../api/accessServiceApi'
import { integrationAssetApi } from '../api/integrationAssetApi'
import { serviceRouteApi, type DryRunResult, type RoutePolicyView, type RouteTargetConfig } from '../api/serviceRouteApi'
import { accessChannelApi } from '../api/accessChannelApi'
import { providerContractVersionApi, type ProviderContractVersionAsset } from '../api/providerContractVersionApi'
import { parseFieldMappingLines } from '../model/productModel'

const queryClient = useQueryClient()
const servicesQuery = useQuery({ queryKey: ['product-services'], queryFn: ({ signal }) => accessServiceApi.list(signal) })
const interfacesQuery = useQuery({ queryKey: ['provider-contracts'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const providersQuery = useQuery({ queryKey: ['providers'], queryFn: ({ signal }) => integrationAssetApi.providers('', signal) })
const endpointsQuery = useQuery({ queryKey: ['product-endpoints'], queryFn: ({ signal }) => integrationAssetApi.endpoints(undefined, signal) })
const credentialsQuery = useQuery({ queryKey: ['product-credentials'], queryFn: ({ signal }) => integrationAssetApi.credentials(undefined, signal) })
const channelsQuery = useQuery({ queryKey: ['product-channels'], queryFn: ({ signal }) => accessChannelApi.channels(undefined, signal) })
const services = computed(() => servicesQuery.data.value ?? [])
const createDialog = ref(false)
const detailService = ref<ProductAccessService | null>(null)
const targetDialog = ref(false)
const routeDialog = ref(false)
const routeView = ref<RoutePolicyView | null>(null)
const routeRows = ref<Array<RouteTargetConfig & { targetName: string; conditionText: string; health: 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN' }>>([])
const routeSettings = reactive({ healthFilterEnabled: true, fallbackMode: 'ONLY_NOT_SENT' as 'ONLY_NOT_SENT' | 'DISABLED' })
const dryRunForm = reactive({ requestId: '', routingKey: '', attributesText: '{}' })
const dryRunResult = ref<DryRunResult | null>(null)
const errorMessage = ref('')
const defaultSchema = JSON.stringify({ type: 'object', additionalProperties: false, properties: {} }, null, 2)
const form = reactive({ serviceCode: '', serviceName: '', description: '', invocationMode: 'SYNC' as 'SYNC' | 'ASYNC' | 'CALLBACK',
  idempotencyClass: 'UNKNOWN' as 'UNKNOWN' | 'IDEMPOTENT' | 'IDEMPOTENT_WITH_KEY' | 'NON_IDEMPOTENT',
  dataClassification: 'INTERNAL' as 'INTERNAL' | 'PUBLIC' | 'CONFIDENTIAL' | 'RESTRICTED', ownerCode: 'local',
  requestSchemaText: defaultSchema, requestExampleText: '{}', responseSchemaText: defaultSchema, responseExampleText: '{}' })
const targetForm = reactive({ providerContractId: undefined as number | undefined, providerContractVersionId: undefined as number | undefined,
  accessChannelId: undefined as number | undefined, endpointId: undefined as number | undefined, targetName: '', ownerCode: 'local',
  requestMappingsText: '$.mobile -> $.phone STRING required', responseMappingsText: '$.success -> $.success BOOLEAN required',
  authenticationMode: 'CHANNEL_PARAMETERS' as 'CHANNEL_PARAMETERS' | 'API_KEY_POLICY' | 'HMAC_SHA256_POLICY', credentialRefId: undefined as number | undefined,
  headerName: 'X-API-Key', prefix: '', sourceTemplate: '${context.operationCode}:${context.attributes.signature_timestamp}',
  encoding: 'HEX_LOWER' as 'HEX_LOWER' | 'BASE64' })
const channelInterfaces = ref<Record<number, number[]>>({})
const providerVersions = ref<ProviderContractVersionAsset[]>([])
const selectedInterface = computed(() => interfacesQuery.data.value?.items.find(item => item.id === targetForm.providerContractId) ?? null)
const eligibleChannels = computed(() => (channelsQuery.data.value ?? []).filter(item => item.status === 'ACTIVE'
  && item.providerId === selectedInterface.value?.providerId
  && (channelInterfaces.value[item.id] ?? []).includes(targetForm.providerContractId ?? 0)))
const selectedChannel = computed(() => eligibleChannels.value.find(item => item.id === targetForm.accessChannelId) ?? null)
const eligibleEndpoints = computed(() => (endpointsQuery.data.value?.items ?? []).filter(item => item.lifecycleStatus === 'PUBLISHED'
  && item.providerContractId === targetForm.providerContractId && item.baseUrl === selectedChannel.value?.baseUrl))
const eligibleCredentials = computed(() => (credentialsQuery.data.value?.items ?? []).filter(item => item.status === 'ACTIVE'
  && item.providerId === selectedInterface.value?.providerId))

function parseJson(text: string, label: string, optional = false): unknown {
  if (optional && !text.trim()) return null
  try { return JSON.parse(text) } catch { throw new Error(`${label}不是合法 JSON`) }
}
function providerName(providerId: number): string {
  return providersQuery.data.value?.items.find(item => item.id === providerId)?.providerName ?? `Provider #${providerId}`
}
function openCreate(): void { errorMessage.value = ''; createDialog.value = true }
function openDetail(service: unknown): void { detailService.value = service as ProductAccessService }
async function openTarget(): Promise<void> {
  errorMessage.value = ''; targetForm.providerContractId = undefined; targetForm.providerContractVersionId = undefined
  targetForm.accessChannelId = undefined; targetForm.endpointId = undefined; targetForm.targetName = ''; providerVersions.value = []
  targetDialog.value = true
  if (!channelsQuery.data.value) await channelsQuery.refetch()
  if (!endpointsQuery.data.value) await endpointsQuery.refetch()
  const channels = channelsQuery.data.value ?? []
  channelInterfaces.value = Object.fromEntries(await Promise.all(channels.map(async item => [item.id, await accessChannelApi.interfaceIds(item.id)])))
}
async function selectTargetInterface(contractId: number): Promise<void> {
  targetForm.providerContractId = contractId; targetForm.providerContractVersionId = undefined
  providerVersions.value = (await providerContractVersionApi.versions(contractId)).filter(item => item.lifecycleStatus === 'PUBLISHED')
  targetForm.providerContractVersionId = providerVersions.value[0]?.id
  targetForm.accessChannelId = eligibleChannels.value[0]?.id
  targetForm.endpointId = eligibleEndpoints.value[0]?.id
  const item = selectedInterface.value
  if (item && !targetForm.targetName) targetForm.targetName = `${providerName(item.providerId)}${item.contractName}实现`
}
function selectChannel(channelId: number): void { targetForm.accessChannelId = channelId; targetForm.endpointId = eligibleEndpoints.value[0]?.id }
const createService = useMutation({
  mutationFn: (input: CreateAccessServiceInput) => accessServiceApi.create(input),
  onSuccess: async created => { createDialog.value = false; await queryClient.invalidateQueries({ queryKey: ['product-services'] }); openDetail(created); ElMessage.success('接入服务与标准契约已创建') },
  onError: error => { errorMessage.value = error instanceof Error ? error.message : '创建失败' }
})
function submitCreate(): void {
  errorMessage.value = ''
  if (!form.serviceCode.trim() || !form.serviceName.trim() || !form.ownerCode.trim()) { errorMessage.value = '请填写服务编码、名称和负责人'; return }
  try {
    createService.mutate({ serviceCode: form.serviceCode.trim(), serviceName: form.serviceName.trim(), description: form.description.trim() || null,
      invocationMode: form.invocationMode, idempotencyClass: form.idempotencyClass, dataClassification: form.dataClassification,
      ownerCode: form.ownerCode.trim(), requestSchema: parseJson(form.requestSchemaText, '标准请求结构') as Record<string, unknown>,
      requestExample: parseJson(form.requestExampleText, '请求样例', true), responseSchema: parseJson(form.responseSchemaText, '标准返回结构') as Record<string, unknown>,
      responseExample: parseJson(form.responseExampleText, '返回样例', true) })
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'JSON 格式错误' }
}
const addTarget = useMutation({
  mutationFn: () => accessServiceApi.provisionTarget(detailService.value!.id, { providerContractId: targetForm.providerContractId!,
    providerContractVersionId: targetForm.providerContractVersionId!, accessChannelId: targetForm.accessChannelId!, endpointId: targetForm.endpointId!,
    targetName: targetForm.targetName.trim(), ownerCode: targetForm.ownerCode.trim(),
    requestMappings: parseFieldMappingLines(targetForm.requestMappingsText), responseMappings: parseFieldMappingLines(targetForm.responseMappingsText),
    authentication: { mode: targetForm.authenticationMode, credentialRefId: targetForm.authenticationMode === 'CHANNEL_PARAMETERS' ? null : targetForm.credentialRefId ?? null,
      headerName: targetForm.authenticationMode === 'CHANNEL_PARAMETERS' ? null : targetForm.headerName.trim(),
      prefix: targetForm.authenticationMode === 'CHANNEL_PARAMETERS' ? null : targetForm.prefix,
      sourceTemplate: targetForm.authenticationMode === 'HMAC_SHA256_POLICY' ? targetForm.sourceTemplate : null,
      encoding: targetForm.authenticationMode === 'HMAC_SHA256_POLICY' ? targetForm.encoding : null } }),
  onSuccess: async () => { targetDialog.value = false; await queryClient.invalidateQueries({ queryKey: ['product-services'] }); detailService.value = await accessServiceApi.get(detailService.value!.id); ElMessage.success('第三方实现、字段映射和可执行版本已发布') },
  onError: error => { errorMessage.value = error instanceof Error ? error.message : '添加失败' }
})
function submitTarget(): void {
  errorMessage.value = ''
  if (!targetForm.providerContractId || !targetForm.providerContractVersionId || !targetForm.accessChannelId || !targetForm.endpointId
    || !targetForm.targetName.trim() || !targetForm.ownerCode.trim()) { errorMessage.value = '请完成接口、通道、协议版本、执行地址和实现名称配置'; return }
  if (targetForm.authenticationMode !== 'CHANNEL_PARAMETERS' && !targetForm.credentialRefId) { errorMessage.value = '认证规则必须选择凭据'; return }
  if (targetForm.authenticationMode === 'HMAC_SHA256_POLICY' && !targetForm.sourceTemplate.trim()) { errorMessage.value = 'HMAC 签名原文不能为空'; return }
  try { parseFieldMappingLines(targetForm.requestMappingsText); parseFieldMappingLines(targetForm.responseMappingsText); addTarget.mutate() }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '字段映射格式错误' }
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
  onSuccess: async () => { routeView.value = await serviceRouteApi.get(detailService.value!.id); ElMessage.success('路由版本已发布，等待后续编译进 Bundle') },
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
    <div class="product-page-heading"><div><span class="eyebrow">ACCESS SERVICES</span><h2>接入服务</h2><p>业务系统只需要记住 serviceCode；标准请求、标准返回和多个第三方实现都在一个服务中管理。</p></div><el-button type="primary" @click="openCreate">＋ 新增接入服务</el-button></div>
    <el-alert type="info" :closable="false" show-icon class="command-notice" title="创建服务时会自动建立并发布 1.0.0 标准请求/返回契约，Domain 与 Capability 由平台内部维护。" />
    <el-alert v-if="servicesQuery.isError.value" type="error" :closable="false" show-icon class="command-notice" title="接入服务读取失败，请确认 Control Plane 已启动。" />
    <div v-loading="servicesQuery.isPending.value" class="surface product-table-card">
      <el-empty v-if="!servicesQuery.isPending.value && !services.length" description="还没有可供业务系统调用的服务"><el-button type="primary" @click="openCreate">＋ 定义第一个服务</el-button></el-empty>
      <el-table v-else :data="services"><el-table-column label="服务名称" prop="serviceName" min-width="180" /><el-table-column label="业务调用编码" min-width="210"><template #default="{ row }"><code>{{ row.serviceCode }}</code></template></el-table-column><el-table-column label="标准报文" width="150"><template #default="{ row }">{{ row.contracts.filter((item: any) => item.lifecycleStatus === 'PUBLISHED').length }}/2 已发布</template></el-table-column><el-table-column label="第三方实现" width="145"><template #default="{ row }">{{ row.targets.filter((item: any) => item.status === 'ACTIVE').length }}/{{ row.targets.length }} 已启用</template></el-table-column><el-table-column label="调用选择" min-width="155"><template #default="{ row }"><span v-if="row.targets.length > 1">优先级 + 权重路由</span><span v-else-if="row.targets.length === 1">固定使用该实现</span><span v-else class="muted-text">需要添加实现</span></template></el-table-column><el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">查看与管理</el-button></template></el-table-column></el-table>
    </div>

    <el-dialog v-model="createDialog" title="新增接入服务" width="940px" :close-on-click-modal="false">
      <el-alert type="success" :closable="false" show-icon title="这里定义的是业务系统看到的稳定接口，不需要选择第三方厂商。" />
      <el-form class="dialog-form" label-position="top"><div class="form-two-columns"><el-form-item label="业务调用编码（serviceCode）" required><el-input v-model="form.serviceCode" placeholder="例如：sms.send" /><small class="form-hint">业务系统调用平台时传递的固定编码。</small></el-form-item><el-form-item label="服务名称" required><el-input v-model="form.serviceName" placeholder="例如：发送短信" /></el-form-item><el-form-item label="调用方式"><el-select v-model="form.invocationMode" style="width:100%"><el-option label="同步等待结果" value="SYNC" /><el-option label="异步受理" value="ASYNC" /><el-option label="回调返回" value="CALLBACK" /></el-select></el-form-item><el-form-item label="重复请求处理"><el-select v-model="form.idempotencyClass" style="width:100%"><el-option label="暂未确定" value="UNKNOWN" /><el-option label="重复调用结果相同" value="IDEMPOTENT" /><el-option label="调用方传幂等键" value="IDEMPOTENT_WITH_KEY" /><el-option label="不可重复调用" value="NON_IDEMPOTENT" /></el-select></el-form-item><el-form-item label="数据敏感级别"><el-select v-model="form.dataClassification" style="width:100%"><el-option label="内部数据" value="INTERNAL" /><el-option label="公开数据" value="PUBLIC" /><el-option label="敏感数据" value="CONFIDENTIAL" /><el-option label="严格受限数据" value="RESTRICTED" /></el-select></el-form-item><el-form-item label="负责人" required><el-input v-model="form.ownerCode" /></el-form-item></div><el-form-item label="用途说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      <div class="schema-two-columns"><el-form-item label="业务标准请求结构（JSON Schema）" required><el-input v-model="form.requestSchemaText" type="textarea" :rows="10" class="schema-editor" /></el-form-item><el-form-item label="业务标准返回结构（JSON Schema）" required><el-input v-model="form.responseSchemaText" type="textarea" :rows="10" class="schema-editor" /></el-form-item><el-form-item label="请求样例 JSON"><el-input v-model="form.requestExampleText" type="textarea" :rows="5" class="schema-editor" /></el-form-item><el-form-item label="返回样例 JSON"><el-input v-model="form.responseExampleText" type="textarea" :rows="5" class="schema-editor" /></el-form-item></div><p v-if="errorMessage" class="command-validation">{{ errorMessage }}</p></el-form>
      <template #footer><el-button @click="createDialog = false">取消</el-button><el-button type="primary" :loading="createService.isPending.value" @click="submitCreate">创建服务和标准报文</el-button></template>
    </el-dialog>

    <el-drawer :model-value="detailService !== null" size="780px" title="接入服务详情" @close="detailService = null"><template v-if="detailService"><div class="product-detail-title"><span>业务调用编码</span><strong class="mono">{{ detailService.serviceCode }}</strong><p>{{ detailService.serviceName }} · {{ detailService.description || '暂无说明' }}</p></div><div class="drawer-section"><h3>业务标准报文</h3><el-table :data="detailService.contracts" size="small"><el-table-column label="方向"><template #default="{ row }">{{ row.kind === 'REQUEST' ? '业务请求' : '业务返回' }}</template></el-table-column><el-table-column prop="contractName" label="名称" /><el-table-column label="版本"><template #default="{ row }"><el-tag type="success">{{ row.semanticVersion }} · {{ row.lifecycleStatus }}</el-tag></template></el-table-column></el-table></div><div class="drawer-section"><div class="asset-toolbar"><div><strong>第三方实现</strong><span>同一个 serviceCode 可以绑定多家厂商接口。</span></div><el-button type="primary" @click="openTarget">＋ 添加第三方实现</el-button></div><el-empty v-if="!detailService.targets.length" description="尚未添加第三方实现" /><el-table v-else :data="detailService.targets" size="small"><el-table-column prop="targetName" label="实现名称" /><el-table-column prop="providerName" label="提供方" /><el-table-column prop="interfaceName" label="第三方接口" /><el-table-column prop="status" label="状态" width="90" /></el-table></div><div class="drawer-section"><div class="asset-toolbar"><div><strong>多目标路由</strong><span>先匹配条件和健康状态，再选最小优先级组，最后按权重选择。</span></div><el-button type="primary" :disabled="!detailService.targets.length" @click="openRoute">配置路由</el-button></div></div></template></el-drawer>

    <el-dialog v-model="targetDialog" class="target-provision-dialog" title="添加可执行的第三方实现" width="980px" :close-on-click-modal="false">
      <el-alert type="success" :closable="false" show-icon title="完成一次提交后，平台会自动创建并发布请求映射、返回映射和 BindingVersion，不需要再进入高级管理逐项拼装。" />
      <el-form class="dialog-form" label-position="top">
        <h3>1. 选择第三方接口与通道</h3>
        <div class="form-two-columns">
          <el-form-item label="第三方接口" required><el-select v-model="targetForm.providerContractId" filterable style="width:100%" placeholder="提供方 / 接口名称" @change="selectTargetInterface"><el-option v-for="item in interfacesQuery.data.value?.items ?? []" :key="item.id" :value="item.id" :label="`${providerName(item.providerId)} / ${item.contractName}`" /></el-select></el-form-item>
          <el-form-item label="已发布报文协议" required><el-select v-model="targetForm.providerContractVersionId" style="width:100%" placeholder="选择协议版本"><el-option v-for="item in providerVersions" :key="item.id" :value="item.id" :label="`${item.semanticVersion} · v${item.versionNo}`" /></el-select></el-form-item>
          <el-form-item label="接入通道" required><el-select v-model="targetForm.accessChannelId" style="width:100%" placeholder="选择已关联该接口的通道" @change="selectChannel"><el-option v-for="item in eligibleChannels" :key="item.id" :value="item.id" :label="`${item.channelName} · ${item.baseUrl}`" /></el-select><small class="form-hint">通道中的 appKey、appSecret 等公共参数会自动继承，接口参数可以覆盖。</small></el-form-item>
          <el-form-item label="实际调用地址" required><el-select v-model="targetForm.endpointId" style="width:100%" placeholder="选择与通道 URL 一致的地址"><el-option v-for="item in eligibleEndpoints" :key="item.id" :value="item.id" :label="`${item.httpMethod} ${item.baseUrl}${item.resourcePath}`" /></el-select></el-form-item>
        </div>
        <el-alert v-if="targetForm.providerContractId && !eligibleChannels.length" type="warning" :closable="false" title="该接口还没有可用通道，请先在“接入通道”中关联接口并配置公共参数。" />
        <h3>2. 配置业务字段与第三方字段</h3>
        <div class="schema-two-columns">
          <el-form-item label="请求字段映射" required><el-input v-model="targetForm.requestMappingsText" type="textarea" :rows="7" class="schema-editor" /><small class="form-hint">每行一条：$.业务字段 -&gt; $.第三方字段 [STRING/NUMBER/BOOLEAN/OBJECT/ARRAY] [required]</small></el-form-item>
          <el-form-item label="返回字段映射" required><el-input v-model="targetForm.responseMappingsText" type="textarea" :rows="7" class="schema-editor" /><small class="form-hint">方向相反：$.第三方字段 -&gt; $.业务字段。</small></el-form-item>
        </div>
        <h3>3. 认证规则</h3>
        <el-radio-group v-model="targetForm.authenticationMode"><el-radio-button value="CHANNEL_PARAMETERS">使用通道公共参数</el-radio-button><el-radio-button value="API_KEY_POLICY">API Key</el-radio-button><el-radio-button value="HMAC_SHA256_POLICY">HMAC-SHA256 签名</el-radio-button></el-radio-group>
        <div v-if="targetForm.authenticationMode !== 'CHANNEL_PARAMETERS'" class="form-two-columns" style="margin-top:14px">
          <el-form-item label="Secret 凭据" required><el-select v-model="targetForm.credentialRefId" style="width:100%"><el-option v-for="item in eligibleCredentials" :key="item.id" :value="item.id" :label="`${item.credentialCode} · ${item.environmentCode}`" /></el-select></el-form-item>
          <el-form-item label="Header 名称"><el-input v-model="targetForm.headerName" :placeholder="targetForm.authenticationMode === 'API_KEY_POLICY' ? 'X-API-Key' : 'X-Signature'" /></el-form-item>
          <el-form-item label="值前缀"><el-input v-model="targetForm.prefix" placeholder="例如 Bearer（可留空）" /></el-form-item>
          <el-form-item v-if="targetForm.authenticationMode === 'HMAC_SHA256_POLICY'" label="签名编码"><el-select v-model="targetForm.encoding" style="width:100%"><el-option label="小写十六进制" value="HEX_LOWER" /><el-option label="Base64" value="BASE64" /></el-select></el-form-item>
          <el-form-item v-if="targetForm.authenticationMode === 'HMAC_SHA256_POLICY'" label="签名原文模板" required style="grid-column:1 / -1"><el-input v-model="targetForm.sourceTemplate" type="textarea" :rows="3" class="schema-editor" /><small class="form-hint">可引用 context、provider 等受控变量；示例依赖通道中 code=timestamp、位置=SIGNATURE 的公共时间参数。</small></el-form-item>
        </div>
        <h3>4. 实现信息</h3>
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
