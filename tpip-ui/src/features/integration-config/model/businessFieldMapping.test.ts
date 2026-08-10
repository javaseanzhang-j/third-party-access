import { describe, expect, it } from 'vitest'
import { autoMatchFields, exampleFromSchema, executeMappingPreview, extractSchemaFields } from './businessFieldMapping'

const schema = { type: 'object', required: ['mobile'], properties: {
  mobile: { type: 'string', title: '手机号码', example: '13800000000' },
  result: { type: 'object', required: ['requestId'], properties: { requestId: { type: 'string', title: '请求编号', example: 'req-1' } } }
} }

describe('businessFieldMapping', () => {
  it('extracts human-facing leaf fields and examples', () => {
    expect(extractSchemaFields(schema)).toEqual([
      { path: '$.mobile', label: '手机号码', type: 'STRING', required: true },
      { path: '$.result.requestId', label: '请求编号', type: 'STRING', required: false }
    ])
    expect(exampleFromSchema(schema)).toEqual({ mobile: '13800000000', result: { requestId: 'req-1' } })
  })

  it('matches fields by terminal property name', () => {
    const source = extractSchemaFields(schema)
    const target = extractSchemaFields({ type: 'object', properties: { phone: { type: 'string' },
      data: { type: 'object', properties: { requestId: { type: 'string' } } } } })
    expect(autoMatchFields(source, target)).toEqual([{ sourcePath: '$.result.requestId', targetPath: '$.data.requestId', targetType: 'STRING', required: false }])
  })

  it('executes nested preview and validates required values', () => {
    const rows = [{ sourcePath: '$.mobile', targetPath: '$.params.phone', targetType: 'STRING' as const, required: true }]
    expect(executeMappingPreview({ mobile: 138 }, rows)).toEqual({ params: { phone: '138' } })
    expect(() => executeMappingPreview({}, rows)).toThrow('必填来源字段')
  })
})
