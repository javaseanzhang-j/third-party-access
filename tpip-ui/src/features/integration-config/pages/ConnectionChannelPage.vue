<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { accessChannelApi, type AccessChannelAsset, type AccessParameterAsset, type AccessPolicyVersionAsset,
  type ParameterDataType, type ParameterLocation, type ParameterSource } from '../api/accessChannelApi'
import { integrationAssetApi } from '../api/integrationAssetApi'

const providers = useQuery({ queryKey: ['product-providers'], queryFn: ({ signal }) => integrationAssetApi.providers('', signal) })
const credentials = useQuery({ queryKey: ['product-credentials'], queryFn: ({ signal }) => integrationAssetApi.credentials(undefined, signal) })
const contracts = useQuery({ queryKey: ['product-contracts'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const channels = useQuery({ queryKey: ['access-channels'], queryFn: ({ signal }) => accessChannelApi.channels(undefined, signal) })
const products = useQuery({ queryKey: ['provider-products'], queryFn: ({ signal }) => accessChannelApi.products(undefined, signal) })
const selectedProviderId = ref<number | null>(null)
const selectedProductId = ref<number | null>(null)
const channelKeyword = ref('')
const selectedChannel = ref<AccessChannelAsset | null>(null)
const parameters = ref<AccessParameterAsset[]>([])
const attachedInterfaceIds = ref<number[]>([])
const detailLoading = ref(false)
const channelDialog = ref(false)
const productDialog = ref(false)
const editingChannel = ref<AccessChannelAsset | null>(null)
const parameterDialog = ref(false)
const attachDialog = ref(false)
const saving = ref(false)
const policyDialog = ref(false)
const policyVersions = ref<AccessPolicyVersionAsset[]>([])
const policyScope = ref<'CHANNEL' | 'INTERFACE'>('CHANNEL')
const policyContractId = ref<number | null>(null)
const channelForm = reactive({ providerId: null as number | null, providerProductId: null as number | null, channelCode: '', channelName: '',
  baseUrl: 'https://', credentialRefId: null as number | null, description: '' })
const productForm = reactive({ productCode: '', productName: '', description: '' })
const parameterForm = reactive({ scope: 'CHANNEL' as 'CHANNEL' | 'INTERFACE', providerContractId: null as number | null,
  parameterCode: '', parameterName: '', location: 'HEADER' as ParameterLocation,
  source: 'FIXED' as ParameterSource, dataType: 'STRING' as ParameterDataType, value: '',
  sourceSelector: '', secretRefId: null as number | null, overrideMode: 'REPLACE' as 'REPLACE' | 'DISABLE',
  required: false, sensitive: false, callerOverridable: false, description: '' })
const attachContractId = ref<number | null>(null)
const policyForm = reactive({ policyName: '通道请求认证规则', injectRequestId: true,
  authenticationMode: 'NONE' as 'NONE' | 'API_KEY' | 'HMAC_SHA256', credentialRefId: null as number | null,
  headerName: 'X-API-Key', prefix: '', sourceTemplate: '${context.operationCode}:${context.attributes.signature_timestamp}',
  encoding: 'HEX_LOWER' as 'HEX_LOWER' | 'BASE64', disabledStepIdsText: '' })
const providerName = (id: number) => providers.data.value?.items.find(item => item.id === id)?.providerName ?? `第三方 #${id}`
const credentialName = (id: number | null) => id
  ? credentials.data.value?.items.find(item => item.id === id)?.credentialCode ?? `凭据 #${id}` : '无需凭据'
const selectedProviderCredentials = computed(() => credentials.data.value?.items
  .filter(item => item.providerId === channelForm.providerId) ?? [])
const visibleChannels = computed(() => (channels.data.value ?? []).filter(item =>
  item.providerId === selectedProviderId.value && item.providerProductId === selectedProductId.value && (!channelKeyword.value.trim() ||
    `${item.channelName} ${item.channelCode} ${item.baseUrl}`.toLowerCase().includes(channelKeyword.value.trim().toLowerCase()))))
const providerProducts = computed(() => (products.data.value ?? []).filter(item =>
  item.providerId === selectedProviderId.value && item.status === 'ACTIVE'))
const formProducts = computed(() => (products.data.value ?? []).filter(item =>
  item.providerId === channelForm.providerId && item.status === 'ACTIVE'))
const productName = (id: number | null) => products.data.value?.find(item => item.id === id)?.productName ?? '未分类服务'
const channelContracts = computed(() => contracts.data.value?.items
  .filter(item => item.providerId === selectedChannel.value?.providerId) ?? [])
const availableContracts = computed(() => channelContracts.value.filter(item => !attachedInterfaceIds.value.includes(item.id)))
const parameterContractName = (id: number | null) => id
  ? contracts.data.value?.items.find(item => item.id === id)?.contractName ?? `接口 #${id}` : '通道公共'
const sourceLabel: Record<ParameterSource, string> = { FIXED: '固定值', SECRET_REF: 'Secret 引用',
  REQUEST: '业务请求字段', SYSTEM_TIME: '系统时间', UUID: '系统生成 UUID', EXPRESSION: '受控表达式',
  MAPPING_OUTPUT: '字段映射结果', POLICY_OUTPUT: '规则处理结果' }
watch(() => providers.data.value?.items, items => {
  if (items?.length && !items.some(item => item.id === selectedProviderId.value)) selectedProviderId.value = items[0]!.id
}, { immediate: true })
watch(selectedProviderId, () => { selectedChannel.value = null; parameters.value = []; attachedInterfaceIds.value = [] })
watch(providerProducts, items => {
  if (!items.some(item => item.id === selectedProductId.value)) selectedProductId.value = items[0]?.id ?? null
}, { immediate: true })
watch(selectedProductId, () => { selectedChannel.value = null; parameters.value = []; attachedInterfaceIds.value = [] })

async function selectChannel(channel: AccessChannelAsset): Promise<void> {
  selectedChannel.value = channel; detailLoading.value = true
  try {
    const [parameterItems, interfaceIds, channelPolicies] = await Promise.all([
      accessChannelApi.parameters(channel.id), accessChannelApi.interfaceIds(channel.id)
      , accessChannelApi.policyVersions(channel.id, 'CHANNEL')
    ])
    parameters.value = parameterItems; attachedInterfaceIds.value = interfaceIds; policyVersions.value = channelPolicies
    policyScope.value = 'CHANNEL'; policyContractId.value = null
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '通道详情读取失败') }
  finally { detailLoading.value = false }
}

async function loadPolicyVersions(): Promise<void> {
  if (!selectedChannel.value || (policyScope.value === 'INTERFACE' && !policyContractId.value)) {
    policyVersions.value = []; return
  }
  policyVersions.value = await accessChannelApi.policyVersions(selectedChannel.value.id, policyScope.value,
    policyScope.value === 'INTERFACE' ? policyContractId.value : null)
}

function openPolicyDialog(): void {
  if (!selectedChannel.value) return
  Object.assign(policyForm, { policyName: policyScope.value === 'CHANNEL' ? '通道公共请求规则' : `${parameterContractName(policyContractId.value)}覆盖规则`,
    injectRequestId: true, authenticationMode: 'NONE', credentialRefId: selectedChannel.value.credentialRefId,
    headerName: 'X-API-Key', prefix: '', sourceTemplate: '${context.operationCode}:${context.attributes.signature_timestamp}',
    encoding: 'HEX_LOWER', disabledStepIdsText: '' })
  policyDialog.value = true
}

function policyDocument(): Record<string, unknown> | null {
  const stages: Record<string, unknown[]> = {}
  if (policyForm.injectRequestId) stages.AFTER_REQUEST_MAPPING = [{ id: 'request-trace',
    use: 'builtin.transport.inject@1.0.0', with: { headers: { 'X-Request-Id': '${context.requestId}' } }, onFailure: 'FAIL' }]
  if (policyForm.authenticationMode !== 'NONE') {
    const credential = credentials.data.value?.items.find(item => item.id === policyForm.credentialRefId)
    if (!credential) throw new Error('认证规则必须选择 Secret 凭据')
    const withValue = policyForm.authenticationMode === 'API_KEY'
      ? { secretRef: credential.secretUri, headerName: policyForm.headerName.trim() || 'X-API-Key', prefix: policyForm.prefix }
      : { secretRef: credential.secretUri, sourceTemplate: policyForm.sourceTemplate,
          headerName: policyForm.headerName.trim() || 'X-Signature', encoding: policyForm.encoding, prefix: policyForm.prefix }
    stages.BEFORE_TRANSPORT = [{ id: 'authentication', use: policyForm.authenticationMode === 'API_KEY'
      ? 'builtin.auth.api-key@1.0.0' : 'builtin.auth.hmac-sha256@1.0.0', with: withValue, onFailure: 'FAIL' }]
  }
  return Object.keys(stages).length ? { apiVersion: 'tpip.policy/v1alpha1', kind: 'PolicyChain', stages } : null
}

async function savePolicyVersion(): Promise<void> {
  if (!selectedChannel.value || !policyForm.policyName.trim()) return
  saving.value = true
  try {
    const disabledStepIds = policyForm.disabledStepIdsText.split(/[,\r\n]+/).map(value => value.trim()).filter(Boolean)
    const document = policyDocument()
    if (!document && !disabledStepIds.length) throw new Error('至少配置一条规则，或填写需要禁用的上层规则编码')
    await accessChannelApi.createPolicyVersion(selectedChannel.value.id, { scope: policyScope.value,
      providerContractId: policyScope.value === 'INTERFACE' ? policyContractId.value : null,
      policyName: policyForm.policyName.trim(), document, disabledStepIds })
    await loadPolicyVersions(); policyDialog.value = false; ElMessage.success('已创建不可变策略草稿版本')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '策略创建失败') }
  finally { saving.value = false }
}

async function publishPolicy(versionId: number): Promise<void> {
  if (!selectedChannel.value) return
  saving.value = true
  try { await accessChannelApi.publishPolicyVersion(selectedChannel.value.id, versionId); await loadPolicyVersions(); ElMessage.success('策略版本已发布，将在下次 Bundle 编译时生效') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '策略发布失败') }
  finally { saving.value = false }
}

