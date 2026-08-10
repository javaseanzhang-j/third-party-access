<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { canonicalAssetApi } from '../api/canonicalAssetApi'
import { deploymentApi, type DeploymentAsset } from '../api/deploymentApi'
import { workspaceReleaseApi } from '../api/workspaceReleaseApi'
import { workspaceVerificationApi, type ReleaseWorkspace } from '../api/workspaceVerificationApi'

const selectedWorkspaceId = ref(0); const selectedBundleId = ref(0); const errorMessage = ref('')
const createDialog = ref(false); const activateDialog = ref(false); const trafficDialog = ref(false); const rollbackDialog = ref(false); const evidenceDialog = ref(false)
const selectedDeployment = ref<DeploymentAsset | null>(null); const deploymentCode = ref(''); const rolloutText = ref('{}')
const initialTraffic = ref(100); const targetTraffic = ref(100); const rollbackCode = ref(''); const rollbackReason = ref('回滚到上一稳定 Bundle。')

const workspaces = useQuery({ queryKey: ['release-workspaces'], queryFn: ({ signal }) => workspaceVerificationApi.workspaces(signal) })
const workspaceItems = computed(() => workspaces.data.value ?? [])
const currentWorkspace = computed(() => workspaceItems.value.find(item => item.id === selectedWorkspaceId.value) ?? null)
const bundles = useQuery({ queryKey: computed(() => ['workspace-bundles', selectedWorkspaceId.value]), queryFn: ({ signal }) => workspaceReleaseApi.bundles(selectedWorkspaceId.value, signal), enabled: computed(() => selectedWorkspaceId.value > 0) })
const publishedBundles = computed(() => (bundles.data.value ?? []).filter(item => item.lifecycleStatus === 'PUBLISHED'))
const currentBundle = computed(() => publishedBundles.value.find(item => item.id === selectedBundleId.value) ?? null)
const operations = useQuery({ queryKey: ['canonical-operations'], queryFn: ({ signal }) => canonicalAssetApi.operations(undefined, signal) })
const operationItems = computed(() => operations.data.value?.items ?? [])
const operationCode = computed(() => operationItems.value.find(item => item.id === currentBundle.value?.operationId)?.operationCode ?? '')
const environment = computed(() => currentBundle.value?.environmentCode ?? currentWorkspace.value?.environmentCode ?? '')
const deployments = useQuery({ queryKey: computed(() => ['deployments', operationCode.value, environment.value]), queryFn: ({ signal }) => deploymentApi.deployments(operationCode.value, environment.value, signal), enabled: computed(() => Boolean(operationCode.value && environment.value)) })
const deploymentItems = computed(() => deployments.data.value ?? [])
const activeRoute = useQuery({ queryKey: computed(() => ['active-route', operationCode.value, environment.value]), queryFn: ({ signal }) => deploymentApi.activeRoute(operationCode.value, environment.value, signal), enabled: computed(() => Boolean(operationCode.value && environment.value)), retry: false })

watch(workspaceItems, items => { if (items.length && !items.some(item => item.id === selectedWorkspaceId.value)) selectedWorkspaceId.value = items.find(item => item.lifecycleStatus === 'COMPILED')?.id ?? items[0]!.id }, { immediate: true })
watch(publishedBundles, items => { if (!items.some(item => item.id === selectedBundleId.value)) selectedBundleId.value = items[0]?.id ?? 0 }, { immediate: true })

