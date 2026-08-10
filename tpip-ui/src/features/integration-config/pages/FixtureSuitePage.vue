<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { fixtureSuiteApi, type CreateFixtureCaseInput, type FixtureSuiteVersionAsset } from '../api/fixtureSuiteApi'
import { mappingAssetApi } from '../api/mappingAssetApi'

interface CaseDraft {
  caseCode: string; caseName: string; executionMode: 'MAPPING' | 'REMOTE_CALL'
  direction: 'OUTBOUND_REQUEST' | 'INBOUND_RESPONSE'; sourceText: string; expectedText: string
  expectedSuccess: boolean; expectedDiagnosticCode: string; assertionsText: string
}

const selectedBindingId = ref(0); const selectedSuiteId = ref(0); const errorMessage = ref('')
const suiteDialog = ref(false); const versionDialog = ref(false); const detailDialog = ref(false)
const detailVersion = ref<FixtureSuiteVersionAsset | null>(null)
const suiteCode = ref(''); const suiteName = ref(''); const suiteDescription = ref('')
const cases = ref<CaseDraft[]>([])

const bindings = useQuery({ queryKey: ['integration-bindings'], queryFn: ({ signal }) => mappingAssetApi.bindings(signal) })
const bindingItems = computed(() => bindings.data.value?.items.filter(item => item.status === 'ACTIVE') ?? [])
const suites = useQuery({ queryKey: computed(() => ['fixture-suites', selectedBindingId.value]), queryFn: ({ signal }) => fixtureSuiteApi.suites(selectedBindingId.value, signal), enabled: computed(() => selectedBindingId.value > 0) })
const suiteItems = computed(() => suites.data.value ?? [])
const versions = useQuery({ queryKey: computed(() => ['fixture-suite-versions', selectedSuiteId.value]), queryFn: ({ signal }) => fixtureSuiteApi.versions(selectedSuiteId.value, signal), enabled: computed(() => selectedSuiteId.value > 0) })
const currentBinding = computed(() => bindingItems.value.find(item => item.id === selectedBindingId.value) ?? null)

watch(bindingItems, items => { if (items.length && !items.some(item => item.id === selectedBindingId.value)) selectedBindingId.value = items[0]!.id }, { immediate: true })
watch(suiteItems, items => { if (!items.some(item => item.id === selectedSuiteId.value)) selectedSuiteId.value = items[0]?.id ?? 0 }, { immediate: true })

