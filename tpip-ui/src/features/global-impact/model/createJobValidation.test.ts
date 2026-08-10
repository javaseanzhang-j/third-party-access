import { validateCreateJobInput } from './createJobValidation'

describe('create global impact job validation', () => {
  it('requires candidate asset identities', () => {
    expect(validateCreateJobInput({ ttlSeconds: 3600 })).toBe('请选择 Global 治理策略。')
    expect(validateCreateJobInput({ candidatePolicyId: 7, ttlSeconds: 3600 }))
      .toBe('请选择可用于影响计算的候选版本。')
  })

  it('enforces the server evidence ttl boundary', () => {
    expect(validateCreateJobInput({ candidatePolicyId: 7, candidateVersionId: 8, ttlSeconds: 299 }))
      .toContain('5 分钟至 24 小时')
    expect(validateCreateJobInput({ candidatePolicyId: 7, candidateVersionId: 8, ttlSeconds: 86_401 }))
      .toContain('5 分钟至 24 小时')
    expect(validateCreateJobInput({ candidatePolicyId: 7, candidateVersionId: 8, ttlSeconds: 300 }))
      .toBeNull()
  })
})
