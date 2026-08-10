<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { canonicalAssetApi, type CanonicalContractVersionAsset, type CompatibilityMode,
  type ContractKind, type CreateCanonicalContractInput, type CreateCapabilityInput,
  type CreateDomainInput, type CreateOperationInput, type DataClassification,
  type IdempotencyClass, type InvocationMode } from '../api/canonicalAssetApi'

const queryClient = useQueryClient()
const activeTab = ref('domains'); const errorMessage = ref('')
const domainDialog = ref(false); const capabilityDialog = ref(false); const operationDialog = ref(false)
const contractDialog = ref(false); const versionDialog = ref(false); const selectedContractId = ref(0)

const domains = useQuery({ queryKey: ['canonical-domains'], queryFn: ({ signal }) => canonicalAssetApi.domains(signal) })
const capabilities = useQuery({ queryKey: ['canonical-capabilities'], queryFn: ({ signal }) => canonicalAssetApi.capabilities(undefined, signal) })
const operations = useQuery({ queryKey: ['canonical-operations'], queryFn: ({ signal }) => canonicalAssetApi.operations(undefined, signal) })
const contracts = useQuery({ queryKey: ['canonical-contracts'], queryFn: ({ signal }) => canonicalAssetApi.contracts(undefined, signal) })
const domainItems = computed(() => domains.data.value?.items ?? [])
const capabilityItems = computed(() => capabilities.data.value?.items ?? [])
const operationItems = computed(() => operations.data.value?.items ?? [])
const contractItems = computed(() => contracts.data.value?.items ?? [])
const versions = useQuery({
  queryKey: computed(() => ['canonical-contract-versions', selectedContractId.value]),
  queryFn: ({ signal }) => canonicalAssetApi.versions(selectedContractId.value, signal),
  enabled: computed(() => selectedContractId.value > 0)
})
watch(contractItems, items => {
  if (items.length && !items.some(item => item.id === selectedContractId.value)) selectedContractId.value = items[0]!.id
}, { immediate: true })

const domainForm = reactive<CreateDomainInput>({ domainCode: '', domainName: '', description: null, ownerCode: '' })
const capabilityForm = reactive<CreateCapabilityInput>({ domainId: 0, capabilityCode: '', capabilityName: '', description: null, ownerCode: '' })
const operationForm = reactive<CreateOperationInput>({ capabilityId: 0, operationCode: '', operationName: '', description: null,
  invocationMode: 'SYNC', idempotencyClass: 'IDEMPOTENT', dataClassification: 'INTERNAL', ownerCode: '' })
const contractForm = reactive<CreateCanonicalContractInput>({ operationId: 0, contractCode: '', contractName: '', contractKind: 'REQUEST', description: null })
const versionForm = reactive({ compatibilityMode: 'BACKWARD' as CompatibilityMode,
  schemaText: '{\n  "type": "object",\n  "properties": {},\n  "additionalProperties": false\n}', exampleText: '{}' })