function apiMessage(error: Error): string { return error instanceof ApiError ? error.message : error.message || '请求失败，请确认 Control Plane 状态。' }
function parseObject(text: string, field: string, nullable = false): Record<string, unknown> | null {
  if (nullable && !text.trim()) return null
  let value: unknown
  try { value = JSON.parse(text) as unknown } catch { throw new Error(`${field} 不是合法 JSON。`) }
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error(`${field} 必须是 JSON Object。`)
  return value as Record<string, unknown>
}
function parseAssertions(text: string): unknown[] | null {
  if (!text.trim()) return null
  let value: unknown
  try { value = JSON.parse(text) as unknown } catch { throw new Error('Assertions 不是合法 JSON。') }
  if (!Array.isArray(value) || !value.length) throw new Error('Assertions 必须是非空 JSON Array。')
  return value
}
function newCase(index = cases.value.length): CaseDraft {
  return { caseCode: `mapping.case-${index + 1}`, caseName: '映射成功场景', executionMode: 'MAPPING',
    direction: 'OUTBOUND_REQUEST', sourceText: '{\n  "customerId": "C1001"\n}',
    expectedText: '{\n  "customer_id": "C1001"\n}', expectedSuccess: true, expectedDiagnosticCode: '',
    assertionsText: '[\n  {"code":"mapping-success","type":"SUCCESS","expected":true}\n]' }
}
function openSuite(): void {
  suiteCode.value = currentBinding.value ? `${currentBinding.value.bindingCode}.fixture` : ''
  suiteName.value = currentBinding.value ? `${currentBinding.value.bindingName} 验证套件` : ''
  suiteDescription.value = '覆盖第三方接入的请求/响应 Mapping 与受控断言。'; errorMessage.value = ''; suiteDialog.value = true
}
function openVersion(): void { cases.value = [newCase(0)]; errorMessage.value = ''; versionDialog.value = true }
function removeCase(index: number): void { if (cases.value.length > 1) cases.value.splice(index, 1) }
function modeChanged(item: CaseDraft): void {
  if (item.executionMode === 'REMOTE_CALL') {
    item.direction = 'OUTBOUND_REQUEST'
    item.assertionsText = '[\n  {"code":"http-ok","type":"HTTP_STATUS","operator":"EQUALS","expected":200}\n]'
  }
}
const createSuite = useMutation({ mutationFn: () => fixtureSuiteApi.createSuite({ bindingId: selectedBindingId.value, suiteCode: suiteCode.value.trim(), suiteName: suiteName.value.trim(), description: suiteDescription.value.trim() || null }), onSuccess: value => { suiteDialog.value = false; void suites.refetch().then(() => { selectedSuiteId.value = value.id }); ElMessage.success('FixtureSuite 已创建') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const createVersion = useMutation({ mutationFn: (input: CreateFixtureCaseInput[]) => fixtureSuiteApi.createVersion(selectedSuiteId.value, input), onSuccess: () => { versionDialog.value = false; void versions.refetch(); ElMessage.success('FixtureSuiteVersion 已创建并计算 checksum') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const publish = useMutation({ mutationFn: (row: FixtureSuiteVersionAsset) => fixtureSuiteApi.publish(row.suiteId, row.id), onSuccess: () => { void versions.refetch(); ElMessage.success('FixtureSuiteVersion 已发布') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })

function submitVersion(): void {
  errorMessage.value = ''
  try {
    const input = cases.value.map((item, index): CreateFixtureCaseInput => ({
      caseCode: item.caseCode.trim(), caseName: item.caseName.trim(), caseOrder: index,
      executionMode: item.executionMode, direction: item.direction,
      source: parseObject(item.sourceText, `Case ${index + 1} Source`)! ,
      expected: parseObject(item.expectedText, `Case ${index + 1} Expected`, true),
      expectedSuccess: item.expectedSuccess, expectedDiagnosticCode: item.expectedDiagnosticCode.trim() || null,
      assertions: parseAssertions(item.assertionsText)
    }))
    if (input.some(item => !item.caseCode || !item.caseName)) throw new Error('每个 Case 都必须填写编码和名称。')
    if (new Set(input.map(item => item.caseCode)).size !== input.length) throw new Error('Case 编码不能重复。')
    createVersion.mutate(input)
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'Fixture 配置解析失败。' }
}
function showDetail(row: FixtureSuiteVersionAsset): void { detailVersion.value = row; detailDialog.value = true }
function requestDetail(row: unknown): void { showDetail(row as FixtureSuiteVersionAsset) }
function requestPublish(row: unknown): void { publish.mutate(row as FixtureSuiteVersionAsset) }
function bindingLabel(id: number): string { const item = bindingItems.value.find(value => value.id === id); return item ? `${item.bindingName} · ${item.bindingCode}` : `Binding #${id}` }
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>FixtureSuite 验证资产</h2><p>用声明式 Fixture 和受控断言验证 Mapping、契约与可选远程调用，发布后作为 Workspace 验证输入。</p></div><el-tag effect="plain">Fixture Assertion Profile 1.0</el-tag></div>
    <el-alert v-if="errorMessage" class="command-notice" type="error" :title="errorMessage" closable @close="errorMessage = ''" show-icon />
    <el-alert class="command-notice" type="info" :closable="false" title="优先使用 MAPPING 模式。REMOTE_CALL 默认关闭，且受幂等、环境、Host、Port、HTTPS、超时、大小和调用次数门禁约束。" show-icon />
    <div class="surface fixture-suite-panel">
      <div class="asset-toolbar canonical-version-toolbar"><div><strong>Binding 与 FixtureSuite</strong><span>FixtureSuite 归属于一个 ACTIVE Binding；已发布版本不可修改。</span></div><div><el-select v-model="selectedBindingId" filterable><el-option v-for="item in bindingItems" :key="item.id" :label="bindingLabel(item.id)" :value="item.id" /></el-select><el-button type="primary" :disabled="!selectedBindingId" @click="openSuite">新建 Suite</el-button></div></div>
      <div class="fixture-suite-layout">
        <div class="fixture-suite-list"><div class="section-title"><h3>Suite</h3><span class="subtle">{{ suiteItems.length }} 项</span></div><AsyncStatePanel :loading="suites.isPending.value" :error="suites.isError.value" error-title="FixtureSuite 读取失败" @retry="suites.refetch()"><el-empty v-if="!suiteItems.length" description="当前 Binding 尚无 FixtureSuite" /><button v-for="item in suiteItems" v-else :key="item.id" :class="{ active: item.id === selectedSuiteId }" @click="selectedSuiteId = item.id"><span><strong>{{ item.suiteName }}</strong><small>{{ item.suiteCode }}</small></span><el-tag size="small" type="success">{{ item.status }}</el-tag></button></AsyncStatePanel></div>
        <div class="fixture-version-list"><div class="section-title"><h3>FixtureSuiteVersion</h3><el-button class="create-action" type="primary" :disabled="!selectedSuiteId" @click="openVersion">创建版本</el-button></div><AsyncStatePanel :loading="versions.isPending.value" :error="versions.isError.value" error-title="FixtureSuiteVersion 读取失败" @retry="versions.refetch()"><el-empty v-if="!versions.data.value?.length" description="尚未创建版本" /><el-table v-else :data="versions.data.value"><el-table-column label="Revision" width="95"><template #default="{ row }"><strong>{{ row.versionNo }}</strong></template></el-table-column><el-table-column label="Cases" width="85"><template #default="{ row }">{{ row.cases.length }}</template></el-table-column><el-table-column label="Checksum" min-width="260"><template #default="{ row }"><span class="mono checksum-cell">{{ row.contentChecksum }}</span></template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ row.lifecycleStatus }}</el-tag></template></el-table-column><el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="primary" @click="requestDetail(row)">查看</el-button><el-button v-if="row.lifecycleStatus === 'DRAFT'" link type="primary" @click="requestPublish(row)">发布</el-button></template></el-table-column></el-table></AsyncStatePanel></div>
      </div>
    </div>

    <el-dialog v-model="suiteDialog" title="新建 FixtureSuite" width="660px"><el-form class="dialog-form" label-position="top"><el-form-item label="Suite Code" required><el-input v-model="suiteCode" /></el-form-item><el-form-item label="Suite Name" required><el-input v-model="suiteName" /></el-form-item><el-form-item label="说明"><el-input v-model="suiteDescription" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="suiteDialog = false">取消</el-button><el-button type="primary" :disabled="!suiteCode.trim() || !suiteName.trim()" :loading="createSuite.isPending.value" @click="createSuite.mutate()">创建稳定资产</el-button></template></el-dialog>

    <el-dialog v-model="versionDialog" title="创建 FixtureSuiteVersion" width="1120px" top="4vh"><el-alert type="warning" :closable="false" title="创建后 Case 内容和 checksum 不可修改；修正测试数据必须创建新版本。" show-icon /><div class="fixture-editor-heading"><span>Fixture Cases（{{ cases.length }}）</span><el-button @click="cases.push(newCase())">添加 Case</el-button></div><div class="fixture-case-editor"><article v-for="(item, index) in cases" :key="index" class="fixture-case-card"><div class="fixture-case-title"><strong>Case {{ index + 1 }}</strong><el-button link type="danger" :disabled="cases.length === 1" @click="removeCase(index)">移除</el-button></div><el-form label-position="top"><div class="form-two-columns"><el-form-item label="Case Code" required><el-input v-model="item.caseCode" /></el-form-item><el-form-item label="Case Name" required><el-input v-model="item.caseName" /></el-form-item><el-form-item label="Execution Mode"><el-select v-model="item.executionMode" @change="modeChanged(item)"><el-option label="MAPPING（推荐）" value="MAPPING" /><el-option label="REMOTE_CALL（受门禁）" value="REMOTE_CALL" /></el-select></el-form-item><el-form-item label="Direction"><el-select v-model="item.direction" :disabled="item.executionMode === 'REMOTE_CALL'"><el-option label="OUTBOUND_REQUEST" value="OUTBOUND_REQUEST" /><el-option label="INBOUND_RESPONSE" value="INBOUND_RESPONSE" /></el-select></el-form-item><el-form-item label="Expected Success"><el-switch v-model="item.expectedSuccess" /></el-form-item><el-form-item label="Expected Diagnostic Code（兼容字段）"><el-input v-model="item.expectedDiagnosticCode" clearable /></el-form-item></div><div class="fixture-json-grid"><el-form-item label="Source JSON Object" required><el-input v-model="item.sourceText" type="textarea" :rows="8" class="schema-editor" /></el-form-item><el-form-item label="Expected JSON Object（可选兼容字段）"><el-input v-model="item.expectedText" type="textarea" :rows="8" class="schema-editor" /></el-form-item><el-form-item label="FAP Assertions JSON Array"><el-input v-model="item.assertionsText" type="textarea" :rows="8" class="schema-editor" /></el-form-item></div></el-form></article></div><template #footer><el-button @click="versionDialog = false">取消</el-button><el-button type="primary" :loading="createVersion.isPending.value" @click="submitVersion">校验并创建 DRAFT</el-button></template></el-dialog>

    <el-dialog v-model="detailDialog" title="FixtureSuiteVersion 内容" width="980px"><template v-if="detailVersion"><div class="fact-grid fixture-version-facts"><div class="fact"><label>Revision</label><div>{{ detailVersion.versionNo }}</div></div><div class="fact"><label>状态</label><div>{{ detailVersion.lifecycleStatus }}</div></div><div class="fact"><label>Cases</label><div>{{ detailVersion.cases.length }}</div></div><div class="fact"><label>Checksum</label><div class="mono">{{ detailVersion.contentChecksum }}</div></div></div><pre class="plan-preview">{{ JSON.stringify(detailVersion.cases, null, 2) }}</pre></template><template #footer><el-button @click="detailDialog = false">关闭</el-button></template></el-dialog>
  </section>
</template>
