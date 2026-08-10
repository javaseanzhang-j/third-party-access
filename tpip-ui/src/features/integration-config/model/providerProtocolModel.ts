import type { AccessChannelAsset } from '../api/accessChannelApi'
import type { BindingVersionAsset } from '../api/bindingVersionApi'
import type { EndpointAsset } from '../api/integrationAssetApi'
import type { BindingAsset } from '../api/mappingAssetApi'
import type { ProviderContractVersionAsset } from '../api/providerContractVersionApi'

export interface ChannelProtocolView {
  channelId: number
  channelName: string
  channelCode: string
  baseUrl: string
  environments: string[]
  frozenVersions: Array<{ versionId: number; semanticVersion: string; bindingVersionNo: number }>
}

export function buildChannelProtocolViews(
  contractId: number,
  channels: AccessChannelAsset[],
  endpoints: EndpointAsset[],
  bindings: BindingAsset[],
  bindingVersions: BindingVersionAsset[],
  protocolVersions: ProviderContractVersionAsset[]
): ChannelProtocolView[] {
  const eligibleBindingIds = new Set(bindings.filter(item => item.providerContractId === contractId).map(item => item.id))
  const endpointsById = new Map(endpoints.map(item => [item.id, item]))
  const versionsById = new Map(protocolVersions.map(item => [item.id, item]))

  return channels.map(channel => {
    const frozen = bindingVersions
      .filter(item => eligibleBindingIds.has(item.bindingId) && item.accessChannelId === channel.id &&
        item.lifecycleStatus === 'PUBLISHED')
      .sort((left, right) => right.versionNo - left.versionNo)
    const environments = [...new Set(frozen.flatMap(item => {
      const endpoint = endpointsById.get(item.endpointId)
      return endpoint ? [endpoint.environmentCode] : []
    }))]
    return {
      channelId: channel.id,
      channelName: channel.channelName,
      channelCode: channel.channelCode,
      baseUrl: channel.baseUrl,
      environments,
      frozenVersions: frozen.flatMap(item => {
        const version = versionsById.get(item.providerContractVersionId)
        return version ? [{ versionId: version.id, semanticVersion: version.semanticVersion,
          bindingVersionNo: item.versionNo }] : []
      })
    }
  }).sort((left, right) => left.channelName.localeCompare(right.channelName, 'zh-CN'))
}

export function protocolVersionUsageCount(versionId: number, rows: ChannelProtocolView[]): number {
  return new Set(rows.filter(row => row.frozenVersions.some(item => item.versionId === versionId))
    .map(row => row.channelId)).size
}

export function recommendedPublishedVersionId(versions: ProviderContractVersionAsset[]): number | null {
  return versions.filter(item => item.lifecycleStatus === 'PUBLISHED')
    .sort((left, right) => right.versionNo - left.versionNo)[0]?.id ?? null
}
