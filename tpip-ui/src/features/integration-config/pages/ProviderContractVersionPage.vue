<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { integrationAssetApi } from '../api/integrationAssetApi'
import { providerContractVersionApi, type ProviderContractVersionAsset } from '../api/providerContractVersionApi'

const errorMessage = ref(''); const dialog = ref(false); const selectedContractId = ref(0)
const contracts = useQuery({ queryKey: ['integration-provider-contracts'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const contractItems = computed(() => contracts.data.value?.items ?? [])
const versions = useQuery({ queryKey: computed(() => ['provider-contract-versions', selectedContractId.value]), queryFn: ({ signal }) => providerContractVersionApi.versions(selectedContractId.value, signal), enabled: computed(() => selectedContractId.value > 0) })
watch(contractItems, items => { if (items.length && !items.some(item => item.id === selectedContractId.value)) selectedContractId.value = items[0]!.id }, { immediate: true })
const form = reactive({ semanticVersion: '1.0.0', requestSchemaText: '{\n  "type": "object",\n  "properties": {},\n  "additionalProperties": false\n}', responseSchemaText: '{\n  "type": "object",\n  "properties": {},\n  "additionalProperties": false\n}', errorSchemaText: '', callbackSchemaText: '', examplesText: '{}' })
function message(error: Error): string { return error instanceof ApiError ? error.message : '请求失败，请确认 Control Plane 状态。' }
function parse(value: string, field: string, nullable = false): unknown | null { if (nullable && !value.trim()) return null; try { return JSON.parse(value) as unknown } catch { throw new Error(`${field} 不是合法 JSON。`) } }
const createVersion = useMutation({ mutationFn: (input: Parameters<typeof providerContractVersionApi.create>[1]) => providerContractVersionApi.create(selectedContractId.value, input), onSuccess: () => { dialog.value = false; void versions.refetch(); ElMessage.success('ProviderContractVersion 已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const publish = useMutation({ mutationFn: (row: ProviderContractVersionAsset) => providerContractVersionApi.publish(row.providerContractId, row.id), onSuccess: () => { void versions.refetch(); ElMessage.success('ProviderContractVersion 已发布') }, onError: (e: Error) => { errorMessage.value = message(e) } })
function requestPublish(row: unknown): void { publish.mutate(row as ProviderContractVersionAsset) }
function submit(): void {
  errorMessage.value = ''
  if (!selectedContractId.value || !/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(form.semanticVersion)) { errorMessage.value = '请选择 ProviderContract，并填写 x.y.z 格式的语义版本。'; return }
  try { createVersion.mutate({ semanticVersion: form.semanticVersion, requestSchema: parse(form.requestSchemaText, 'Request Schema', true), responseSchema: parse(form.responseSchemaText, 'Response Schema', true), errorSchema: parse(form.errorSchemaText, 'Error Schema', true), callbackSchema: parse(form.callbackSchemaText, 'Callback Schema', true), examples: parse(form.examplesText, 'Examples', true) }) } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'JSON 解析失败。' }
}
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>第三方协议版本</h2><p>定义第三方请求、响应、错误和回调报文 Schema，作为 JSONPath Mapping 的另一端。</p></div><el-tag effect="plain">Provider Schema</el-tag></div>
    <el-alert v-if="errorMessage" class="command-notice" type="error" :title="errorMessage" closable @close="errorMessage = ''" show-icon />
    <el-alert class="command-notice" type="info" :closable="false" title="ProviderContract 是稳定身份；Schema 变化创建新 Version。只有 PUBLISHED Version 可以进入 Mapping。" show-icon />
    <div class="surface provider-contract-version-panel">
      <div class="asset-toolbar canonical-version-toolbar"><div><strong>ProviderContractVersion</strong><span>JSON 内容由服务端规范化并生成不可变 checksum。</span></div><div><el-select v-model="selectedContractId" filterable placeholder="选择 ProviderContract"><el-option v-for="item in contractItems" :key="item.id" :label="`${item.contractName} · ${item.contractCode}`" :value="item.id" /></el-select><el-button type="primary" :disabled="!selectedContractId" @click="dialog = true">新增 Version</el-button></div></div>
      <AsyncStatePanel :loading="versions.isPending.value || contracts.isPending.value" :error="versions.isError.value || contracts.isError.value" error-title="ProviderContractVersion 读取失败" @retry="versions.refetch()">
        <el-empty v-if="!versions.data.value?.length" description="该 ProviderContract 尚未创建版本" />
        <el-table v-else :data="versions.data.value" row-key="id"><el-table-column label="版本" width="150"><template #default="{ row }"><strong>{{ row.semanticVersion }}</strong><div class="subtle">Revision {{ row.versionNo }}</div></template></el-table-column><el-table-column label="报文" width="230"><template #default="{ row }"><el-tag v-if="row.requestSchema" size="small">REQUEST</el-tag><el-tag v-if="row.responseSchema" size="small" type="success">RESPONSE</el-tag><el-tag v-if="row.errorSchema" size="small" type="danger">ERROR</el-tag><el-tag v-if="row.callbackSchema" size="small" type="warning">CALLBACK</el-tag></template></el-table-column><el-table-column label="Checksum" min-width="330"><template #default="{ row }"><span class="mono checksum-cell">{{ row.contentChecksum }}</span></template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ row.lifecycleStatus }}</el-tag></template></el-table-column><el-table-column label="操作" width="90"><template #default="{ row }"><el-button v-if="row.lifecycleStatus === 'DRAFT'" link type="primary" :loading="publish.isPending.value" @click="requestPublish(row)">发布</el-button></template></el-table-column></el-table>
      </AsyncStatePanel>
    </div>
    <el-dialog v-model="dialog" title="新增 ProviderContractVersion" width="900px"><el-alert type="warning" :closable="false" title="发布后不可修改；不适用的报文 Schema 可以留空。" show-icon /><el-form class="dialog-form" label-position="top"><el-form-item label="语义版本" required><el-input v-model="form.semanticVersion" placeholder="1.0.0" /></el-form-item><div class="schema-two-columns"><el-form-item label="Request Schema"><el-input v-model="form.requestSchemaText" type="textarea" :rows="11" class="schema-editor" /></el-form-item><el-form-item label="Response Schema"><el-input v-model="form.responseSchemaText" type="textarea" :rows="11" class="schema-editor" /></el-form-item><el-form-item label="Error Schema"><el-input v-model="form.errorSchemaText" type="textarea" :rows="7" class="schema-editor" placeholder="可选" /></el-form-item><el-form-item label="Callback Schema"><el-input v-model="form.callbackSchemaText" type="textarea" :rows="7" class="schema-editor" placeholder="可选" /></el-form-item></div><el-form-item label="Examples"><el-input v-model="form.examplesText" type="textarea" :rows="5" class="schema-editor" /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="createVersion.isPending.value" @click="submit">创建 DRAFT</el-button></template></el-dialog>
  </section>
</template>
