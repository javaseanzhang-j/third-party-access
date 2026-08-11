<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import BusinessContractFieldEditor from '../components/BusinessContractFieldEditor.vue'
import McpLocalTestConsole from '../components/McpLocalTestConsole.vue'
import { canonicalAssetApi, type OperationAsset } from '../api/canonicalAssetApi'
import { mcpToolAssetApi, type McpConfirmationMode, type McpToolDetail,
  type McpContractImpact, type McpContractImpactLevel, type McpToolSummary,
  type McpToolValidationReport, type McpToolVersion } from '../api/mcpToolAssetApi'
import { buildBusinessMessageSchema, businessMessageFieldsFromSchema,
  type BusinessMessageField } from '../model/businessMessageSchema'

const queryClient = useQueryClient()
const keyword = ref('')
const wizardVisible = ref(false)
const wizardStep = ref(0)
const wizardMode = ref<'CREATE' | 'REVISION'>('CREATE')
const revisionToolId = ref<number | null>(null)
const selectedDetail = ref<McpToolDetail | null>(null)
const busy = ref(false)
const contractLoading = ref(false)
const errorMessage = ref('')
const requestFields = ref<BusinessMessageField[]>([])
const responseFields = ref<BusinessMessageField[]>([])
const validationReports = reactive<Record<number, McpToolValidationReport>>({})
const impactReports = reactive<Record<number, McpContractImpact>>({})
const impactLoading = reactive<Record<number, boolean>>({})
const clientGuideVisible = ref(false)
const testConsoleVisible = ref(false)
const mcpEndpoint = computed(() => `http://${window.location.hostname || '127.0.0.1'}:18083/mcp`)
const clientConfig = computed(() => JSON.stringify({ mcpServers: { 'tpip-local': { url: mcpEndpoint.value } } }, null, 2))

const toolsQuery = useQuery({ queryKey: ['mcp-tool-assets'], queryFn: ({ signal }) => mcpToolAssetApi.tools(signal) })
const operationsQuery = useQuery({ queryKey: ['canonical-operations'], queryFn: ({ signal }) => canonicalAssetApi.operations(undefined, signal) })
const tools = computed(() => toolsQuery.data.value ?? [])
const operations = computed(() => (operationsQuery.data.value?.items ?? []).filter(item => item.status === 'ACTIVE'))
const filteredTools = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!search) return tools.value
  return tools.value.filter(item => [item.tool.displayName, item.tool.toolName, item.serviceName, item.serviceCode]
    .some(value => value.toLowerCase().includes(search)))
})
const metrics = computed(() => ({
  total: tools.value.length,
  published: tools.value.filter(item => item.latestPublishedVersion).length,
  draft: tools.value.filter(item => item.latestVersion?.lifecycleStatus === 'DRAFT').length,
  serviceCount: new Set(tools.value.map(item => item.serviceCode)).size
}))

const form = reactive({
  operationId: 0, toolName: '', displayName: '', description: '', ownerCode: '', fixedScenario: '',
  readOnly: false, destructive: false, idempotent: false, openWorld: true,
  confirmationMode: 'NONE' as McpConfirmationMode
})

watch(() => form.destructive, value => {
  if (value && form.confirmationMode === 'NONE') form.confirmationMode = 'REQUIRED'
})

function apiMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : error instanceof Error ? error.message : '操作失败，请确认 Control Plane 状态。'
}
function emptySchema(): Record<string, unknown> { return { type: 'object', properties: {}, additionalProperties: false } }
function fieldsSchema(fields: BusinessMessageField[]): Record<string, unknown> { return buildBusinessMessageSchema(fields) ?? emptySchema() }
function resetForm(): void {
  Object.assign(form, { operationId: 0, toolName: '', displayName: '', description: '', ownerCode: '',
    fixedScenario: '', readOnly: false, destructive: false, idempotent: false, openWorld: true,
    confirmationMode: 'NONE' as McpConfirmationMode })
  requestFields.value = []
  responseFields.value = []
  errorMessage.value = ''
  wizardStep.value = 0
  revisionToolId.value = null
}
function openCreate(): void { resetForm(); wizardMode.value = 'CREATE'; wizardVisible.value = true }
function openRevision(value: unknown): void {
  const item = value as McpToolSummary
  resetForm(); wizardMode.value = 'REVISION'; revisionToolId.value = item.tool.id
  Object.assign(form, { operationId: item.tool.operationId, toolName: item.tool.toolName,
    displayName: item.tool.displayName, description: item.latestVersion?.description ?? item.tool.description,
    ownerCode: item.tool.ownerCode, fixedScenario: item.latestVersion?.fixedScenario ?? '',
    readOnly: item.latestVersion?.readOnly ?? false, destructive: item.latestVersion?.destructive ?? false,
    idempotent: item.latestVersion?.idempotent ?? false, openWorld: item.latestVersion?.openWorld ?? true,
    confirmationMode: item.latestVersion?.confirmationMode ?? 'NONE' })
  if (item.latestVersion) {
    requestFields.value = businessMessageFieldsFromSchema(JSON.parse(item.latestVersion.inputSchema))
    responseFields.value = item.latestVersion.outputSchema
      ? businessMessageFieldsFromSchema(JSON.parse(item.latestVersion.outputSchema)) : []
  }
  wizardVisible.value = true
}
function operationLabel(item: OperationAsset): string { return `${item.operationName} · ${item.operationCode}` }
function selectedOperation(): OperationAsset | undefined { return operations.value.find(item => item.id === form.operationId) }

async function operationChanged(): Promise<void> {
  if (wizardMode.value !== 'CREATE') return
  const operation = selectedOperation()
  if (!operation) return
  form.toolName = operation.operationCode.replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 64)
  form.displayName = operation.operationName
  form.description = `允许AI助手受控使用“${operation.operationName}”业务能力，具体第三方厂商由TPIP路由决定。`
  form.ownerCode = operation.ownerCode
  form.idempotent = ['IDEMPOTENT', 'IDEMPOTENT_WITH_KEY'].includes(operation.idempotencyClass)
  await loadPublishedContracts(operation.id)
}

async function loadPublishedContracts(operationId: number): Promise<void> {
  contractLoading.value = true
  try {
    const contracts = (await canonicalAssetApi.contracts(operationId)).items.filter(item => item.status === 'ACTIVE')
    const load = async (kind: 'REQUEST' | 'RESPONSE'): Promise<BusinessMessageField[]> => {
      const contract = contracts.find(item => item.contractKind === kind)
      if (!contract) return []
      const versions = await canonicalAssetApi.versions(contract.id)
      const published = versions.filter(item => item.lifecycleStatus === 'PUBLISHED')
        .sort((left, right) => right.versionNo - left.versionNo)[0]
      return published ? businessMessageFieldsFromSchema(published.schemaDocument) : []
    }
    const [request, response] = await Promise.all([load('REQUEST'), load('RESPONSE')])
    requestFields.value = request
    responseFields.value = response
    if (!request.length) ElMessage.info('该服务没有已发布的请求契约，请补充工具输入字段。')
  } catch (error) { errorMessage.value = apiMessage(error) }
  finally { contractLoading.value = false }
}

function validateStep(): boolean {
  errorMessage.value = ''
  if (wizardStep.value === 0 && (!form.operationId || !form.toolName.trim() || !form.displayName.trim()
      || !form.description.trim() || !form.ownerCode.trim())) {
    errorMessage.value = '请选择业务标准服务，并完整填写工具名称、用途和负责人。'; return false
  }
  if (wizardStep.value === 2 && form.destructive && form.confirmationMode === 'NONE') {
    errorMessage.value = '可能产生不可恢复影响的工具必须要求调用确认。'; return false
  }
  try { if (wizardStep.value === 1) { fieldsSchema(requestFields.value); fieldsSchema(responseFields.value) } }
  catch (error) { errorMessage.value = apiMessage(error); return false }
  return true
}
function nextStep(): void { if (validateStep()) wizardStep.value = Math.min(3, wizardStep.value + 1) }

