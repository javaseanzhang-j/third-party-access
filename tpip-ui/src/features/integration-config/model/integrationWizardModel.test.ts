import { assessWizardStages, emptyWizardDraft, parseWizardDraft, serializeWizardDraft, wizardProgress,
  type IntegrationWizardFacts } from './integrationWizardModel'

const completeFacts: IntegrationWizardFacts = {
  hasOperation: true, publishedCanonicalKinds: ['REQUEST', 'RESPONSE'], hasProviderContract: true,
  hasPublishedProviderVersion: true, hasPublishedEndpoint: true, hasBinding: true,
  publishedMappingDirections: ['OUTBOUND_REQUEST', 'INBOUND_RESPONSE'], hasPublishedBindingVersion: true,
  hasPublishedFixtureVersion: true, workspaceSelected: true, workspaceHasBindingVersion: true,
  hasPassedVerification: true, workspaceLifecycle: 'COMPILED', hasPublishedBundle: true,
  hasDeployment: true, hasActiveRoute: true
}

describe('integration wizard model', () => {
  it('derives a fully completed eight-stage journey', () => {
    const stages = assessWizardStages(completeFacts)
    expect(stages).toHaveLength(8)
    expect(stages.every(item => item.status === 'COMPLETE')).toBe(true)
    expect(wizardProgress(stages)).toBe(100)
  })

  it('makes the first gap actionable and locks later incomplete stages', () => {
    const stages = assessWizardStages({ ...completeFacts, publishedMappingDirections: ['OUTBOUND_REQUEST'],
      hasPublishedBindingVersion: false, hasPublishedFixtureVersion: false, workspaceHasBindingVersion: false,
      hasPassedVerification: false, hasPublishedBundle: false, hasDeployment: false, hasActiveRoute: false })
    expect(stages[2]?.status).toBe('ACTION_REQUIRED')
    expect(stages[3]?.status).toBe('BLOCKED')
    expect(stages[2]?.evidence).toContain('入站映射发布版本')
  })

  it('persists identifiers only and rejects invalid stored values', () => {
    const draft = { ...emptyWizardDraft('old'), operationId: 7, providerContractId: 9,
      bindingId: 11, workspaceId: 13, environmentCode: ' test ' }
    const serialized = serializeWizardDraft(draft, 'now')
    expect(serialized).not.toContain('secret')
    expect(parseWizardDraft(serialized, 'fallback')).toEqual({ ...draft, environmentCode: 'test', updatedAt: 'now' })
    expect(parseWizardDraft('{broken', 'fallback')).toEqual(emptyWizardDraft('fallback'))
    expect(parseWizardDraft(JSON.stringify({ version: 1, operationId: -1, environmentCode: '' }), 'fallback').operationId).toBeNull()
  })
})
