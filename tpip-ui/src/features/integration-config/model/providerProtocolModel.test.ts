import { describe, expect, it } from 'vitest'
import { buildChannelProtocolViews, protocolVersionUsageCount, recommendedPublishedVersionId } from './providerProtocolModel'

describe('provider protocol presentation', () => {
  const versions = [
    { id: 10, providerContractId: 3, versionNo: 1, semanticVersion: '1.0.0', requestSchema: {}, responseSchema: {},
      errorSchema: null, callbackSchema: null, examples: null, contentChecksum: 'a', lifecycleStatus: 'PUBLISHED' as const,
      publishedAt: '', createdAt: '' },
    { id: 11, providerContractId: 3, versionNo: 2, semanticVersion: '1.1.0', requestSchema: {}, responseSchema: {},
      errorSchema: null, callbackSchema: null, examples: null, contentChecksum: 'b', lifecycleStatus: 'PUBLISHED' as const,
      publishedAt: '', createdAt: '' }
  ]

  it('shows the immutable protocol version frozen by each channel binding version', () => {
    const rows = buildChannelProtocolViews(3,
      [{ id: 20, providerId: 1, providerProductId: 2, channelCode: 'aliyun.cn', channelName: '阿里云国内短信',
        baseUrl: 'https://sms.example.com', credentialRefId: null, description: null, status: 'ACTIVE' as const,
        rowVersion: 0, createdAt: '', updatedAt: '' }],
      [{ id: 30, providerContractId: 3, endpointCode: 'sms.local', environmentCode: 'local', revisionNo: 1,
        protocolScheme: 'HTTPS' as const, baseUrl: 'https://sms.example.com', resourcePath: '/', httpMethod: 'POST' as const,
        contentType: 'application/json', charsetName: 'UTF-8', connectTimeoutMs: 1000, readTimeoutMs: 1000,
        totalTimeoutMs: 2000, credentialRefId: null, lifecycleStatus: 'PUBLISHED' as const, contentChecksum: '',
        publishedAt: '', createdAt: '' }],
      [{ id: 40, bindingCode: 'sms.aliyun', bindingName: '阿里云短信实现', operationId: 1, providerContractId: 3,
        ownerCode: 'local', status: 'ACTIVE' as const, rowVersion: 0, createdAt: '', updatedAt: '' }],
      [{ id: 50, bindingId: 40, versionNo: 1, canonicalRequestContractVersionId: 1,
        canonicalResponseContractVersionId: 2, providerContractVersionId: 10, endpointId: 30, accessChannelId: 20,
        requestMappingVersionId: 1, responseMappingVersionId: 2, callbackMappingVersionId: null, policyVersionId: null,
        errorMappingVersionId: null, idempotencyClass: 'IDEMPOTENT_WITH_KEY', complianceMetadata: {}, routingAttributes: {},
        contentChecksum: '', lifecycleStatus: 'PUBLISHED' as const, publishedAt: '', createdAt: '' }], versions)
    expect(rows[0]).toMatchObject({ channelName: '阿里云国内短信', environments: ['local'],
      frozenVersions: [{ semanticVersion: '1.0.0', bindingVersionNo: 1 }] })
    expect(protocolVersionUsageCount(10, rows)).toBe(1)
  })

  it('recommends the latest published revision without invalidating older published versions', () => {
    expect(recommendedPublishedVersionId(versions)).toBe(11)
  })
})
