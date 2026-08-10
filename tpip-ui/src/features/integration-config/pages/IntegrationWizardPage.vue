<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { canonicalAssetApi } from '../api/canonicalAssetApi'
import { integrationAssetApi } from '../api/integrationAssetApi'
import { integrationWizardApi } from '../api/integrationWizardApi'
import { mappingAssetApi } from '../api/mappingAssetApi'
import { workspaceVerificationApi } from '../api/workspaceVerificationApi'
import { assessWizardStages, emptyWizardDraft, parseWizardDraft, serializeWizardDraft, wizardProgress,
  WIZARD_STORAGE_KEY, type IntegrationWizardFacts, type WizardStageAssessment } from '../model/integrationWizardModel'

const router = useRouter()
const draft = ref(parseWizardDraft(window.localStorage.getItem(WIZARD_STORAGE_KEY)))
const lastSavedAt = ref(draft.value.updatedAt)

const operations = useQuery({ queryKey: ['canonical-operations'], queryFn: ({ signal }) => canonicalAssetApi.operations(undefined, signal) })
const providerContracts = useQuery({ queryKey: ['integration-provider-contracts'], queryFn: ({ signal }) => integrationAssetApi.contracts(undefined, signal) })
const bindings = useQuery({ queryKey: ['integration-bindings'], queryFn: ({ signal }) => mappingAssetApi.bindings(signal) })
const workspaces = useQuery({ queryKey: ['release-workspaces'], queryFn: ({ signal }) => workspaceVerificationApi.workspaces(signal) })

const operationItems = computed(() => operations.data.value?.items ?? [])
const providerContractItems = computed(() => providerContracts.data.value?.items ?? [])
const bindingItems = computed(() => bindings.data.value?.items ?? [])
const workspaceItems = computed(() => workspaces.data.value ?? [])
const currentOperation = computed(() => operationItems.value.find(item => item.id === draft.value.operationId) ?? null)
const matchingBindings = computed(() => bindingItems.value.filter(item =>
  item.operationId === draft.value.operationId && item.providerContractId === draft.value.providerContractId))
const matchingWorkspaces = computed(() => workspaceItems.value.filter(item => item.environmentCode === draft.value.environmentCode))

watch([operationItems, providerContractItems, bindingItems], () => {
  const savedBinding = bindingItems.value.find(item => item.id === draft.value.bindingId)
  if (savedBinding) {
    draft.value.operationId = savedBinding.operationId
    draft.value.providerContractId = savedBinding.providerContractId
    return
  }
  const matching = bindingItems.value.find(item => item.operationId === draft.value.operationId &&
    item.providerContractId === draft.value.providerContractId)
  if (matching) { draft.value.bindingId = matching.id; return }
  if (!draft.value.operationId && !draft.value.providerContractId && bindingItems.value.length) {
    const latest = [...bindingItems.value].sort((a, b) => b.id - a.id)[0]!
    draft.value.operationId = latest.operationId; draft.value.providerContractId = latest.providerContractId
    draft.value.bindingId = latest.id
  }
}, { immediate: true })

watch(matchingWorkspaces, items => {
  if (items.length && !items.some(item => item.id === draft.value.workspaceId)) {
    draft.value.workspaceId = [...items].sort((a, b) => b.id - a.id)[0]!.id
  }
}, { immediate: true })

watch(draft, value => {
  const now = new Date().toISOString()
  window.localStorage.setItem(WIZARD_STORAGE_KEY, serializeWizardDraft(value, now))
  lastSavedAt.value = now
}, { deep: true })

const selection = computed(() => currentOperation.value && draft.value.providerContractId ? {
  operationId: currentOperation.value.id, operationCode: currentOperation.value.operationCode,
  providerContractId: draft.value.providerContractId, bindingId: draft.value.bindingId,
  workspaceId: draft.value.workspaceId, environmentCode: draft.value.environmentCode.trim()
} : null)
const assessment = useQuery({
  queryKey: computed(() => ['integration-wizard-assessment', selection.value]),
  queryFn: ({ signal }) => integrationWizardApi.assess(selection.value!, signal),
  enabled: computed(() => Boolean(selection.value?.environmentCode)), retry: false
})
const emptyFacts = computed<IntegrationWizardFacts>(() => ({
  hasOperation: Boolean(draft.value.operationId), publishedCanonicalKinds: [],
  hasProviderContract: Boolean(draft.value.providerContractId), hasPublishedProviderVersion: false,
  hasPublishedEndpoint: false, hasBinding: Boolean(draft.value.bindingId), publishedMappingDirections: [],
  hasPublishedBindingVersion: false, hasPublishedFixtureVersion: false,
  workspaceSelected: Boolean(draft.value.workspaceId), workspaceHasBindingVersion: false,
  hasPassedVerification: false, workspaceLifecycle: null, hasPublishedBundle: false,
  hasDeployment: false, hasActiveRoute: false
}))
const stages = computed(() => assessWizardStages(assessment.data.value ?? emptyFacts.value))
const progress = computed(() => wizardProgress(stages.value))
const nextStage = computed(() => stages.value.find(item => item.status === 'ACTION_REQUIRED') ?? null)
const completedCount = computed(() => stages.value.filter(item => item.status === 'COMPLETE').length)
const topLevelError = computed(() => [operations, providerContracts, bindings, workspaces].some(item => item.isError.value))
const loading = computed(() => [operations, providerContracts, bindings, workspaces].some(item => item.isPending.value) || assessment.isPending.value)

