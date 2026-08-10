<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { bindingVersionApi } from '../api/bindingVersionApi'
import { fixtureSuiteApi } from '../api/fixtureSuiteApi'
import { integrationAssetApi } from '../api/integrationAssetApi'
import { mappingAssetApi } from '../api/mappingAssetApi'
import { workspaceVerificationApi, type ReleaseWorkspace, type VerificationCheck, type VerificationJob, type WorkspaceRisk } from '../api/workspaceVerificationApi'

const selectedWorkspaceId = ref(0); const selectedBindingId = ref(0); const selectedBindingVersionId = ref(0)
const selectedSuiteId = ref(0); const selectedFixtureVersionId = ref(0); const errorMessage = ref('')
const createDialog = ref(false); const checkDialog = ref(false); const selectedJob = ref<VerificationJob | null>(null)
const checks = ref<VerificationCheck[]>([])
const workspaceCode = ref(''); const workspaceName = ref(''); const environmentCode = ref('local')
const riskLevel = ref<WorkspaceRisk>('LOW'); const ownerCode = ref('local-owner')

const workspaces = useQuery({ queryKey: ['release-workspaces'], queryFn: ({ signal }) => workspaceVerificationApi.workspaces(signal) })
const bindings = useQuery({ queryKey: ['integration-bindings'], queryFn: ({ signal }) => mappingAssetApi.bindings(signal) })
const endpoints = useQuery({ queryKey: ['integration-endpoints'], queryFn: ({ signal }) => integrationAssetApi.endpoints(undefined, signal) })
const workspaceItems = computed(() => workspaces.data.value ?? [])
const bindingItems = computed(() => bindings.data.value?.items.filter(item => item.status === 'ACTIVE') ?? [])
const endpointItems = computed(() => endpoints.data.value?.items ?? [])
const currentWorkspace = computed(() => workspaceItems.value.find(item => item.id === selectedWorkspaceId.value) ?? null)

const assets = useQuery({ queryKey: computed(() => ['release-workspace-assets', selectedWorkspaceId.value]), queryFn: ({ signal }) => workspaceVerificationApi.assets(selectedWorkspaceId.value, signal), enabled: computed(() => selectedWorkspaceId.value > 0) })
const jobs = useQuery({ queryKey: computed(() => ['workspace-verification-jobs', selectedWorkspaceId.value]), queryFn: ({ signal }) => workspaceVerificationApi.jobs(selectedWorkspaceId.value, signal), enabled: computed(() => selectedWorkspaceId.value > 0) })
const currentAsset = computed(() => assets.data.value?.[0] ?? null)
const bindingVersions = useQuery({ queryKey: computed(() => ['binding-versions', selectedBindingId.value]), queryFn: ({ signal }) => bindingVersionApi.versions(selectedBindingId.value, signal), enabled: computed(() => selectedBindingId.value > 0) })
const eligibleBindingVersions = computed(() => (bindingVersions.data.value ?? []).filter(version => version.lifecycleStatus === 'PUBLISHED' && endpointItems.value.find(endpoint => endpoint.id === version.endpointId)?.environmentCode === currentWorkspace.value?.environmentCode))
const suites = useQuery({ queryKey: computed(() => ['fixture-suites', selectedBindingId.value]), queryFn: ({ signal }) => fixtureSuiteApi.suites(selectedBindingId.value, signal), enabled: computed(() => selectedBindingId.value > 0) })
const suiteItems = computed(() => suites.data.value?.filter(item => item.status === 'ACTIVE') ?? [])
const fixtureVersions = useQuery({ queryKey: computed(() => ['fixture-suite-versions', selectedSuiteId.value]), queryFn: ({ signal }) => fixtureSuiteApi.versions(selectedSuiteId.value, signal), enabled: computed(() => selectedSuiteId.value > 0) })
const publishedFixtureVersions = computed(() => (fixtureVersions.data.value ?? []).filter(item => item.lifecycleStatus === 'PUBLISHED'))
const canVerify = computed(() => currentWorkspace.value?.lifecycleStatus === 'DRAFT' && Boolean(currentAsset.value) && selectedFixtureVersionId.value > 0)
const verificationPassed = computed(() => ['VERIFIED', 'IN_REVIEW', 'APPROVED', 'COMPILED'].includes(currentWorkspace.value?.lifecycleStatus ?? ''))

