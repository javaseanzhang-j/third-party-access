<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { accessChannelApi, type AccessChannelAsset, type EffectiveParameterAsset } from '../api/accessChannelApi'
import { integrationAssetApi } from '../api/integrationAssetApi'
import { buildThirdPartyInterfaces, type ThirdPartyInterfaceView } from '../model/productModel'

const router = useRouter()
const providers = useQuery({ queryKey: ['product-providers'], queryFn: ({ signal }) => integrationAssetApi.providers('', signal) })
const contracts = useQuery({ queryKey: ['product-contracts'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const endpoints = useQuery({ queryKey: ['product-endpoints'], queryFn: ({ signal }) => integrationAssetApi.endpoints(undefined, signal) })
const channels = useQuery({ queryKey: ['access-channels'], queryFn: ({ signal }) => accessChannelApi.channels(undefined, signal) })
const loading = computed(() => [providers, contracts, endpoints, channels].some(item => item.isPending.value))
const hasError = computed(() => [providers, contracts, endpoints, channels].some(item => item.isError.value))
const interfaces = computed(() => buildThirdPartyInterfaces(providers.data.value?.items ?? [],
  contracts.data.value?.items ?? [], endpoints.data.value?.items ?? []))
const dialog = ref(false)
const selectedInterface = ref<ThirdPartyInterfaceView | null>(null)
const selectedChannelId = ref<number | null>(null)
const effective = ref<EffectiveParameterAsset[]>([])
const saving = ref(false)
const providerChannels = computed(() => channels.data.value?.filter(item =>
  item.providerId === selectedInterface.value?.providerId) ?? [])
const selectedChannel = computed(() => providerChannels.value.find(item => item.id === selectedChannelId.value) ?? null)
const statusLabel = (status: string) => status === 'PUBLISHED' ? '已发布' : status === 'DRAFT' ? '草稿' : '已停用'
const sourceLabel: Record<string, string> = { FIXED: '固定值', SECRET_REF: 'Secret 引用', REQUEST: '业务请求',
  SYSTEM_TIME: '系统时间', UUID: 'UUID', EXPRESSION: '表达式', MAPPING_OUTPUT: '字段映射', POLICY_OUTPUT: '规则输出' }

function openChannelDialog(item: unknown): void {
  selectedInterface.value = item as ThirdPartyInterfaceView
  selectedChannelId.value = null; effective.value = []; dialog.value = true
}

async function attachAndPreview(): Promise<void> {
  if (!selectedInterface.value || !selectedChannelId.value) return
  saving.value = true
  try {
    await accessChannelApi.attachInterface(selectedChannelId.value, selectedInterface.value.providerContractId)
    effective.value = await accessChannelApi.effective(selectedChannelId.value,
      selectedInterface.value.providerContractId)
    ElMessage.success('接口已加入通道，已生成最终有效参数')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '配置失败') }
  finally { saving.value = false }
}
</script>

<template>
  <section>
    <div class="product-page-heading">
      <div><span class="eyebrow">THIRD-PARTY INTERFACES</span><h2>第三方接口</h2>
        <p>配置接口的 Method 和 Path，并选择它继承公共地址、凭据和参数的接入通道。</p></div>
      <el-button type="primary" @click="router.push('/integration-assets/provider-access')">＋ 新增第三方接口</el-button>
    </div>
    <el-alert v-if="hasError" type="error" :closable="false" show-icon class="command-notice"
      title="第三方接口或接入通道读取失败，请确认 Control Plane 已启动并执行 V41 迁移。" />
    <div v-loading="loading" class="surface product-table-card">
      <el-empty v-if="!loading && !interfaces.length" description="还没有第三方接口">
        <el-button type="primary" @click="router.push('/integration-assets/provider-access')">＋ 配置第一个接口</el-button>
      </el-empty>
      <el-table v-else :data="interfaces">
        <el-table-column label="第三方系统" prop="providerName" min-width="140" />
        <el-table-column label="接口名称" prop="contractName" min-width="170" />
        <el-table-column label="方式" prop="method" width="90" />
        <el-table-column label="当前执行地址" min-width="330"><template #default="scope"><code>{{ scope.row.baseUrl }}{{ scope.row.resourcePath }}</code></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.lifecycleStatus === 'PUBLISHED' ? 'success' : 'info'">{{ statusLabel(scope.row.lifecycleStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="160"><template #default="scope"><el-button link type="primary" @click="openChannelDialog(scope.row)">配置通道</el-button><el-button link @click="router.push('/integration-assets/provider-access')">高级管理</el-button></template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="配置接口接入通道" width="760px">
      <div v-if="selectedInterface" class="interface-channel-summary"><span>{{ selectedInterface.providerName }}</span>
        <strong>{{ selectedInterface.contractName }}</strong><code>{{ selectedInterface.method }} {{ selectedInterface.resourcePath }}</code></div>
      <el-form label-position="top"><el-form-item label="选择接入通道"><el-select v-model="selectedChannelId" filterable placeholder="选择同一第三方下的通道"><el-option v-for="item in providerChannels" :key="item.id" :label="`${item.channelName} · ${item.baseUrl}`" :value="item.id" /></el-select></el-form-item></el-form>
      <el-alert v-if="selectedChannel" type="info" :closable="false" :title="`最终地址：${selectedChannel.baseUrl}${selectedInterface?.resourcePath ?? ''}`" />
      <div v-if="effective.length" class="effective-preview"><h4>最终有效参数</h4>
        <el-table :data="effective" size="small"><el-table-column label="参数" min-width="150"><template #default="scope">{{ scope.row.parameter.parameterName }}<small class="table-secondary">{{ scope.row.parameter.parameterCode }}</small></template></el-table-column>
          <el-table-column label="位置" prop="parameter.location" width="100" /><el-table-column label="来源" width="110"><template #default="scope">{{ sourceLabel[scope.row.parameter.source] }}</template></el-table-column>
          <el-table-column label="配置来源" width="120"><template #default="scope">{{ scope.row.resolvedFrom === 'CHANNEL' ? '通道公共' : '接口专用' }}</template></el-table-column>
          <el-table-column label="值" min-width="150"><template #default="scope">{{ scope.row.parameter.sensitive ? '••••••' : scope.row.parameter.valueDocument ?? scope.row.parameter.sourceSelector ?? '运行时生成' }}</template></el-table-column></el-table></div>
      <template #footer><el-button @click="dialog = false">关闭</el-button><el-button type="primary" :loading="saving" :disabled="!selectedChannelId" @click="attachAndPreview">保存并预览有效配置</el-button></template>
    </el-dialog>
  </section>
</template>
