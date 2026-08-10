<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { accessChannelApi, type AccessChannelAsset, type AccessParameterAsset,
  type ParameterDataType, type ParameterLocation, type ParameterSource } from '../api/accessChannelApi'
import { integrationAssetApi } from '../api/integrationAssetApi'

const providers = useQuery({ queryKey: ['product-providers'], queryFn: ({ signal }) => integrationAssetApi.providers('', signal) })
const credentials = useQuery({ queryKey: ['product-credentials'], queryFn: ({ signal }) => integrationAssetApi.credentials(undefined, signal) })
const contracts = useQuery({ queryKey: ['product-contracts'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const channels = useQuery({ queryKey: ['access-channels'], queryFn: ({ signal }) => accessChannelApi.channels(undefined, signal) })
const selectedChannel = ref<AccessChannelAsset | null>(null)
const parameters = ref<AccessParameterAsset[]>([])
const attachedInterfaceIds = ref<number[]>([])
const detailLoading = ref(false)
const channelDialog = ref(false)
const editingChannel = ref<AccessChannelAsset | null>(null)
const parameterDialog = ref(false)
const attachDialog = ref(false)
const saving = ref(false)
const channelForm = reactive({ providerId: null as number | null, channelCode: '', channelName: '',
  baseUrl: 'https://', credentialRefId: null as number | null, description: '' })
const parameterForm = reactive({ scope: 'CHANNEL' as 'CHANNEL' | 'INTERFACE', providerContractId: null as number | null,
  parameterCode: '', parameterName: '', location: 'HEADER' as ParameterLocation,
  source: 'FIXED' as ParameterSource, dataType: 'STRING' as ParameterDataType, value: '',
  sourceSelector: '', secretRefId: null as number | null, overrideMode: 'REPLACE' as 'REPLACE' | 'DISABLE',
  required: false, sensitive: false, callerOverridable: false, description: '' })
const attachContractId = ref<number | null>(null)
const providerName = (id: number) => providers.data.value?.items.find(item => item.id === id)?.providerName ?? `第三方 #${id}`
const credentialName = (id: number | null) => id
  ? credentials.data.value?.items.find(item => item.id === id)?.credentialCode ?? `凭据 #${id}` : '无需凭据'
const selectedProviderCredentials = computed(() => credentials.data.value?.items
  .filter(item => item.providerId === channelForm.providerId) ?? [])
const channelContracts = computed(() => contracts.data.value?.items
  .filter(item => item.providerId === selectedChannel.value?.providerId) ?? [])
const availableContracts = computed(() => channelContracts.value.filter(item => !attachedInterfaceIds.value.includes(item.id)))
const parameterContractName = (id: number | null) => id
  ? contracts.data.value?.items.find(item => item.id === id)?.contractName ?? `接口 #${id}` : '通道公共'
const sourceLabel: Record<ParameterSource, string> = { FIXED: '固定值', SECRET_REF: 'Secret 引用',
  REQUEST: '业务请求字段', SYSTEM_TIME: '系统时间', UUID: '系统生成 UUID', EXPRESSION: '受控表达式',
  MAPPING_OUTPUT: '字段映射结果', POLICY_OUTPUT: '规则处理结果' }

async function selectChannel(channel: AccessChannelAsset): Promise<void> {
  selectedChannel.value = channel; detailLoading.value = true
  try {
    const [parameterItems, interfaceIds] = await Promise.all([
      accessChannelApi.parameters(channel.id), accessChannelApi.interfaceIds(channel.id)
    ])
    parameters.value = parameterItems; attachedInterfaceIds.value = interfaceIds
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '通道详情读取失败') }
  finally { detailLoading.value = false }
}

function openCreateChannel(): void {
  editingChannel.value = null
  Object.assign(channelForm, { providerId: null, channelCode: '', channelName: '', baseUrl: 'https://',
    credentialRefId: null, description: '' })
  channelDialog.value = true
}

function openEditChannel(): void {
  if (!selectedChannel.value) return
  editingChannel.value = selectedChannel.value
  Object.assign(channelForm, { providerId: selectedChannel.value.providerId,
    channelCode: selectedChannel.value.channelCode, channelName: selectedChannel.value.channelName,
    baseUrl: selectedChannel.value.baseUrl, credentialRefId: selectedChannel.value.credentialRefId,
    description: selectedChannel.value.description ?? '' })
  channelDialog.value = true
}

async function saveChannel(): Promise<void> {
  if (!channelForm.providerId || !channelForm.channelCode.trim() || !channelForm.channelName.trim()) {
    ElMessage.warning('请填写第三方系统、通道名称和通道编码'); return
  }
  saving.value = true
  try {
    const saved = editingChannel.value
      ? await accessChannelApi.update(editingChannel.value.id, { channelName: channelForm.channelName.trim(),
        baseUrl: channelForm.baseUrl.trim(), credentialRefId: channelForm.credentialRefId,
        description: channelForm.description.trim() || null, status: editingChannel.value.status,
        rowVersion: editingChannel.value.rowVersion })
      : await accessChannelApi.create({ providerId: channelForm.providerId,
        channelCode: channelForm.channelCode.trim(), channelName: channelForm.channelName.trim(),
        baseUrl: channelForm.baseUrl.trim(), credentialRefId: channelForm.credentialRefId,
        description: channelForm.description.trim() || null })
    channelDialog.value = false; await channels.refetch(); await selectChannel(saved)
    ElMessage.success(editingChannel.value ? '接入通道已更新' : '接入通道已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') }
  finally { saving.value = false }
}

async function attachInterface(): Promise<void> {
  if (!selectedChannel.value || !attachContractId.value) return
  saving.value = true
  try {
    await accessChannelApi.attachInterface(selectedChannel.value.id, attachContractId.value)
    attachedInterfaceIds.value = await accessChannelApi.interfaceIds(selectedChannel.value.id)
    attachDialog.value = false; attachContractId.value = null; ElMessage.success('第三方接口已加入通道')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '关联失败') }
  finally { saving.value = false }
}

function parameterValue(): unknown {
  if (parameterForm.source !== 'FIXED' || parameterForm.overrideMode === 'DISABLE') return null
  if (parameterForm.dataType === 'STRING') return parameterForm.value
  try { return JSON.parse(parameterForm.value) as unknown }
  catch { throw new Error('固定值不是有效的 JSON 数据') }
}

async function saveParameter(): Promise<void> {
  if (!selectedChannel.value || !parameterForm.parameterCode.trim() || !parameterForm.parameterName.trim()) {
    ElMessage.warning('请填写参数名称和参数编码'); return
  }
  saving.value = true
  try {
    await accessChannelApi.upsertParameter(selectedChannel.value.id, { scope: parameterForm.scope,
      providerContractId: parameterForm.scope === 'INTERFACE' ? parameterForm.providerContractId : null,
      parameterCode: parameterForm.parameterCode.trim(), parameterName: parameterForm.parameterName.trim(),
      location: parameterForm.location, source: parameterForm.source, dataType: parameterForm.dataType,
      value: parameterValue(), sourceSelector: parameterForm.sourceSelector.trim() || null,
      secretRefId: parameterForm.source === 'SECRET_REF' ? parameterForm.secretRefId : null,
      overrideMode: parameterForm.overrideMode, required: parameterForm.required,
      sensitive: parameterForm.sensitive, callerOverridable: parameterForm.callerOverridable,
      description: parameterForm.description.trim() || null })
    parameters.value = await accessChannelApi.parameters(selectedChannel.value.id)
    parameterDialog.value = false; ElMessage.success('参数配置已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
</script>

<template>
  <section>
    <div class="product-page-heading">
      <div><span class="eyebrow">CONNECTION CHANNELS</span><h2>接入通道</h2>
        <p>集中管理第三方服务地址、接入账号和所有接口共用的参数。</p></div>
      <el-button type="primary" @click="openCreateChannel">＋ 新增接入通道</el-button>
    </div>
    <el-alert v-if="channels.isError.value" type="error" :closable="false" show-icon class="command-notice"
      title="接入通道读取失败，请确认数据库已执行 V41 迁移且 Control Plane 已重启。" />
    <div class="channel-workspace">
      <div v-loading="channels.isPending.value" class="surface product-table-card channel-list-card">
        <el-empty v-if="!channels.isPending.value && !channels.data.value?.length" description="还没有接入通道">
          <el-button type="primary" @click="openCreateChannel">＋ 创建第一个通道</el-button>
        </el-empty>
        <button v-for="channel in channels.data.value" :key="channel.id" type="button" class="channel-list-item"
          :class="{ active: selectedChannel?.id === channel.id }" @click="selectChannel(channel)">
          <span><strong>{{ channel.channelName }}</strong><small>{{ providerName(channel.providerId) }}</small></span>
          <code>{{ channel.baseUrl }}</code>
        </button>
      </div>
      <div v-loading="detailLoading" class="surface channel-detail-card">
        <el-empty v-if="!selectedChannel" description="请选择一个接入通道" />
        <template v-else>
          <div class="channel-detail-heading"><div><span>{{ providerName(selectedChannel.providerId) }}</span>
            <h3>{{ selectedChannel.channelName }}</h3><code>{{ selectedChannel.baseUrl }}</code></div>
            <div><el-tag :type="selectedChannel.status === 'ACTIVE' ? 'success' : 'info'">{{ selectedChannel.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag><el-button link type="primary" @click="openEditChannel">编辑通道</el-button></div></div>
          <div class="channel-facts"><div><span>接入凭据</span><strong>{{ credentialName(selectedChannel.credentialRefId) }}</strong></div>
            <div><span>已关联接口</span><strong>{{ attachedInterfaceIds.length }}</strong></div>
            <div><span>公共及覆盖参数</span><strong>{{ parameters.length }}</strong></div></div>
          <div class="channel-section-heading"><div><h4>第三方接口</h4><p>同一个通道下的接口共享服务地址和公共配置。</p></div>
            <el-button @click="attachDialog = true">加入接口</el-button></div>
          <div class="channel-interface-tags"><el-tag v-for="id in attachedInterfaceIds" :key="id" effect="plain">{{ parameterContractName(id) }}</el-tag>
            <span v-if="!attachedInterfaceIds.length">尚未加入接口</span></div>
          <div class="channel-section-heading"><div><h4>公共参数与接口覆盖</h4><p>接口未配置时继承通道参数；接口可以覆盖或禁用同名参数。</p></div>
            <el-button type="primary" class="create-action" @click="parameterDialog = true">新增参数</el-button></div>
          <el-table :data="parameters" size="small">
            <el-table-column label="作用范围" width="150"><template #default="scope">{{ scope.row.scope === 'CHANNEL' ? '通道公共' : parameterContractName(scope.row.providerContractId) }}</template></el-table-column>
            <el-table-column label="参数" min-width="150"><template #default="scope"><strong>{{ scope.row.parameterName }}</strong><small class="table-secondary">{{ scope.row.parameterCode }}</small></template></el-table-column>
            <el-table-column label="位置" prop="location" width="105" />
            <el-table-column label="来源" width="125"><template #default="scope">{{ sourceLabel[scope.row.source as ParameterSource] }}</template></el-table-column>
            <el-table-column label="行为" width="90"><template #default="scope">{{ scope.row.overrideMode === 'DISABLE' ? '禁用继承' : scope.row.scope === 'CHANNEL' ? '公共值' : '覆盖' }}</template></el-table-column>
            <el-table-column label="值" min-width="150"><template #default="scope"><span v-if="scope.row.sensitive">••••••</span><code v-else>{{ scope.row.valueDocument ?? scope.row.sourceSelector ?? '运行时生成' }}</code></template></el-table-column>
          </el-table>
        </template>
      </div>
    </div>

    <el-dialog v-model="channelDialog" :title="editingChannel ? '编辑接入通道' : '新增接入通道'" width="620px">
      <el-form label-position="top" class="dialog-form"><div class="form-two-columns">
        <el-form-item label="第三方系统"><el-select v-model="channelForm.providerId" filterable :disabled="Boolean(editingChannel)" @change="channelForm.credentialRefId = null"><el-option v-for="item in providers.data.value?.items" :key="item.id" :label="item.providerName" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="通道名称"><el-input v-model="channelForm.channelName" placeholder="例如：阿里云短信" /></el-form-item>
        <el-form-item label="通道编码"><el-input v-model="channelForm.channelCode" :disabled="Boolean(editingChannel)" placeholder="例如：aliyun.sms" /></el-form-item>
        <el-form-item label="接入凭据"><el-select v-model="channelForm.credentialRefId" clearable placeholder="无需凭据"><el-option v-for="item in selectedProviderCredentials" :key="item.id" :label="item.credentialCode" :value="item.id" /></el-select></el-form-item>
      </div><el-form-item label="服务地址 baseUrl"><el-input v-model="channelForm.baseUrl" placeholder="https://api.example.com" /></el-form-item>
      <el-form-item label="说明"><el-input v-model="channelForm.description" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="channelDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveChannel">{{ editingChannel ? '保存修改' : '创建通道' }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="attachDialog" title="将第三方接口加入通道" width="520px">
      <el-form label-position="top"><el-form-item label="第三方接口"><el-select v-model="attachContractId" filterable><el-option v-for="item in availableContracts" :key="item.id" :label="item.contractName" :value="item.id" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="attachDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="attachInterface">确认加入</el-button></template>
    </el-dialog>

    <el-dialog v-model="parameterDialog" title="配置通道参数" width="760px">
      <el-form label-position="top" class="dialog-form"><div class="form-two-columns">
        <el-form-item label="作用范围"><el-radio-group v-model="parameterForm.scope"><el-radio-button value="CHANNEL">通道公共</el-radio-button><el-radio-button value="INTERFACE">接口专用</el-radio-button></el-radio-group></el-form-item>
        <el-form-item v-if="parameterForm.scope === 'INTERFACE'" label="指定接口"><el-select v-model="parameterForm.providerContractId"><el-option v-for="item in channelContracts.filter(item => attachedInterfaceIds.includes(item.id))" :key="item.id" :label="item.contractName" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="参数名称"><el-input v-model="parameterForm.parameterName" placeholder="例如：应用标识" /></el-form-item>
        <el-form-item label="参数编码"><el-input v-model="parameterForm.parameterCode" placeholder="例如：appKey" /></el-form-item>
        <el-form-item label="注入位置"><el-select v-model="parameterForm.location"><el-option v-for="item in ['PATH','QUERY','HEADER','COOKIE','BODY','SIGNATURE']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="参数来源"><el-select v-model="parameterForm.source"><el-option v-for="(label,key) in sourceLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item>
        <el-form-item label="数据类型"><el-select v-model="parameterForm.dataType"><el-option v-for="item in ['STRING','NUMBER','BOOLEAN','OBJECT','ARRAY']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item v-if="parameterForm.scope === 'INTERFACE'" label="接口处理"><el-select v-model="parameterForm.overrideMode"><el-option label="覆盖公共参数" value="REPLACE" /><el-option label="禁用公共参数" value="DISABLE" /></el-select></el-form-item>
        <el-form-item v-if="parameterForm.source === 'SECRET_REF'" label="Secret 引用"><el-select v-model="parameterForm.secretRefId"><el-option v-for="item in credentials.data.value?.items.filter(item => item.providerId === selectedChannel?.providerId)" :key="item.id" :label="item.credentialCode" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-else-if="parameterForm.source === 'FIXED'" label="固定值"><el-input v-model="parameterForm.value" :placeholder="parameterForm.dataType === 'STRING' ? '直接输入文本' : '请输入有效 JSON'" /></el-form-item>
        <el-form-item v-else label="来源字段或表达式"><el-input v-model="parameterForm.sourceSelector" placeholder="例如：$.mobile" /></el-form-item>
      </div><el-form-item><el-checkbox v-model="parameterForm.required">必填</el-checkbox><el-checkbox v-model="parameterForm.sensitive">敏感参数</el-checkbox><el-checkbox v-model="parameterForm.callerOverridable">允许调用方覆盖</el-checkbox></el-form-item>
      <el-form-item label="说明"><el-input v-model="parameterForm.description" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="parameterDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveParameter">保存参数</el-button></template>
    </el-dialog>
  </section>
</template>
