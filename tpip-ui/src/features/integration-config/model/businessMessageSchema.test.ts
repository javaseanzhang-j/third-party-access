import { describe, expect, it } from 'vitest'
import { buildBusinessMessageExample, buildBusinessMessageSchema, countSchemaFields } from './businessMessageSchema'

describe('businessMessageSchema', () => {
  it('builds nested JSON Schema and examples from human-facing fields', () => {
    const fields = [
      { path: '$.mobile', name: '手机号码', type: 'STRING' as const, required: true, description: '接收短信的号码', example: '13800000000' },
      { path: '$.result.requestId', name: '请求编号', type: 'STRING' as const, required: false, description: '', example: 'req-1' }
    ]
    const schema = buildBusinessMessageSchema(fields)
    expect(schema).toMatchObject({ type: 'object', required: ['mobile'], properties: {
      mobile: { type: 'string', title: '手机号码', example: '13800000000' },
      result: { type: 'object', properties: { requestId: { type: 'string', title: '请求编号' } } }
    } })
    expect(buildBusinessMessageExample(fields)).toEqual({ mobile: '13800000000', result: { requestId: 'req-1' } })
    expect(countSchemaFields(schema)).toBe(2)
  })

  it('rejects duplicate and conflicting paths', () => {
    const field = { path: '$.data', name: '数据', type: 'STRING' as const, required: false, description: '', example: '' }
    expect(() => buildBusinessMessageSchema([field, field])).toThrow('重复')
    expect(() => buildBusinessMessageSchema([field, { ...field, path: '$.data.id' }])).toThrow('冲突')
  })

  it('validates typed examples', () => {
    expect(() => buildBusinessMessageSchema([{ path: '$.success', name: '成功', type: 'BOOLEAN', required: true,
      description: '', example: 'yes' }])).toThrow('true 或 false')
  })

  it('requires parent objects for a required nested field', () => {
    expect(buildBusinessMessageSchema([{ path: '$.data.id', name: '编号', type: 'STRING', required: true,
      description: '', example: '1' }])).toMatchObject({ required: ['data'], properties: {
      data: { required: ['id'] }
    } })
  })
})
