import { ApiError } from '@/api/http'
import { commandErrorMessage } from './commandPresentation'

describe('global impact command presentation', () => {
  it('turns command conflicts into an explicit refresh instruction', () => {
    expect(commandErrorMessage(new ApiError(409, 'changed',
      'TPIP_GLOBAL_IMPACT_JOB_COMMAND_CONFLICT', { refreshRequired: true })))
      .toContain('页面已刷新')
  })

  it('preserves validation feedback without exposing raw response json', () => {
    expect(commandErrorMessage(new ApiError(400, 'reason is invalid', 'TPIP_VALIDATION_ERROR')))
      .toBe('请求未通过校验：reason is invalid')
  })
})
