<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import AsyncStatePanel from '@/components/AsyncStatePanel.vue'
import { workspaceReleaseApi, type ApprovalDecision, type DeploymentBundle } from '../api/workspaceReleaseApi'
import { workspaceVerificationApi, type ReleaseWorkspace } from '../api/workspaceVerificationApi'

const selectedWorkspaceId = ref(0); const errorMessage = ref('')
const approvalDialog = ref(false); const bundleDialog = ref(false); const manifestDialog = ref(false)
const approvalStage = ref<'RELEASE' | 'SECURITY'>('RELEASE'); const decision = ref<ApprovalDecision>('APPROVED')
const approverCode = ref('release-reviewer'); const decisionComment = ref('已核对服务端验证证据与依赖闭包。')
const evidenceText = ref('{}'); const bundleCode = ref('')
const selectedBundle = ref<DeploymentBundle | null>(null)

const workspaces = useQuery({ queryKey: ['release-workspaces'], queryFn: ({ signal }) => workspaceVerificationApi.workspaces(signal) })
const workspaceItems = computed(() => workspaces.data.value ?? [])
const currentWorkspace = computed(() => workspaceItems.value.find(item => item.id === selectedWorkspaceId.value) ?? null)
const approvals = useQuery({ queryKey: computed(() => ['workspace-approvals', selectedWorkspaceId.value]), queryFn: ({ signal }) => workspaceReleaseApi.approvals(selectedWorkspaceId.value, signal), enabled: computed(() => selectedWorkspaceId.value > 0) })
const bundles = useQuery({ queryKey: computed(() => ['workspace-bundles', selectedWorkspaceId.value]), queryFn: ({ signal }) => workspaceReleaseApi.bundles(selectedWorkspaceId.value, signal), enabled: computed(() => selectedWorkspaceId.value > 0) })
const jobs = useQuery({ queryKey: computed(() => ['workspace-verification-jobs', selectedWorkspaceId.value]), queryFn: ({ signal }) => workspaceVerificationApi.jobs(selectedWorkspaceId.value, signal), enabled: computed(() => selectedWorkspaceId.value > 0) })
const approvalItems = computed(() => approvals.data.value ?? [])
const bundleItems = computed(() => bundles.data.value ?? [])
const latestPassed = computed(() => jobs.data.value?.find(item => item.status === 'PASSED' && item.totalCount > 0) ?? null)
const needsSecurity = computed(() => ['HIGH', 'CRITICAL'].includes(currentWorkspace.value?.riskLevel ?? ''))
const releaseApproved = computed(() => approvalItems.value.some(item => item.approvalStage === 'RELEASE' && item.decision === 'APPROVED'))
const securityApproved = computed(() => approvalItems.value.some(item => item.approvalStage === 'SECURITY' && item.decision === 'APPROVED'))
const approvalComplete = computed(() => releaseApproved.value && (!needsSecurity.value || securityApproved.value))
const publishedBundle = computed(() => bundleItems.value.find(item => item.lifecycleStatus === 'PUBLISHED') ?? null)

watch(workspaceItems, items => { if (items.length && !items.some(item => item.id === selectedWorkspaceId.value)) selectedWorkspaceId.value = items[0]!.id }, { immediate: true })

