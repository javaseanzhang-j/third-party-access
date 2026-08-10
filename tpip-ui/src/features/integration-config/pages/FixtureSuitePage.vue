<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { fixtureSuiteApi, type CreateFixtureCaseInput, type FixtureSuiteVersionAsset } from '../api/fixtureSuiteApi'
import { mappingAssetApi } from '../api/mappingAssetApi'
import { createFixtureCaseInput, type FixtureCaseDraft } from '../model/fixtureEditorModel'

const selectedBindingId = ref(0); const selectedSuiteId = ref(0); const errorMessage = ref('')
const suiteDialog = ref(false); const versionDialog = ref(false); const detailDialog = ref(false)
const detailVersion = ref<FixtureSuiteVersionAsset | null>(null)
const suiteCode = ref(''); const suiteName = ref(''); const suiteDescription = ref('')
const cases = ref<FixtureCaseDraft[]>([])

const bindings = useQuery({ queryKey: ['integration-bindings'], queryFn: ({ signal }) => mappingAssetApi.bindings(signal) })
const bindingItems = computed(() => bindings.data.value?.items.filter(item => item.status === 'ACTIVE') ?? [])
const suites = useQuery({ queryKey: computed(() => ['fixture-suites', selectedBindingId.value]), queryFn: ({ signal }) => fixtureSuiteApi.suites(selectedBindingId.value, signal), enabled: computed(() => selectedBindingId.value > 0) })
const suiteItems = computed(() => suites.data.value ?? [])
const versions = useQuery({ queryKey: computed(() => ['fixture-suite-versions', selectedSuiteId.value]), queryFn: ({ signal }) => fixtureSuiteApi.versions(selectedSuiteId.value, signal), enabled: computed(() => selectedSuiteId.value > 0) })
const currentBinding = computed(() => bindingItems.value.find(item => item.id === selectedBindingId.value) ?? null)

watch(bindingItems, items => { if (items.length && !items.some(item => item.id === selectedBindingId.value)) selectedBindingId.value = items[0]!.id }, { immediate: true })
watch(suiteItems, items => { if (!items.some(item => item.id === selectedSuiteId.value)) selectedSuiteId.value = items[0]?.id ?? 0 }, { immediate: true })

