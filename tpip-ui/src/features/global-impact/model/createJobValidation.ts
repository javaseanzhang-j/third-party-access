import type { CreateGlobalImpactJobInput } from '../api/globalImpactCreationApi'

export const minimumTtlSeconds = 300
export const maximumTtlSeconds = 86_400

export function validateCreateJobInput(input: Partial<CreateGlobalImpactJobInput>): string | null {
  if (!Number.isInteger(input.candidatePolicyId) || (input.candidatePolicyId ?? 0) <= 0)
    return '请选择 Global 治理策略。'
  if (!Number.isInteger(input.candidateVersionId) || (input.candidateVersionId ?? 0) <= 0)
    return '请选择可用于影响计算的候选版本。'
  if (!Number.isInteger(input.ttlSeconds) || (input.ttlSeconds ?? 0) < minimumTtlSeconds
      || (input.ttlSeconds ?? 0) > maximumTtlSeconds)
    return '证据有效期必须是 5 分钟至 24 小时之间的整数秒。'
  return null
}
