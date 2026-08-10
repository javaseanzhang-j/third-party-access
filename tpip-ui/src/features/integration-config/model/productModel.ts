import type { OperationAsset } from '../api/canonicalAssetApi'
import type {
  CredentialAsset,
  EndpointAsset,
  ProviderAsset,
  ProviderContractAsset
} from '../api/integrationAssetApi'
import type { BindingAsset } from '../api/mappingAssetApi'

export interface ConnectionChannelView {
  key: string
  providerId: number
  providerName: string
  baseUrl: string
  credentialRefId: number | null
  credentialName: string
  interfaceCount: number
  publishedInterfaceCount: number
}

export interface ThirdPartyInterfaceView {
  endpointId: number
  providerId: number
  providerContractId: number
  providerName: string
  contractName: string
  endpointCode: string
  method: string
  baseUrl: string
  resourcePath: string
  lifecycleStatus: string
}

export interface AccessServiceView {
  operationId: number
  serviceCode: string
  serviceName: string
  invocationMode: string
  targetCount: number
  activeTargetCount: number
}

export interface SimpleFieldMapping {
  sourcePath: string
  targetPath: string
  targetType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT' | 'ARRAY'
  required: boolean
}

/** Parses one human-editable mapping per line: $.source -> $.target [TYPE] [required]. */
export function parseFieldMappingLines(value: string): SimpleFieldMapping[] {
  const lines = value.split(/\r?\n/).map(item => item.trim()).filter(item => item && !item.startsWith('#'))
  if (!lines.length) throw new Error('至少填写一条字段映射')
  return lines.map((line, index) => {
    const match = line.match(/^(\$\.[^\s]+)\s*->\s*(\$\.[^\s]+)(?:\s+(STRING|NUMBER|BOOLEAN|OBJECT|ARRAY))?(?:\s+(required))?$/i)
    if (!match) throw new Error(`第 ${index + 1} 行格式错误，应为：$.来源字段 -> $.目标字段 [类型] [required]`)
    return { sourcePath: match[1]!, targetPath: match[2]!,
      targetType: (match[3]?.toUpperCase() ?? 'STRING') as SimpleFieldMapping['targetType'],
      required: match[4]?.toLowerCase() === 'required' }
  })
}

function channelKey(endpoint: EndpointAsset, providerId: number): string {
  return `${providerId}|${endpoint.baseUrl}|${endpoint.credentialRefId ?? 'anonymous'}`
}

export function buildConnectionChannels(
  providers: ProviderAsset[],
  credentials: CredentialAsset[],
  contracts: ProviderContractAsset[],
  endpoints: EndpointAsset[]
): ConnectionChannelView[] {
  const providersById = new Map(providers.map(item => [item.id, item]))
  const contractsById = new Map(contracts.map(item => [item.id, item]))
  const credentialsById = new Map(credentials.map(item => [item.id, item]))
  const channels = new Map<string, ConnectionChannelView>()

  endpoints.forEach(endpoint => {
    const contract = contractsById.get(endpoint.providerContractId)
    if (!contract) return
    const provider = providersById.get(contract.providerId)
    if (!provider) return
    const key = channelKey(endpoint, provider.id)
    const current = channels.get(key)
    if (current) {
      current.interfaceCount += 1
      if (endpoint.lifecycleStatus === 'PUBLISHED') current.publishedInterfaceCount += 1
      return
    }
    const credential = endpoint.credentialRefId ? credentialsById.get(endpoint.credentialRefId) : undefined
    channels.set(key, {
      key,
      providerId: provider.id,
      providerName: provider.providerName,
      baseUrl: endpoint.baseUrl,
      credentialRefId: endpoint.credentialRefId,
      credentialName: credential?.credentialCode ?? '无需凭据',
      interfaceCount: 1,
      publishedInterfaceCount: endpoint.lifecycleStatus === 'PUBLISHED' ? 1 : 0
    })
  })

  return [...channels.values()].sort((left, right) =>
    left.providerName.localeCompare(right.providerName, 'zh-CN') || left.baseUrl.localeCompare(right.baseUrl))
}

export function buildThirdPartyInterfaces(
  providers: ProviderAsset[],
  contracts: ProviderContractAsset[],
  endpoints: EndpointAsset[]
): ThirdPartyInterfaceView[] {
  const providersById = new Map(providers.map(item => [item.id, item]))
  const contractsById = new Map(contracts.map(item => [item.id, item]))
  return endpoints.flatMap(endpoint => {
    const contract = contractsById.get(endpoint.providerContractId)
    const provider = contract ? providersById.get(contract.providerId) : undefined
    if (!contract || !provider) return []
    return [{
      endpointId: endpoint.id,
      providerId: provider.id,
      providerContractId: contract.id,
      providerName: provider.providerName,
      contractName: contract.contractName,
      endpointCode: endpoint.endpointCode,
      method: endpoint.httpMethod,
      baseUrl: endpoint.baseUrl,
      resourcePath: endpoint.resourcePath,
      lifecycleStatus: endpoint.lifecycleStatus
    }]
  }).sort((left, right) => left.providerName.localeCompare(right.providerName, 'zh-CN') ||
    left.contractName.localeCompare(right.contractName, 'zh-CN'))
}

export function buildAccessServices(operations: OperationAsset[], bindings: BindingAsset[]): AccessServiceView[] {
  return operations.map(operation => {
    const targets = bindings.filter(binding => binding.operationId === operation.id)
    return {
      operationId: operation.id,
      serviceCode: operation.operationCode,
      serviceName: operation.operationName,
      invocationMode: operation.invocationMode,
      targetCount: targets.length,
      activeTargetCount: targets.filter(target => target.status === 'ACTIVE').length
    }
  }).sort((left, right) => left.serviceName.localeCompare(right.serviceName, 'zh-CN'))
}