watch(workspaceItems, items => { if (items.length && !items.some(item => item.id === selectedWorkspaceId.value)) selectedWorkspaceId.value = items[0]!.id }, { immediate: true })
watch([currentAsset, bindingItems], () => {
  if (currentAsset.value) selectedBindingId.value = bindingItems.value.find(item => item.bindingCode === currentAsset.value?.assetCode)?.id ?? 0
  else if (!bindingItems.value.some(item => item.id === selectedBindingId.value)) selectedBindingId.value = bindingItems.value[0]?.id ?? 0
})
watch(eligibleBindingVersions, items => { if (!items.some(item => item.id === selectedBindingVersionId.value)) selectedBindingVersionId.value = items[0]?.id ?? 0 }, { immediate: true })
watch(suiteItems, items => { if (!items.some(item => item.id === selectedSuiteId.value)) selectedSuiteId.value = items[0]?.id ?? 0 }, { immediate: true })
watch(publishedFixtureVersions, items => { if (!items.some(item => item.id === selectedFixtureVersionId.value)) selectedFixtureVersionId.value = items[0]?.id ?? 0 }, { immediate: true })

function apiMessage(error: Error): string { return error instanceof ApiError ? error.message : error.message || '请求失败，请确认 Control Plane 状态。' }
function refreshWorkspace(): void { void workspaces.refetch(); void assets.refetch(); void jobs.refetch() }
function openCreate(): void {
  const suffix = new Date().toISOString().replace(/\D/g, '').slice(0, 14)
  workspaceCode.value = `integration.local.${suffix}`; workspaceName.value = '本地第三方接入验证'; environmentCode.value = 'local'
  riskLevel.value = 'LOW'; ownerCode.value = 'local-owner'; errorMessage.value = ''; createDialog.value = true
}
const createWorkspace = useMutation({ mutationFn: () => workspaceVerificationApi.createWorkspace({ workspaceCode: workspaceCode.value.trim(), workspaceName: workspaceName.value.trim(), baseBundleId: null, environmentCode: environmentCode.value.trim(), riskLevel: riskLevel.value, ownerCode: ownerCode.value.trim() }), onSuccess: value => { createDialog.value = false; void workspaces.refetch().then(() => { selectedWorkspaceId.value = value.id }); ElMessage.success('Workspace DRAFT 已创建') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const addAsset = useMutation({ mutationFn: () => workspaceVerificationApi.addBindingVersion(selectedWorkspaceId.value, selectedBindingId.value, selectedBindingVersionId.value), onSuccess: () => { void assets.refetch(); ElMessage.success('BindingVersion 已装配') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const verify = useMutation({ mutationFn: () => workspaceVerificationApi.verify(selectedWorkspaceId.value, selectedFixtureVersionId.value, currentWorkspace.value!.rowVersion), onSuccess: result => { refreshWorkspace(); ElMessage.success(result.status === 'PASSED' ? '服务端验证通过，Workspace 已进入 VERIFIED' : '验证已完成，请查看失败 Check') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const retry = useMutation({ mutationFn: (job: VerificationJob) => workspaceVerificationApi.retry(job.id, currentWorkspace.value!.rowVersion), onSuccess: result => { refreshWorkspace(); ElMessage.success(result.status === 'PASSED' ? '重试通过' : '重试完成，仍有失败 Check') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const loadChecks = useMutation({ mutationFn: (job: VerificationJob) => workspaceVerificationApi.checks(job.id), onSuccess: (result, job) => { checks.value = result; selectedJob.value = job; checkDialog.value = true }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })

function requestChecks(row: unknown): void { loadChecks.mutate(row as VerificationJob) }
function requestRetry(row: unknown): void { retry.mutate(row as VerificationJob) }
function workspaceLabel(item: ReleaseWorkspace): string { return `${item.workspaceName} · ${item.environmentCode} · ${item.lifecycleStatus}` }
function bindingLabel(id: number): string { const item = bindingItems.value.find(value => value.id === id); return item ? `${item.bindingName} · ${item.bindingCode}` : `Binding #${id}` }
function statusType(status: string): 'success' | 'danger' | 'warning' | 'info' { return status === 'PASSED' || status === 'VERIFIED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'RUNNING' ? 'warning' : 'info' }
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>Workspace 配置与服务端验证</h2><p>把已发布 BindingVersion 和 FixtureSuiteVersion 组成候选发布单元，由 Verification Engine 生成真实 Check 与证据。</p></div><el-tag effect="plain">Server Executed</el-tag></div>
    <el-alert v-if="errorMessage" class="command-notice" type="error" :title="errorMessage" closable @close="errorMessage = ''" show-icon />
    <el-alert class="command-notice" type="info" :closable="false" title="验证成功会把 DRAFT 自动转换为 VERIFIED；页面不会用前端模拟结果。失败保留 DRAFT，可查看完整 Check 后重试。" show-icon />

    <div class="workspace-verification-steps">
      <div :class="{ done: currentWorkspace }"><span>1</span><strong>Workspace</strong><small>{{ currentWorkspace ? currentWorkspace.lifecycleStatus : '未选择' }}</small></div>
      <div :class="{ done: currentAsset }"><span>2</span><strong>BindingVersion</strong><small>{{ currentAsset ? `#${currentAsset.assetVersionId}` : '待装配' }}</small></div>
      <div :class="{ done: selectedFixtureVersionId }"><span>3</span><strong>FixtureSuiteVersion</strong><small>{{ selectedFixtureVersionId ? `#${selectedFixtureVersionId}` : '待选择' }}</small></div>
      <div :class="{ done: verificationPassed }"><span>4</span><strong>Verification</strong><small>{{ verificationPassed ? 'PASSED' : '待执行' }}</small></div>
    </div>

    <div class="surface workspace-release-panel">
      <div class="asset-toolbar canonical-version-toolbar"><div><strong>候选 Workspace</strong><span>配置工作台只负责验证发布前候选；投入运行后的资产追溯仍位于运营治理菜单。</span></div><div><el-select v-model="selectedWorkspaceId" filterable><el-option v-for="item in workspaceItems" :key="item.id" :label="workspaceLabel(item)" :value="item.id" /></el-select><el-button type="primary" @click="openCreate">新建 Workspace</el-button></div></div>
      <AsyncStatePanel :loading="workspaces.isPending.value" :error="workspaces.isError.value" error-title="Workspace 读取失败" @retry="workspaces.refetch()">
        <template v-if="currentWorkspace"><div class="workspace-release-facts"><div><span>编码</span><strong class="mono">{{ currentWorkspace.workspaceCode }}</strong></div><div><span>环境</span><strong>{{ currentWorkspace.environmentCode }}</strong></div><div><span>风险</span><strong>{{ currentWorkspace.riskLevel }}</strong></div><div><span>负责人</span><strong>{{ currentWorkspace.ownerCode }}</strong></div><div><span>Row Version</span><strong>{{ currentWorkspace.rowVersion }}</strong></div><div><span>生命周期</span><el-tag :type="statusType(currentWorkspace.lifecycleStatus)">{{ currentWorkspace.lifecycleStatus }}</el-tag></div></div></template>
        <el-empty v-else description="暂无 Workspace，请先创建" />
      </AsyncStatePanel>
    </div>

    <div v-if="currentWorkspace" class="workspace-configuration-grid">
      <div class="surface workspace-config-card"><div class="section-title"><h3>① 装配 BindingVersion</h3><el-tag v-if="currentAsset" type="success">已冻结</el-tag></div><template v-if="currentAsset"><div class="current-version-callout"><span>Workspace Asset</span><strong style="font-size:20px">{{ currentAsset.assetCode }}</strong><small>BindingVersion #{{ currentAsset.assetVersionId }} · {{ currentAsset.changeType }}</small></div></template><template v-else><el-form class="workspace-asset-form" label-position="top"><el-form-item label="ACTIVE Binding"><el-select v-model="selectedBindingId" filterable><el-option v-for="item in bindingItems" :key="item.id" :label="bindingLabel(item.id)" :value="item.id" /></el-select></el-form-item><el-form-item label="同环境 Published BindingVersion"><el-select v-model="selectedBindingVersionId"><el-option v-for="item in eligibleBindingVersions" :key="item.id" :label="`Revision ${item.versionNo} · #${item.id}`" :value="item.id" /></el-select><small class="form-hint">自动按 Endpoint 环境 {{ currentWorkspace.environmentCode }} 过滤。</small></el-form-item><el-button type="primary" :disabled="currentWorkspace.lifecycleStatus !== 'DRAFT' || !selectedBindingVersionId" :loading="addAsset.isPending.value" @click="addAsset.mutate()">装配不可变版本</el-button></el-form></template></div>
      <div class="surface workspace-config-card"><div class="section-title"><h3>② 选择 FixtureSuiteVersion</h3><el-tag v-if="selectedFixtureVersionId" type="success">可验证</el-tag></div><el-form class="workspace-asset-form" label-position="top"><el-form-item label="ACTIVE FixtureSuite"><el-select v-model="selectedSuiteId" :disabled="!currentAsset"><el-option v-for="item in suiteItems" :key="item.id" :label="`${item.suiteName} · ${item.suiteCode}`" :value="item.id" /></el-select></el-form-item><el-form-item label="Published Version"><el-select v-model="selectedFixtureVersionId" :disabled="!currentAsset"><el-option v-for="item in publishedFixtureVersions" :key="item.id" :label="`Revision ${item.versionNo} · ${item.cases.length} Cases · #${item.id}`" :value="item.id" /></el-select><small class="form-hint">只显示属于当前 Binding 的 ACTIVE Suite 和已发布版本。</small></el-form-item><el-button type="primary" :disabled="!canVerify" :loading="verify.isPending.value" @click="verify.mutate()">执行服务端验证</el-button></el-form></div>
    </div>

    <div v-if="currentWorkspace" class="surface verification-jobs-panel"><div class="section-title"><h3>Verification Jobs</h3><el-button :loading="jobs.isFetching.value" @click="jobs.refetch()">刷新</el-button></div><AsyncStatePanel :loading="jobs.isPending.value" :error="jobs.isError.value" error-title="验证任务读取失败" @retry="jobs.refetch()"><el-empty v-if="!jobs.data.value?.length" description="尚未执行服务端验证" /><el-table v-else :data="jobs.data.value"><el-table-column label="Run" width="85"><template #default="{ row }">#{{ row.runNo }}</template></el-table-column><el-table-column prop="runType" label="类型" width="110" /><el-table-column label="结果" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column><el-table-column label="通过 / 失败 / 总数" width="160"><template #default="{ row }">{{ row.passedCount }} / {{ row.failedCount }} / {{ row.totalCount }}</template></el-table-column><el-table-column label="Fixture Version" width="130"><template #default="{ row }">#{{ row.resultSummary.fixtureSuiteVersionId ?? '—' }}</template></el-table-column><el-table-column prop="evidenceUri" label="证据" min-width="230" /><el-table-column label="操作" width="170"><template #default="{ row }"><el-button link type="primary" @click="requestChecks(row)">查看 Checks</el-button><el-button v-if="row.status === 'FAILED' && currentWorkspace?.lifecycleStatus === 'DRAFT'" link type="primary" @click="requestRetry(row)">重试</el-button></template></el-table-column></el-table></AsyncStatePanel></div>

    <el-dialog v-model="createDialog" title="新建配置 Workspace" width="720px"><el-form class="dialog-form" label-position="top"><div class="form-two-columns"><el-form-item label="Workspace Code" required><el-input v-model="workspaceCode" /></el-form-item><el-form-item label="Workspace Name" required><el-input v-model="workspaceName" /></el-form-item><el-form-item label="Environment" required><el-input v-model="environmentCode" /></el-form-item><el-form-item label="Risk Level"><el-select v-model="riskLevel"><el-option v-for="item in ['LOW','MEDIUM','HIGH','CRITICAL']" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="Owner Code" required><el-input v-model="ownerCode" /></el-form-item></div></el-form><template #footer><el-button @click="createDialog = false">取消</el-button><el-button type="primary" :disabled="!workspaceCode.trim() || !workspaceName.trim() || !environmentCode.trim() || !ownerCode.trim()" :loading="createWorkspace.isPending.value" @click="createWorkspace.mutate()">创建 DRAFT</el-button></template></el-dialog>

    <el-dialog v-model="checkDialog" :title="`Verification #${selectedJob?.runNo ?? ''} Checks`" width="1050px"><div v-if="selectedJob" class="verification-check-summary"><span>状态 <strong>{{ selectedJob.status }}</strong></span><span>通过 <strong>{{ selectedJob.passedCount }}</strong></span><span>失败 <strong>{{ selectedJob.failedCount }}</strong></span><span>证据 <strong class="mono">{{ selectedJob.evidenceUri }}</strong></span></div><el-table :data="checks"><el-table-column prop="checkCode" label="Check Code" min-width="190" /><el-table-column prop="checkName" label="名称" min-width="200" /><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column><el-table-column label="Details / Evidence" min-width="360"><template #default="{ row }"><details><summary>查看 JSON 证据</summary><pre class="verification-evidence">{{ JSON.stringify({ details: row.resultDetails, evidence: row.evidenceDocument }, null, 2) }}</pre></details></template></el-table-column></el-table><template #footer><el-button @click="checkDialog = false">关闭</el-button></template></el-dialog>
  </section>
</template>
