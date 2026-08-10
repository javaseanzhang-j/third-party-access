export type WizardStageCode = 'CANONICAL' | 'PROVIDER' | 'MAPPING' | 'EXECUTION' |
  'FIXTURE' | 'VERIFICATION' | 'RELEASE' | 'RUNTIME'
export type WizardStageStatus = 'COMPLETE' | 'ACTION_REQUIRED' | 'BLOCKED'

export interface IntegrationWizardDraft {
  version: 1
  operationId: number | null
  providerContractId: number | null
  bindingId: number | null
  workspaceId: number | null
  environmentCode: string
  updatedAt: string
}

export interface IntegrationWizardFacts {
  hasOperation: boolean
  publishedCanonicalKinds: string[]
  hasProviderContract: boolean
  hasPublishedProviderVersion: boolean
  hasPublishedEndpoint: boolean
  hasBinding: boolean
  publishedMappingDirections: string[]
  hasPublishedBindingVersion: boolean
  hasPublishedFixtureVersion: boolean
  workspaceSelected: boolean
  workspaceHasBindingVersion: boolean
  hasPassedVerification: boolean
  workspaceLifecycle: string | null
  hasPublishedBundle: boolean
  hasDeployment: boolean
  hasActiveRoute: boolean
}

export interface WizardStageAssessment {
  code: WizardStageCode
  order: number
  title: string
  description: string
  route: string
  status: WizardStageStatus
  evidence: string
}

export const WIZARD_STORAGE_KEY = 'tpip.integration-wizard.v1'

export function emptyWizardDraft(now = new Date().toISOString()): IntegrationWizardDraft {
  return { version: 1, operationId: null, providerContractId: null, bindingId: null,
    workspaceId: null, environmentCode: 'test', updatedAt: now }
}

function positiveId(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? value : null
}

export function parseWizardDraft(raw: string | null, now = new Date().toISOString()): IntegrationWizardDraft {
  if (!raw) return emptyWizardDraft(now)
  try {
    const value = JSON.parse(raw) as Record<string, unknown>
    if (value.version !== 1) return emptyWizardDraft(now)
    const environmentCode = typeof value.environmentCode === 'string' && value.environmentCode.trim()
      ? value.environmentCode.trim().slice(0, 64) : 'test'
    return { version: 1, operationId: positiveId(value.operationId),
      providerContractId: positiveId(value.providerContractId), bindingId: positiveId(value.bindingId),
      workspaceId: positiveId(value.workspaceId), environmentCode,
      updatedAt: typeof value.updatedAt === 'string' ? value.updatedAt : now }
  } catch { return emptyWizardDraft(now) }
}

export function serializeWizardDraft(draft: IntegrationWizardDraft, now = new Date().toISOString()): string {
  return JSON.stringify({ version: 1, operationId: positiveId(draft.operationId),
    providerContractId: positiveId(draft.providerContractId), bindingId: positiveId(draft.bindingId),
    workspaceId: positiveId(draft.workspaceId), environmentCode: draft.environmentCode.trim() || 'test',
    updatedAt: now })
}

function missing(values: Array<[boolean, string]>): string {
  return values.filter(([present]) => !present).map(([, label]) => label).join('、')
}