function contextChanged(): void {
  const match = matchingBindings.value[0]
  draft.value.bindingId = match?.id ?? null
  draft.value.workspaceId = null
}
function environmentChanged(): void { draft.value.workspaceId = null }
function stageType(status: WizardStageAssessment['status']): 'success' | 'warning' | 'info' {
  return status === 'COMPLETE' ? 'success' : status === 'ACTION_REQUIRED' ? 'warning' : 'info'
}
function stageLabel(status: WizardStageAssessment['status']): string {
  return status === 'COMPLETE' ? '已完成' : status === 'ACTION_REQUIRED' ? '下一步' : '等待前置'
}
function openStage(stage: WizardStageAssessment): void {
  if (stage.status === 'BLOCKED') return
  void router.push(stage.route)
}
function refresh(): void {
  void operations.refetch(); void providerContracts.refetch(); void bindings.refetch(); void workspaces.refetch()
  if (selection.value) void assessment.refetch()
}
function resetJourney(): void {
  window.localStorage.removeItem(WIZARD_STORAGE_KEY)
  draft.value = emptyWizardDraft()
  ElMessage.success('仅已清除本地向导上下文，平台资产未被修改')
}
function formatTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>第三方接入向导</h2><p>以现有专家资产和服务端生命周期为事实来源，定位第一个配置缺口，并在返回后自动恢复接入上下文。</p></div><el-tag effect="plain">Guided Journey</el-tag></div>
    <el-alert v-if="topLevelError || assessment.isError.value" class="command-notice" type="error" :closable="false"
      title="接入状态聚合失败，请确认 Control Plane 可用后重试。" show-icon />
    <el-alert class="command-notice" type="info" :closable="false"
      title="向导不会复制资产或绕过发布门禁；浏览器只保存 Operation、Contract、Binding、Workspace ID 和环境，不保存 Secret、报文或 Policy 内容。" show-icon />

    <div class="surface wizard-context-panel">
      <div class="section-title"><div><h3>接入上下文</h3><span class="subtle">自动保存于当前浏览器 · {{ formatTime(lastSavedAt) }}</span></div><div><el-button :loading="loading" @click="refresh">重新评估</el-button><el-button @click="resetJourney">重置向导</el-button></div></div>
      <div class="wizard-context-grid">
        <label><span>业务 Operation</span><el-select v-model="draft.operationId" filterable placeholder="先创建业务标准接口" @change="contextChanged"><el-option v-for="item in operationItems" :key="item.id" :label="`${item.operationName} · ${item.operationCode}`" :value="item.id" /></el-select></label>
        <label><span>第三方接口</span><el-select v-model="draft.providerContractId" filterable placeholder="先创建第三方接口" @change="contextChanged"><el-option v-for="item in providerContractItems" :key="item.id" :label="`${item.contractName} · ${item.contractCode}`" :value="item.id" /></el-select></label>
        <label><span>Environment</span><el-input v-model="draft.environmentCode" placeholder="test" @change="environmentChanged" /></label>
        <label><span>Binding</span><el-select v-model="draft.bindingId" clearable placeholder="等待 Operation 与 Contract 关联"><el-option v-for="item in matchingBindings" :key="item.id" :label="`${item.bindingName} · ${item.bindingCode}`" :value="item.id" /></el-select></label>
        <label class="wizard-context-wide"><span>Workspace</span><el-select v-model="draft.workspaceId" clearable filterable placeholder="发布前选择同环境 Workspace"><el-option v-for="item in matchingWorkspaces" :key="item.id" :label="`${item.workspaceName} · ${item.lifecycleStatus} · ${item.environmentCode}`" :value="item.id" /></el-select></label>
      </div>
    </div>

    <div class="wizard-summary-grid">
      <div class="surface wizard-progress-card"><span>总体进度</span><strong>{{ progress }}%</strong><el-progress :percentage="progress" :stroke-width="10" :show-text="false" /><small>{{ completedCount }} / {{ stages.length }} 个阶段已具备服务端证据</small></div>
      <div class="surface wizard-next-card"><span>当前建议</span><strong>{{ nextStage?.title ?? '接入链路已就绪' }}</strong><p>{{ nextStage?.evidence ?? '可以进入 Runtime 调用控制台验证 Canonical Request。' }}</p><el-button type="primary" :disabled="!nextStage" @click="nextStage && openStage(nextStage)">{{ nextStage ? '继续配置' : '全部完成' }}</el-button><el-button v-if="!nextStage" @click="router.push('/integration-assets/runtime-invoke')">验证调用</el-button></div>
    </div>

    <div class="wizard-stage-list" :class="{ 'is-loading': loading }">
      <article v-for="stage in stages" :key="stage.code" class="surface wizard-stage-card" :class="`wizard-stage-card--${stage.status.toLowerCase()}`">
        <div class="wizard-stage-index">{{ String(stage.order).padStart(2, '0') }}</div>
        <div class="wizard-stage-copy"><div><h3>{{ stage.title }}</h3><el-tag :type="stageType(stage.status)" size="small">{{ stageLabel(stage.status) }}</el-tag></div><p>{{ stage.description }}</p><small>{{ stage.evidence }}</small></div>
        <el-button link type="primary" :disabled="stage.status === 'BLOCKED'" @click="openStage(stage)">{{ stage.status === 'COMPLETE' ? '查看资产' : '进入处理' }} →</el-button>
      </article>
    </div>
  </section>
</template>
