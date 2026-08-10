import { describe, expect, it } from 'vitest'
import { createFixtureCaseInput, type FixtureCaseDraft } from './fixtureEditorModel'

const draft = (overrides: Partial<FixtureCaseDraft> = {}): FixtureCaseDraft => ({
  caseCode: 'sms.mapping', caseName: '短信映射', executionMode: 'MAPPING', direction: 'OUTBOUND_REQUEST',
  validationMode: 'FULL_MATCH', sourceText: '{"phone":"13800138000"}', expectedText: '{"Phone":"13800138000"}',
  expectedSuccess: true, expectedDiagnosticCode: '', assertionsText: '[{"code":"success","type":"SUCCESS","expected":true}]',
  ...overrides
})

describe('fixture editor model', () => {
  it('uses expected JSON only for full-result comparison', () => {
    expect(createFixtureCaseInput(draft(), 0)).toMatchObject({ expected: { Phone: '13800138000' }, assertions: null })
  })

  it('uses assertions exclusively in rule mode', () => {
    expect(createFixtureCaseInput(draft({ validationMode: 'RULES' }), 0)).toMatchObject({
      expected: null, expectedDiagnosticCode: null,
      assertions: [{ code: 'success', type: 'SUCCESS', expected: true }]
    })
  })

  it('forces remote calls to rule validation', () => {
    expect(createFixtureCaseInput(draft({ executionMode: 'REMOTE_CALL', validationMode: 'FULL_MATCH' }), 0))
      .toMatchObject({ expected: null, assertions: [{ code: 'success', type: 'SUCCESS', expected: true }] })
  })
})