export function assessWizardStages(facts: IntegrationWizardFacts): WizardStageAssessment[] {
  const canonicalKinds = new Set(facts.publishedCanonicalKinds)
  const mappingDirections = new Set(facts.publishedMappingDirections)
  const definitions: Array<Omit<WizardStageAssessment, 'status' | 'evidence'> & { complete: boolean; evidence: string }> = [
    { code: 'CANONICAL', order: 1, title: '业务标准接口', route: '/integration-assets/canonical',
      description: 'Operation 与 Canonical Request/Response Schema',
      complete: facts.hasOperation && canonicalKinds.has('REQUEST') && canonicalKinds.has('RESPONSE'),
      evidence: missing([[facts.hasOperation, 'Operation'], [canonicalKinds.has('REQUEST'), 'REQUEST 发布版本'], [canonicalKinds.has('RESPONSE'), 'RESPONSE 发布版本']]) },
    { code: 'PROVIDER', order: 2, title: '第三方协议与端点', route: '/integration-assets/provider-access',
      description: 'ProviderContractVersion 与环境化 Endpoint',
      complete: facts.hasProviderContract && facts.hasPublishedProviderVersion && facts.hasPublishedEndpoint,
      evidence: missing([[facts.hasProviderContract, 'ProviderContract'], [facts.hasPublishedProviderVersion, '协议发布版本'], [facts.hasPublishedEndpoint, '当前环境 Endpoint']]) },
    { code: 'MAPPING', order: 3, title: 'Binding 与字段映射', route: '/integration-assets/mappings',
      description: '出站请求与入站响应 JSONPath Mapping',
      complete: facts.hasBinding && mappingDirections.has('OUTBOUND_REQUEST') && mappingDirections.has('INBOUND_RESPONSE'),
      evidence: missing([[facts.hasBinding, 'Binding'], [mappingDirections.has('OUTBOUND_REQUEST'), '出站映射发布版本'], [mappingDirections.has('INBOUND_RESPONSE'), '入站映射发布版本']]) },
    { code: 'EXECUTION', order: 4, title: 'Policy 与执行闭包', route: '/integration-assets/binding-versions',
      description: '冻结可执行依赖并发布 BindingVersion', complete: facts.hasPublishedBindingVersion,
      evidence: facts.hasPublishedBindingVersion ? '' : 'BindingVersion 发布版本' },
    { code: 'FIXTURE', order: 5, title: 'FixtureSuite', route: '/integration-assets/fixture-suites',
      description: '可重复执行的验证场景与断言', complete: facts.hasPublishedFixtureVersion,
      evidence: facts.hasPublishedFixtureVersion ? '' : 'FixtureSuite 发布版本' },
    { code: 'VERIFICATION', order: 6, title: 'Workspace 验证', route: '/integration-assets/workspaces',
      description: '装配执行闭包并生成服务端 PASSED 证据',
      complete: facts.workspaceSelected && facts.workspaceHasBindingVersion && facts.hasPassedVerification,
      evidence: missing([[facts.workspaceSelected, 'Workspace'], [facts.workspaceHasBindingVersion, 'BindingVersion 装配'], [facts.hasPassedVerification, 'PASSED 验证']]) },
    { code: 'RELEASE', order: 7, title: '评审与 Bundle', route: '/integration-assets/releases',
      description: '审批、编译并发布不可变 Bundle', complete: facts.hasPublishedBundle,
      evidence: facts.hasPublishedBundle ? '' : `PUBLISHED Bundle${facts.workspaceLifecycle ? `（当前 ${facts.workspaceLifecycle}）` : ''}` },
    { code: 'RUNTIME', order: 8, title: 'Deployment 与 Runtime', route: '/integration-assets/deployments',
      description: '预热激活并生成 ACTIVE Route', complete: facts.hasDeployment && facts.hasActiveRoute,
      evidence: missing([[facts.hasDeployment, 'Deployment'], [facts.hasActiveRoute, 'ACTIVE Route']]) }
  ]
  let prerequisiteComplete = true
  return definitions.map(item => {
    const status: WizardStageStatus = item.complete ? 'COMPLETE' : prerequisiteComplete ? 'ACTION_REQUIRED' : 'BLOCKED'
    prerequisiteComplete = prerequisiteComplete && item.complete
    return { code: item.code, order: item.order, title: item.title, route: item.route,
      description: item.description, status, evidence: item.complete ? '服务端资产已就绪' : item.evidence }
  })
}

export function wizardProgress(stages: WizardStageAssessment[]): number {
  if (!stages.length) return 0
  return Math.round(stages.filter(item => item.status === 'COMPLETE').length * 100 / stages.length)
}