function apiMessage(error: Error): string { return error instanceof ApiError ? error.message : error.message || '请求失败，请确认 Control Plane 与 Runtime 状态。' }
function parseRollout(): Record<string, unknown> {
  let value: unknown
  try { value = JSON.parse(rolloutText.value) as unknown } catch { throw new Error('Rollout Metadata 不是合法 JSON。') }
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('Rollout Metadata 必须是 JSON Object。')
  return value as Record<string, unknown>
}
function refresh(): void { void deployments.refetch(); void activeRoute.refetch() }
function openCreate(): void {
  const suffix = new Date().toISOString().replace(/\D/g, '').slice(0, 14)
  deploymentCode.value = operationCode.value ? `${operationCode.value}.${environment.value}.${suffix}` : `deployment.${suffix}`
  rolloutText.value = JSON.stringify({ strategy: deploymentItems.value.some(item => item.deploymentStatus === 'ACTIVE') ? 'CANARY' : 'FULL' }, null, 2)
  errorMessage.value = ''; createDialog.value = true
}
function openActivate(row: DeploymentAsset): void { selectedDeployment.value = row; initialTraffic.value = row.previousDeploymentId ? 10 : 100; activateDialog.value = true }
function openTraffic(row: DeploymentAsset): void { selectedDeployment.value = row; targetTraffic.value = Math.min(100, Math.max(Number(row.trafficPercentage) + 10, 20)); trafficDialog.value = true }
function openRollback(row: DeploymentAsset): void { selectedDeployment.value = row; rollbackCode.value = `${row.deploymentCode}.rollback.${Date.now()}`; rollbackReason.value = '回滚到上一稳定 Bundle。'; rollbackDialog.value = true }
function showEvidence(row: DeploymentAsset): void { selectedDeployment.value = row; evidenceDialog.value = true }

