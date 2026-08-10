import { getJson, postJson } from '@/api/http'

export type DeploymentStatus = 'PENDING' | 'PREHEATING' | 'READY' | 'PREHEAT_FAILED' | 'ACTIVE' | 'DEPRECATED' | 'ROLLED_BACK'
export interface DeploymentAsset {
  id: number; deploymentCode: string; bundleId: number; operationId: number; environmentCode: string
  deploymentStatus: DeploymentStatus; trafficPercentage: number; previousDeploymentId: number | null
  instanceStatus: Record<string, unknown>; preheatEvidence: Record<string, unknown>
  rolloutMetadata: Record<string, unknown>; rowVersion: number; deployedBy: string
  deployedAt: string; activatedAt: string | null; endedAt: string | null; updatedAt: string
}
export interface ActiveRouteTarget {
  deploymentId: number; deploymentCode: string; bundleCode: string; bundleVersion: string
  artifactChecksum: string; trafficPercentage: number
}
export interface ActiveRoute {
  operationCode: string; environmentCode: string; revision: string; targets: ActiveRouteTarget[]
}

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export const deploymentApi = {
  deployments: (operationCode: string, environmentCode: string, signal?: AbortSignal) =>
    getJson<DeploymentAsset[]>(`/control/v1/deployments?operationCode=${encodeURIComponent(operationCode)}&environmentCode=${encodeURIComponent(environmentCode)}`, signal),
  create: (deploymentCode: string, bundleId: number, rolloutMetadata: Record<string, unknown>) =>
    postJson<DeploymentAsset>('/control/v1/deployments', { deploymentCode, bundleId, rolloutMetadata }, headers),
  preheat: (deploymentId: number, rowVersion: number) =>
    postJson<DeploymentAsset>(`/control/v1/deployments/${deploymentId}:preheat`, { rowVersion }, headers),
  activate: (deploymentId: number, rowVersion: number, initialTraffic: number) =>
    postJson<DeploymentAsset>(`/control/v1/deployments/${deploymentId}:activate`, { rowVersion, initialTraffic }, headers),
  traffic: (deploymentId: number, rowVersion: number, targetTraffic: number) =>
    postJson<DeploymentAsset>(`/control/v1/deployments/${deploymentId}:traffic`, { rowVersion, targetTraffic }, headers),
  rollback: (deploymentId: number, rowVersion: number, rollbackDeploymentCode: string, reason: string) =>
    postJson<DeploymentAsset>(`/control/v1/deployments/${deploymentId}:rollback`, { rowVersion, rollbackDeploymentCode, reason }, headers),
  activeRoute: (operationCode: string, environmentCode: string, signal?: AbortSignal) =>
    getJson<ActiveRoute>(`/runtime-config/v1/routes/${encodeURIComponent(operationCode)}/environments/${encodeURIComponent(environmentCode)}`, signal)
}
