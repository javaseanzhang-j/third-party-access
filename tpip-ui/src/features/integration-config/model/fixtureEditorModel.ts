import type { CreateFixtureCaseInput, FixtureDirection, FixtureExecutionMode } from '../api/fixtureSuiteApi'

export type FixtureValidationMode = 'FULL_MATCH' | 'RULES'

export interface FixtureCaseDraft {
  caseCode: string
  caseName: string
  executionMode: FixtureExecutionMode
  direction: FixtureDirection
  validationMode: FixtureValidationMode
  sourceText: string
  expectedText: string
  expectedSuccess: boolean
  expectedDiagnosticCode: string
  assertionsText: string
}

function objectValue(text: string, field: string, nullable = false): Record<string, unknown> | null {
  if (nullable && !text.trim()) return null
  let value: unknown
  try { value = JSON.parse(text) as unknown } catch { throw new Error(`${field} 不是合法 JSON。`) }
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error(`${field} 必须是 JSON 对象。`)
  return value as Record<string, unknown>
}

function assertionValue(text: string): unknown[] {
  let value: unknown
  try { value = JSON.parse(text) as unknown } catch { throw new Error('验证规则不是合法 JSON。') }
  if (!Array.isArray(value) || !value.length) throw new Error('验证规则至少需要一条。')
  return value
}

export function createFixtureCaseInput(item: FixtureCaseDraft, caseOrder: number): CreateFixtureCaseInput {
  const rulesMode = item.executionMode === 'REMOTE_CALL' || item.validationMode === 'RULES'
  const expectedSuccess = rulesMode ? true : item.expectedSuccess
  return {
    caseCode: item.caseCode.trim(),
    caseName: item.caseName.trim(),
    caseOrder,
    executionMode: item.executionMode,
    direction: item.direction,
    source: objectValue(item.sourceText, `Case ${caseOrder + 1} 输入报文`)! ,
    expected: !rulesMode && expectedSuccess
      ? objectValue(item.expectedText, `Case ${caseOrder + 1} 期望输出报文`)
      : null,
    expectedSuccess,
    expectedDiagnosticCode: !rulesMode && !expectedSuccess
      ? item.expectedDiagnosticCode.trim() || null
      : null,
    assertions: rulesMode ? assertionValue(item.assertionsText) : null
  }
}