function openCreateChannel(): void {
  editingChannel.value = null
  Object.assign(channelForm, { providerId: selectedProviderId.value, providerProductId: selectedProductId.value,
    channelCode: '', channelName: '', baseUrl: 'https://',
    credentialRefId: null, description: '' })
  channelDialog.value = true
}

function openEditChannel(): void {
  if (!selectedChannel.value) return
  editingChannel.value = selectedChannel.value
  Object.assign(channelForm, { providerId: selectedChannel.value.providerId,
    providerProductId: selectedChannel.value.providerProductId,
    channelCode: selectedChannel.value.channelCode, channelName: selectedChannel.value.channelName,
    baseUrl: selectedChannel.value.baseUrl, credentialRefId: selectedChannel.value.credentialRefId,
    description: selectedChannel.value.description ?? '' })
  channelDialog.value = true
}

async function saveChannel(): Promise<void> {
  if (!channelForm.providerId || !channelForm.providerProductId || !channelForm.channelCode.trim() || !channelForm.channelName.trim()) {
    ElMessage.warning('请选择第三方系统和产品服务，并填写通道名称、编码'); return
  }
  saving.value = true
  try {
    const saved = editingChannel.value
      ? await accessChannelApi.update(editingChannel.value.id, { channelName: channelForm.channelName.trim(),
        baseUrl: channelForm.baseUrl.trim(), credentialRefId: channelForm.credentialRefId,
        description: channelForm.description.trim() || null, status: editingChannel.value.status,
        rowVersion: editingChannel.value.rowVersion })
      : await accessChannelApi.create({ providerId: channelForm.providerId, providerProductId: channelForm.providerProductId,
        channelCode: channelForm.channelCode.trim(), channelName: channelForm.channelName.trim(),
        baseUrl: channelForm.baseUrl.trim(), credentialRefId: channelForm.credentialRefId,
        description: channelForm.description.trim() || null })
    selectedProviderId.value = saved.providerId; selectedProductId.value = saved.providerProductId
    channelDialog.value = false; await channels.refetch(); await selectChannel(saved)
    ElMessage.success(editingChannel.value ? '接入通道已更新' : '接入通道已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') }
  finally { saving.value = false }
}

