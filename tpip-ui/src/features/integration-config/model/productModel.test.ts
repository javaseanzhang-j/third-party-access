import { describe, expect, it } from 'vitest'
import { buildAccessServices, buildConnectionChannels, buildThirdPartyInterfaces, parseFieldMappingLines } from './productModel'

const provider = {
  id: 1, providerCode: 'aliyun', providerName: '阿里云', providerType: 'PLATFORM' as const,
  description: null, ownerCode: 'local', status: 'ACTIVE' as const, rowVersion: 0,
  createdAt: '', updatedAt: ''
}
const contract = {
  id: 10, providerId: 1, contractCode: 'sms-send', contractName: '发送短信', protocolType: 'HTTP' as const,
  description: null, status: 'ACTIVE' as const, rowVersion: 0, createdAt: '', updatedAt: ''
}
const endpoint = (id: number, path: string, status: 'DRAFT' | 'PUBLISHED') => ({
  id, providerContractId: 10, endpointCode: `endpoint-${id}`, environmentCode: 'local', revisionNo: 1,
  protocolScheme: 'HTTPS' as const, baseUrl: 'https://dysmsapi.aliyuncs.com', resourcePath: path,
  httpMethod: 'POST' as const, contentType: 'application/json', charsetName: 'UTF-8', connectTimeoutMs: 1000,
  readTimeoutMs: 2000, totalTimeoutMs: 3000, credentialRefId: 20, lifecycleStatus: status,
  contentChecksum: '', publishedAt: null, createdAt: ''
})

describe('product model read projection', () => {
  it('groups endpoints sharing provider, base URL and credential into one channel', () => {
    const credentials = [{ id: 20, providerId: 1, credentialCode: '阿里云短信凭据', environmentCode: 'local',
      credentialType: 'API_KEY' as const, secretUri: 'secret://aliyun', secretMetadata: null,
      status: 'ACTIVE' as const, rowVersion: 0, createdAt: '', updatedAt: '' }]
    const result = buildConnectionChannels([provider], credentials, [contract], [
      endpoint(100, '/SendSms', 'PUBLISHED'), endpoint(101, '/QuerySendDetails', 'DRAFT')
    ])
    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({ providerName: '阿里云', interfaceCount: 2,
      publishedInterfaceCount: 1, credentialName: '阿里云短信凭据' })
  })

  it('presents endpoint assets as human-facing third-party interfaces', () => {
    const result = buildThirdPartyInterfaces([provider], [contract], [endpoint(100, '/SendSms', 'PUBLISHED')])
    expect(result[0]).toMatchObject({ providerName: '阿里云', contractName: '发送短信', method: 'POST',
      resourcePath: '/SendSms' })
  })

  it('treats operation code as serviceCode and bindings as adapter targets', () => {
    const operations = [{ id: 30, capabilityId: 1, operationCode: 'sms.send', operationName: '发送短信',
      description: null, invocationMode: 'SYNC' as const, idempotencyClass: 'IDEMPOTENT_WITH_KEY' as const,
      dataClassification: 'CONFIDENTIAL' as const, ownerCode: 'local', status: 'ACTIVE' as const,
      rowVersion: 0, createdAt: '', updatedAt: '' }]
    const bindings = [
      { id: 40, bindingCode: 'sms.aliyun', bindingName: '阿里云短信适配', operationId: 30,
        providerContractId: 10, ownerCode: 'local', status: 'ACTIVE' as const, rowVersion: 0,
        createdAt: '', updatedAt: '' },
      { id: 41, bindingCode: 'sms.tencent', bindingName: '腾讯云短信适配', operationId: 30,
        providerContractId: 11, ownerCode: 'local', status: 'INACTIVE' as const, rowVersion: 0,
        createdAt: '', updatedAt: '' }
    ]
    expect(buildAccessServices(operations, bindings)[0]).toMatchObject({ serviceCode: 'sms.send',
      targetCount: 2, activeTargetCount: 1 })
  })

  it('parses readable field mapping lines and reports the failing line', () => {
    expect(parseFieldMappingLines('# request\n$.mobile -> $.phone STRING required\n$.name -> $.realName')).toEqual([
      { sourcePath: '$.mobile', targetPath: '$.phone', targetType: 'STRING', required: true },
      { sourcePath: '$.name', targetPath: '$.realName', targetType: 'STRING', required: false }
    ])
    expect(() => parseFieldMappingLines('mobile = phone')).toThrow('第 1 行格式错误')
  })
})