const createDeployment = useMutation({ mutationFn: () => deploymentApi.create(deploymentCode.value.trim(), selectedBundleId.value, parseRollout()), onSuccess: () => { createDialog.value = false; refresh(); ElMessage.success('Deployment PENDING 已创建') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const preheat = useMutation({ mutationFn: (row: DeploymentAsset) => deploymentApi.preheat(row.id, row.rowVersion), onSuccess: value => { refresh(); ElMessage.success(value.deploymentStatus === 'READY' ? 'Runtime 预热通过' : '预热未达到法定实例数') }, onError: (e: Error) => { errorMessage.value = apiMessage(e); refresh() } })
const activate = useMutation({ mutationFn: () => deploymentApi.activate(selectedDeployment.value!.id, selectedDeployment.value!.rowVersion, initialTraffic.value), onSuccess: () => { activateDialog.value = false; refresh(); ElMessage.success('Deployment 已激活') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const increaseTraffic = useMutation({ mutationFn: () => deploymentApi.traffic(selectedDeployment.value!.id, selectedDeployment.value!.rowVersion, targetTraffic.value), onSuccess: () => { trafficDialog.value = false; refresh(); ElMessage.success('Canary 流量已单调增加') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const rollback = useMutation({ mutationFn: () => deploymentApi.rollback(selectedDeployment.value!.id, selectedDeployment.value!.rowVersion, rollbackCode.value.trim(), rollbackReason.value.trim()), onSuccess: () => { rollbackDialog.value = false; refresh(); ElMessage.success('已创建 100% 回滚 Deployment') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })

function submitCreate(): void { errorMessage.value = ''; try { parseRollout(); createDeployment.mutate() } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'Metadata 解析失败。' } }
function requestPreheat(row: unknown): void { preheat.mutate(row as DeploymentAsset) }
function requestActivate(row: unknown): void { openActivate(row as DeploymentAsset) }
function requestTraffic(row: unknown): void { openTraffic(row as DeploymentAsset) }
function requestRollback(row: unknown): void { openRollback(row as DeploymentAsset) }
function requestEvidence(row: unknown): void { showEvidence(row as DeploymentAsset) }
function workspaceLabel(item: ReleaseWorkspace): string { return `${item.workspaceName} · ${item.environmentCode} · ${item.lifecycleStatus}` }
function statusType(status: string): 'success' | 'danger' | 'warning' | 'info' { return status === 'ACTIVE' ? 'success' : ['PREHEAT_FAILED', 'ROLLED_BACK'].includes(status) ? 'danger' : ['PENDING', 'PREHEATING', 'READY'].includes(status) ? 'warning' : 'info' }
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>Deployment 激活与回滚</h2><p>把 PUBLISHED Bundle 预热到 Runtime，受控激活、确定性灰度、单调扩量，并通过新 Deployment 回滚。</p></div><el-tag effect="plain">Dynamic Route</el-tag></div>
    <el-alert v-if="errorMessage" class="command-notice" type="error" :title="errorMessage" closable @close="errorMessage = ''" show-icon />
    <el-alert class="command-notice" type="info" :closable="false" title="预热会真实调用 Runtime 并校验 Bundle、Schema Profile、Endpoint、Policy Provider、Secret Reference 和 checksum；未达到法定实例数不能激活。" show-icon />

    <div class="surface deployment-context-panel"><div class="asset-toolbar deployment-context-toolbar"><div><strong>发布上下文</strong><span>从已发布 Bundle 创建 Deployment，环境直接继承 Bundle。</span></div><div><el-select v-model="selectedWorkspaceId" filterable><el-option v-for="item in workspaceItems" :key="item.id" :label="workspaceLabel(item)" :value="item.id" /></el-select><el-select v-model="selectedBundleId"><el-option v-for="item in publishedBundles" :key="item.id" :label="`${item.bundleCode}@${item.bundleVersion}`" :value="item.id" /></el-select><el-button type="primary" :disabled="!selectedBundleId || !operationCode" @click="openCreate">创建 Deployment</el-button></div></div><div v-if="currentBundle" class="deployment-context-facts"><div><span>Operation</span><strong>{{ operationCode }}</strong></div><div><span>Environment</span><strong>{{ environment }}</strong></div><div><span>Bundle</span><strong>{{ currentBundle.bundleCode }}@{{ currentBundle.bundleVersion }}</strong></div><div><span>Checksum</span><strong class="mono">{{ currentBundle.artifactChecksum }}</strong></div></div></div>

    <div class="surface active-route-panel"><div class="section-title"><h3>Active Route</h3><el-button :loading="activeRoute.isFetching.value" @click="activeRoute.refetch()">刷新路由</el-button></div><template v-if="activeRoute.data.value"><div class="active-route-revision"><span>Route Revision</span><strong class="mono">{{ activeRoute.data.value.revision }}</strong></div><div class="active-route-targets"><div v-for="target in activeRoute.data.value.targets" :key="target.deploymentId"><span>{{ target.trafficPercentage }}%</span><strong>{{ target.deploymentCode }}</strong><small>{{ target.bundleCode }}@{{ target.bundleVersion }}</small></div></div></template><el-empty v-else description="当前 Operation + Environment 没有活动路由" :image-size="55" /></div>

    <div class="surface deployment-list-panel"><div class="section-title"><h3>Deployment History</h3><el-button :loading="deployments.isFetching.value" @click="refresh">刷新</el-button></div><AsyncStatePanel :loading="deployments.isPending.value" :error="deployments.isError.value" error-title="Deployment 读取失败" @retry="deployments.refetch()"><el-empty v-if="!deploymentItems.length" description="该 Operation 与环境尚无 Deployment" /><el-table v-else :data="deploymentItems"><el-table-column label="Deployment" min-width="230"><template #default="{ row }"><strong>{{ row.deploymentCode }}</strong><small class="bundle-version">#{{ row.id }} · Bundle #{{ row.bundleId }}</small></template></el-table-column><el-table-column label="状态" width="125"><template #default="{ row }"><el-tag :type="statusType(row.deploymentStatus)">{{ row.deploymentStatus }}</el-tag></template></el-table-column><el-table-column label="流量" width="90"><template #default="{ row }">{{ row.trafficPercentage }}%</template></el-table-column><el-table-column label="Previous" width="100"><template #default="{ row }">{{ row.previousDeploymentId ? `#${row.previousDeploymentId}` : '—' }}</template></el-table-column><el-table-column prop="rowVersion" label="RV" width="70" /><el-table-column prop="deployedBy" label="操作者" width="120" /><el-table-column label="操作" width="290"><template #default="{ row }"><el-button link type="primary" @click="requestEvidence(row)">证据</el-button><el-button v-if="row.deploymentStatus === 'PENDING' || row.deploymentStatus === 'PREHEAT_FAILED'" link type="primary" @click="requestPreheat(row)">预热</el-button><el-button v-if="row.deploymentStatus === 'READY'" link type="primary" @click="requestActivate(row)">激活</el-button><el-button v-if="row.deploymentStatus === 'ACTIVE' && row.previousDeploymentId && Number(row.trafficPercentage) < 100" link type="primary" @click="requestTraffic(row)">扩量</el-button><el-button v-if="row.deploymentStatus === 'ACTIVE' && row.previousDeploymentId" link type="danger" @click="requestRollback(row)">回滚</el-button></template></el-table-column></el-table></AsyncStatePanel></div>

    <el-dialog v-model="createDialog" title="创建 Deployment" width="720px"><el-alert type="warning" :closable="false" title="创建只生成 PENDING 记录，不会自动预热或激活。" show-icon /><el-form class="dialog-form" label-position="top"><el-form-item label="Deployment Code" required><el-input v-model="deploymentCode" /></el-form-item><el-form-item label="PUBLISHED Bundle"><el-input :model-value="currentBundle ? `${currentBundle.bundleCode}@${currentBundle.bundleVersion}` : ''" disabled /></el-form-item><el-form-item label="Rollout Metadata"><el-input v-model="rolloutText" type="textarea" :rows="7" class="schema-editor" /></el-form-item></el-form><template #footer><el-button @click="createDialog = false">取消</el-button><el-button type="primary" :disabled="!deploymentCode.trim()" :loading="createDeployment.isPending.value" @click="submitCreate">创建 PENDING</el-button></template></el-dialog>
    <el-dialog v-model="activateDialog" title="激活 Deployment" width="620px"><el-alert type="info" :closable="false" :title="selectedDeployment?.previousDeploymentId ? '检测到上一 ACTIVE Deployment，可使用 Canary 初始流量。' : '首个 Deployment 必须以 100% 流量激活。'" show-icon /><el-form class="dialog-form" label-position="top"><el-form-item label="Initial Traffic (%)"><el-input-number v-model="initialTraffic" :min="1" :max="100" :precision="2" :disabled="!selectedDeployment?.previousDeploymentId" /></el-form-item></el-form><template #footer><el-button @click="activateDialog = false">取消</el-button><el-button type="primary" :loading="activate.isPending.value" @click="activate.mutate()">确认激活</el-button></template></el-dialog>
    <el-dialog v-model="trafficDialog" title="单调增加 Canary 流量" width="620px"><el-form class="dialog-form" label-position="top"><el-form-item label="Target Traffic (%)"><el-input-number v-model="targetTraffic" :min="1" :max="100" :precision="2" /><small class="form-hint">只能增加；降低流量必须执行回滚。</small></el-form-item></el-form><template #footer><el-button @click="trafficDialog = false">取消</el-button><el-button type="primary" :loading="increaseTraffic.isPending.value" @click="increaseTraffic.mutate()">确认扩量</el-button></template></el-dialog>
    <el-dialog v-model="rollbackDialog" title="回滚 Deployment" width="700px"><el-alert type="warning" :closable="false" title="回滚会结束当前 Deployment，并新建一个 100% 指向上一 Bundle 的不可变 Deployment。" show-icon /><el-form class="dialog-form" label-position="top"><el-form-item label="Rollback Deployment Code" required><el-input v-model="rollbackCode" /></el-form-item><el-form-item label="Reason" required><el-input v-model="rollbackReason" type="textarea" :rows="4" /></el-form-item></el-form><template #footer><el-button @click="rollbackDialog = false">取消</el-button><el-button type="danger" :disabled="!rollbackCode.trim() || !rollbackReason.trim()" :loading="rollback.isPending.value" @click="rollback.mutate()">确认回滚</el-button></template></el-dialog>
    <el-dialog v-model="evidenceDialog" title="Deployment Evidence" width="900px"><template v-if="selectedDeployment"><div class="fact-grid deployment-evidence-facts"><div class="fact"><label>Deployment</label><div>{{ selectedDeployment.deploymentCode }}</div></div><div class="fact"><label>Status / Traffic</label><div>{{ selectedDeployment.deploymentStatus }} / {{ selectedDeployment.trafficPercentage }}%</div></div></div><pre class="plan-preview">{{ JSON.stringify({ instanceStatus: selectedDeployment.instanceStatus, preheatEvidence: selectedDeployment.preheatEvidence, rolloutMetadata: selectedDeployment.rolloutMetadata }, null, 2) }}</pre></template><template #footer><el-button @click="evidenceDialog = false">关闭</el-button></template></el-dialog>
  </section>
</template>