async function saveProduct(): Promise<void> {
  if (!selectedProviderId.value || !productForm.productCode.trim() || !productForm.productName.trim()) {
    ElMessage.warning('请填写产品服务编码和名称'); return
  }
  saving.value = true
  try {
    const created = await accessChannelApi.createProduct({ providerId: selectedProviderId.value,
      productCode: productForm.productCode.trim(), productName: productForm.productName.trim(),
      description: productForm.description.trim() || null })
    await products.refetch(); selectedProductId.value = created.id; productDialog.value = false
    ElMessage.success('第三方产品服务已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') }
  finally { saving.value = false }
}

async function attachInterface(): Promise<void> {
  if (!selectedChannel.value || !attachContractId.value) return
  saving.value = true
  try {
    await accessChannelApi.attachInterface(selectedChannel.value.id, attachContractId.value)
    attachedInterfaceIds.value = await accessChannelApi.interfaceIds(selectedChannel.value.id)
    attachDialog.value = false; attachContractId.value = null; ElMessage.success('第三方接口已加入通道')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '关联失败') }
  finally { saving.value = false }
}

function parameterValue(): unknown {
  if (parameterForm.source !== 'FIXED' || parameterForm.overrideMode === 'DISABLE') return null
  if (parameterForm.dataType === 'STRING') return parameterForm.value
  try { return JSON.parse(parameterForm.value) as unknown }
  catch { throw new Error('固定值不是有效的 JSON 数据') }
}