function message(error: Error): string { return error instanceof ApiError ? error.message : '请求失败，请确认 Control Plane 与数据库状态。' }
function invalidate(): void {
  for (const key of ['canonical-domains', 'canonical-capabilities', 'canonical-operations', 'canonical-contracts']) {
    void queryClient.invalidateQueries({ queryKey: [key] })
  }
}
const createDomain = useMutation({ mutationFn: (input: CreateDomainInput) => canonicalAssetApi.createDomain(input), onSuccess: () => { domainDialog.value = false; invalidate(); ElMessage.success('Domain 已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const createCapability = useMutation({ mutationFn: (input: CreateCapabilityInput) => canonicalAssetApi.createCapability(input), onSuccess: () => { capabilityDialog.value = false; invalidate(); ElMessage.success('Capability 已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const createOperation = useMutation({ mutationFn: (input: CreateOperationInput) => canonicalAssetApi.createOperation(input), onSuccess: () => { operationDialog.value = false; invalidate(); ElMessage.success('Operation 已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const createContract = useMutation({ mutationFn: (input: CreateCanonicalContractInput) => canonicalAssetApi.createContract(input), onSuccess: result => { contractDialog.value = false; selectedContractId.value = result.id; invalidate(); ElMessage.success('Canonical Contract 已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const createVersion = useMutation({ mutationFn: (input: { contractId: number; schema: Record<string, unknown>; example: unknown | null }) => canonicalAssetApi.createVersion(input.contractId, { schemaStandard: 'JSON_SCHEMA_2020_12', schemaDocument: input.schema, exampleDocument: input.example, compatibilityMode: versionForm.compatibilityMode }), onSuccess: () => { versionDialog.value = false; void versions.refetch(); ElMessage.success('标准契约版本已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const publishVersion = useMutation({ mutationFn: (version: CanonicalContractVersionAsset) => canonicalAssetApi.publishVersion(version.contractId, version.id), onSuccess: () => { void versions.refetch(); ElMessage.success('Contract Version 已发布') }, onError: (e: Error) => { errorMessage.value = message(e) } })
function requestPublish(row: unknown): void { publishVersion.mutate(row as CanonicalContractVersionAsset) }

function requireCode(code: string): boolean { return /^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$/.test(code) }
function clean(value: string | null): string | null { return value?.trim() || null }
function submitDomain(): void { errorMessage.value = ''; if (!requireCode(domainForm.domainCode) || !domainForm.domainName.trim() || !domainForm.ownerCode.trim()) { errorMessage.value = '请填写合法 Domain 编码、名称和负责人。'; return }; createDomain.mutate({ ...domainForm, description: clean(domainForm.description) }) }
function submitCapability(): void { errorMessage.value = ''; if (!capabilityForm.domainId || !requireCode(capabilityForm.capabilityCode) || !capabilityForm.capabilityName.trim() || !capabilityForm.ownerCode.trim()) { errorMessage.value = '请选择 Domain，并填写合法 Capability 编码、名称和负责人。'; return }; createCapability.mutate({ ...capabilityForm, description: clean(capabilityForm.description) }) }
function submitOperation(): void { errorMessage.value = ''; if (!operationForm.capabilityId || !requireCode(operationForm.operationCode) || !operationForm.operationName.trim() || !operationForm.ownerCode.trim()) { errorMessage.value = '请选择 Capability，并填写合法 Operation 编码、名称和负责人。'; return }; createOperation.mutate({ ...operationForm, description: clean(operationForm.description) }) }
function submitContract(): void { errorMessage.value = ''; if (!contractForm.operationId || !requireCode(contractForm.contractCode) || !contractForm.contractName.trim()) { errorMessage.value = '请选择 Operation，并填写合法 Contract 编码和名称。'; return }; createContract.mutate({ ...contractForm, description: clean(contractForm.description) }) }
function parseJson(value: string, field: string, nullable = false): unknown {
  if (nullable && !value.trim()) return null
  try { return JSON.parse(value) as unknown } catch { throw new Error(`${field} 不是合法 JSON。`) }
}
function submitVersion(): void {
  errorMessage.value = ''
  if (!selectedContractId.value) { errorMessage.value = '请选择标准契约。'; return }
  try {
    const schema = parseJson(versionForm.schemaText, 'Schema')
    if (!schema || Array.isArray(schema) || typeof schema !== 'object') throw new Error('Schema 顶层必须是 JSON Object。')
    const example = parseJson(versionForm.exampleText, 'Example', true)
    createVersion.mutate({ contractId: selectedContractId.value, schema: schema as Record<string, unknown>, example })
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'JSON 解析失败。' }
}
function domainName(id: number): string { const item = domainItems.value.find(value => value.id === id); return item ? `${item.domainName} · ${item.domainCode}` : `Domain #${id}` }
function capabilityName(id: number): string { const item = capabilityItems.value.find(value => value.id === id); return item ? `${item.capabilityName} · ${item.capabilityCode}` : `Capability #${id}` }
function operationName(id: number): string { const item = operationItems.value.find(value => value.id === id); return item ? `${item.operationName} · ${item.operationCode}` : `Operation #${id}` }
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>业务标准接口</h2><p>定义稳定的业务语义和标准 JSON 契约，隔离第三方字段与协议变化。</p></div><el-tag effect="plain">标准模型</el-tag></div>
    <el-alert v-if="errorMessage" class="command-notice" type="error" :title="errorMessage" closable @close="errorMessage = ''" show-icon />
    <el-alert class="command-notice" type="info" :closable="false" title="先定义业务语义，再配置第三方适配。契约版本号由平台自动生成，发布后不可修改。" show-icon />
    <div class="surface integration-asset-tabs canonical-tabs">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="业务域" name="domains">
          <div class="asset-toolbar"><div><strong>业务域</strong><span>按业务责任边界组织能力，不按第三方厂商划分。</span></div><el-button type="primary" @click="domainDialog = true">新增业务域</el-button></div>
          <AsyncStatePanel :loading="domains.isPending.value" :error="domains.isError.value" error-title="Domain 读取失败" @retry="domains.refetch()">
            <el-empty v-if="!domainItems.length" description="尚未创建 Domain" />
            <el-table v-else :data="domainItems" row-key="id"><el-table-column prop="domainName" label="名称" min-width="180" /><el-table-column label="编码" min-width="240"><template #default="{ row }"><span class="mono">{{ row.domainCode }}</span></template></el-table-column><el-table-column prop="ownerCode" label="负责人" width="150" /><el-table-column prop="status" label="状态" width="100" /></el-table>
          </AsyncStatePanel>
        </el-tab-pane>
        <el-tab-pane label="业务能力" name="capabilities">
          <div class="asset-toolbar"><div><strong>业务能力</strong><span>从属于 Domain，表达可长期复用的业务能力。</span></div><el-button type="primary" :disabled="!domainItems.length" @click="capabilityDialog = true">新增 Capability</el-button></div>
          <AsyncStatePanel :loading="capabilities.isPending.value" :error="capabilities.isError.value" error-title="Capability 读取失败" @retry="capabilities.refetch()">
            <el-empty v-if="!capabilityItems.length" description="尚未创建 Capability" />
            <el-table v-else :data="capabilityItems" row-key="id"><el-table-column label="Capability" min-width="260"><template #default="{ row }"><strong>{{ row.capabilityName }}</strong><div class="subtle mono">{{ row.capabilityCode }}</div></template></el-table-column><el-table-column label="Domain" min-width="230"><template #default="{ row }">{{ domainName(row.domainId) }}</template></el-table-column><el-table-column prop="ownerCode" label="负责人" width="140" /><el-table-column prop="status" label="状态" width="100" /></el-table>
          </AsyncStatePanel>
        </el-tab-pane>
        <el-tab-pane label="业务操作" name="operations">
          <div class="asset-toolbar"><div><strong>标准业务操作</strong><span>业务系统调用的稳定语义入口，不包含第三方 URL 和字段。</span></div><el-button type="primary" :disabled="!capabilityItems.length" @click="operationDialog = true">新增 Operation</el-button></div>
          <AsyncStatePanel :loading="operations.isPending.value" :error="operations.isError.value" error-title="Operation 读取失败" @retry="operations.refetch()">
            <el-empty v-if="!operationItems.length" description="尚未创建 Operation" />
            <el-table v-else :data="operationItems" row-key="id"><el-table-column label="Operation" min-width="260"><template #default="{ row }"><strong>{{ row.operationName }}</strong><div class="subtle mono">{{ row.operationCode }}</div></template></el-table-column><el-table-column label="Capability" min-width="220"><template #default="{ row }">{{ capabilityName(row.capabilityId) }}</template></el-table-column><el-table-column prop="invocationMode" label="调用" width="90" /><el-table-column prop="idempotencyClass" label="幂等" width="170" /><el-table-column prop="dataClassification" label="数据分级" width="120" /></el-table>
          </AsyncStatePanel>
        </el-tab-pane>
        <el-tab-pane label="标准契约" name="contracts">
          <div class="asset-toolbar"><div><strong>Canonical Contract 稳定身份</strong><span>按 REQUEST、RESPONSE、ERROR 或 EVENT 定义标准报文角色。</span></div><el-button type="primary" :disabled="!operationItems.length" @click="contractDialog = true">新增 Contract</el-button></div>
          <AsyncStatePanel :loading="contracts.isPending.value" :error="contracts.isError.value" error-title="Contract 读取失败" @retry="contracts.refetch()">
            <el-empty v-if="!contractItems.length" description="尚未创建 Canonical Contract" />
            <el-table v-else :data="contractItems" row-key="id"><el-table-column label="Contract" min-width="280"><template #default="{ row }"><strong>{{ row.contractName }}</strong><div class="subtle mono">{{ row.contractCode }}</div></template></el-table-column><el-table-column label="Operation" min-width="240"><template #default="{ row }">{{ operationName(row.operationId) }}</template></el-table-column><el-table-column prop="contractKind" label="类型" width="110" /><el-table-column prop="status" label="状态" width="100" /><el-table-column label="版本" width="100"><template #default="{ row }"><el-button link type="primary" @click="selectedContractId = row.id; activeTab = 'versions'">管理</el-button></template></el-table-column></el-table>
          </AsyncStatePanel>
        </el-tab-pane>
        <el-tab-pane label="契约版本" name="versions">
          <div class="asset-toolbar canonical-version-toolbar"><div><strong>不可变 JSON Schema 版本</strong><span>Schema 会在服务端规范化并生成 SHA-256 checksum。</span></div><div><el-select v-model="selectedContractId" filterable placeholder="选择 Contract"><el-option v-for="item in contractItems" :key="item.id" :label="`${item.contractName} · ${item.contractKind}`" :value="item.id" /></el-select><el-button type="primary" :disabled="!selectedContractId" @click="versionDialog = true">新增 Version</el-button></div></div>
          <AsyncStatePanel :loading="versions.isPending.value" :error="versions.isError.value" error-title="Contract Version 读取失败" @retry="versions.refetch()">
            <el-empty v-if="!selectedContractId || !versions.data.value?.length" description="该 Contract 尚未创建版本" />
            <el-table v-else :data="versions.data.value" row-key="id"><el-table-column label="版本" width="150"><template #default="{ row }"><strong>{{ row.semanticVersion }}</strong><div class="subtle">内部修订 {{ row.versionNo }}</div></template></el-table-column><el-table-column prop="schemaStandard" label="结构标准" width="210" /><el-table-column prop="compatibilityMode" label="兼容策略" width="130" /><el-table-column label="内容摘要" min-width="300"><template #default="{ row }"><span class="mono checksum-cell">{{ row.contentChecksum }}</span></template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ row.lifecycleStatus === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></template></el-table-column><el-table-column label="操作" width="90"><template #default="{ row }"><el-button v-if="row.lifecycleStatus === 'DRAFT'" link type="primary" :loading="publishVersion.isPending.value" @click="requestPublish(row)">发布</el-button></template></el-table-column></el-table>
          </AsyncStatePanel>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="domainDialog" title="新增 Domain" width="560px"><el-form label-position="top"><el-form-item label="Domain 编码" required><el-input v-model="domainForm.domainCode" placeholder="customer" /></el-form-item><el-form-item label="名称" required><el-input v-model="domainForm.domainName" /></el-form-item><el-form-item label="负责人" required><el-input v-model="domainForm.ownerCode" /></el-form-item><el-form-item label="说明"><el-input v-model="domainForm.description" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="domainDialog = false">取消</el-button><el-button type="primary" :loading="createDomain.isPending.value" @click="submitDomain">创建</el-button></template></el-dialog>
    <el-dialog v-model="capabilityDialog" title="新增 Capability" width="580px"><el-form label-position="top"><el-form-item label="Domain" required><el-select v-model="capabilityForm.domainId" filterable style="width:100%"><el-option v-for="item in domainItems" :key="item.id" :label="domainName(item.id)" :value="item.id" /></el-select></el-form-item><el-form-item label="Capability 编码" required><el-input v-model="capabilityForm.capabilityCode" placeholder="customer.profile" /></el-form-item><el-form-item label="名称" required><el-input v-model="capabilityForm.capabilityName" /></el-form-item><el-form-item label="负责人" required><el-input v-model="capabilityForm.ownerCode" /></el-form-item><el-form-item label="说明"><el-input v-model="capabilityForm.description" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="capabilityDialog = false">取消</el-button><el-button type="primary" :loading="createCapability.isPending.value" @click="submitCapability">创建</el-button></template></el-dialog>
    <el-dialog v-model="operationDialog" title="新增 Operation" width="700px"><el-form label-position="top"><el-form-item label="Capability" required><el-select v-model="operationForm.capabilityId" filterable style="width:100%"><el-option v-for="item in capabilityItems" :key="item.id" :label="capabilityName(item.id)" :value="item.id" /></el-select></el-form-item><div class="form-two-columns"><el-form-item label="Operation 编码" required><el-input v-model="operationForm.operationCode" placeholder="customer.lookup" /></el-form-item><el-form-item label="名称" required><el-input v-model="operationForm.operationName" /></el-form-item></div><div class="form-three-columns"><el-form-item label="调用模式"><el-select v-model="operationForm.invocationMode"><el-option v-for="item in (['SYNC','ASYNC','CALLBACK'] as InvocationMode[])" :key="item" :value="item" /></el-select></el-form-item><el-form-item label="幂等分类"><el-select v-model="operationForm.idempotencyClass"><el-option v-for="item in (['IDEMPOTENT','IDEMPOTENT_WITH_KEY','NON_IDEMPOTENT','UNKNOWN'] as IdempotencyClass[])" :key="item" :value="item" /></el-select></el-form-item><el-form-item label="数据分级"><el-select v-model="operationForm.dataClassification"><el-option v-for="item in (['PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED'] as DataClassification[])" :key="item" :value="item" /></el-select></el-form-item></div><el-form-item label="负责人" required><el-input v-model="operationForm.ownerCode" /></el-form-item><el-form-item label="说明"><el-input v-model="operationForm.description" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="operationDialog = false">取消</el-button><el-button type="primary" :loading="createOperation.isPending.value" @click="submitOperation">创建</el-button></template></el-dialog>
    <el-dialog v-model="contractDialog" title="新增 Canonical Contract" width="620px"><el-form label-position="top"><el-form-item label="Operation" required><el-select v-model="contractForm.operationId" filterable style="width:100%"><el-option v-for="item in operationItems" :key="item.id" :label="operationName(item.id)" :value="item.id" /></el-select></el-form-item><div class="form-two-columns"><el-form-item label="Contract 编码" required><el-input v-model="contractForm.contractCode" placeholder="customer.lookup.request" /></el-form-item><el-form-item label="类型" required><el-select v-model="contractForm.contractKind"><el-option v-for="item in (['REQUEST','RESPONSE','ERROR','EVENT'] as ContractKind[])" :key="item" :value="item" /></el-select></el-form-item></div><el-form-item label="名称" required><el-input v-model="contractForm.contractName" /></el-form-item><el-form-item label="说明"><el-input v-model="contractForm.description" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="contractDialog = false">取消</el-button><el-button type="primary" :loading="createContract.isPending.value" @click="submitContract">创建</el-button></template></el-dialog>
    <el-dialog v-model="versionDialog" title="新增标准契约版本" width="820px"><el-alert type="warning" :closable="false" title="版本号由平台自动生成，发布后不可修改。请先通过样例确认结构，再执行发布。" show-icon /><el-form class="dialog-form" label-position="top"><el-form-item label="兼容策略" required><el-select v-model="versionForm.compatibilityMode"><el-option label="不检查兼容性" value="NONE" /><el-option label="向后兼容" value="BACKWARD" /><el-option label="向前兼容" value="FORWARD" /><el-option label="双向兼容" value="FULL" /></el-select></el-form-item><el-form-item label="JSON Schema 2020-12" required><el-input v-model="versionForm.schemaText" type="textarea" :rows="12" class="schema-editor" /></el-form-item><el-form-item label="报文样例 JSON（可选）"><el-input v-model="versionForm.exampleText" type="textarea" :rows="6" class="schema-editor" /></el-form-item></el-form><template #footer><el-button @click="versionDialog = false">取消</el-button><el-button type="primary" :loading="createVersion.isPending.value" @click="submitVersion">创建草稿</el-button></template></el-dialog>
  </section>
</template>