async function submit(): Promise<void> {
  if (!validateStep()) return
  busy.value = true; errorMessage.value = ''
  try {
    let toolId = revisionToolId.value
    if (wizardMode.value === 'CREATE') {
      const detail = await mcpToolAssetApi.createTool({ operationId: form.operationId,
        toolName: form.toolName.trim(), displayName: form.displayName.trim(),
        description: form.description.trim(), ownerCode: form.ownerCode.trim() })
      toolId = detail.summary.tool.id
    }
    if (!toolId) throw new Error('工具身份创建失败。')
    const version = await mcpToolAssetApi.createVersion(toolId, {
      title: form.displayName.trim(), description: form.description.trim(),
      fixedScenario: form.fixedScenario.trim() || null, inputSchema: fieldsSchema(requestFields.value),
      outputSchema: responseFields.value.length ? fieldsSchema(responseFields.value) : null,
      readOnly: form.readOnly, destructive: form.destructive, idempotent: form.idempotent,
      openWorld: form.openWorld, confirmationMode: form.confirmationMode
    })
    const report = await mcpToolAssetApi.validateVersion(toolId, version.id)
    validationReports[version.id] = report
    wizardVisible.value = false
    await reloadAndOpen(toolId)
    report.ready ? ElMessage.success('工具草稿已创建并通过发布前检查')
      : ElMessage.warning('工具草稿已保存，但仍有问题需要处理')
  } catch (error) { errorMessage.value = apiMessage(error) }
  finally { busy.value = false }
}

async function reloadAndOpen(toolId?: number): Promise<void> {
  await queryClient.invalidateQueries({ queryKey: ['mcp-tool-assets'] })
  if (toolId) selectedDetail.value = await mcpToolAssetApi.tool(toolId)
}
async function openDetail(value: unknown): Promise<void> {
  const item = value as McpToolSummary
  try { selectedDetail.value = await mcpToolAssetApi.tool(item.tool.id) }
  catch (error) { ElMessage.error(apiMessage(error)) }
}
async function validateVersion(version: McpToolVersion): Promise<void> {
  if (!selectedDetail.value) return
  try {
    const report = await mcpToolAssetApi.validateVersion(selectedDetail.value.summary.tool.id, version.id)
    validationReports[version.id] = report
    report.ready ? ElMessage.success('发布前检查通过') : ElMessage.warning(report.issues.join('；'))
  } catch (error) { ElMessage.error(apiMessage(error)) }
}
async function loadContractImpact(version: McpToolVersion): Promise<void> {
  if (!selectedDetail.value) return
  impactLoading[version.id] = true
  try { impactReports[version.id] = await mcpToolAssetApi.contractImpact(selectedDetail.value.summary.tool.id, version.id) }
  catch (error) { ElMessage.error(apiMessage(error)) }
  finally { impactLoading[version.id] = false }
}
function impactText(level: McpContractImpactLevel): string {
  return { CURRENT: '与最新业务契约一致', ADDITIVE: '发现可兼容新增', BREAKING: '存在不兼容变化', UNAVAILABLE: '暂时无法分析' }[level]
}
function impactType(level: McpContractImpactLevel): 'success' | 'warning' | 'error' | 'info' {
  return { CURRENT: 'success', ADDITIVE: 'warning', BREAKING: 'error', UNAVAILABLE: 'info' }[level] as 'success' | 'warning' | 'error' | 'info'
}
async function copyClientConfig(): Promise<void> {
  try { await navigator.clipboard.writeText(clientConfig.value); ElMessage.success('客户端配置已复制') }
  catch { ElMessage.warning('浏览器未允许复制，请手动选择配置内容') }
}
async function publishVersion(version: McpToolVersion): Promise<void> {
  if (!selectedDetail.value) return
  try {
    await ElMessageBox.confirm('发布后该版本内容不可修改。确认开放给已授权的AI客户端？', '确认发布工具版本',
      { confirmButtonText: '确认发布', cancelButtonText: '暂不发布', type: 'warning' })
    busy.value = true
    await mcpToolAssetApi.publishVersion(selectedDetail.value.summary.tool.id, version.id)
    await reloadAndOpen(selectedDetail.value.summary.tool.id)
    ElMessage.success('工具版本已发布；MCP服务重启后加载最新快照')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiMessage(error))
  } finally { busy.value = false }
}
function statusText(value: unknown): string {
  const item = value as McpToolSummary
  if (item.latestVersion?.lifecycleStatus === 'DRAFT') return item.latestPublishedVersion ? '有新草稿' : '待发布'
  return item.latestPublishedVersion ? '已开放' : '未配置'
}
function statusType(value: unknown): 'success' | 'warning' | 'info' {
  const item = value as McpToolSummary
  return item.latestVersion?.lifecycleStatus === 'DRAFT' ? 'warning' : item.latestPublishedVersion ? 'success' : 'info'
}
function confirmationText(value: McpConfirmationMode): string {
  return { NONE: '无需额外确认', REQUIRED: '按风险要求确认', ALWAYS: '每次调用都确认' }[value]
}
</script>

