<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { accessChannelApi } from '../api/accessChannelApi'
import type { AccessParameterAsset, ParameterLocation, ParameterScope, ParameterSource } from '../api/accessChannelApi'
import { integrationAssetApi, type EndpointMethod } from '../api/integrationAssetApi'
import { businessIntegrationApi, type BusinessRequestPreview,
  type ChannelAuthenticationVersion, type InterfaceTransportVersion } from '../api/businessIntegrationApi'
import BusinessMessageStructurePanel from '../components/BusinessMessageStructurePanel.vue'

const queryClient = useQueryClient()
const providers = useQuery({ queryKey: ['business-workspace-providers'], queryFn: ({ signal }) => integrationAssetApi.providers('', signal) })
const products = useQuery({ queryKey: ['business-workspace-products'], queryFn: ({ signal }) => accessChannelApi.products(undefined, signal) })
const channels = useQuery({ queryKey: ['business-workspace-channels'], queryFn: ({ signal }) => accessChannelApi.channels(undefined, signal) })
const contracts = useQuery({ queryKey: ['business-workspace-interfaces'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const credentials = useQuery({ queryKey: ['business-workspace-secret-refs'], queryFn: ({ signal }) => integrationAssetApi.credentials(undefined, signal) })
const profiles = useQuery({ queryKey: ['business-workspace-credential-profiles'], queryFn: ({ signal }) => businessIntegrationApi.credentialProfiles(undefined, signal) })
const templates = useQuery({ queryKey: ['business-workspace-auth-templates'], queryFn: ({ signal }) => businessIntegrationApi.authenticationTemplates(undefined, signal) })

const selectedProviderId = ref<number | null>(null)
const selectedProductId = ref<number | null>(null)
const selectedChannelId = ref<number | null>(null)
const selectedInterfaceId = ref<number | null>(null)
const productInterfaceIds = ref<number[]>([])
const attachedInterfaceIds = ref<number[]>([])
const authenticationVersions = ref<ChannelAuthenticationVersion[]>([])
const transportVersions = ref<InterfaceTransportVersion[]>([])
const accessParameters = ref<AccessParameterAsset[]>([])
const publishedMessageStructureCount = ref(0)
const contextLoading = ref(false)
const saving = ref(false)
const preview = ref<BusinessRequestPreview | null>(null)
const previewDrawer = ref(false)
const activeDetailTab = ref('authentication')

const providerDialog = ref(false); const productDialog = ref(false); const channelDialog = ref(false)
const interfaceDialog = ref(false); const credentialDialog = ref(false); const authenticationDialog = ref(false)
const transportDialog = ref(false)
const parameterDialog = ref(false)

const providerItems = computed(() => providers.data.value?.items ?? [])
const selectedProvider = computed(() => providerItems.value.find(item => item.id === selectedProviderId.value) ?? null)
const providerProducts = computed(() => (products.data.value ?? []).filter(item => item.providerId === selectedProviderId.value))
const selectedProduct = computed(() => providerProducts.value.find(item => item.id === selectedProductId.value) ?? null)
const productChannels = computed(() => (channels.data.value ?? []).filter(item =>
  item.providerId === selectedProviderId.value && item.providerProductId === selectedProductId.value))
const selectedChannel = computed(() => productChannels.value.find(item => item.id === selectedChannelId.value) ?? null)
const providerContracts = computed(() => (contracts.data.value?.items ?? []).filter(item => item.providerId === selectedProviderId.value))
const productInterfaces = computed(() => providerContracts.value.filter(item => productInterfaceIds.value.includes(item.id)))
const channelInterfaces = computed(() => productInterfaces.value.filter(item => attachedInterfaceIds.value.includes(item.id)))
const selectedInterface = computed(() => channelInterfaces.value.find(item => item.id === selectedInterfaceId.value) ?? null)
const providerProfiles = computed(() => (profiles.data.value ?? []).filter(item => item.profile.providerId === selectedProviderId.value))
const providerSecrets = computed(() => (credentials.data.value?.items ?? []).filter(item => item.providerId === selectedProviderId.value))
const availableTemplates = computed(() => (templates.data.value ?? []).filter(item => item.template.status === 'ACTIVE'
  && item.versions.some(version => version.lifecycleStatus === 'PUBLISHED')))
const templateOptions = computed(() => availableTemplates.value.flatMap(item => item.versions
  .filter(version => version.lifecycleStatus === 'PUBLISHED')
  .map(version => ({ id: version.id, label: item.template.templateName, templateType: item.template.templateType }))))
const publishedAuthentication = computed(() => authenticationVersions.value.find(item => item.lifecycleStatus === 'PUBLISHED'))
const publishedTransport = computed(() => transportVersions.value.find(item => item.lifecycleStatus === 'PUBLISHED'))
const selectedCredentialProfile = computed(() => providerProfiles.value.find(item =>
  item.profile.id === authenticationForm.credentialProfileId) ?? null)
const selectedPublicCredentialField = computed(() => selectedCredentialProfile.value?.items.find(item =>
  item.valueSource === 'PUBLIC_VALUE' && !item.sensitive) ?? null)
const pageLoading = computed(() => [providers, products, channels, contracts, credentials, profiles, templates]
  .some(item => item.isPending.value))

const providerForm = reactive({ providerCode: '', providerName: '', description: '', ownerCode: 'local-user' })
const productForm = reactive({ productCode: '', productName: '', description: '' })
const channelForm = reactive({ channelCode: '', channelName: '', baseUrl: '', description: '' })
const interfaceForm = reactive<{ contractCode: string; contractName: string; description: string; resourcePath: string
  httpMethod: EndpointMethod; contentType: string }>({ contractCode: '', contractName: '', description: '',
    resourcePath: '/', httpMethod: 'POST', contentType: 'application/json' })
const credentialForm = reactive({ profileCode: '', profileName: '', credentialType: 'ACCESS_KEY',
  publicFieldName: 'AccessKey ID', publicFieldValue: '', secretFieldName: 'AccessKey Secret', secretRefId: null as number | null })
const authenticationForm = reactive({ templateVersionId: null as number | null, credentialProfileId: null as number | null,
  headerName: 'Authorization', prefix: '', sourceTemplate: '${method}\n${path}\n${body}', encoding: 'HEX_LOWER',
  bindPublicCredential: true, identityLocation: 'QUERY' as Exclude<ParameterLocation, 'SIGNATURE'>,
  identityParameterName: 'AccessKeyId' })
const transportForm = reactive<{ resourcePath: string; httpMethod: EndpointMethod; contentType: string; charsetName: string
  connectTimeoutMs: number; readTimeoutMs: number; totalTimeoutMs: number }>({ resourcePath: '/', httpMethod: 'POST',
    contentType: 'application/json', charsetName: 'UTF-8', connectTimeoutMs: 1000, readTimeoutMs: 3000, totalTimeoutMs: 5000 })
const parameterForm = reactive({ scope: 'CHANNEL' as ParameterScope, providerContractId: null as number | null,
  parameterCode: '', parameterName: '', location: 'HEADER' as ParameterLocation, source: 'FIXED' as ParameterSource,
  fixedValue: '', sourceSelector: '', secretRefId: null as number | null, overrideMode: 'REPLACE' as 'REPLACE' | 'DISABLE',
  required: true, sensitive: false, callerOverridable: false, description: '' })

watch(providerItems, items => { if (!selectedProviderId.value && items.length) selectedProviderId.value = items[0]!.id }, { immediate: true })
watch(selectedProviderId, () => {
  selectedProductId.value = providerProducts.value[0]?.id ?? null
}, { flush: 'post' })
watch(providerProducts, items => {
  if (!items.some(item => item.id === selectedProductId.value)) selectedProductId.value = items[0]?.id ?? null
})
watch(selectedProductId, async value => {
  productInterfaceIds.value = value ? await accessChannelApi.productInterfaceIds(value) : []
  selectedChannelId.value = productChannels.value[0]?.id ?? null
  selectedInterfaceId.value = productInterfaces.value[0]?.id ?? null
}, { flush: 'post' })
watch(productChannels, items => {
  if (!items.some(item => item.id === selectedChannelId.value)) selectedChannelId.value = items[0]?.id ?? null
})
watch(channelInterfaces, items => {
  if (!items.some(item => item.id === selectedInterfaceId.value)) selectedInterfaceId.value = items[0]?.id ?? null
})
watch(selectedChannelId, async value => {
  attachedInterfaceIds.value = []; authenticationVersions.value = []; accessParameters.value = []
  if (!value) return
  contextLoading.value = true
  try {
    [attachedInterfaceIds.value, authenticationVersions.value, accessParameters.value] = await Promise.all([
      accessChannelApi.interfaceIds(value), businessIntegrationApi.channelAuthenticationVersions(value),
      accessChannelApi.parameters(value)
    ])
    if (!channelInterfaces.value.some(item => item.id === selectedInterfaceId.value)) {
      selectedInterfaceId.value = channelInterfaces.value[0]?.id ?? null
    }
  } finally { contextLoading.value = false }
})
watch(selectedInterfaceId, async value => {
  publishedMessageStructureCount.value = 0
  transportVersions.value = value ? await businessIntegrationApi.transportVersions(value) : []
})

function reset<T extends object>(target: T, value: Partial<T>): void { Object.assign(target, value) }
function codeValid(value: string): boolean { return /^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$/.test(value) }
function statusText(value: string): string { return value === 'PUBLISHED' ? '已发布' : value === 'DRAFT' ? '草稿' : '已停用' }
function profileName(id: number): string { return providerProfiles.value.find(item => item.profile.id === id)?.profile.profileName ?? `凭据组 ${id}` }
function templateName(versionId: number): string {
  return availableTemplates.value.find(item => item.versions.some(version => version.id === versionId))?.template.templateName ?? `认证版本 ${versionId}`
}
function transportVersion(value: InterfaceTransportVersion): string {
  return typeof value.semanticVersion === 'string' ? value.semanticVersion
    : `${value.semanticVersion.major}.${value.semanticVersion.minor}.${value.semanticVersion.patch}`
}
async function refreshBase(): Promise<void> {
  await Promise.all(['business-workspace-providers', 'business-workspace-products', 'business-workspace-channels',
    'business-workspace-interfaces', 'business-workspace-credential-profiles'].map(key => queryClient.invalidateQueries({ queryKey: [key] })))
}

async function createProvider(): Promise<void> {
  if (!codeValid(providerForm.providerCode) || !providerForm.providerName.trim()) { ElMessage.warning('请填写正确的系统编码和名称'); return }
  saving.value = true
  try {
    const created = await integrationAssetApi.createProvider({ providerCode: providerForm.providerCode.trim(),
      providerName: providerForm.providerName.trim(), providerType: 'SUPPLIER', description: providerForm.description.trim() || null,
      ownerCode: providerForm.ownerCode.trim() || 'local-user' })
    await refreshBase(); selectedProviderId.value = created.id; providerDialog.value = false; ElMessage.success('第三方系统已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') } finally { saving.value = false }
}
async function createProduct(): Promise<void> {
  if (!selectedProviderId.value || !codeValid(productForm.productCode) || !productForm.productName.trim()) { ElMessage.warning('请填写产品编码和名称'); return }
  saving.value = true
  try {
    const created = await accessChannelApi.createProduct({ providerId: selectedProviderId.value,
      productCode: productForm.productCode.trim(), productName: productForm.productName.trim(), description: productForm.description.trim() || null })
    await refreshBase(); selectedProductId.value = created.id; productDialog.value = false; ElMessage.success('产品服务已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') } finally { saving.value = false }
}
async function createChannel(): Promise<void> {
  if (!selectedProviderId.value || !selectedProductId.value || !codeValid(channelForm.channelCode)
    || !channelForm.channelName.trim() || !channelForm.baseUrl.trim()) { ElMessage.warning('请完整填写通道信息'); return }
  saving.value = true
  try {
    const created = await accessChannelApi.create({ providerId: selectedProviderId.value, providerProductId: selectedProductId.value,
      channelCode: channelForm.channelCode.trim(), channelName: channelForm.channelName.trim(), baseUrl: channelForm.baseUrl.trim(),
      credentialRefId: null, description: channelForm.description.trim() || null })
    await refreshBase(); selectedChannelId.value = created.id; channelDialog.value = false; ElMessage.success('接入通道已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') } finally { saving.value = false }
}
async function createInterface(): Promise<void> {
  if (!selectedProviderId.value || !selectedChannelId.value || !codeValid(interfaceForm.contractCode)
    || !interfaceForm.contractName.trim()) { ElMessage.warning('请先选择通道并完整填写接口信息'); return }
  saving.value = true
  try {
    const created = await integrationAssetApi.createContract({ providerId: selectedProviderId.value,
      contractCode: interfaceForm.contractCode.trim(), contractName: interfaceForm.contractName.trim(), protocolType: 'HTTP',
      description: interfaceForm.description.trim() || null })
    await accessChannelApi.attachInterface(selectedChannelId.value, created.id)
    const version = await businessIntegrationApi.createTransportVersion(created.id, { resourcePath: interfaceForm.resourcePath,
      httpMethod: interfaceForm.httpMethod, contentType: interfaceForm.contentType || null, charsetName: 'UTF-8',
      connectTimeoutMs: 1000, readTimeoutMs: 3000, totalTimeoutMs: 5000, transportMetadata: null })
    await refreshBase(); productInterfaceIds.value = await accessChannelApi.productInterfaceIds(selectedProductId.value!)
    selectedInterfaceId.value = created.id; transportVersions.value = [version]; interfaceDialog.value = false
    ElMessage.success('接口已创建，调用信息已保存为草稿')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') } finally { saving.value = false }
}
async function createCredentialProfile(): Promise<void> {
  if (!selectedProviderId.value || !codeValid(credentialForm.profileCode) || !credentialForm.profileName.trim()
    || !credentialForm.publicFieldValue.trim() || !credentialForm.secretRefId) { ElMessage.warning('请完整填写账号凭据'); return }
  saving.value = true
  try {
    await businessIntegrationApi.createCredentialProfile({ providerId: selectedProviderId.value,
      profileCode: credentialForm.profileCode.trim(), profileName: credentialForm.profileName.trim(),
      credentialType: credentialForm.credentialType, description: null, items: [
        { fieldCode: 'access-key-id', fieldName: credentialForm.publicFieldName, valueSource: 'PUBLIC_VALUE',
          publicValue: credentialForm.publicFieldValue.trim(), secretRefId: null, sensitive: false, description: '第三方分配的公开账号标识' },
        { fieldCode: 'secret', fieldName: credentialForm.secretFieldName, valueSource: 'SECRET_REF',
          publicValue: null, secretRefId: credentialForm.secretRefId, sensitive: true, description: '只保存 Secret 引用' }
      ] })
    await queryClient.invalidateQueries({ queryKey: ['business-workspace-credential-profiles'] })
    credentialDialog.value = false; ElMessage.success('账号凭据已创建，未保存密钥明文')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') } finally { saving.value = false }
}
async function createAuthentication(): Promise<void> {
  if (!selectedChannelId.value || !authenticationForm.templateVersionId || !authenticationForm.credentialProfileId)
    { ElMessage.warning('请选择认证方式和账号凭据'); return }
  if (authenticationForm.bindPublicCredential && selectedPublicCredentialField.value
      && !authenticationForm.identityParameterName.trim()) {
    ElMessage.warning('请填写公开账号字段发送给第三方时使用的参数名'); return
  }
  const selectedTemplate = availableTemplates.value.find(item => item.versions.some(version => version.id === authenticationForm.templateVersionId))
  const configuration: Record<string, unknown> = { headerName: authenticationForm.headerName, prefix: authenticationForm.prefix }
  if (authenticationForm.bindPublicCredential && selectedPublicCredentialField.value) {
    configuration.credentialBindings = [{ fieldCode: selectedPublicCredentialField.value.fieldCode,
      location: authenticationForm.identityLocation, parameterName: authenticationForm.identityParameterName.trim() }]
  }
  if (selectedTemplate?.template.templateType === 'HMAC_SHA256') {
    configuration.sourceTemplate = authenticationForm.sourceTemplate; configuration.encoding = authenticationForm.encoding
  }
  saving.value = true
  try {
    await businessIntegrationApi.createChannelAuthentication(selectedChannelId.value, {
      authenticationTemplateVersionId: authenticationForm.templateVersionId,
      credentialProfileId: authenticationForm.credentialProfileId, configuration })
    authenticationVersions.value = await businessIntegrationApi.channelAuthenticationVersions(selectedChannelId.value)
    authenticationDialog.value = false; ElMessage.success('认证配置已保存为草稿')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { saving.value = false }
}
async function publishAuthentication(item: ChannelAuthenticationVersion): Promise<void> {
  if (!selectedChannelId.value) return
  saving.value = true
  try { await businessIntegrationApi.publishChannelAuthentication(selectedChannelId.value, item.id)
    authenticationVersions.value = await businessIntegrationApi.channelAuthenticationVersions(selectedChannelId.value)
    ElMessage.success('认证配置已发布')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '发布失败') } finally { saving.value = false }
}
async function createTransport(): Promise<void> {
  if (!selectedInterfaceId.value) return
  saving.value = true
  try {
    await businessIntegrationApi.createTransportVersion(selectedInterfaceId.value, { ...transportForm, transportMetadata: null })
    transportVersions.value = await businessIntegrationApi.transportVersions(selectedInterfaceId.value)
    transportDialog.value = false; ElMessage.success('接口调用信息已保存为新草稿版本')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { saving.value = false }
}
async function publishTransport(item: InterfaceTransportVersion): Promise<void> {
  if (!selectedInterfaceId.value) return
  saving.value = true
  try { await businessIntegrationApi.publishTransportVersion(selectedInterfaceId.value, item.id)
    transportVersions.value = await businessIntegrationApi.transportVersions(selectedInterfaceId.value); ElMessage.success('接口调用信息已发布')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '发布失败') } finally { saving.value = false }
}
async function showPreview(): Promise<void> {
  if (!selectedChannelId.value || !selectedInterfaceId.value) return
  contextLoading.value = true
  try { preview.value = await businessIntegrationApi.requestPreview(selectedChannelId.value, selectedInterfaceId.value)
    previewDrawer.value = true
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '当前配置还不能生成请求预览') }
  finally { contextLoading.value = false }
}
async function saveParameter(): Promise<void> {
  if (!selectedChannelId.value || !parameterForm.parameterCode.trim() || !parameterForm.parameterName.trim()) {
    ElMessage.warning('请填写参数名称和发送名称'); return
  }
  if (parameterForm.scope === 'INTERFACE' && !parameterForm.providerContractId) {
    ElMessage.warning('接口专用参数必须选择接口'); return
  }
  if (parameterForm.source === 'SECRET_REF' && !parameterForm.secretRefId) {
    ElMessage.warning('请选择 Secret 引用'); return
  }
  if (parameterForm.source === 'SECRET_REF' && !['HEADER', 'COOKIE'].includes(parameterForm.location)) {
    ElMessage.warning('Secret 只能安全地发送到请求头或 Cookie'); return
  }
  let value: unknown = null
  if (parameterForm.source === 'FIXED') value = parameterForm.fixedValue
  saving.value = true
  try {
    await accessChannelApi.upsertParameter(selectedChannelId.value, { scope: parameterForm.scope,
      providerContractId: parameterForm.scope === 'INTERFACE' ? parameterForm.providerContractId : null,
      parameterCode: parameterForm.parameterCode.trim(), parameterName: parameterForm.parameterName.trim(),
      location: parameterForm.location, source: parameterForm.source, dataType: 'STRING', value,
      sourceSelector: ['REQUEST','MAPPING_OUTPUT','POLICY_OUTPUT'].includes(parameterForm.source)
        ? parameterForm.sourceSelector.trim() || null : null,
      secretRefId: parameterForm.source === 'SECRET_REF' ? parameterForm.secretRefId : null,
      overrideMode: parameterForm.overrideMode, required: parameterForm.required,
      sensitive: parameterForm.sensitive || parameterForm.source === 'SECRET_REF',
      callerOverridable: parameterForm.callerOverridable, description: parameterForm.description.trim() || null })
    accessParameters.value = await accessChannelApi.parameters(selectedChannelId.value)
    parameterDialog.value = false; ElMessage.success('请求参数已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
function publishAuthenticationRow(value: unknown): void { void publishAuthentication(value as ChannelAuthenticationVersion) }
function publishTransportRow(value: unknown): void { void publishTransport(value as InterfaceTransportVersion) }
function transportVersionRow(value: unknown): string { return transportVersion(value as InterfaceTransportVersion) }
function parameterSourceText(value: ParameterSource): string {
  return { FIXED: '固定值', SECRET_REF: 'Secret 引用', REQUEST: '业务请求传入', SYSTEM_TIME: '系统时间',
    UUID: '自动生成 UUID', EXPRESSION: '表达式', MAPPING_OUTPUT: '字段映射结果', POLICY_OUTPUT: '规则结果' }[value]
}
function parameterLocationText(value: ParameterLocation): string {
  return { PATH: '路径', QUERY: '查询参数', HEADER: '请求头', COOKIE: 'Cookie', BODY: '请求体', SIGNATURE: '签名' }[value]
}
</script>

<template>
  <section v-loading="pageLoading" class="business-access-page">
    <div class="business-hero">
      <div><span>第三方接入</span><h2>把一个第三方服务接入平台</h2>
        <p>从第三方系统和产品出发，依次配置服务通道、账号认证、接口调用和报文结构。技术执行资产由平台自动生成。</p></div>
      <el-button type="primary" class="create-action" @click="reset(providerForm,{providerCode:'',providerName:'',description:''}); providerDialog = true">新增第三方系统</el-button>
    </div>

    <div class="business-context surface">
      <label><span>第三方系统</span><el-select v-model="selectedProviderId" filterable placeholder="请选择第三方系统">
        <el-option v-for="item in providerItems" :key="item.id" :label="item.providerName" :value="item.id" /></el-select></label>
      <label><span>产品或服务</span><el-select v-model="selectedProductId" filterable placeholder="请选择产品或服务">
        <el-option v-for="item in providerProducts" :key="item.id" :label="item.productName" :value="item.id" /></el-select></label>
      <el-button :disabled="!selectedProviderId" @click="reset(productForm,{productCode:'',productName:'',description:''}); productDialog = true">新增产品服务</el-button>
      <div class="business-context__identity"><strong>{{ selectedProvider?.providerName ?? '尚未选择' }}</strong><span>{{ selectedProduct?.productName ?? '请创建产品服务' }}</span></div>
    </div>

    <el-empty v-if="!selectedProvider" description="创建或选择一个第三方系统后开始配置" class="surface" />
    <template v-else-if="!selectedProduct">
      <div class="surface business-empty"><h3>{{ selectedProvider.providerName }} 还没有产品服务</h3><p>例如阿里云下可以建立“短信服务”“对象存储”“人脸识别”等产品。</p>
        <el-button type="primary" class="create-action" @click="productDialog = true">创建第一个产品服务</el-button></div>
    </template>
    <template v-else>
      <div class="business-progress">
        <div class="done"><span>1</span><strong>第三方与产品</strong><small>{{ selectedProvider.providerName }} · {{ selectedProduct.productName }}</small></div>
        <div :class="{ done: productChannels.length }"><span>2</span><strong>接入通道</strong><small>{{ productChannels.length ? `${productChannels.length} 个通道` : '等待配置服务地址' }}</small></div>
        <div :class="{ done: providerProfiles.length && publishedAuthentication }"><span>3</span><strong>账号与认证</strong><small>{{ publishedAuthentication ? '认证配置已发布' : '等待配置账号认证' }}</small></div>
        <div :class="{ done: productInterfaces.length && publishedTransport }"><span>4</span><strong>第三方接口</strong><small>{{ productInterfaces.length ? `${productInterfaces.length} 个接口` : '等待配置接口' }}</small></div>
        <div :class="{ done: publishedMessageStructureCount }"><span>5</span><strong>报文结构</strong><small>{{ publishedMessageStructureCount ? `${publishedMessageStructureCount} 个已发布版本` : '等待定义请求与返回字段' }}</small></div>
      </div>

      <div class="business-workspace">
        <aside class="surface business-channel-list">
          <div class="business-section-title"><div><span>服务入口</span><h3>接入通道</h3></div>
            <el-button link type="primary" @click="reset(channelForm,{channelCode:'',channelName:'',baseUrl:'',description:''}); channelDialog = true">新增通道</el-button></div>
          <el-empty v-if="!productChannels.length" description="还没有接入通道" :image-size="62" />
          <button v-for="item in productChannels" :key="item.id" type="button" :class="{ active: item.id === selectedChannelId }" @click="selectedChannelId = item.id">
            <strong>{{ item.channelName }}</strong><span>{{ item.baseUrl }}</span><small>{{ item.status === 'ACTIVE' ? '正在使用' : '已停用' }}</small></button>
        </aside>

        <main class="surface business-detail" v-loading="contextLoading">
          <el-empty v-if="!selectedChannel" description="创建或选择一个接入通道" />
          <template v-else>
            <header class="business-detail__header"><div><span>{{ selectedProvider.providerName }} · {{ selectedProduct.productName }}</span>
              <h3>{{ selectedChannel.channelName }}</h3><code>{{ selectedChannel.baseUrl }}</code></div>
              <el-tag type="success">正在使用</el-tag></header>
            <el-tabs v-model="activeDetailTab" class="business-tabs">
              <el-tab-pane label="账号与认证" name="authentication">
                <div class="business-pane-heading"><div><h4>通道账号与认证方式</h4><p>账号信息按字段管理；密钥只保存 Secret 引用。认证方式发布后自动转成执行规则。</p></div>
                  <div><el-button @click="credentialDialog = true">新增账号凭据</el-button><el-button type="primary" class="create-action" :disabled="!providerProfiles.length" @click="authenticationDialog = true">配置认证方式</el-button></div></div>
                <div class="credential-card-grid">
                  <div v-for="item in providerProfiles" :key="item.profile.id" class="credential-card">
                    <span>账号凭据</span><strong>{{ item.profile.profileName }}</strong><small>{{ item.profile.credentialType }}</small>
                    <ul><li v-for="field in item.items" :key="field.id"><span>{{ field.fieldName }}</span><code>{{ field.sensitive ? '••••••（Secret 引用）' : field.displayValue }}</code></li></ul>
                  </div>
                  <div v-if="!providerProfiles.length" class="credential-card credential-card--empty">还没有账号凭据</div>
                </div>
                <el-table :data="authenticationVersions" size="small" empty-text="还没有认证配置">
                  <el-table-column label="认证方式" min-width="170"><template #default="scope"><strong>{{ templateName(scope.row.authenticationTemplateVersionId) }}</strong></template></el-table-column>
                  <el-table-column label="使用账号" min-width="160"><template #default="scope">{{ profileName(scope.row.credentialProfileId) }}</template></el-table-column>
                  <el-table-column label="配置版本" width="100"><template #default="scope">第 {{ scope.row.versionNo }} 版</template></el-table-column>
                  <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ statusText(scope.row.lifecycleStatus) }}</el-tag></template></el-table-column>
                  <el-table-column label="操作" width="100"><template #default="scope"><el-button v-if="scope.row.lifecycleStatus === 'DRAFT'" link type="primary" @click="publishAuthenticationRow(scope.row)">发布</el-button></template></el-table-column>
                </el-table>
              </el-tab-pane>

              <el-tab-pane label="第三方接口" name="interfaces">
                <div class="business-pane-heading"><div><h4>当前产品下的第三方接口</h4><p>一个接口维护自己的 Path、请求方式和版本；Base URL 继承当前通道。</p></div>
                  <el-button type="primary" class="create-action" @click="interfaceDialog = true">新增接口</el-button></div>
                <div class="interface-selector-row"><el-select v-model="selectedInterfaceId" filterable placeholder="选择当前通道的接口">
                  <el-option v-for="item in channelInterfaces" :key="item.id" :label="item.contractName" :value="item.id" /></el-select>
                  <el-button :disabled="!selectedInterface" @click="transportDialog = true">新增调用版本</el-button>
                  <el-button type="primary" :disabled="!publishedTransport || !publishedAuthentication" @click="showPreview">查看最终请求</el-button></div>
                <el-empty v-if="!selectedInterface" description="还没有第三方接口" />
                <template v-else><div class="selected-interface-summary"><span>接口名称</span><strong>{{ selectedInterface.contractName }}</strong><small>{{ selectedInterface.description || '暂无说明' }}</small></div>
                  <el-table :data="transportVersions" size="small" empty-text="还没有接口调用信息">
                    <el-table-column label="版本" width="90"><template #default="scope">{{ transportVersionRow(scope.row) }}</template></el-table-column>
                    <el-table-column label="请求方式" width="100" prop="httpMethod" />
                    <el-table-column label="接口路径" min-width="180" prop="resourcePath" />
                    <el-table-column label="最终地址" min-width="300"><template #default="scope"><code>{{ selectedChannel.baseUrl }}{{ scope.row.resourcePath }}</code></template></el-table-column>
                    <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ statusText(scope.row.lifecycleStatus) }}</el-tag></template></el-table-column>
                    <el-table-column label="操作" width="100"><template #default="scope"><el-button v-if="scope.row.lifecycleStatus === 'DRAFT'" link type="primary" @click="publishTransportRow(scope.row)">发布</el-button></template></el-table-column>
                  </el-table></template>
              </el-tab-pane>

              <el-tab-pane label="报文结构" name="message-structure">
                <BusinessMessageStructurePanel :interface-id="selectedInterfaceId" :interface-name="selectedInterface?.contractName ?? ''" @published-change="publishedMessageStructureCount = $event" />
              </el-tab-pane>

              <el-tab-pane label="公共参数与接口覆盖" name="advanced">
                <div class="business-pane-heading"><div><h4>请求参数封装</h4><p>配置所有接口共用的 Header、Query、Body 参数；某个接口可以覆盖或禁用公共参数。</p></div>
                  <el-button type="primary" class="create-action" @click="parameterDialog = true">新增请求参数</el-button></div>
                <el-table :data="accessParameters" size="small" empty-text="还没有请求参数配置">
                  <el-table-column label="使用范围" width="150"><template #default="scope">{{ scope.row.scope === 'CHANNEL' ? '通道所有接口' : channelInterfaces.find(item => item.id === scope.row.providerContractId)?.contractName ?? '接口专用' }}</template></el-table-column>
                  <el-table-column label="参数名称" min-width="170"><template #default="scope"><strong>{{ scope.row.parameterName }}</strong><small class="table-secondary">发送为：{{ scope.row.parameterCode }}</small></template></el-table-column>
                  <el-table-column label="发送位置" width="100"><template #default="scope">{{ parameterLocationText(scope.row.location) }}</template></el-table-column>
                  <el-table-column label="取值方式" width="120"><template #default="scope">{{ parameterSourceText(scope.row.source) }}</template></el-table-column>
                  <el-table-column label="预览值" min-width="170"><template #default="scope"><span v-if="scope.row.sensitive">••••••</span><code v-else>{{ scope.row.valueDocument ?? scope.row.sourceSelector ?? '运行时生成' }}</code></template></el-table-column>
                  <el-table-column label="行为" width="100"><template #default="scope">{{ scope.row.overrideMode === 'DISABLE' ? '禁用公共值' : scope.row.scope === 'CHANNEL' ? '公共配置' : '接口覆盖' }}</template></el-table-column>
                </el-table>
                <div class="business-guidance-card"><h4>复杂签名和特殊封装</h4><p>标准参数优先使用上方表单。只有无法表单化的复杂逻辑才进入受控执行规则，高级用户可以继续配置接口专用规则。</p>
                  <router-link :to="`/integration-assets/channels?providerId=${selectedProviderId}&productId=${selectedProductId}&channelId=${selectedChannelId}`">进入高级执行规则</router-link></div>
              </el-tab-pane>
            </el-tabs>
          </template>
        </main>
      </div>
    </template>

    <el-dialog v-model="providerDialog" title="新增第三方系统" width="560px"><el-form label-position="top"><div class="form-two-columns">
      <el-form-item label="系统名称"><el-input v-model="providerForm.providerName" placeholder="例如：阿里云" /></el-form-item>
      <el-form-item label="系统编码"><el-input v-model="providerForm.providerCode" placeholder="例如：aliyun" /></el-form-item></div>
      <el-form-item label="系统说明"><el-input v-model="providerForm.description" type="textarea" placeholder="这个第三方系统提供什么服务" /></el-form-item></el-form>
      <template #footer><el-button @click="providerDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="createProvider">创建系统</el-button></template></el-dialog>
    <el-dialog v-model="productDialog" title="新增产品或服务" width="560px"><el-alert type="info" :closable="false" :title="`归属于：${selectedProvider?.providerName ?? '-'}`" />
      <el-form label-position="top"><div class="form-two-columns"><el-form-item label="产品名称"><el-input v-model="productForm.productName" placeholder="例如：短信服务" /></el-form-item>
        <el-form-item label="产品编码"><el-input v-model="productForm.productCode" placeholder="例如：sms" /></el-form-item></div><el-form-item label="说明"><el-input v-model="productForm.description" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="productDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="createProduct">创建产品服务</el-button></template></el-dialog>
    <el-dialog v-model="channelDialog" title="新增接入通道" width="620px"><el-alert type="info" :closable="false" title="通道负责服务地址，同一通道下的接口共享这个地址和账号认证。" />
      <el-form label-position="top"><div class="form-two-columns"><el-form-item label="通道名称"><el-input v-model="channelForm.channelName" placeholder="例如：阿里云短信默认通道" /></el-form-item>
        <el-form-item label="通道编码"><el-input v-model="channelForm.channelCode" placeholder="例如：aliyun.sms.default" /></el-form-item></div>
        <el-form-item label="服务地址"><el-input v-model="channelForm.baseUrl" placeholder="https://dysmsapi.aliyuncs.com" /></el-form-item><el-form-item label="说明"><el-input v-model="channelForm.description" /></el-form-item></el-form>
      <template #footer><el-button @click="channelDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="createChannel">创建通道</el-button></template></el-dialog>
    <el-dialog v-model="interfaceDialog" title="新增第三方接口" width="680px"><el-alert type="info" :closable="false" :title="`接口将加入通道：${selectedChannel?.channelName ?? '-'}`" />
      <el-form label-position="top"><div class="form-two-columns"><el-form-item label="接口名称"><el-input v-model="interfaceForm.contractName" placeholder="例如：发送短信" /></el-form-item>
        <el-form-item label="接口编码"><el-input v-model="interfaceForm.contractCode" placeholder="例如：aliyun.sms.send" /></el-form-item></div>
        <div class="interface-call-form"><el-form-item label="请求方式"><el-select v-model="interfaceForm.httpMethod"><el-option v-for="item in ['GET','POST','PUT','PATCH','DELETE']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
          <el-form-item label="接口路径"><el-input v-model="interfaceForm.resourcePath" placeholder="/" /></el-form-item><el-form-item label="报文类型"><el-input v-model="interfaceForm.contentType" /></el-form-item></div>
        <el-form-item label="接口说明"><el-input v-model="interfaceForm.description" /></el-form-item></el-form>
      <template #footer><el-button @click="interfaceDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="createInterface">创建接口并保存调用信息</el-button></template></el-dialog>
    <el-dialog v-model="credentialDialog" title="新增账号凭据" width="680px"><el-alert type="warning" :closable="false" title="平台不保存密钥明文，只保存本地开发 Secret 或外部密钥服务的引用。" />
      <el-form label-position="top"><div class="form-two-columns"><el-form-item label="账号名称"><el-input v-model="credentialForm.profileName" placeholder="例如：阿里云短信账号 A" /></el-form-item>
        <el-form-item label="账号编码"><el-input v-model="credentialForm.profileCode" placeholder="例如：aliyun.sms.account-a" /></el-form-item></div>
        <div class="credential-field-editor"><strong>公开账号标识</strong><div class="form-two-columns"><el-form-item label="字段名称"><el-input v-model="credentialForm.publicFieldName" /></el-form-item><el-form-item label="字段值"><el-input v-model="credentialForm.publicFieldValue" placeholder="例如：AccessKey ID" /></el-form-item></div></div>
        <div class="credential-field-editor"><strong>敏感密钥</strong><div class="form-two-columns"><el-form-item label="字段名称"><el-input v-model="credentialForm.secretFieldName" /></el-form-item><el-form-item label="Secret 引用"><el-select v-model="credentialForm.secretRefId" filterable placeholder="选择已登记的 Secret 引用"><el-option v-for="item in providerSecrets" :key="item.id" :label="item.credentialCode" :value="item.id" /></el-select><router-link v-if="!providerSecrets.length" class="secret-help-link" to="/integration-assets/advanced/provider-assets">还没有 Secret 引用，前往本地 Secret 管理</router-link></el-form-item></div></div></el-form>
      <template #footer><el-button @click="credentialDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="createCredentialProfile">创建账号凭据</el-button></template></el-dialog>
    <el-dialog v-model="authenticationDialog" title="配置通道认证方式" width="640px"><el-form label-position="top"><el-form-item label="认证方式"><el-select v-model="authenticationForm.templateVersionId" filterable placeholder="选择认证方式">
      <el-option v-for="item in templateOptions" :key="item.id" :label="item.label" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="使用账号"><el-select v-model="authenticationForm.credentialProfileId" filterable placeholder="选择账号凭据"><el-option v-for="item in providerProfiles" :key="item.profile.id" :label="item.profile.profileName" :value="item.profile.id" /></el-select></el-form-item>
      <div class="form-two-columns"><el-form-item label="认证头名称"><el-input v-model="authenticationForm.headerName" /></el-form-item><el-form-item label="值前缀"><el-input v-model="authenticationForm.prefix" placeholder="例如：Bearer（可留空）" /></el-form-item></div>
      <div v-if="selectedPublicCredentialField" class="credential-binding-form"><el-checkbox v-model="authenticationForm.bindPublicCredential">同时发送公开账号字段“{{ selectedPublicCredentialField.fieldName }}”</el-checkbox>
        <div v-if="authenticationForm.bindPublicCredential" class="form-two-columns"><el-form-item label="发送位置"><el-select v-model="authenticationForm.identityLocation"><el-option label="请求头 Header" value="HEADER" /><el-option label="查询参数 Query" value="QUERY" /><el-option label="请求体 Body" value="BODY" /></el-select></el-form-item>
          <el-form-item label="第三方要求的参数名"><el-input v-model="authenticationForm.identityParameterName" placeholder="例如：AccessKeyId、appKey" /></el-form-item></div></div>
      <el-form-item v-if="availableTemplates.find(item => item.versions.some(version => version.id === authenticationForm.templateVersionId))?.template.templateType === 'HMAC_SHA256'" label="签名原文模板"><el-input v-model="authenticationForm.sourceTemplate" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="authenticationDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="createAuthentication">保存草稿</el-button></template></el-dialog>
    <el-dialog v-model="transportDialog" title="新增接口调用版本" width="680px"><el-alert type="info" :closable="false" title="版本号由平台自动递增，已发布版本不会被原地修改。" />
      <el-form label-position="top"><div class="interface-call-form"><el-form-item label="请求方式"><el-select v-model="transportForm.httpMethod"><el-option v-for="item in ['GET','POST','PUT','PATCH','DELETE']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="接口路径"><el-input v-model="transportForm.resourcePath" /></el-form-item><el-form-item label="报文类型"><el-input v-model="transportForm.contentType" /></el-form-item></div>
        <div class="form-three-columns"><el-form-item label="连接超时（毫秒）"><el-input-number v-model="transportForm.connectTimeoutMs" :min="1" /></el-form-item><el-form-item label="读取超时（毫秒）"><el-input-number v-model="transportForm.readTimeoutMs" :min="1" /></el-form-item><el-form-item label="总超时（毫秒）"><el-input-number v-model="transportForm.totalTimeoutMs" :min="1" /></el-form-item></div></el-form>
      <template #footer><el-button @click="transportDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="createTransport">保存新版本</el-button></template></el-dialog>

    <el-dialog v-model="parameterDialog" title="新增请求参数" width="700px"><el-alert type="info" :closable="false" title="公共参数对当前通道所有接口生效；接口专用配置可以覆盖同名公共参数。" />
      <el-form label-position="top"><div class="form-two-columns"><el-form-item label="使用范围"><el-radio-group v-model="parameterForm.scope"><el-radio-button value="CHANNEL">通道所有接口</el-radio-button><el-radio-button value="INTERFACE">某个接口专用</el-radio-button></el-radio-group></el-form-item>
        <el-form-item v-if="parameterForm.scope === 'INTERFACE'" label="选择接口"><el-select v-model="parameterForm.providerContractId" filterable><el-option v-for="item in channelInterfaces" :key="item.id" :label="item.contractName" :value="item.id" /></el-select></el-form-item></div>
        <div class="form-two-columns"><el-form-item label="业务名称"><el-input v-model="parameterForm.parameterName" placeholder="例如：应用标识" /></el-form-item><el-form-item label="发送给第三方的参数名"><el-input v-model="parameterForm.parameterCode" placeholder="例如：appKey、X-App-Id" /></el-form-item></div>
        <div class="form-three-columns"><el-form-item label="发送位置"><el-select v-model="parameterForm.location"><el-option label="请求头 Header" value="HEADER" /><el-option label="查询参数 Query" value="QUERY" /><el-option label="请求体 Body" value="BODY" /><el-option label="Cookie" value="COOKIE" /><el-option label="路径参数 Path" value="PATH" /></el-select></el-form-item>
          <el-form-item label="取值方式"><el-select v-model="parameterForm.source"><el-option label="固定配置值" value="FIXED" /><el-option label="业务请求传入" value="REQUEST" /><el-option label="Secret 引用" value="SECRET_REF" /><el-option label="当前系统时间" value="SYSTEM_TIME" /><el-option label="自动生成 UUID" value="UUID" /><el-option label="字段映射结果" value="MAPPING_OUTPUT" /></el-select></el-form-item>
          <el-form-item label="覆盖行为"><el-select v-model="parameterForm.overrideMode"><el-option label="设置或覆盖" value="REPLACE" /><el-option v-if="parameterForm.scope === 'INTERFACE'" label="禁用同名公共参数" value="DISABLE" /></el-select></el-form-item></div>
        <el-form-item v-if="parameterForm.source === 'FIXED'" label="固定值"><el-input v-model="parameterForm.fixedValue" /></el-form-item>
        <el-form-item v-if="['REQUEST','MAPPING_OUTPUT','POLICY_OUTPUT'].includes(parameterForm.source)" label="取值路径"><el-input v-model="parameterForm.sourceSelector" placeholder="例如：$.tenantId" /></el-form-item>
        <el-form-item v-if="parameterForm.source === 'SECRET_REF'" label="Secret 引用"><el-select v-model="parameterForm.secretRefId" filterable><el-option v-for="item in providerSecrets" :key="item.id" :label="item.credentialCode" :value="item.id" /></el-select></el-form-item>
        <div class="parameter-options"><el-checkbox v-model="parameterForm.required">必填</el-checkbox><el-checkbox v-model="parameterForm.sensitive">按敏感值展示</el-checkbox><el-checkbox v-model="parameterForm.callerOverridable">允许调用方覆盖</el-checkbox></div></el-form>
      <template #footer><el-button @click="parameterDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveParameter">保存参数</el-button></template></el-dialog>

    <el-drawer v-model="previewDrawer" title="最终请求预览" size="620px"><template v-if="preview"><el-alert type="success" :closable="false" :title="preview.notice" />
      <div class="request-preview"><span>最终请求</span><strong>{{ preview.target.httpMethod }} {{ preview.target.finalUrl }}</strong>
        <dl><div><dt>接入通道</dt><dd>{{ preview.target.channelName }}</dd></div><div><dt>第三方接口</dt><dd>{{ preview.target.interfaceName }}</dd></div><div><dt>认证方式</dt><dd>{{ preview.authentication.templateName }}</dd></div><div><dt>账号凭据</dt><dd>{{ preview.authentication.credentialProfileName }}</dd></div></dl></div>
      <h4>认证注入效果</h4><el-table :data="preview.authentication.effects" size="small"><el-table-column label="位置" prop="target" width="90" /><el-table-column label="名称" prop="name" width="150" /><el-table-column label="说明" prop="description" /></el-table>
      <h4>凭据字段</h4><el-table :data="preview.authentication.credentialFields" size="small"><el-table-column label="字段" prop="fieldName" width="150" /><el-table-column label="来源" prop="sourceType" width="110" /><el-table-column label="预览值" prop="displayValue" /></el-table></template></el-drawer>
  </section>
</template>
