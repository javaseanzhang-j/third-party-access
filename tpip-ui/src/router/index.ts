import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/integration-assets' },
    {
      path: '/integration-assets',
      name: 'integration-configuration',
      meta: { title: '第三方接入配置' },
      component: () => import('@/features/integration-config/pages/IntegrationConfigurationHomePage.vue')
    },
    {
      path: '/integration-assets/wizard',
      name: 'integration-wizard',
      meta: { title: '第三方接入向导' },
      component: () => import('@/features/integration-config/pages/IntegrationWizardPage.vue')
    },
    {
      path: '/integration-assets/provider-access',
      name: 'provider-access',
      meta: { title: '第三方系统配置' },
      component: () => import('@/features/integration-config/pages/ProviderAccessPage.vue')
    },
    {
      path: '/integration-assets/channels',
      name: 'connection-channels',
      meta: { title: '接入通道' },
      component: () => import('@/features/integration-config/pages/ConnectionChannelPage.vue')
    },
    {
      path: '/integration-assets/interfaces',
      name: 'third-party-interfaces',
      meta: { title: '第三方接口' },
      component: () => import('@/features/integration-config/pages/ThirdPartyInterfacePage.vue')
    },
    {
      path: '/integration-assets/services',
      name: 'access-services',
      meta: { title: '接入服务' },
      component: () => import('@/features/integration-config/pages/AccessServicePage.vue')
    },
    {
      path: '/integration-assets/canonical',
      name: 'canonical-assets',
      meta: { title: '业务标准接口' },
      component: () => import('@/features/integration-config/pages/CanonicalAssetPage.vue')
    },
    {
      path: '/integration-assets/provider-contract-versions',
      name: 'provider-contract-versions',
      meta: { title: '第三方协议版本' },
      component: () => import('@/features/integration-config/pages/ProviderContractVersionPage.vue')
    },
    {
      path: '/integration-assets/mappings',
      name: 'mapping-configuration',
      meta: { title: 'JSONPath 字段映射' },
      component: () => import('@/features/integration-config/pages/MappingConfigurationPage.vue')
    },
    {
      path: '/integration-assets/policies',
      name: 'policy-configuration',
      meta: { title: 'Policy DSL' },
      component: () => import('@/features/integration-config/pages/PolicyConfigurationPage.vue')
    },
    {
      path: '/integration-assets/binding-versions',
      name: 'binding-versions',
      meta: { title: 'BindingVersion 执行闭包' },
      component: () => import('@/features/integration-config/pages/BindingVersionPage.vue')
    },
    {
      path: '/integration-assets/fixture-suites',
      name: 'fixture-suites',
      meta: { title: 'FixtureSuite 验证资产' },
      component: () => import('@/features/integration-config/pages/FixtureSuitePage.vue')
    },
    {
      path: '/integration-assets/workspaces',
      name: 'integration-workspaces',
      meta: { title: 'Workspace 配置与验证' },
      component: () => import('@/features/integration-config/pages/WorkspaceVerificationPage.vue')
    },
    {
      path: '/integration-assets/releases',
      name: 'integration-releases',
      meta: { title: 'Workspace 评审与 Bundle 发布' },
      component: () => import('@/features/integration-config/pages/WorkspaceReleasePage.vue')
    },
    {
      path: '/integration-assets/deployments',
      name: 'integration-deployments',
      meta: { title: 'Deployment 激活与回滚' },
      component: () => import('@/features/integration-config/pages/DeploymentPage.vue')
    },
    {
      path: '/integration-assets/runtime-invoke',
      name: 'runtime-invoke',
      meta: { title: 'Runtime 调用控制台' },
      component: () => import('@/features/integration-config/pages/RuntimeInvokePage.vue')
    },
    {
      path: '/global-impact-operations',
      name: 'global-impact-operations',
      component: () => import('@/features/global-impact/pages/GlobalImpactOperationsPage.vue')
    },
    {
      path: '/governance-policies',
      name: 'governance-policies',
      component: () => import('@/features/governance-policy/pages/GovernancePolicyListPage.vue')
    },
    {
      path: '/governance-policies/:policyId',
      name: 'governance-policy-detail',
      component: () => import('@/features/governance-policy/pages/GovernancePolicyDetailPage.vue')
    },
    {
      path: '/governance-policies/:policyId/versions/:versionId',
      name: 'governance-policy-version-detail',
      component: () => import('@/features/governance-policy/pages/GovernancePolicyVersionDetailPage.vue')
    },
    {
      path: '/workspaces',
      name: 'workspace-assets',
      component: () => import('@/features/workspace/pages/WorkspaceListPage.vue')
    },
    {
      path: '/workspaces/:workspaceId',
      name: 'workspace-asset-detail',
      component: () => import('@/features/workspace/pages/WorkspaceDetailPage.vue')
    },
    {
      path: '/drift-workbench',
      name: 'drift-workbench',
      component: () => import('@/features/drift-workbench/pages/DriftWorkbenchPage.vue')
    },
    {
      path: '/drift-operations/:commandKey?',
      name: 'drift-operation-evidence',
      component: () => import('@/features/drift-workbench/pages/DriftOperationEvidencePage.vue')
    },
    {
      path: '/drift-governance-metrics',
      name: 'drift-governance-metrics',
      component: () => import('@/features/drift-workbench/pages/DriftGovernanceMetricsPage.vue')
    },
    {
      path: '/drift-governance-evaluations',
      name: 'drift-governance-evaluations',
      component: () => import('@/features/drift-workbench/pages/DriftGovernanceEvaluationPage.vue')
    },
    {
      path: '/drift-reminder-batches',
      name: 'drift-reminder-batches',
      component: () => import('@/features/drift-workbench/pages/DriftReminderBatchAssetsPage.vue')
    },
    {
      path: '/global-impact-jobs',
      name: 'global-impact-jobs',
      component: () => import('@/features/global-impact/pages/GlobalImpactJobListPage.vue')
    },
    {
      path: '/global-impact-jobs/new',
      name: 'global-impact-job-create',
      component: () => import('@/features/global-impact/pages/GlobalImpactJobCreatePage.vue')
    },
    {
      path: '/global-impact-jobs/:jobId',
      name: 'global-impact-job-detail',
      component: () => import('@/features/global-impact/pages/GlobalImpactJobDetailPage.vue')
    },
    {
      path: '/global-impact-snapshots/:snapshotId',
      name: 'global-impact-snapshot-detail',
      component: () => import('@/features/global-impact/pages/GlobalImpactSnapshotDetailPage.vue')
    },
    { path: '/:pathMatch(.*)*', redirect: '/integration-assets' }
  ]
})
