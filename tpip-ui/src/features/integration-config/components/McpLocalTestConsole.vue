<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ApiError } from '@/api/http'
import { mcpLocalApi, type McpLocalCallResult, type McpLocalStatus, type McpLocalTool } from '../api/mcpLocalApi'

const visible = defineModel<boolean>({ required: true })
const status = ref<McpLocalStatus | null>(null)
const tools = ref<McpLocalTool[]>([])
const selectedName = ref('')
const argumentsText = ref('{}')
const scenario = ref('')
const idempotencyKey = ref('')
const result = ref<McpLocalCallResult | null>(null)
const busy = ref(false)
const error = ref('')
const selectedTool = computed(() => tools.value.find(item => item.name === selectedName.value) ?? null)

watch(visible, value => { if (value) void load() })
watch(selectedName, () => {
  result.value = null
  if (selectedTool.value) argumentsText.value = JSON.stringify(example(selectedTool.value.inputSchema), null, 2)
})

function message(value: unknown): string {
  if (value instanceof ApiError && (value.status === 502 || value.status === 503)) {
    return '本地 MCP 服务不可用，请先启动 tpip-mcp-server-app（默认端口 18083）后重新连接'
  }
  return value instanceof ApiError ? value.message : value instanceof Error ? value.message : '本地 MCP 服务操作失败'
}
async function load(): Promise<void> {
  busy.value = true; error.value = ''
  try {
    const [nextStatus, nextTools] = await Promise.all([mcpLocalApi.status(), mcpLocalApi.tools()])
    status.value = nextStatus; tools.value = nextTools
    if (!nextTools.some(item => item.name === selectedName.value)) selectedName.value = nextTools[0]?.name ?? ''
  } catch (failure) { error.value = message(failure) }
  finally { busy.value = false }
}
async function refreshCatalog(): Promise<void> {
  busy.value = true; error.value = ''
  try { await mcpLocalApi.refresh(); await load(); ElMessage.success('MCP运行目录已刷新，客户端会收到工具列表变化通知') }
  catch (failure) { error.value = message(failure); busy.value = false }
}
async function callTool(): Promise<void> {
  if (!selectedTool.value) return
  error.value = ''; result.value = null
  let argumentsDocument: unknown
  try { argumentsDocument = JSON.parse(argumentsText.value) }
  catch { error.value = '调用参数不是有效的 JSON'; return }
  if (!argumentsDocument || typeof argumentsDocument !== 'object' || Array.isArray(argumentsDocument)) {
    error.value = '调用参数必须是 JSON 对象'; return
  }
  if (selectedTool.value.destructive || selectedTool.value.confirmationMode !== 'NONE') {
    try {
      await ElMessageBox.confirm('该工具要求调用确认。测试会经过真实 Runtime，并可能访问第三方系统，是否继续？',
        '确认执行本地测试', { type: 'warning', confirmButtonText: '确认调用', cancelButtonText: '取消' })
    } catch { return }
  }
  busy.value = true
  try {
    result.value = await mcpLocalApi.call(selectedTool.value.name,
      argumentsDocument as Record<string, unknown>, scenario.value, idempotencyKey.value)
  } catch (failure) { error.value = message(failure) }
  finally { busy.value = false }
}
function example(schema: Record<string, unknown>): Record<string, unknown> {
  const properties = schema.properties && typeof schema.properties === 'object'
    ? schema.properties as Record<string, Record<string, unknown>> : {}
  return Object.fromEntries(Object.entries(properties).map(([name, value]) => [name, exampleValue(value)]))
}
function exampleValue(schema: Record<string, unknown>): unknown {
  if (schema.example !== undefined) return schema.example
  if (schema.default !== undefined) return schema.default
  if (Array.isArray(schema.enum) && schema.enum.length) return schema.enum[0]
  if (schema.type === 'object') return example(schema)
  if (schema.type === 'array') return [exampleValue((schema.items as Record<string, unknown>) ?? { type: 'string' })]
  if (schema.type === 'boolean') return false
  if (schema.type === 'integer' || schema.type === 'number') return 0
  return ''
}
function format(value: unknown): string { return JSON.stringify(value, null, 2) }
</script>

