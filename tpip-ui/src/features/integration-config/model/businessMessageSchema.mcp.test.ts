import { buildBusinessMessageSchema, businessMessageFieldsFromSchema } from './businessMessageSchema'

describe('MCP business field schema conversion', () => {
  it('turns a published business contract into editable Chinese field rows', () => {
    const fields = businessMessageFieldsFromSchema({
      type: 'object', required: ['mobile'], properties: {
        mobile: { type: 'string', title: '接收手机号', description: '短信接收方' },
        context: { type: 'object', properties: { traceId: { type: 'string', title: '业务流水号' } } }
      }
    })

    expect(fields).toEqual([
      expect.objectContaining({ path: '$.mobile', name: '接收手机号', required: true }),
      expect.objectContaining({ path: '$.context.traceId', name: '业务流水号' })
    ])
    expect(buildBusinessMessageSchema(fields)).toMatchObject({ type: 'object' })
  })
})
