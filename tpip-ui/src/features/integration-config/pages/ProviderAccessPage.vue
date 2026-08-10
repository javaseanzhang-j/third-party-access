<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { integrationAssetApi, type CreateCredentialInput, type CreateEndpointInput,
  type CreateProviderContractInput, type CreateProviderInput, type CredentialType,
  type EndpointAsset, type EndpointMethod, type EndpointProbe, type EndpointScheme,
  type ProtocolType, type ProviderType } from '../api/integrationAssetApi'

const queryClient = useQueryClient(); const activeTab = ref('providers'); const errorMessage = ref('')
const providerDialog = ref(false); const credentialDialog = ref(false); const contractDialog = ref(false); const endpointDialog = ref(false)
const providers = useQuery({ queryKey: ['integration-providers'], queryFn: ({ signal }) => integrationAssetApi.providers('', signal) })
const credentials = useQuery({ queryKey: ['integration-credentials'], queryFn: ({ signal }) => integrationAssetApi.credentials(undefined, signal) })
const contracts = useQuery({ queryKey: ['integration-provider-contracts'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const endpoints = useQuery({ queryKey: ['integration-endpoints'], queryFn: ({ signal }) => integrationAssetApi.endpoints(undefined, signal) })
const providerItems = computed(() => providers.data.value?.items ?? [])
const credentialItems = computed(() => credentials.data.value?.items ?? [])
const contractItems = computed(() => contracts.data.value?.items ?? [])

const providerForm = reactive<CreateProviderInput>({ providerCode: '', providerName: '', providerType: 'SUPPLIER', description: null, ownerCode: '' })
const credentialForm = reactive<CreateCredentialInput>({ providerId: 0, credentialCode: '', environmentCode: 'test', credentialType: 'API_KEY', secretUri: 'env://TPIP_SECRET_', secretMetadata: null })
const contractForm = reactive<CreateProviderContractInput>({ providerId: 0, contractCode: '', contractName: '', protocolType: 'HTTP', description: null })
const endpointForm = reactive<CreateEndpointInput>({ providerContractId: 0, endpointCode: '', environmentCode: 'test', protocolScheme: 'HTTPS', baseUrl: '', resourcePath: '/', httpMethod: 'POST', contentType: 'application/json', charsetName: 'UTF-8', connectTimeoutMs: 1000, readTimeoutMs: 3000, totalTimeoutMs: 5000, credentialRefId: null, networkConfig: {}, tlsConfig: null })

function message(error: Error): string { return error instanceof ApiError ? error.message : '请求失败，请确认 Control Plane 与数据库状态。' }
function invalidate(): void {
  for (const key of ['integration-providers', 'integration-credentials', 'integration-provider-contracts', 'integration-endpoints']) {
    void queryClient.invalidateQueries({ queryKey: [key] })
  }
}
const createProvider = useMutation({ mutationFn: (input: CreateProviderInput) => integrationAssetApi.createProvider(input), onSuccess: () => { providerDialog.value = false; invalidate(); ElMessage.success('Provider 已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const createCredential = useMutation({ mutationFn: (input: CreateCredentialInput) => integrationAssetApi.createCredential(input), onSuccess: () => { credentialDialog.value = false; invalidate(); ElMessage.success('CredentialRef 已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const createContract = useMutation({ mutationFn: (input: CreateProviderContractInput) => integrationAssetApi.createContract(input), onSuccess: () => { contractDialog.value = false; invalidate(); ElMessage.success('第三方接口已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const createEndpoint = useMutation({ mutationFn: (input: CreateEndpointInput) => integrationAssetApi.createEndpoint(input), onSuccess: () => { endpointDialog.value = false; invalidate(); ElMessage.success('Endpoint Revision 已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const endpointCommand = useMutation<EndpointAsset | EndpointProbe, Error, { action: 'publish' | 'probe'; id: number }>({ mutationFn: (input) => input.action === 'publish' ? integrationAssetApi.publishEndpoint(input.id) : integrationAssetApi.probeEndpoint(input.id), onSuccess: (result, input) => { invalidate(); ElMessage.success(input.action === 'publish' ? 'Endpoint 已发布' : `探测完成：${'outcome' in result ? result.outcome : 'SUCCESS'}`) }, onError: (e: Error) => { errorMessage.value = message(e) } })

function requireCode(code: string): boolean { return /^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$/.test(code) }
function submitProvider(): void {
  errorMessage.value = ''
  if (!requireCode(providerForm.providerCode) || !providerForm.providerName.trim() || !providerForm.ownerCode.trim()) { errorMessage.value = '请填写合法 Provider 编码、名称和负责人。'; return }
  createProvider.mutate({ ...providerForm, description: providerForm.description?.trim() || null })
}
function submitCredential(): void {
  errorMessage.value = ''
  if (!credentialForm.providerId || !requireCode(credentialForm.credentialCode) || !/^env:\/\/TPIP_SECRET_[A-Z0-9_]{1,100}$/.test(credentialForm.secretUri)) { errorMessage.value = '请选择 Provider，并使用 env://TPIP_SECRET_* 格式的 Secret 引用。'; return }
  createCredential.mutate({ ...credentialForm })
}
function submitContract(): void {
  errorMessage.value = ''
  if (!contractForm.providerId || !requireCode(contractForm.contractCode) || !contractForm.contractName.trim()) { errorMessage.value = '请选择 Provider，并填写合法 Contract 编码和名称。'; return }
  createContract.mutate({ ...contractForm, description: contractForm.description?.trim() || null })
}
function submitEndpoint(): void {
  errorMessage.value = ''
  if (!endpointForm.providerContractId || !requireCode(endpointForm.endpointCode) || !endpointForm.baseUrl.trim() || !endpointForm.resourcePath.startsWith('/')) { errorMessage.value = '请选择第三方接口，并填写合法的调用地址编码、基础地址和以 / 开头的资源路径。'; return }
  if (endpointForm.totalTimeoutMs < endpointForm.connectTimeoutMs || endpointForm.totalTimeoutMs < endpointForm.readTimeoutMs) { errorMessage.value = '总超时不能小于连接或读取超时。'; return }
  createEndpoint.mutate({ ...endpointForm })
}
function providerName(id: number): string { const item = providerItems.value.find(value => value.id === id); return item ? `${item.providerName} · ${item.providerCode}` : `Provider #${id}` }
function contractName(id: number): string { const item = contractItems.value.find(value => value.id === id); return item ? `${item.contractName} · ${item.contractCode}` : `Contract #${id}` }
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>第三方系统配置</h2><p>管理第三方提供方、凭据引用、第三方接口和实际调用地址。</p></div><el-tag effect="plain">本地配置模式</el-tag></div>
    <el-alert v-if="errorMessage" class="command-notice" type="error" :title="errorMessage" :closable="true" @close="errorMessage = ''" show-icon />
    <div class="surface integration-asset-tabs">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="Provider" name="providers">
          <div class="asset-toolbar"><div><strong>第三方稳定身份</strong><span>供应商、渠道或外部平台，不包含环境和接口版本。</span></div><el-button type="primary" @click="providerDialog = true">新增 Provider</el-button></div>
          <AsyncStatePanel :loading="providers.isPending.value" :error="providers.isError.value" error-title="Provider 读取失败" @retry="providers.refetch()">
            <el-empty v-if="!providerItems.length" description="尚未创建 Provider" />
            <el-table v-else :data="providerItems" row-key="id"><el-table-column prop="providerName" label="名称" min-width="180" /><el-table-column label="编码" min-width="220"><template #default="{ row }"><span class="mono">{{ row.providerCode }}</span></template></el-table-column><el-table-column prop="providerType" label="类型" width="120" /><el-table-column prop="ownerCode" label="负责人" width="150" /><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag></template></el-table-column></el-table>
          </AsyncStatePanel>
        </el-tab-pane>
        <el-tab-pane label="CredentialRef" name="credentials">
          <div class="asset-toolbar"><div><strong>Secret 引用</strong><span>只登记引用和非敏感元数据，不保存、展示或测试真实 Secret 值。</span></div><el-button type="primary" :disabled="!providerItems.length" @click="credentialDialog = true">新增 CredentialRef</el-button></div>
          <el-alert class="asset-guidance" type="warning" :closable="false" title="当前 Runtime 只实现 env://TPIP_SECRET_*。环境变量必须在对应 Java 进程启动前注入。" show-icon />
          <AsyncStatePanel :loading="credentials.isPending.value" :error="credentials.isError.value" error-title="CredentialRef 读取失败" @retry="credentials.refetch()">
            <el-empty v-if="!credentialItems.length" description="尚未登记 Secret 引用" />
            <el-table v-else :data="credentialItems" row-key="id"><el-table-column label="Provider" min-width="210"><template #default="{ row }">{{ providerName(row.providerId) }}</template></el-table-column><el-table-column label="Credential" min-width="220"><template #default="{ row }"><strong>{{ row.credentialCode }}</strong><div class="subtle">{{ row.credentialType }} · {{ row.environmentCode }}</div></template></el-table-column><el-table-column label="Secret Reference" min-width="300"><template #default="{ row }"><span class="mono">{{ row.secretUri }}</span></template></el-table-column><el-table-column prop="status" label="状态" width="100" /></el-table>
          </AsyncStatePanel>
        </el-tab-pane>
        <el-tab-pane label="第三方接口" name="contracts">
          <div class="asset-toolbar"><div><strong>第三方接口</strong><span>先登记接口的稳定身份；请求、返回、错误和回调字段在“第三方报文结构”中配置。</span></div><el-button type="primary" :disabled="!providerItems.length" @click="contractDialog = true">新增第三方接口</el-button></div>
          <AsyncStatePanel :loading="contracts.isPending.value" :error="contracts.isError.value" error-title="第三方接口读取失败" @retry="contracts.refetch()">
            <el-empty v-if="!contractItems.length" description="尚未创建第三方接口" />
            <el-table v-else :data="contractItems" row-key="id"><el-table-column label="第三方接口" min-width="260"><template #default="{ row }"><strong>{{ row.contractName }}</strong><div class="subtle mono">{{ row.contractCode }}</div></template></el-table-column><el-table-column label="第三方提供方" min-width="220"><template #default="{ row }">{{ providerName(row.providerId) }}</template></el-table-column><el-table-column prop="protocolType" label="通信方式" width="110" /><el-table-column prop="status" label="状态" width="100" /></el-table>
          </AsyncStatePanel>
        </el-tab-pane>
        <el-tab-pane label="Endpoint" name="endpoints">
          <div class="asset-toolbar"><div><strong>环境化执行端点</strong><span>地址变化创建新 Revision；DRAFT 探测通过后再显式发布。</span></div><el-button type="primary" :disabled="!contractItems.length" @click="endpointDialog = true">新增 Endpoint Revision</el-button></div>
          <AsyncStatePanel :loading="endpoints.isPending.value" :error="endpoints.isError.value" error-title="Endpoint 读取失败" @retry="endpoints.refetch()">
            <el-empty v-if="!endpoints.data.value?.items.length" description="尚未创建 Endpoint" />
            <el-table v-else :data="endpoints.data.value?.items" row-key="id"><el-table-column label="Endpoint" min-width="230"><template #default="{ row }"><strong>{{ row.endpointCode }}</strong><div class="subtle">{{ row.environmentCode }} · Revision {{ row.revisionNo }}</div></template></el-table-column><el-table-column label="地址" min-width="330"><template #default="{ row }"><span class="mono">{{ row.httpMethod }} {{ row.baseUrl }}{{ row.resourcePath }}</span></template></el-table-column><el-table-column label="Contract" min-width="210"><template #default="{ row }">{{ contractName(row.providerContractId) }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ row.lifecycleStatus }}</el-tag></template></el-table-column><el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" :loading="endpointCommand.isPending.value" @click="endpointCommand.mutate({ action: 'probe', id: row.id })">探测</el-button><el-button v-if="row.lifecycleStatus === 'DRAFT'" link type="primary" :loading="endpointCommand.isPending.value" @click="endpointCommand.mutate({ action: 'publish', id: row.id })">发布</el-button></template></el-table-column></el-table>
          </AsyncStatePanel>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="providerDialog" title="新增 Provider" width="560px"><el-form label-position="top" @submit.prevent="submitProvider"><el-form-item label="Provider 编码" required><el-input v-model="providerForm.providerCode" placeholder="vendor.customer" /></el-form-item><el-form-item label="名称" required><el-input v-model="providerForm.providerName" /></el-form-item><el-form-item label="类型" required><el-select v-model="providerForm.providerType" style="width:100%"><el-option v-for="item in (['SUPPLIER','CHANNEL','PLATFORM'] as ProviderType[])" :key="item" :value="item" /></el-select></el-form-item><el-form-item label="负责人" required><el-input v-model="providerForm.ownerCode" /></el-form-item><el-form-item label="说明"><el-input v-model="providerForm.description" type="textarea" /></el-form-item><p v-if="errorMessage" class="command-validation">{{ errorMessage }}</p></el-form><template #footer><el-button @click="providerDialog = false">取消</el-button><el-button type="primary" :loading="createProvider.isPending.value" @click="submitProvider">创建</el-button></template></el-dialog>
    <el-dialog v-model="credentialDialog" title="新增 CredentialRef" width="600px"><el-alert type="info" :closable="false" title="这里只填写引用。真实 Secret 在应用启动环境中配置，平台不会读取后回显。" show-icon /><el-form class="dialog-form" label-position="top" @submit.prevent="submitCredential"><el-form-item label="Provider" required><el-select v-model="credentialForm.providerId" filterable style="width:100%"><el-option v-for="item in providerItems" :key="item.id" :label="providerName(item.id)" :value="item.id" /></el-select></el-form-item><el-form-item label="Credential 编码" required><el-input v-model="credentialForm.credentialCode" placeholder="vendor.customer.api-key" /></el-form-item><div class="form-two-columns"><el-form-item label="环境" required><el-input v-model="credentialForm.environmentCode" /></el-form-item><el-form-item label="类型" required><el-select v-model="credentialForm.credentialType" style="width:100%"><el-option v-for="item in (['API_KEY','BASIC_AUTH','OAUTH2_CLIENT','BEARER_TOKEN','SIGNING_KEY','MTLS_CERTIFICATE'] as CredentialType[])" :key="item" :value="item" /></el-select></el-form-item></div><el-form-item label="Secret Reference" required><el-input v-model="credentialForm.secretUri" class="mono" /></el-form-item><p v-if="errorMessage" class="command-validation">{{ errorMessage }}</p></el-form><template #footer><el-button @click="credentialDialog = false">取消</el-button><el-button type="primary" :loading="createCredential.isPending.value" @click="submitCredential">创建引用</el-button></template></el-dialog>
    <el-dialog v-model="contractDialog" title="新增第三方接口" width="580px"><el-form label-position="top" @submit.prevent="submitContract"><el-form-item label="第三方提供方" required><el-select v-model="contractForm.providerId" filterable style="width:100%"><el-option v-for="item in providerItems" :key="item.id" :label="providerName(item.id)" :value="item.id" /></el-select></el-form-item><el-form-item label="接口编码" required><el-input v-model="contractForm.contractCode" placeholder="vendor.customer.lookup" /></el-form-item><el-form-item label="接口名称" required><el-input v-model="contractForm.contractName" /></el-form-item><el-form-item label="通信方式" required><el-select v-model="contractForm.protocolType" style="width:100%"><el-option v-for="item in (['HTTP','SOAP','GRAPHQL','GRPC'] as ProtocolType[])" :key="item" :value="item" /></el-select></el-form-item><el-form-item label="说明"><el-input v-model="contractForm.description" type="textarea" /></el-form-item><p v-if="errorMessage" class="command-validation">{{ errorMessage }}</p></el-form><template #footer><el-button @click="contractDialog = false">取消</el-button><el-button type="primary" :loading="createContract.isPending.value" @click="submitContract">创建</el-button></template></el-dialog>
    <el-dialog v-model="endpointDialog" title="新增调用地址版本" width="720px"><el-form label-position="top" @submit.prevent="submitEndpoint"><el-form-item label="第三方接口" required><el-select v-model="endpointForm.providerContractId" filterable style="width:100%"><el-option v-for="item in contractItems" :key="item.id" :label="contractName(item.id)" :value="item.id" /></el-select></el-form-item><div class="form-two-columns"><el-form-item label="调用地址编码" required><el-input v-model="endpointForm.endpointCode" placeholder="vendor.customer.lookup" /></el-form-item><el-form-item label="环境" required><el-input v-model="endpointForm.environmentCode" /></el-form-item></div><div class="endpoint-address-row"><el-form-item label="网络协议" required><el-select v-model="endpointForm.protocolScheme"><el-option v-for="item in (['HTTP','HTTPS'] as EndpointScheme[])" :key="item" :value="item" /></el-select></el-form-item><el-form-item label="请求方法" required><el-select v-model="endpointForm.httpMethod"><el-option v-for="item in (['GET','POST','PUT','PATCH','DELETE'] as EndpointMethod[])" :key="item" :value="item" /></el-select></el-form-item><el-form-item label="基础地址" required><el-input v-model="endpointForm.baseUrl" placeholder="https://api.vendor.example" /></el-form-item></div><el-form-item label="资源路径" required><el-input v-model="endpointForm.resourcePath" placeholder="/vendor/v1/query" /></el-form-item><el-form-item label="凭据引用"><el-select v-model="endpointForm.credentialRefId" clearable filterable style="width:100%"><el-option v-for="item in credentialItems" :key="item.id" :label="`${item.credentialCode} · ${item.environmentCode}`" :value="item.id" /></el-select></el-form-item><div class="form-three-columns"><el-form-item label="连接超时 ms"><el-input-number v-model="endpointForm.connectTimeoutMs" :min="1" /></el-form-item><el-form-item label="读取超时 ms"><el-input-number v-model="endpointForm.readTimeoutMs" :min="1" /></el-form-item><el-form-item label="总超时 ms"><el-input-number v-model="endpointForm.totalTimeoutMs" :min="1" /></el-form-item></div><p v-if="errorMessage" class="command-validation">{{ errorMessage }}</p></el-form><template #footer><el-button @click="endpointDialog = false">取消</el-button><el-button type="primary" :loading="createEndpoint.isPending.value" @click="submitEndpoint">创建版本</el-button></template></el-dialog>
  </section>
</template>