<template>
  <el-dialog v-model="visible" title="本地 MCP 工具调用测试" width="min(1040px, calc(100vw - 32px))" destroy-on-close>
    <el-alert type="warning" :closable="false" show-icon title="这里执行的是真实授权调用，不是页面模拟。请求仍会经过服务授权、Runtime、路由、Mapping、Policy和审计。" />
    <el-alert v-if="error" type="error" :closable="false" :title="error" show-icon />
    <div v-if="status" class="runtime-status"><div><span>本地应用</span><strong>{{ status.applicationCode }}</strong></div><div><span>已加载资产</span><strong>{{ status.catalog.configuredToolCount }}</strong></div><div><span>当前可调用</span><strong>{{ status.catalog.exposedToolCount }}</strong></div><div><span>最近刷新</span><strong>{{ new Date(status.catalog.lastSuccessfulAt).toLocaleString() }}</strong></div><el-button :loading="busy" @click="refreshCatalog">立即刷新目录</el-button></div>
    <div v-else class="connection-actions"><span>连接本地 MCP 服务后，平台只会加载当前应用已授权的工具。</span><el-button :loading="busy" @click="load">重新连接</el-button></div>
    <div v-loading="busy" class="test-layout">
      <section class="tool-picker"><label>选择当前应用已授权的工具</label><el-select v-model="selectedName" filterable placeholder="暂无可调用工具"><el-option v-for="item in tools" :key="item.name" :label="`${item.title} · ${item.name}`" :value="item.name" /></el-select><template v-if="selectedTool"><h4>{{ selectedTool.title }}</h4><code>{{ selectedTool.name }} → {{ selectedTool.serviceCode }}</code><p>{{ selectedTool.description }}</p><div class="tool-flags"><el-tag>第 {{ selectedTool.versionNo }} 版</el-tag><el-tag v-if="selectedTool.fixedScenario" type="warning">固定场景 {{ selectedTool.fixedScenario }}</el-tag><el-tag v-if="selectedTool.destructive" type="danger">可能产生不可恢复影响</el-tag><el-tag v-if="selectedTool.openWorld" type="info">访问外部系统</el-tag></div><details><summary>查看工具输入结构</summary><pre>{{ format(selectedTool.inputSchema) }}</pre></details></template><el-empty v-else description="MCP服务未返回当前应用可调用的工具，请检查发布和服务授权" /></section>
      <section class="call-editor"><label>业务调用参数</label><el-input v-model="argumentsText" type="textarea" :rows="12" spellcheck="false" /><div class="optional-fields"><el-input v-model="scenario" placeholder="业务场景（可选）" /><el-input v-model="idempotencyKey" placeholder="幂等键（可选）" /></div><el-button type="primary" :disabled="!selectedTool" :loading="busy" @click="callTool">执行真实授权调用</el-button><div v-if="result" class="call-result" :class="result.success ? 'is-success' : 'is-error'"><strong>{{ result.success ? '调用成功' : '调用失败' }} · {{ result.resultCode }}</strong><span>requestId：{{ result.requestId }}</span><p>{{ result.message }}</p><pre>{{ format(result.structuredContent) }}</pre></div></section>
    </div>
    <template #footer><el-button @click="visible = false">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>
.el-alert{margin-bottom:12px}.runtime-status{display:grid;grid-template-columns:1.1fr .7fr .7fr 1.5fr auto;gap:8px;align-items:stretch;margin:14px 0}.runtime-status>div{padding:12px;background:#f0f5f3}.runtime-status span,.runtime-status strong{display:block}.runtime-status span{color:var(--muted);font-size:10px}.runtime-status strong{margin-top:6px;font-size:12px}.connection-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:14px 0;padding:12px 14px;background:#f0f5f3;color:var(--muted);font-size:11px}.test-layout{display:grid;grid-template-columns:.9fr 1.1fr;gap:14px;min-height:470px}.test-layout section{padding:18px;border:1px solid var(--line);background:#fbfcfb}.test-layout label{display:block;margin-bottom:9px;font-weight:700}.tool-picker .el-select{width:100%}.tool-picker h4{margin:20px 0 6px;font-size:18px}.tool-picker code{color:#26705c;font-size:10px}.tool-picker p{color:var(--muted);font-size:11px;line-height:1.7}.tool-flags{display:flex;gap:6px;flex-wrap:wrap}.tool-picker details{margin-top:16px}.tool-picker summary{cursor:pointer;color:#37695b;font-size:11px}.test-layout pre{max-height:220px;overflow:auto;padding:12px;background:#122a24;color:#d7f4e8;font:10px/1.6 ui-monospace,monospace}.optional-fields{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin:10px 0}.call-result{margin-top:14px;padding:14px;border-left:4px solid #4a9f7d;background:#edf7f3}.call-result.is-error{border-left-color:#c85961;background:#fff1f1}.call-result strong,.call-result span{display:block}.call-result span,.call-result p{margin-top:6px;font-size:10px}@media(max-width:900px){.runtime-status{grid-template-columns:1fr 1fr}.test-layout{grid-template-columns:1fr}.optional-fields{grid-template-columns:1fr}}@media(max-width:560px){.connection-actions{align-items:stretch;flex-direction:column}.connection-actions .el-button{margin:0;width:100%}}
</style>