<template>
  <section class="mcp-workbench">
    <div class="page-heading">
      <div><h2>AI 工具开放</h2><p>把已经稳定运行的业务标准服务，按授权开放给 AI 助手使用。</p></div>
      <div class="heading-actions"><el-button size="large" @click="testConsoleVisible = true">本地调用测试</el-button><el-button size="large" @click="clientGuideVisible = true">客户端接入</el-button><el-button class="mcp-create-button" type="primary" size="large" @click="openCreate">＋ 开放新的 AI 工具</el-button></div>
    </div>
    <el-alert type="info" :closable="false" show-icon title="AI 只看到业务工具，不会看到阿里云、腾讯云、华为云等具体通道；厂商选择、故障切换和字段转换仍由 TPIP 处理。" />
    <div class="metric-strip mcp-metrics">
      <div class="metric"><span>工具总数</span><strong>{{ metrics.total }}</strong></div>
      <div class="metric"><span>已开放</span><strong>{{ metrics.published }}</strong></div>
      <div class="metric"><span>待处理草稿</span><strong>{{ metrics.draft }}</strong></div>
      <div class="metric"><span>覆盖业务服务</span><strong>{{ metrics.serviceCount }}</strong></div>
    </div>
    <div class="surface mcp-tool-list">
      <div class="filter-bar"><el-input v-model="keyword" clearable class="grow" placeholder="按工具名称、业务服务或调用编码搜索" /><el-button @click="toolsQuery.refetch()">刷新</el-button></div>
      <AsyncStatePanel :loading="toolsQuery.isPending.value" :error="toolsQuery.isError.value" error-title="AI工具读取失败" @retry="toolsQuery.refetch()">
        <el-empty v-if="!filteredTools.length" description="尚未开放AI工具；请先选择一个业务标准服务" />
        <el-table v-else :data="filteredTools" row-key="tool.id" @row-click="openDetail">
          <el-table-column label="AI工具" min-width="260"><template #default="{ row }"><strong>{{ row.tool.displayName }}</strong><div class="subtle">{{ row.tool.description }}</div><code class="mono">{{ row.tool.toolName }}</code></template></el-table-column>
          <el-table-column label="使用的业务服务" min-width="250"><template #default="{ row }"><strong>{{ row.serviceName }}</strong><div class="subtle mono">{{ row.serviceCode }}</div></template></el-table-column>
          <el-table-column label="当前版本" width="120"><template #default="{ row }"><span v-if="row.latestVersion">第 {{ row.latestVersion.versionNo }} 版</span><span v-else>尚无版本</span></template></el-table-column>
          <el-table-column label="开放状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row)">{{ statusText(row) }}</el-tag></template></el-table-column>
          <el-table-column prop="tool.ownerCode" label="负责人" width="140" />
          <el-table-column label="操作" width="170"><template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">查看</el-button><el-button link @click.stop="openRevision(row)">创建新版本</el-button></template></el-table-column>
        </el-table>
      </AsyncStatePanel>
    </div>

    <el-dialog v-model="wizardVisible" :title="wizardMode === 'CREATE' ? '开放新的 AI 工具' : '创建工具新版本'" width="1040px" destroy-on-close>
      <el-steps :active="wizardStep" finish-status="success" align-center class="mcp-steps"><el-step title="选择业务服务" /><el-step title="确认业务字段" /><el-step title="设置使用限制" /><el-step title="确认并保存" /></el-steps>
      <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon closable @close="errorMessage = ''" />
      <div v-if="wizardStep === 0" class="wizard-pane">
        <div class="wizard-copy"><strong>这个工具要帮助 AI 完成什么业务？</strong><span>先选择稳定的业务标准服务。第三方厂商和接口不在这里选择。</span></div>
        <el-form label-position="top"><el-form-item label="业务标准服务" required><el-select v-model="form.operationId" filterable :disabled="wizardMode === 'REVISION'" style="width:100%" @change="operationChanged"><el-option v-for="item in operations" :key="item.id" :label="operationLabel(item)" :value="item.id" /></el-select></el-form-item>
          <div class="form-two-columns"><el-form-item label="用户看到的工具名称" required><el-input v-model="form.displayName" placeholder="例如：发送业务短信" /></el-form-item><el-form-item label="工具调用编码（系统使用）" required><el-input v-model="form.toolName" :disabled="wizardMode === 'REVISION'" placeholder="例如：send_business_sms" /><small>创建后保持稳定，AI客户端使用该编码调用。</small></el-form-item></div>
          <el-form-item label="工具用途和边界" required><el-input v-model="form.description" type="textarea" :rows="4" placeholder="说明能做什么、不能做什么，以及返回什么结果" /></el-form-item><el-form-item label="负责人" required><el-input v-model="form.ownerCode" /></el-form-item></el-form>
      </div>
      <div v-else-if="wizardStep === 1" class="wizard-pane"><div class="wizard-copy"><strong>AI需要提交哪些业务字段？</strong><span>平台会优先带入已发布的业务标准契约，你只需核对中文含义。这里不填写第三方原始报文。</span></div><el-skeleton v-if="contractLoading" :rows="6" animated /><template v-else><BusinessContractFieldEditor v-model="requestFields" title="工具输入字段" description="AI调用工具时需要提供的业务信息" path-example="例如 $.mobile" /><BusinessContractFieldEditor v-model="responseFields" title="工具返回字段" description="AI调用成功后能够读取的标准结果；可保持为空" path-example="例如 $.messageId" /></template></div>
      <div v-else-if="wizardStep === 2" class="wizard-pane"><div class="wizard-copy"><strong>这个工具可以怎样使用？</strong><span>这些说明会同时提供给AI客户端；平台服务授权仍是最终边界。</span></div><div class="risk-grid"><label><el-switch v-model="form.readOnly" /><span><strong>只读取信息</strong><small>不会改变第三方系统中的数据</small></span></label><label><el-switch v-model="form.idempotent" /><span><strong>允许安全重试</strong><small>相同请求重复执行不会产生额外业务结果</small></span></label><label><el-switch v-model="form.destructive" /><span><strong>可能产生不可恢复影响</strong><small>例如删除、覆盖或发起真实交易</small></span></label><label><el-switch v-model="form.openWorld" /><span><strong>会访问外部系统</strong><small>调用TPIP之外的第三方服务</small></span></label></div><div class="form-two-columns risk-options"><el-form-item label="固定业务场景（可选）"><el-input v-model="form.fixedScenario" placeholder="例如 verification-code" /><small>填写后，AI不能改成其他场景。</small></el-form-item><el-form-item label="调用前确认方式" required><el-select v-model="form.confirmationMode"><el-option label="无需额外确认" value="NONE" /><el-option label="按风险要求确认" value="REQUIRED" /><el-option label="每次调用都确认" value="ALWAYS" /></el-select></el-form-item></div></div>
      <div v-else class="wizard-pane"><div class="wizard-copy"><strong>确认开放内容</strong><span>保存后先形成草稿并自动执行发布前检查，不会直接开放给AI。</span></div><div class="review-grid"><div><span>工具</span><strong>{{ form.displayName }}</strong><code>{{ form.toolName }}</code></div><div><span>业务服务</span><strong>{{ selectedOperation()?.operationName }}</strong><code>{{ selectedOperation()?.operationCode }}</code></div><div><span>业务字段</span><strong>输入 {{ requestFields.length }} 个 · 返回 {{ responseFields.length }} 个</strong><small>来源于业务标准契约并经过人工确认</small></div><div><span>使用限制</span><strong>{{ form.fixedScenario || '不限制固定场景' }}</strong><small>{{ confirmationText(form.confirmationMode) }}</small></div></div><el-alert type="warning" :closable="false" show-icon title="草稿验证通过后，仍需在工具详情中明确点击“发布”，已发布版本不可原地修改。" /></div>
      <template #footer><el-button @click="wizardVisible = false">取消</el-button><el-button v-if="wizardStep > 0" @click="wizardStep--">上一步</el-button><el-button v-if="wizardStep < 3" type="primary" @click="nextStep">下一步</el-button><el-button v-else type="primary" :loading="busy" @click="submit">保存草稿并检查</el-button></template>
    </el-dialog>

    <el-drawer :model-value="selectedDetail !== null" title="AI工具详情" size="900px" @close="selectedDetail = null">
      <template v-if="selectedDetail">
        <div class="mcp-detail-hero"><span>{{ selectedDetail.summary.serviceName }}</span><h3>{{ selectedDetail.summary.tool.displayName }}</h3><code>{{ selectedDetail.summary.tool.toolName }} → {{ selectedDetail.summary.serviceCode }}</code><p>{{ selectedDetail.summary.tool.description }}</p></div>
        <div class="asset-toolbar"><div><strong>工具版本</strong><span>历史发布版本只读保留；只有最新已发布版本进入MCP快照。</span></div><el-button type="primary" @click="openRevision(selectedDetail.summary)">创建新版本</el-button></div>
        <el-empty v-if="!selectedDetail.versions.length" description="尚未创建工具版本" />
        <div v-for="version in selectedDetail.versions" :key="version.id" class="version-card">
          <div class="version-card__head"><div><el-tag :type="version.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">第 {{ version.versionNo }} 版 · {{ version.lifecycleStatus === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag><strong>{{ version.title }}</strong></div><div class="version-actions"><el-button :loading="impactLoading[version.id]" @click="loadContractImpact(version)">检查契约变化</el-button><template v-if="version.lifecycleStatus === 'DRAFT'"><el-button @click="validateVersion(version)">发布前检查</el-button><el-button type="primary" :loading="busy" @click="publishVersion(version)">发布</el-button></template></div></div>
          <p>{{ version.description }}</p>
          <div class="version-facts"><span>固定场景：{{ version.fixedScenario || '无' }}</span><span>确认方式：{{ confirmationText(version.confirmationMode) }}</span><span>外部访问：{{ version.openWorld ? '是' : '否' }}</span><span>安全重试：{{ version.idempotent ? '是' : '否' }}</span></div>
          <el-alert v-if="validationReports[version.id]" :type="validationReports[version.id]!.ready ? 'success' : 'warning'" :closable="false" :title="validationReports[version.id]!.ready ? '发布前检查通过' : validationReports[version.id]!.issues.join('；')" />
          <div v-if="impactReports[version.id]" class="impact-result">
            <el-alert :type="impactType(impactReports[version.id]!.level)" :closable="false" :title="impactText(impactReports[version.id]!.level)" />
            <div v-if="impactReports[version.id]!.requestContract || impactReports[version.id]!.responseContract" class="impact-sources"><span v-if="impactReports[version.id]!.requestContract">请求契约：{{ impactReports[version.id]!.requestContract!.contractName }} {{ impactReports[version.id]!.requestContract!.semanticVersion }}</span><span v-if="impactReports[version.id]!.responseContract">返回契约：{{ impactReports[version.id]!.responseContract!.contractName }} {{ impactReports[version.id]!.responseContract!.semanticVersion }}</span></div>
            <ul v-if="impactReports[version.id]!.changes.length"><li v-for="change in impactReports[version.id]!.changes" :key="`${change.direction}-${change.path}-${change.message}`"><el-tag size="small" :type="change.level === 'BREAKING' ? 'danger' : 'warning'">{{ change.level === 'BREAKING' ? '需创建新版本' : '可按需同步' }}</el-tag><code>{{ change.direction }} {{ change.path }}</code><span>{{ change.message }}</span></li></ul>
            <p v-for="issue in impactReports[version.id]!.issues" :key="issue" class="impact-issue">{{ issue }}</p>
          </div>
          <small class="checksum">内容校验值 {{ version.contentChecksum }}</small>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="clientGuideVisible" title="连接本地 AI 客户端" width="min(720px, calc(100vw - 32px))">
      <el-steps :active="3" finish-status="success" simple><el-step title="发布工具" /><el-step title="授权业务服务" /><el-step title="启动 MCP 服务" /></el-steps>
      <div class="client-guide"><el-alert type="info" :closable="false" title="客户端只连接 MCP 服务，不需要保存 TPIP 的 appKey 或 Secret；本机 MCP 服务负责身份映射和签名。" /><label>本地 MCP 地址</label><code>{{ mcpEndpoint }}</code><label>通用 Streamable HTTP 配置参考</label><el-input :model-value="clientConfig" type="textarea" :rows="7" readonly /><el-button type="primary" @click="copyClientConfig">复制配置</el-button><p>不同客户端的配置文件名称可能不同，但服务器名称和 URL 含义相同。发布工具或调整授权后，可等待自动刷新，或在“本地调用测试”中立即刷新目录，无需重启 MCP 服务。</p></div>
      <template #footer><el-button @click="clientGuideVisible = false">关闭</el-button></template>
    </el-dialog>
    <McpLocalTestConsole v-model="testConsoleVisible" />
  </section>
</template>

<style scoped>
.mcp-workbench>.el-alert{margin-bottom:16px}.heading-actions{display:flex;gap:10px}.mcp-create-button{min-width:190px;color:#fff!important;font-weight:750}.mcp-metrics{margin-top:16px}.mcp-tool-list{overflow:hidden}.mcp-tool-list :deep(.el-table__row){cursor:pointer}.mcp-tool-list strong,.mcp-tool-list code{display:block}.mcp-tool-list code{margin-top:5px;color:#28715d;font-size:10px}.mcp-steps{margin:4px 0 24px}.wizard-pane{min-height:430px;padding:20px 8px 4px}.wizard-copy{margin-bottom:20px;padding:16px 18px;border-left:4px solid var(--mint);background:#eff7f3}.wizard-copy strong,.wizard-copy span{display:block}.wizard-copy strong{font-size:17px}.wizard-copy span{margin-top:6px;color:var(--muted);font-size:11px}.wizard-pane small{display:block;margin-top:6px;color:var(--muted);font-size:10px}.risk-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.risk-grid label{padding:18px;display:flex;gap:14px;align-items:flex-start;border:1px solid var(--line);background:#f8faf9}.risk-grid label span,.risk-grid strong,.risk-grid small{display:block}.risk-grid small{margin-top:5px}.risk-options{margin-top:18px}.review-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:18px}.review-grid>div{min-height:112px;padding:17px;border:1px solid var(--line);background:#f8faf9}.review-grid span,.review-grid strong,.review-grid code,.review-grid small{display:block}.review-grid span{color:var(--muted);font-size:10px}.review-grid strong{margin-top:8px}.review-grid code,.review-grid small{margin-top:6px;color:#39705f;font-size:10px}.mcp-detail-hero{margin-bottom:18px;padding:22px;border-left:5px solid var(--mint);background:#edf7f2}.mcp-detail-hero span,.mcp-detail-hero code{color:#39705f;font-size:10px}.mcp-detail-hero h3{margin:7px 0;font-size:24px}.mcp-detail-hero p{margin:12px 0 0;color:var(--muted)}.version-card{margin:12px 0;padding:18px;border:1px solid var(--line);background:#fbfcfb}.version-card__head{display:flex;justify-content:space-between;gap:15px}.version-card__head strong{display:block;margin-top:8px}.version-actions{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}.version-card>p{color:var(--muted);font-size:12px}.version-facts{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.version-facts span{padding:6px 9px;background:#edf4f1;color:#3e665a;font-size:10px}.impact-result{margin-top:12px}.impact-sources{display:flex;gap:12px;margin:10px 0;color:#587068;font-size:10px}.impact-result ul{padding:0;list-style:none}.impact-result li{display:grid;grid-template-columns:105px 150px 1fr;gap:8px;align-items:center;padding:8px 0;border-bottom:1px solid var(--line);font-size:11px}.impact-result li code{color:#286e5b}.impact-issue{color:#8a6423;font-size:11px}.client-guide{display:flex;flex-direction:column;gap:12px;padding:22px 4px 4px}.client-guide label{margin-top:5px;font-weight:700}.client-guide>code{padding:12px;background:#edf4f1;color:#286e5b}.client-guide .el-button{align-self:flex-start}.client-guide p{color:var(--muted);font-size:11px;line-height:1.7}.checksum{display:block;margin-top:12px;color:#819089;font:9px/1.5 ui-monospace,monospace;overflow-wrap:anywhere}@media(max-width:1100px){.risk-grid,.review-grid{grid-template-columns:1fr}.impact-result li{grid-template-columns:1fr}.heading-actions{flex-wrap:wrap}}
</style>