function apiMessage(error: Error): string { return error instanceof ApiError ? error.message : error.message || '请求失败，请确认 Control Plane 状态。' }
function newCase(index = cases.value.length): FixtureCaseDraft {
  return { caseCode: `mapping.case-${index + 1}`, caseName: '映射成功场景', executionMode: 'MAPPING',
    direction: 'OUTBOUND_REQUEST', validationMode: 'FULL_MATCH', sourceText: '{}',
    expectedText: '{}', expectedSuccess: true, expectedDiagnosticCode: '',
    assertionsText: '[\n  {"code":"mapping-success","type":"SUCCESS","expected":true}\n]' }
}
function openSuite(): void {
  suiteCode.value = currentBinding.value ? `${currentBinding.value.bindingCode}.fixture` : ''
  suiteName.value = currentBinding.value ? `${currentBinding.value.bindingName} 验证套件` : ''
  suiteDescription.value = '覆盖第三方接入的请求/响应 Mapping 与受控断言。'; errorMessage.value = ''; suiteDialog.value = true
}
function openVersion(): void { cases.value = [newCase(0)]; errorMessage.value = ''; versionDialog.value = true }
function removeCase(index: number): void { if (cases.value.length > 1) cases.value.splice(index, 1) }
function modeChanged(item: FixtureCaseDraft): void {
  if (item.executionMode === 'REMOTE_CALL') {
    item.direction = 'OUTBOUND_REQUEST'
    item.validationMode = 'RULES'
    item.assertionsText = '[\n  {"code":"http-ok","type":"HTTP_STATUS","operator":"EQUALS","expected":200}\n]'
  }
}
const createSuite = useMutation({ mutationFn: () => fixtureSuiteApi.createSuite({ bindingId: selectedBindingId.value, suiteCode: suiteCode.value.trim(), suiteName: suiteName.value.trim(), description: suiteDescription.value.trim() || null }), onSuccess: value => { suiteDialog.value = false; void suites.refetch().then(() => { selectedSuiteId.value = value.id }); ElMessage.success('FixtureSuite 已创建') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const createVersion = useMutation({ mutationFn: (input: CreateFixtureCaseInput[]) => fixtureSuiteApi.createVersion(selectedSuiteId.value, input), onSuccess: () => { versionDialog.value = false; void versions.refetch(); ElMessage.success('FixtureSuiteVersion 已创建并计算 checksum') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const publish = useMutation({ mutationFn: (row: FixtureSuiteVersionAsset) => fixtureSuiteApi.publish(row.suiteId, row.id), onSuccess: () => { void versions.refetch(); ElMessage.success('FixtureSuiteVersion 已发布') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })

function submitVersion(): void {
  errorMessage.value = ''
  try {
    const input = cases.value.map((item, index) => createFixtureCaseInput(item, index))
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

    <el-dialog v-model="versionDialog" title="创建验证用例版本" width="1040px" top="4vh"><el-alert type="warning" :closable="false" title="创建后用例内容和摘要不可修改；修正测试数据必须创建新版本。" show-icon /><div class="fixture-editor-heading"><span>验证场景（{{ cases.length }}）</span><el-button @click="cases.push(newCase())">添加场景</el-button></div><div class="fixture-case-editor"><article v-for="(item, index) in cases" :key="index" class="fixture-case-card"><div class="fixture-case-title"><strong>场景 {{ index + 1 }}</strong><el-button link type="danger" :disabled="cases.length === 1" @click="removeCase(index)">移除</el-button></div><el-form label-position="top">
      <div class="form-two-columns"><el-form-item label="场景编码" required><el-input v-model="item.caseCode" /></el-form-item><el-form-item label="场景名称" required><el-input v-model="item.caseName" /></el-form-item><el-form-item label="验证方式"><el-select v-model="item.executionMode" @change="modeChanged(item)"><el-option label="字段映射验证（推荐）" value="MAPPING" /><el-option label="受控接口调用" value="REMOTE_CALL" /></el-select></el-form-item><el-form-item label="映射方向"><el-select v-model="item.direction" :disabled="item.executionMode === 'REMOTE_CALL'"><el-option label="业务请求 → 第三方请求" value="OUTBOUND_REQUEST" /><el-option label="第三方返回 → 业务返回" value="INBOUND_RESPONSE" /></el-select></el-form-item></div>
      <el-form-item label="结果验证方式"><el-radio-group v-model="item.validationMode" :disabled="item.executionMode === 'REMOTE_CALL'"><el-radio-button value="FULL_MATCH">完整结果比较</el-radio-button><el-radio-button value="RULES">按验证规则检查</el-radio-button></el-radio-group><div class="fixture-mode-help">完整结果比较会比较整个输出 JSON；规则检查只验证指定字段。两种方式互斥，不会再出现期望 JSON 被忽略的情况。</div></el-form-item>
      <div class="fixture-json-grid fixture-json-grid--two"><el-form-item label="输入报文" required><el-input v-model="item.sourceText" type="textarea" :rows="9" class="schema-editor" /></el-form-item><template v-if="item.validationMode === 'FULL_MATCH' && item.executionMode === 'MAPPING'"><div><el-form-item label="预期执行成功"><el-switch v-model="item.expectedSuccess" /></el-form-item><el-form-item v-if="item.expectedSuccess" label="期望输出报文（完整匹配）" required><el-input v-model="item.expectedText" type="textarea" :rows="7" class="schema-editor" /></el-form-item><el-form-item v-else label="预期错误码"><el-input v-model="item.expectedDiagnosticCode" clearable placeholder="可选；留空表示只要求执行失败" /></el-form-item></div></template><el-form-item v-else label="验证规则"><el-input v-model="item.assertionsText" type="textarea" :rows="9" class="schema-editor" /><div class="fixture-mode-help">高级规则格式支持成功状态、JSONPath、JSON Schema、HTTP 状态和响应头；受控接口调用必须使用验证规则。</div></el-form-item></div>
    </el-form></article></div><template #footer><el-button @click="versionDialog = false">取消</el-button><el-button type="primary" :loading="createVersion.isPending.value" @click="submitVersion">校验并创建草稿</el-button></template></el-dialog>

    <el-dialog v-model="detailDialog" title="FixtureSuiteVersion 内容" width="980px"><template v-if="detailVersion"><div class="fact-grid fixture-version-facts"><div class="fact"><label>Revision</label><div>{{ detailVersion.versionNo }}</div></div><div class="fact"><label>状态</label><div>{{ detailVersion.lifecycleStatus }}</div></div><div class="fact"><label>Cases</label><div>{{ detailVersion.cases.length }}</div></div><div class="fact"><label>Checksum</label><div class="mono">{{ detailVersion.contentChecksum }}</div></div></div><pre class="plan-preview">{{ JSON.stringify(detailVersion.cases, null, 2) }}</pre></template><template #footer><el-button @click="detailDialog = false">关闭</el-button></template></el-dialog>
  </section>
</template>
