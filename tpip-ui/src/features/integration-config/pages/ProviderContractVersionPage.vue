<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { accessChannelApi } from '../api/accessChannelApi'
import { bindingVersionApi } from '../api/bindingVersionApi'
import { integrationAssetApi } from '../api/integrationAssetApi'
import { mappingAssetApi } from '../api/mappingAssetApi'
import { providerContractVersionApi, type ProviderContractVersionAsset } from '../api/providerContractVersionApi'
import { buildChannelProtocolViews, protocolVersionUsageCount, recommendedPublishedVersionId } from '../model/providerProtocolModel'

const errorMessage = ref(''); const dialog = ref(false)
const selectedProviderId = ref(0); const selectedProductId = ref(0); const selectedContractId = ref(0)
const providers = useQuery({ queryKey: ['integration-providers'], queryFn: ({ signal }) => integrationAssetApi.providers('', signal) })
const products = useQuery({ queryKey: ['provider-products'], queryFn: ({ signal }) => accessChannelApi.products(undefined, signal) })
const contracts = useQuery({ queryKey: ['integration-provider-contracts'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const providerItems = computed(() => providers.data.value?.items.filter(item => item.status === 'ACTIVE') ?? [])
const productItems = computed(() => (products.data.value ?? []).filter(item => item.status === 'ACTIVE' && item.providerId === selectedProviderId.value))
const productInterfaces = useQuery({ queryKey: computed(() => ['provider-product-interfaces', selectedProductId.value]),
  queryFn: ({ signal }) => accessChannelApi.productInterfaceIds(selectedProductId.value, signal),
  enabled: computed(() => selectedProductId.value > 0) })
const contractItems = computed(() => {
  const ids = new Set(productInterfaces.data.value ?? [])
  return (contracts.data.value?.items ?? []).filter(item => item.status === 'ACTIVE' &&
    item.providerId === selectedProviderId.value && ids.has(item.id))
})
watch(providerItems, items => { if (!items.some(item => item.id === selectedProviderId.value)) selectedProviderId.value = items[0]?.id ?? 0 }, { immediate: true })
watch(productItems, items => { if (!items.some(item => item.id === selectedProductId.value)) selectedProductId.value = items[0]?.id ?? 0 }, { immediate: true })
watch(contractItems, items => { if (!items.some(item => item.id === selectedContractId.value)) selectedContractId.value = items[0]?.id ?? 0 }, { immediate: true })

const versions = useQuery({ queryKey: computed(() => ['provider-contract-versions', selectedContractId.value]),
  queryFn: ({ signal }) => providerContractVersionApi.versions(selectedContractId.value, signal),
  enabled: computed(() => selectedContractId.value > 0) })
const endpoints = useQuery({ queryKey: computed(() => ['provider-contract-endpoints', selectedContractId.value]),
  queryFn: ({ signal }) => integrationAssetApi.endpoints(selectedContractId.value, signal),
  enabled: computed(() => selectedContractId.value > 0) })
const usageContext = useQuery({ queryKey: computed(() => ['provider-contract-channel-usage', selectedProductId.value, selectedContractId.value]),
  enabled: computed(() => selectedProductId.value > 0 && selectedContractId.value > 0),
  queryFn: async ({ signal }) => {
    const [allChannels, bindingPage] = await Promise.all([
      accessChannelApi.channels(selectedProviderId.value, signal), mappingAssetApi.bindings(signal)
    ])
    const candidates = allChannels.filter(item => item.providerProductId === selectedProductId.value)
    const relations = await Promise.all(candidates.map(async item => ({ item,
      interfaceIds: await accessChannelApi.interfaceIds(item.id, signal) })))
    const channelItems = relations.filter(item => item.interfaceIds.includes(selectedContractId.value)).map(item => item.item)
    const bindingItems = bindingPage.items.filter(item => item.providerContractId === selectedContractId.value)
    const bindingVersions = (await Promise.all(bindingItems.map(item => bindingVersionApi.versions(item.id, signal)))).flat()
    return { channels: channelItems, bindings: bindingItems, bindingVersions }
  } })
const channelRows = computed(() => buildChannelProtocolViews(selectedContractId.value,
  usageContext.data.value?.channels ?? [], endpoints.data.value?.items ?? [], usageContext.data.value?.bindings ?? [],
  usageContext.data.value?.bindingVersions ?? [], versions.data.value ?? []))
const publishedVersions = computed(() => (versions.data.value ?? []).filter(item => item.lifecycleStatus === 'PUBLISHED'))
const recommendedId = computed(() => recommendedPublishedVersionId(versions.data.value ?? []))
const selectedContract = computed(() => contractItems.value.find(item => item.id === selectedContractId.value) ?? null)

const form = reactive({ requestSchemaText: '{\n  "type": "object",\n  "properties": {},\n  "additionalProperties": false\n}', responseSchemaText: '{\n  "type": "object",\n  "properties": {},\n  "additionalProperties": false\n}', errorSchemaText: '', callbackSchemaText: '', examplesText: '{}' })
function message(error: Error): string { return error instanceof ApiError ? error.message : '请求失败，请确认控制服务状态。' }
function parse(value: string, field: string, nullable = false): unknown | null { if (nullable && !value.trim()) return null; try { return JSON.parse(value) as unknown } catch { throw new Error(`${field}不是合法 JSON。`) } }
const createVersion = useMutation({ mutationFn: (input: Parameters<typeof providerContractVersionApi.create>[1]) => providerContractVersionApi.create(selectedContractId.value, input), onSuccess: () => { dialog.value = false; void versions.refetch(); ElMessage.success('报文结构版本已创建') }, onError: (e: Error) => { errorMessage.value = message(e) } })
const publish = useMutation({ mutationFn: (row: ProviderContractVersionAsset) => providerContractVersionApi.publish(row.providerContractId, row.id), onSuccess: () => { void versions.refetch(); ElMessage.success('报文结构版本已发布') }, onError: (e: Error) => { errorMessage.value = message(e) } })
function requestPublish(row: unknown): void { publish.mutate(row as ProviderContractVersionAsset) }
function submit(): void {
  errorMessage.value = ''
  if (!selectedContractId.value) { errorMessage.value = '请先选择第三方接口。'; return }
  try { createVersion.mutate({ requestSchema: parse(form.requestSchemaText, '请求结构', true), responseSchema: parse(form.responseSchemaText, '返回结构', true), errorSchema: parse(form.errorSchemaText, '错误结构', true), callbackSchema: parse(form.callbackSchemaText, '回调结构', true), examples: parse(form.examplesText, '报文样例', true) }) } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'JSON 解析失败。' }
}
function usageText(value: unknown): string {
  const row = value as ProviderContractVersionAsset
  const count = protocolVersionUsageCount(row.id, channelRows.value)
  if (count) return `${count} 个通道已冻结使用`
  return row.id === recommendedId.value ? '新配置默认推荐' : '可供新配置选择'
}
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>第三方报文结构</h2><p>定义第三方接口的请求、返回、错误和回调报文；先按提供方、产品和接口定位，再查看各通道实际使用的版本。</p></div><el-tag effect="plain">报文结构</el-tag></div>
    <el-alert v-if="errorMessage" class="command-notice" type="error" :title="errorMessage" closable @close="errorMessage = ''" show-icon />
    <el-alert class="command-notice" type="info" :closable="false" title="报文结构版本属于第三方接口；通道通过可执行配置冻结具体版本。发布新版本不会自动切换正在运行的通道。" show-icon />
    <div class="surface provider-contract-version-panel">
      <div class="protocol-selector-grid">
        <label><span>第三方提供方</span><el-select v-model="selectedProviderId" filterable placeholder="选择提供方"><el-option v-for="item in providerItems" :key="item.id" :label="item.providerName" :value="item.id" /></el-select></label>
        <label><span>产品/服务</span><el-select v-model="selectedProductId" filterable :disabled="!selectedProviderId" placeholder="选择产品/服务"><el-option v-for="item in productItems" :key="item.id" :label="item.productName" :value="item.id" /></el-select></label>
        <label><span>第三方接口</span><el-select v-model="selectedContractId" filterable :disabled="!selectedProductId" placeholder="选择第三方接口"><el-option v-for="item in contractItems" :key="item.id" :label="`${item.contractName} · ${item.contractCode}`" :value="item.id" /></el-select></label>
        <el-button type="primary" :disabled="!selectedContractId" @click="dialog = true">新增报文结构版本</el-button>
      </div>

      <div class="section-title protocol-section-title"><div><h3>报文结构版本</h3><span class="subtle">{{ selectedContract?.contractName ?? '请先选择接口' }}</span></div></div>
      <el-alert v-if="publishedVersions.length > 1" class="command-notice" type="warning" :closable="false" title="存在多个已发布版本是正常的：它们都是可追溯、可回滚的不可变版本；通道实际使用哪个版本，请查看下方“通道与报文结构使用关系”。" show-icon />
      <AsyncStatePanel :loading="versions.isPending.value || contracts.isPending.value" :error="versions.isError.value || contracts.isError.value" error-title="报文结构版本读取失败" @retry="versions.refetch()">
        <el-empty v-if="!selectedContractId || !versions.data.value?.length" description="该第三方接口尚未创建报文结构版本" />
        <el-table v-else :data="versions.data.value" row-key="id">
          <el-table-column label="版本" width="150"><template #default="{ row }"><strong>{{ row.semanticVersion }}</strong><div class="subtle">内部修订 {{ row.versionNo }}</div></template></el-table-column>
          <el-table-column label="报文结构" width="230"><template #default="{ row }"><el-tag v-if="row.requestSchema" size="small">请求</el-tag><el-tag v-if="row.responseSchema" size="small" type="success">返回</el-tag><el-tag v-if="row.errorSchema" size="small" type="danger">错误</el-tag><el-tag v-if="row.callbackSchema" size="small" type="warning">回调</el-tag></template></el-table-column>
          <el-table-column label="采用情况" min-width="190"><template #default="{ row }"><el-tag v-if="row.id === recommendedId && row.lifecycleStatus === 'PUBLISHED'" size="small" type="success">当前推荐</el-tag><div class="subtle">{{ usageText(row) }}</div></template></el-table-column>
          <el-table-column label="内容摘要" min-width="260"><template #default="{ row }"><span class="mono checksum-cell">{{ row.contentChecksum }}</span></template></el-table-column>
          <el-table-column label="生命周期" width="110"><template #default="{ row }"><el-tag :type="row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ row.lifecycleStatus === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="90"><template #default="{ row }"><el-button v-if="row.lifecycleStatus === 'DRAFT'" link type="primary" :loading="publish.isPending.value" @click="requestPublish(row)">发布</el-button></template></el-table-column>
        </el-table>
      </AsyncStatePanel>

      <div class="section-title protocol-section-title"><div><h3>通道与报文结构使用关系</h3><span class="subtle">展示已发布可执行配置冻结的报文结构版本；报文结构仍统一归属于第三方接口。</span></div></div>
      <AsyncStatePanel :loading="usageContext.isPending.value || endpoints.isPending.value" :error="usageContext.isError.value || endpoints.isError.value" error-title="通道与报文结构关系读取失败" @retry="usageContext.refetch()">
        <el-empty v-if="!channelRows.length" description="当前接口尚未关联通道，或尚未形成可执行配置版本" />
        <el-table v-else :data="channelRows" row-key="channelId">
          <el-table-column label="接入通道" min-width="220"><template #default="{ row }"><strong>{{ row.channelName }}</strong><div class="subtle">{{ row.channelCode }}</div></template></el-table-column>
          <el-table-column label="通道地址" min-width="300"><template #default="{ row }"><span class="mono">{{ row.baseUrl }}</span></template></el-table-column>
          <el-table-column label="环境" width="130"><template #default="{ row }">{{ row.environments.join('、') || '尚未冻结' }}</template></el-table-column>
          <el-table-column label="实际使用的报文结构" min-width="270"><template #default="{ row }"><template v-if="row.frozenVersions.length"><el-tag v-for="item in row.frozenVersions" :key="`${item.versionId}-${item.bindingVersionNo}`" class="protocol-version-tag" type="success">{{ item.semanticVersion }} · 执行修订 {{ item.bindingVersionNo }}</el-tag></template><span v-else class="muted-text">尚未由可执行配置选定</span></template></el-table-column>
          <el-table-column label="可用已发布版本" min-width="180"><template #default><span>{{ publishedVersions.map(item => item.semanticVersion).join('、') || '暂无' }}</span></template></el-table-column>
        </el-table>
      </AsyncStatePanel>
    </div>

    <el-dialog v-model="dialog" title="新增报文结构版本" width="900px"><el-alert type="warning" :closable="false" title="版本号由平台自动生成；发布后不可修改，不适用的报文结构可以留空。" show-icon /><el-form class="dialog-form" label-position="top"><div class="schema-two-columns"><el-form-item label="请求结构"><el-input v-model="form.requestSchemaText" type="textarea" :rows="11" class="schema-editor" /></el-form-item><el-form-item label="返回结构"><el-input v-model="form.responseSchemaText" type="textarea" :rows="11" class="schema-editor" /></el-form-item><el-form-item label="错误结构"><el-input v-model="form.errorSchemaText" type="textarea" :rows="7" class="schema-editor" placeholder="可选" /></el-form-item><el-form-item label="回调结构"><el-input v-model="form.callbackSchemaText" type="textarea" :rows="7" class="schema-editor" placeholder="可选" /></el-form-item></div><el-form-item label="报文样例"><el-input v-model="form.examplesText" type="textarea" :rows="5" class="schema-editor" /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="createVersion.isPending.value" @click="submit">创建草稿</el-button></template></el-dialog>
  </section>
</template>