async function saveParameter(): Promise<void> {
  if (!selectedChannel.value || !parameterForm.parameterCode.trim() || !parameterForm.parameterName.trim()) {
    ElMessage.warning('请填写参数名称和参数编码'); return
  }
  saving.value = true
  try {
    await accessChannelApi.upsertParameter(selectedChannel.value.id, { scope: parameterForm.scope,
      providerContractId: parameterForm.scope === 'INTERFACE' ? parameterForm.providerContractId : null,
      parameterCode: parameterForm.parameterCode.trim(), parameterName: parameterForm.parameterName.trim(),
      location: parameterForm.location, source: parameterForm.source, dataType: parameterForm.dataType,
      value: parameterValue(), sourceSelector: parameterForm.sourceSelector.trim() || null,
      secretRefId: parameterForm.source === 'SECRET_REF' ? parameterForm.secretRefId : null,
      overrideMode: parameterForm.overrideMode, required: parameterForm.required,
      sensitive: parameterForm.sensitive, callerOverridable: parameterForm.callerOverridable,
      description: parameterForm.description.trim() || null })
    parameters.value = await accessChannelApi.parameters(selectedChannel.value.id)
    parameterDialog.value = false; ElMessage.success('参数配置已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
</script>

<template>
  <section>
    <div class="product-page-heading">
      <div><span class="eyebrow">接入通道</span><h2>接入通道</h2>
        <p>集中管理第三方服务地址、接入账号和所有接口共用的参数。</p></div>
      <el-button type="primary" @click="openCreateChannel">＋ 新增接入通道</el-button>
    </div>
    <el-alert v-if="channels.isError.value" type="error" :closable="false" show-icon class="command-notice"
      title="接入通道读取失败，请确认数据库迁移已执行且控制服务已重启。" />
    <div class="surface" style="margin-bottom:16px;padding:16px 18px;display:flex;gap:14px;align-items:center">
      <strong style="white-space:nowrap">第三方系统</strong>
      <el-select v-model="selectedProviderId" filterable placeholder="选择要管理的第三方系统" style="max-width:320px">
        <el-option v-for="item in providers.data.value?.items ?? []" :key="item.id" :label="item.providerName" :value="item.id" />
      </el-select>
      <strong style="white-space:nowrap">产品/服务</strong>
      <el-select v-model="selectedProductId" filterable placeholder="选择产品或服务" style="max-width:260px">
        <el-option v-for="item in providerProducts" :key="item.id" :label="item.productName" :value="item.id" />
      </el-select>
      <el-button :disabled="!selectedProviderId" @click="Object.assign(productForm,{productCode:'',productName:'',description:''}); productDialog = true">新增产品服务</el-button>
      <el-input v-model="channelKeyword" clearable placeholder="在当前第三方中搜索服务、通道编码或地址" style="max-width:420px" />
      <span class="subtle">当前仅显示 {{ providerName(selectedProviderId ?? 0) }} / {{ productName(selectedProductId) }} 的 {{ visibleChannels.length }} 个通道</span>
    </div>
    <div class="channel-workspace">
      <div v-loading="channels.isPending.value" class="surface product-table-card channel-list-card">
        <el-empty v-if="!channels.isPending.value && !visibleChannels.length" :description="selectedProviderId ? '当前第三方还没有匹配的接入通道' : '请先选择第三方系统'">
          <el-button type="primary" @click="openCreateChannel">＋ 创建第一个通道</el-button>
        </el-empty>
        <button v-for="channel in visibleChannels" :key="channel.id" type="button" class="channel-list-item"
          :class="{ active: selectedChannel?.id === channel.id }" @click="selectChannel(channel)">
          <span><strong>{{ channel.channelName }}</strong><small>{{ productName(channel.providerProductId) }}</small></span>
          <code>{{ channel.baseUrl }}</code>
        </button>
      </div>
      <div v-loading="detailLoading" class="surface channel-detail-card">
        <el-empty v-if="!selectedChannel" description="请选择一个接入通道" />
        <template v-else>
          <div class="channel-detail-heading"><div><span>{{ providerName(selectedChannel.providerId) }} · {{ productName(selectedChannel.providerProductId) }}</span>
            <h3>{{ selectedChannel.channelName }}</h3><code>{{ selectedChannel.baseUrl }}</code></div>
            <div><el-tag :type="selectedChannel.status === 'ACTIVE' ? 'success' : 'info'">{{ selectedChannel.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag><el-button link type="primary" @click="openEditChannel">编辑通道</el-button></div></div>
          <div class="channel-facts"><div><span>接入凭据</span><strong>{{ credentialName(selectedChannel.credentialRefId) }}</strong></div>
            <div><span>已关联接口</span><strong>{{ attachedInterfaceIds.length }}</strong></div>
            <div><span>公共及覆盖参数</span><strong>{{ parameters.length }}</strong></div></div>
          <div class="channel-section-heading"><div><h4>第三方接口</h4><p>同一个通道下的接口共享服务地址和公共配置。</p></div>
            <el-button @click="attachDialog = true">加入接口</el-button></div>
          <div class="channel-interface-tags"><el-tag v-for="id in attachedInterfaceIds" :key="id" effect="plain">{{ parameterContractName(id) }}</el-tag>
            <span v-if="!attachedInterfaceIds.length">尚未加入接口</span></div>
          <div class="channel-section-heading"><div><h4>公共参数与接口覆盖</h4><p>接口未配置时继承通道参数；接口可以覆盖或禁用同名参数。</p></div>
            <el-button type="primary" class="create-action" @click="parameterDialog = true">新增参数</el-button></div>
          <el-table :data="parameters" size="small">
            <el-table-column label="作用范围" width="150"><template #default="scope">{{ scope.row.scope === 'CHANNEL' ? '通道公共' : parameterContractName(scope.row.providerContractId) }}</template></el-table-column>
            <el-table-column label="参数" min-width="150"><template #default="scope"><strong>{{ scope.row.parameterName }}</strong><small class="table-secondary">{{ scope.row.parameterCode }}</small></template></el-table-column>
            <el-table-column label="位置" prop="location" width="105" />
            <el-table-column label="来源" width="125"><template #default="scope">{{ sourceLabel[scope.row.source as ParameterSource] }}</template></el-table-column>
            <el-table-column label="行为" width="90"><template #default="scope">{{ scope.row.overrideMode === 'DISABLE' ? '禁用继承' : scope.row.scope === 'CHANNEL' ? '公共值' : '覆盖' }}</template></el-table-column>
            <el-table-column label="值" min-width="150"><template #default="scope"><span v-if="scope.row.sensitive">••••••</span><code v-else>{{ scope.row.valueDocument ?? scope.row.sourceSelector ?? '运行时生成' }}</code></template></el-table-column>
          </el-table>
          <div class="channel-section-heading"><div><h4>公共规则与接口覆盖</h4><p>按通道公共 → 接口覆盖 → 具体实现的顺序合并，发布后在新发布包中冻结。</p></div>
            <el-button type="primary" class="create-action" :disabled="policyScope === 'INTERFACE' && !policyContractId" @click="openPolicyDialog">新增规则版本</el-button></div>
          <div class="route-settings"><el-radio-group v-model="policyScope" @change="policyContractId = null; loadPolicyVersions()"><el-radio-button value="CHANNEL">通道公共规则</el-radio-button><el-radio-button value="INTERFACE">接口覆盖规则</el-radio-button></el-radio-group>
            <el-select v-if="policyScope === 'INTERFACE'" v-model="policyContractId" placeholder="选择已关联接口" style="min-width:260px" @change="loadPolicyVersions"><el-option v-for="id in attachedInterfaceIds" :key="id" :label="parameterContractName(id)" :value="id" /></el-select></div>
          <el-empty v-if="!policyVersions.length" description="当前范围还没有策略版本" :image-size="60" />
          <el-table v-else :data="policyVersions" size="small">
            <el-table-column label="规则" min-width="190"><template #default="scope"><strong>{{ scope.row.policyName }}</strong><small class="table-secondary">{{ scope.row.policyCode }}</small></template></el-table-column>
            <el-table-column label="版本" width="80"><template #default="scope">v{{ scope.row.versionNo }}</template></el-table-column>
            <el-table-column label="内容" min-width="180"><template #default="scope"><span>{{ scope.row.normalizedDocument ? '受控执行规则' : '仅禁用上层规则' }}</span><small v-if="scope.row.disabledStepIds.length" class="table-secondary">禁用：{{ scope.row.disabledStepIds.join(', ') }}</small></template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ scope.row.lifecycleStatus === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="90"><template #default="scope"><el-button v-if="scope.row.lifecycleStatus === 'DRAFT'" link type="primary" :loading="saving" @click="publishPolicy(scope.row.id)">发布</el-button></template></el-table-column>
          </el-table>
        </template>
      </div>
    </div>

    <el-dialog v-model="channelDialog" :title="editingChannel ? '编辑接入通道' : '新增接入通道'" width="620px">
      <el-form label-position="top" class="dialog-form"><div class="form-two-columns">
        <el-form-item label="第三方系统"><el-select v-model="channelForm.providerId" filterable :disabled="Boolean(editingChannel)" @change="channelForm.credentialRefId = null; channelForm.providerProductId = null"><el-option v-for="item in providers.data.value?.items" :key="item.id" :label="item.providerName" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="产品/服务"><el-select v-model="channelForm.providerProductId" filterable :disabled="Boolean(editingChannel)" placeholder="例如：短信服务"><el-option v-for="item in formProducts" :key="item.id" :label="item.productName" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="通道名称"><el-input v-model="channelForm.channelName" placeholder="例如：阿里云短信" /></el-form-item>
        <el-form-item label="通道编码"><el-input v-model="channelForm.channelCode" :disabled="Boolean(editingChannel)" placeholder="例如：aliyun.sms" /></el-form-item>
        <el-form-item label="接入凭据"><el-select v-model="channelForm.credentialRefId" clearable placeholder="无需凭据"><el-option v-for="item in selectedProviderCredentials" :key="item.id" :label="item.credentialCode" :value="item.id" /></el-select></el-form-item>
      </div><el-form-item label="服务地址 baseUrl"><el-input v-model="channelForm.baseUrl" placeholder="https://api.example.com" /></el-form-item>
      <el-form-item label="说明"><el-input v-model="channelForm.description" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="channelDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveChannel">{{ editingChannel ? '保存修改' : '创建通道' }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="productDialog" :title="`新增 ${providerName(selectedProviderId ?? 0)} 产品服务`" width="560px">
      <el-alert type="info" :closable="false" title="产品服务用于隔离短信、对象存储、人脸识别等不同能力下的通道和接口。" show-icon />
      <el-form label-position="top" class="dialog-form" style="margin-top:16px"><div class="form-two-columns">
        <el-form-item label="产品服务名称" required><el-input v-model="productForm.productName" placeholder="例如：短信服务" /></el-form-item>
        <el-form-item label="产品服务编码" required><el-input v-model="productForm.productCode" placeholder="例如：sms" /></el-form-item>
      </div><el-form-item label="说明"><el-input v-model="productForm.description" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="productDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveProduct">创建产品服务</el-button></template>
    </el-dialog>

    <el-dialog v-model="attachDialog" title="将第三方接口加入通道" width="520px">
      <el-form label-position="top"><el-form-item label="第三方接口"><el-select v-model="attachContractId" filterable><el-option v-for="item in availableContracts" :key="item.id" :label="item.contractName" :value="item.id" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="attachDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="attachInterface">确认加入</el-button></template>
    </el-dialog>

    <el-dialog v-model="parameterDialog" title="配置通道参数" width="760px">
      <el-form label-position="top" class="dialog-form"><div class="form-two-columns">
        <el-form-item label="作用范围"><el-radio-group v-model="parameterForm.scope"><el-radio-button value="CHANNEL">通道公共</el-radio-button><el-radio-button value="INTERFACE">接口专用</el-radio-button></el-radio-group></el-form-item>
        <el-form-item v-if="parameterForm.scope === 'INTERFACE'" label="指定接口"><el-select v-model="parameterForm.providerContractId"><el-option v-for="item in channelContracts.filter(item => attachedInterfaceIds.includes(item.id))" :key="item.id" :label="item.contractName" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="参数名称"><el-input v-model="parameterForm.parameterName" placeholder="例如：应用标识" /></el-form-item>
        <el-form-item label="参数编码"><el-input v-model="parameterForm.parameterCode" placeholder="例如：appKey" /></el-form-item>
        <el-form-item label="注入位置"><el-select v-model="parameterForm.location"><el-option v-for="item in ['PATH','QUERY','HEADER','COOKIE','BODY','SIGNATURE']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="参数来源"><el-select v-model="parameterForm.source"><el-option v-for="(label,key) in sourceLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item>
        <el-form-item label="数据类型"><el-select v-model="parameterForm.dataType"><el-option v-for="item in ['STRING','NUMBER','BOOLEAN','OBJECT','ARRAY']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item v-if="parameterForm.scope === 'INTERFACE'" label="接口处理"><el-select v-model="parameterForm.overrideMode"><el-option label="覆盖公共参数" value="REPLACE" /><el-option label="禁用公共参数" value="DISABLE" /></el-select></el-form-item>
        <el-form-item v-if="parameterForm.source === 'SECRET_REF'" label="Secret 引用"><el-select v-model="parameterForm.secretRefId"><el-option v-for="item in credentials.data.value?.items.filter(item => item.providerId === selectedChannel?.providerId)" :key="item.id" :label="item.credentialCode" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-else-if="parameterForm.source === 'FIXED'" label="固定值"><el-input v-model="parameterForm.value" :placeholder="parameterForm.dataType === 'STRING' ? '直接输入文本' : '请输入有效 JSON'" /></el-form-item>
        <el-form-item v-else label="来源字段或表达式"><el-input v-model="parameterForm.sourceSelector" placeholder="例如：$.mobile" /></el-form-item>
      </div><el-form-item><el-checkbox v-model="parameterForm.required">必填</el-checkbox><el-checkbox v-model="parameterForm.sensitive">敏感参数</el-checkbox><el-checkbox v-model="parameterForm.callerOverridable">允许调用方覆盖</el-checkbox></el-form-item>
      <el-form-item label="说明"><el-input v-model="parameterForm.description" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="parameterDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveParameter">保存参数</el-button></template>
    </el-dialog>

    <el-dialog v-model="policyDialog" :title="policyScope === 'CHANNEL' ? '新增通道公共规则版本' : '新增接口覆盖规则版本'" width="780px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon title="这里使用可读模板生成受控 Policy DSL；相同规则编码会覆盖上层规则，禁用列表会移除上层规则。" />
      <el-form label-position="top" class="dialog-form" style="margin-top:16px"><el-form-item label="规则版本名称" required><el-input v-model="policyForm.policyName" /></el-form-item>
        <el-checkbox v-model="policyForm.injectRequestId">自动传递请求追踪号（规则编码 request-trace）</el-checkbox>
        <el-form-item label="请求认证方式" style="margin-top:16px"><el-radio-group v-model="policyForm.authenticationMode"><el-radio-button value="NONE">不新增认证</el-radio-button><el-radio-button value="API_KEY">API Key</el-radio-button><el-radio-button value="HMAC_SHA256">HMAC-SHA256</el-radio-button></el-radio-group></el-form-item>
        <div v-if="policyForm.authenticationMode !== 'NONE'" class="form-two-columns">
          <el-form-item label="Secret 凭据" required><el-select v-model="policyForm.credentialRefId" style="width:100%"><el-option v-for="item in credentials.data.value?.items.filter(item => item.providerId === selectedChannel?.providerId)" :key="item.id" :label="item.credentialCode" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="Header 名称"><el-input v-model="policyForm.headerName" :placeholder="policyForm.authenticationMode === 'API_KEY' ? 'X-API-Key' : 'X-Signature'" /></el-form-item>
          <el-form-item label="值前缀"><el-input v-model="policyForm.prefix" placeholder="可留空" /></el-form-item>
          <el-form-item v-if="policyForm.authenticationMode === 'HMAC_SHA256'" label="签名编码"><el-select v-model="policyForm.encoding" style="width:100%"><el-option label="小写十六进制" value="HEX_LOWER" /><el-option label="Base64" value="BASE64" /></el-select></el-form-item>
          <el-form-item v-if="policyForm.authenticationMode === 'HMAC_SHA256'" label="签名原文模板" style="grid-column:1 / -1"><el-input v-model="policyForm.sourceTemplate" type="textarea" :rows="3" class="schema-editor" /></el-form-item>
        </div>
        <el-form-item v-if="policyScope === 'INTERFACE'" label="禁用上层规则编码"><el-input v-model="policyForm.disabledStepIdsText" type="textarea" :rows="2" placeholder="每行或逗号分隔，例如：request-trace, authentication" /><small class="form-hint">如果接口只想关闭通道规则，可以不选择任何新增规则，只填写这里。</small></el-form-item>
      </el-form>
      <template #footer><el-button @click="policyDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePolicyVersion">编译并创建草稿</el-button></template>
    </el-dialog>
  </section>
</template>
