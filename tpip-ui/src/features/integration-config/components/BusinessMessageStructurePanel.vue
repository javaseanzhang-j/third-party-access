<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { providerContractVersionApi, type ProviderContractVersionAsset } from '../api/providerContractVersionApi'
import { buildBusinessMessageExample, buildBusinessMessageSchema, countSchemaFields,
  type BusinessFieldType, type BusinessMessageField } from '../model/businessMessageSchema'

const props = defineProps<{ interfaceId: number | null; interfaceName: string }>()
const emit = defineEmits<{ publishedChange: [count: number] }>()
const dialog = ref(false); const detailDrawer = ref(false); const saving = ref(false)
const mode = ref<'FORM' | 'JSON'>('FORM'); const selectedVersion = ref<ProviderContractVersionAsset | null>(null)
const requestFields = ref<BusinessMessageField[]>([]); const responseFields = ref<BusinessMessageField[]>([])
const requestSchemaText = ref(defaultSchema()); const responseSchemaText = ref(defaultSchema())
const errorSchemaText = ref(''); const callbackSchemaText = ref(''); const examplesText = ref('{}')

const versions = useQuery({
  queryKey: computed(() => ['business-message-structures', props.interfaceId]),
  queryFn: ({ signal }) => providerContractVersionApi.versions(props.interfaceId!, signal),
  enabled: computed(() => Boolean(props.interfaceId))
})
const publishedCount = computed(() => (versions.data.value ?? []).filter(item => item.lifecycleStatus === 'PUBLISHED').length)
watch(publishedCount, value => emit('publishedChange', value), { immediate: true })

function newField(path = ''): BusinessMessageField {
  return { path, name: '', type: 'STRING', required: false, description: '', example: '' }
}
function openDialog(): void {
  requestFields.value = [newField('$.')]; responseFields.value = [newField('$.')]
  requestSchemaText.value = defaultSchema(); responseSchemaText.value = defaultSchema()
  errorSchemaText.value = ''; callbackSchemaText.value = ''; examplesText.value = '{}'; mode.value = 'FORM'; dialog.value = true
}
function removeField(target: 'request' | 'response', index: number): void {
  ;(target === 'request' ? requestFields.value : responseFields.value).splice(index, 1)
}
function addField(target: 'request' | 'response'): void {
  ;(target === 'request' ? requestFields.value : responseFields.value).push(newField('$.'))
}
function parse(value: string, label: string, nullable = false): unknown | null {
  if (nullable && !value.trim()) return null
  try { return JSON.parse(value) as unknown }
  catch { throw new Error(`${label}不是合法 JSON`) }
}
async function createVersion(): Promise<void> {
  if (!props.interfaceId) return
  saving.value = true
  try {
    let requestSchema: unknown | null; let responseSchema: unknown | null
    let errorSchema: unknown | null; let callbackSchema: unknown | null; let examples: unknown | null
    if (mode.value === 'FORM') {
      const request = requestFields.value.filter(item => item.path.trim() !== '$.' && item.path.trim())
      const response = responseFields.value.filter(item => item.path.trim() !== '$.' && item.path.trim())
      requestSchema = buildBusinessMessageSchema(request); responseSchema = buildBusinessMessageSchema(response)
      if (!requestSchema && !responseSchema) throw new Error('请求字段和返回字段至少配置一项')
      errorSchema = null; callbackSchema = null
      examples = { request: buildBusinessMessageExample(request), response: buildBusinessMessageExample(response) }
    } else {
      requestSchema = parse(requestSchemaText.value, '请求结构', true)
      responseSchema = parse(responseSchemaText.value, '返回结构', true)
      errorSchema = parse(errorSchemaText.value, '错误结构', true)
      callbackSchema = parse(callbackSchemaText.value, '回调结构', true)
      examples = parse(examplesText.value, '报文样例', true)
    }
    await providerContractVersionApi.create(props.interfaceId, {
      requestSchema, responseSchema, errorSchema, callbackSchema, examples
    })
    await versions.refetch(); dialog.value = false; ElMessage.success('报文结构已保存为新草稿版本')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存报文结构失败') }
  finally { saving.value = false }
}
async function publish(value: unknown): Promise<void> {
  const item = value as ProviderContractVersionAsset
  saving.value = true
  try {
    await providerContractVersionApi.publish(item.providerContractId, item.id)
    await versions.refetch(); ElMessage.success('报文结构版本已发布')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '发布失败') }
  finally { saving.value = false }
}
function showDetail(value: unknown): void { selectedVersion.value = value as ProviderContractVersionAsset; detailDrawer.value = true }
function pretty(value: unknown): string { return value == null ? '未配置' : JSON.stringify(value, null, 2) }
function defaultSchema(): string { return '{\n  "type": "object",\n  "properties": {},\n  "additionalProperties": false\n}' }
function typeText(value: BusinessFieldType): string {
  return { STRING: '文本', NUMBER: '数字', BOOLEAN: '是/否', OBJECT: '对象', ARRAY: '数组' }[value]
}
</script>