function apiMessage(error: Error): string { return error instanceof ApiError ? error.message : error.message || '请求失败，请确认 Control Plane 状态。' }
function refresh(): void { void workspaces.refetch(); void approvals.refetch(); void bundles.refetch(); void jobs.refetch() }
function parseEvidence(): Record<string, unknown> {
  let value: unknown
  try { value = JSON.parse(evidenceText.value) as unknown } catch { throw new Error('Evidence Snapshot 不是合法 JSON。') }
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('Evidence Snapshot 必须是 JSON Object。')
  return value as Record<string, unknown>
}
function openApproval(): void {
  approvalStage.value = releaseApproved.value && needsSecurity.value && !securityApproved.value ? 'SECURITY' : 'RELEASE'
  approverCode.value = approvalStage.value === 'SECURITY' ? 'security-reviewer' : 'release-reviewer'
  decision.value = 'APPROVED'; decisionComment.value = '已核对服务端验证证据与依赖闭包。'
  const run = latestPassed.value
  evidenceText.value = JSON.stringify({ verificationRunId: run?.id ?? null, evidenceUri: run?.evidenceUri ?? null,
    runType: run?.runType ?? null, passedCount: run?.passedCount ?? 0, failedCount: run?.failedCount ?? 0,
    fixtureSuiteVersionId: run?.resultSummary.fixtureSuiteVersionId ?? null }, null, 2)
  errorMessage.value = ''; approvalDialog.value = true
}
function openCompile(): void {
  bundleCode.value = currentWorkspace.value ? `bundle.${currentWorkspace.value.workspaceCode}` : ''
  errorMessage.value = ''; bundleDialog.value = true
}
const submitReview = useMutation({ mutationFn: () => workspaceReleaseApi.submitReview(selectedWorkspaceId.value, currentWorkspace.value!.rowVersion), onSuccess: () => { refresh(); ElMessage.success('Workspace 已提交评审') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const decideApproval = useMutation({ mutationFn: () => workspaceReleaseApi.decide(selectedWorkspaceId.value, { approvalStage: approvalStage.value, decision: decision.value, decisionComment: decisionComment.value.trim() || null, evidenceSnapshot: parseEvidence(), rowVersion: currentWorkspace.value!.rowVersion, approverCode: approverCode.value.trim() }), onSuccess: () => { approvalDialog.value = false; refresh(); ElMessage.success(decision.value === 'APPROVED' ? '审批已记录' : 'Workspace 已驳回到 DRAFT') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const compileBundle = useMutation({ mutationFn: () => workspaceReleaseApi.compile(selectedWorkspaceId.value, bundleCode.value.trim(), currentWorkspace.value!.rowVersion), onSuccess: value => { bundleDialog.value = false; selectedBundle.value = value; refresh(); ElMessage.success(`发布包 ${value.bundleVersion} 已编译就绪`) }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })
const publishBundle = useMutation({ mutationFn: (bundle: DeploymentBundle) => workspaceReleaseApi.publish(bundle.id), onSuccess: value => { selectedBundle.value = value; refresh(); ElMessage.success('Bundle 已发布') }, onError: (e: Error) => { errorMessage.value = apiMessage(e) } })

function submitDecision(): void {
  errorMessage.value = ''
  if (!approverCode.value.trim()) { errorMessage.value = '审批人代码不能为空。'; return }
  if (approverCode.value.trim() === currentWorkspace.value?.ownerCode) { errorMessage.value = 'Workspace Owner 不能审批自己的 Workspace。'; return }
  try { parseEvidence(); decideApproval.mutate() } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'Evidence 解析失败。' }
}
function requestPublish(row: unknown): void { publishBundle.mutate(row as DeploymentBundle) }
function showManifest(row: unknown): void { selectedBundle.value = row as DeploymentBundle; manifestDialog.value = true }
function statusLabel(value: string): string { return ({ DRAFT: '草稿', VERIFIED: '已验证', IN_REVIEW: '评审中', APPROVED: '已批准', COMPILED: '已编译', READY: '待发布', PUBLISHED: '已发布', REJECTED: '已拒绝', PASSED: '已通过', FAILED: '失败', LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '严重', RELEASE: '发布评审', SECURITY: '安全评审' } as Record<string,string>)[value] ?? value }
function workspaceLabel(item: ReleaseWorkspace): string { return `${item.workspaceName} · ${statusLabel(item.riskLevel)}风险 · ${statusLabel(item.lifecycleStatus)}` }
function statusType(status: string): 'success' | 'danger' | 'warning' | 'info' { return ['PUBLISHED', 'APPROVED', 'COMPILED'].includes(status) ? 'success' : status === 'REJECTED' ? 'danger' : ['IN_REVIEW', 'READY'].includes(status) ? 'warning' : 'info' }
function formatTime(value: string | null): string { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
</script>

<template>
  <section>
    <div class="page-heading"><div><h2>验证空间评审与发布</h2><p>基于服务端验证证据完成受控审批，将已批准的配置编译为不可变发布包。</p></div><el-tag effect="plain">发布门禁</el-tag></div>
    <el-alert v-if="errorMessage" class="command-notice" type="error" :title="errorMessage" closable @close="errorMessage = ''" show-icon />
    <el-alert class="command-notice" type="warning" :closable="false" title="本地审批人是审计标签，不是可信认证身份。高风险和严重风险配置必须由发布评审、安全评审两个不同审批人批准，且审批人不能是配置负责人。" show-icon />

    <div class="workspace-release-steps">
      <div :class="{ done: currentWorkspace && currentWorkspace.lifecycleStatus !== 'DRAFT' }"><span>1</span><strong>完成验证</strong><small>{{ latestPassed ? `验证 #${latestPassed.runNo}` : '等待证据' }}</small></div>
      <div :class="{ done: ['IN_REVIEW','APPROVED','COMPILED'].includes(currentWorkspace?.lifecycleStatus ?? '') }"><span>2</span><strong>提交评审</strong><small>{{ currentWorkspace?.lifecycleStatus === 'VERIFIED' ? '待提交' : '已提交' }}</small></div>
      <div :class="{ done: approvalComplete }"><span>3</span><strong>完成批准</strong><small>{{ needsSecurity ? '发布 + 安全评审' : '发布评审' }}</small></div>
      <div :class="{ done: Boolean(publishedBundle) }"><span>4</span><strong>发布包</strong><small>{{ publishedBundle ? '已发布' : statusLabel(bundleItems[0]?.lifecycleStatus ?? '待编译') }}</small></div>
    </div>

    <div class="surface workspace-release-panel">
      <div class="asset-toolbar canonical-version-toolbar"><div><strong>发布候选</strong><span>选择已完成验证的配置空间；草稿应返回验证页面继续处理。</span></div><div><el-select v-model="selectedWorkspaceId" filterable><el-option v-for="item in workspaceItems" :key="item.id" :label="workspaceLabel(item)" :value="item.id" /></el-select><el-button :loading="workspaces.isFetching.value" @click="refresh">刷新</el-button></div></div>
      <AsyncStatePanel :loading="workspaces.isPending.value" :error="workspaces.isError.value" error-title="Workspace 读取失败" @retry="workspaces.refetch()"><template v-if="currentWorkspace"><div class="workspace-release-facts"><div><span>编码</span><strong class="mono">{{ currentWorkspace.workspaceCode }}</strong></div><div><span>环境</span><strong>{{ currentWorkspace.environmentCode }}</strong></div><div><span>风险</span><strong>{{ currentWorkspace.riskLevel }}</strong></div><div><span>Owner</span><strong>{{ currentWorkspace.ownerCode }}</strong></div><div><span>Row Version</span><strong>{{ currentWorkspace.rowVersion }}</strong></div><div><span>生命周期</span><el-tag :type="statusType(currentWorkspace.lifecycleStatus)">{{ currentWorkspace.lifecycleStatus }}</el-tag></div></div></template></AsyncStatePanel>
    </div>

    <div v-if="currentWorkspace" class="release-gate-grid">
      <div class="surface release-gate-card"><div class="section-title"><h3>① 验证证据与提交评审</h3><el-tag v-if="latestPassed" type="success">SERVER PASSED</el-tag></div><div v-if="latestPassed" class="release-evidence"><div><span>Run</span><strong>#{{ latestPassed.runNo }} · {{ latestPassed.runType }}</strong></div><div><span>Checks</span><strong>{{ latestPassed.passedCount }} / {{ latestPassed.totalCount }} PASSED</strong></div><div><span>Fixture</span><strong>#{{ latestPassed.resultSummary.fixtureSuiteVersionId }}</strong></div><div><span>Evidence</span><strong class="mono">{{ latestPassed.evidenceUri }}</strong></div></div><el-empty v-else description="没有带 Check 的 PASSED 服务端验证" :image-size="60" /><el-button type="primary" :disabled="currentWorkspace.lifecycleStatus !== 'VERIFIED' || !latestPassed" :loading="submitReview.isPending.value" @click="submitReview.mutate()">Submit Review</el-button></div>
      <div class="surface release-gate-card"><div class="section-title"><h3>② 分阶段批准</h3><el-tag :type="approvalComplete ? 'success' : 'warning'">{{ approvalComplete ? 'GATE PASSED' : 'WAITING' }}</el-tag></div><div class="approval-gates"><div :class="{ passed: releaseApproved }"><span>{{ releaseApproved ? '✓' : '○' }}</span><strong>RELEASE</strong><small>发布完整性与验证证据</small></div><div v-if="needsSecurity" :class="{ passed: securityApproved }"><span>{{ securityApproved ? '✓' : '○' }}</span><strong>SECURITY</strong><small>高风险安全复核</small></div></div><el-button type="primary" :disabled="currentWorkspace.lifecycleStatus !== 'IN_REVIEW'" @click="openApproval">记录审批决定</el-button></div>
    </div>

    <div v-if="currentWorkspace" class="surface approval-history-panel"><div class="section-title"><h3>Approval History</h3><span class="subtle">拒绝会让 Workspace 回到 DRAFT</span></div><el-empty v-if="!approvalItems.length" description="暂无审批记录" /><el-table v-else :data="approvalItems"><el-table-column prop="approvalStage" label="阶段" width="120" /><el-table-column prop="approverCode" label="审批人" width="170" /><el-table-column label="决定" width="110"><template #default="{ row }"><el-tag :type="statusType(row.decision)">{{ row.decision }}</el-tag></template></el-table-column><el-table-column prop="decisionComment" label="意见" min-width="260" /><el-table-column label="证据" width="130"><template #default="{ row }"><span class="mono">Run #{{ row.evidenceSnapshot.verificationRunId ?? '—' }}</span></template></el-table-column><el-table-column label="时间" width="180"><template #default="{ row }">{{ formatTime(row.decidedAt) }}</template></el-table-column></el-table></div>

    <div v-if="currentWorkspace" class="surface bundle-release-panel"><div class="section-title"><div><h3>③ 发布包编译与发布</h3><span class="subtle">已批准 → 待发布 → 已发布</span></div><el-button type="primary" :disabled="currentWorkspace.lifecycleStatus !== 'APPROVED'" @click="openCompile">编译发布包</el-button></div><el-empty v-if="!bundleItems.length" description="尚未生成发布包" /><el-table v-else :data="bundleItems"><el-table-column label="发布包" min-width="220"><template #default="{ row }"><strong>{{ row.bundleCode }}</strong><small class="bundle-version">{{ row.bundleVersion }}</small></template></el-table-column><el-table-column prop="bindingVersionId" label="可执行配置版本" width="145" /><el-table-column prop="environmentCode" label="环境" width="100" /><el-table-column label="内容摘要" min-width="280"><template #default="{ row }"><span class="mono checksum-cell">{{ row.artifactChecksum }}</span></template></el-table-column><el-table-column label="签名" width="110"><template #default="{ row }">{{ row.signatureMetadata.status ?? '—' }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.lifecycleStatus)">{{ statusLabel(row.lifecycleStatus) }}</el-tag></template></el-table-column><el-table-column label="操作" width="170"><template #default="{ row }"><el-button link type="primary" @click="showManifest(row)">查看清单</el-button><el-button v-if="row.lifecycleStatus === 'READY'" link type="primary" @click="requestPublish(row)">发布</el-button></template></el-table-column></el-table></div>

    <el-dialog v-model="approvalDialog" title="记录 Workspace 审批" width="760px"><el-alert type="info" :closable="false" :title="needsSecurity ? '该 Workspace 为高风险，需要 RELEASE 与 SECURITY 两阶段、不同审批人批准。' : '该 Workspace 只需要 RELEASE 阶段批准。'" show-icon /><el-form class="dialog-form" label-position="top"><div class="form-two-columns"><el-form-item label="Approval Stage"><el-select v-model="approvalStage"><el-option label="RELEASE" value="RELEASE" :disabled="releaseApproved" /><el-option v-if="needsSecurity" label="SECURITY" value="SECURITY" :disabled="securityApproved" /></el-select></el-form-item><el-form-item label="Decision"><el-select v-model="decision"><el-option label="APPROVED" value="APPROVED" /><el-option label="REJECTED" value="REJECTED" /></el-select></el-form-item><el-form-item label="Approver Code" required><el-input v-model="approverCode" /><small class="form-hint">作为本次 X-Operator 审计标签。</small></el-form-item><el-form-item label="Decision Comment"><el-input v-model="decisionComment" /></el-form-item></div><el-form-item label="Evidence Snapshot"><el-input v-model="evidenceText" type="textarea" :rows="9" class="schema-editor" /></el-form-item></el-form><template #footer><el-button @click="approvalDialog = false">取消</el-button><el-button :type="decision === 'REJECTED' ? 'danger' : 'primary'" :loading="decideApproval.isPending.value" @click="submitDecision">提交决定</el-button></template></el-dialog>

    <el-dialog v-model="bundleDialog" title="编译发布包" width="680px"><el-alert type="warning" :closable="false" title="版本号由平台根据基准发布包自动生成。编译会冻结配置清单、生成内容摘要，并将验证空间标记为已编译。" show-icon /><el-form class="dialog-form" label-position="top"><el-form-item label="发布包编码" required><el-input v-model="bundleCode" /></el-form-item></el-form><template #footer><el-button @click="bundleDialog = false">取消</el-button><el-button type="primary" :disabled="!bundleCode.trim()" :loading="compileBundle.isPending.value" @click="compileBundle.mutate()">编译发布包</el-button></template></el-dialog>
    <el-dialog v-model="manifestDialog" title="发布包配置清单" width="960px"><template v-if="selectedBundle"><div class="bundle-manifest-header"><span>{{ selectedBundle.bundleCode }}@{{ selectedBundle.bundleVersion }}</span><strong class="mono">{{ selectedBundle.artifactChecksum }}</strong></div><pre class="plan-preview">{{ JSON.stringify(selectedBundle.manifestDocument, null, 2) }}</pre></template><template #footer><el-button @click="manifestDialog = false">关闭</el-button></template></el-dialog>
  </section>
</template>