<template>
  <div class="business-message-panel">
    <div class="business-pane-heading"><div><h4>第三方请求与返回报文</h4>
      <p>这里记录第三方接口真实使用的字段。版本发布后不可修改，接口升级时新增版本。</p></div>
      <el-button type="primary" class="create-action" :disabled="!interfaceId" @click="openDialog">新增报文结构</el-button></div>
    <el-alert v-if="publishedCount > 1" type="info" :closable="false" title="多个已发布版本表示可追溯的历史协议，不代表同时执行；具体实现发布时会冻结其中一个版本。" />
    <el-empty v-if="!interfaceId" description="请先在“第三方接口”中选择接口" />
    <el-table v-else v-loading="versions.isPending.value" :data="versions.data.value ?? []" size="small" empty-text="当前接口还没有报文结构">
      <el-table-column label="版本" width="120"><template #default="scope"><strong>{{ scope.row.semanticVersion }}</strong><small class="table-secondary">第 {{ scope.row.versionNo }} 次修订</small></template></el-table-column>
      <el-table-column label="请求字段" width="100"><template #default="scope">{{ countSchemaFields(scope.row.requestSchema) }} 个</template></el-table-column>
      <el-table-column label="返回字段" width="100"><template #default="scope">{{ countSchemaFields(scope.row.responseSchema) }} 个</template></el-table-column>
      <el-table-column label="包含内容" min-width="190"><template #default="scope"><el-tag v-if="scope.row.requestSchema" size="small">请求</el-tag><el-tag v-if="scope.row.responseSchema" size="small" type="success">返回</el-tag><el-tag v-if="scope.row.errorSchema" size="small" type="danger">错误</el-tag><el-tag v-if="scope.row.callbackSchema" size="small" type="warning">回调</el-tag></template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ scope.row.lifecycleStatus === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="150"><template #default="scope"><el-button link @click="showDetail(scope.row)">查看结构</el-button><el-button v-if="scope.row.lifecycleStatus === 'DRAFT'" link type="primary" :loading="saving" @click="publish(scope.row)">发布</el-button></template></el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="新增第三方报文结构" width="1040px" class="message-structure-dialog">
      <el-alert type="info" :closable="false" title="版本号由平台自动生成。普通接口使用字段表单；嵌套数组、组合类型等复杂结构可以切换高级 JSON。" />
      <el-radio-group v-model="mode" class="message-mode-switch"><el-radio-button value="FORM">业务字段表单</el-radio-button><el-radio-button value="JSON">高级 JSON Schema</el-radio-button></el-radio-group>
      <template v-if="mode === 'FORM'">
        <div v-for="(group, groupIndex) in [{ title:'请求字段', target:'request' as const, items:requestFields },{ title:'返回字段', target:'response' as const, items:responseFields }]" :key="group.target" class="message-field-group">
          <div class="message-field-heading"><div><strong>{{ group.title }}</strong><small>{{ groupIndex === 0 ? '业务系统发给第三方' : '第三方返回给平台' }}</small></div><el-button @click="addField(group.target)">增加字段</el-button></div>
          <div v-for="(field,index) in group.items" :key="index" class="message-field-row">
            <el-input v-model="field.name" placeholder="中文含义，如手机号码" />
            <el-input v-model="field.path" placeholder="JSONPath，如 $.mobile" />
            <el-select v-model="field.type"><el-option v-for="item in ['STRING','NUMBER','BOOLEAN','OBJECT','ARRAY'] as BusinessFieldType[]" :key="item" :label="typeText(item)" :value="item" /></el-select>
            <el-input v-model="field.example" placeholder="示例值" />
            <el-input v-model="field.description" placeholder="字段说明（可选）" />
            <el-checkbox v-model="field.required">必填</el-checkbox>
            <el-button link type="danger" @click="removeField(group.target,index)">删除</el-button>
          </div>
        </div>
      </template>
      <el-form v-else label-position="top" class="dialog-form"><div class="schema-two-columns"><el-form-item label="请求结构 JSON Schema"><el-input v-model="requestSchemaText" type="textarea" :rows="12" class="schema-editor" /></el-form-item><el-form-item label="返回结构 JSON Schema"><el-input v-model="responseSchemaText" type="textarea" :rows="12" class="schema-editor" /></el-form-item><el-form-item label="错误结构（可选）"><el-input v-model="errorSchemaText" type="textarea" :rows="7" class="schema-editor" /></el-form-item><el-form-item label="回调结构（可选）"><el-input v-model="callbackSchemaText" type="textarea" :rows="7" class="schema-editor" /></el-form-item></div><el-form-item label="报文样例 JSON"><el-input v-model="examplesText" type="textarea" :rows="6" class="schema-editor" /></el-form-item></el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="createVersion">保存草稿</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailDrawer" title="报文结构详情" size="720px"><template v-if="selectedVersion"><div class="message-version-summary"><strong>{{ interfaceName }} · {{ selectedVersion.semanticVersion }}</strong><el-tag :type="selectedVersion.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ selectedVersion.lifecycleStatus === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></div><el-tabs><el-tab-pane label="请求结构"><pre class="message-json-preview">{{ pretty(selectedVersion.requestSchema) }}</pre></el-tab-pane><el-tab-pane label="返回结构"><pre class="message-json-preview">{{ pretty(selectedVersion.responseSchema) }}</pre></el-tab-pane><el-tab-pane label="错误/回调"><h4>错误结构</h4><pre class="message-json-preview">{{ pretty(selectedVersion.errorSchema) }}</pre><h4>回调结构</h4><pre class="message-json-preview">{{ pretty(selectedVersion.callbackSchema) }}</pre></el-tab-pane><el-tab-pane label="样例"><pre class="message-json-preview">{{ pretty(selectedVersion.examples) }}</pre></el-tab-pane></el-tabs></template></el-drawer>
  </div>
</template>
