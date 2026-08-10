import { getJson, postJson, putJson } from '@/api/http'

export type RouteHealth = 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN'
export interface RouteTargetConfig { id?: number; routeVersionId?: number; bindingId: number; enabled: boolean; priority: number; weight: number; healthRequirement: 'HEALTHY_ONLY' | 'HEALTHY_OR_UNKNOWN'; manualStatus: 'AVAILABLE' | 'DRAINED'; conditions: Record<string, unknown> }
export interface RouteVersion { id: number; policyId: number; versionNo: number; healthFilterEnabled: boolean; fallbackMode: 'DISABLED' | 'ONLY_NOT_SENT'; contentChecksum: string; lifecycleStatus: 'DRAFT' | 'PUBLISHED'; publishedAt: string | null; createdAt: string; targets: RouteTargetConfig[] }
export interface RoutePolicyView { serviceId: number; serviceCode: string; policy: { id: number; policyName: string } | null; versions: RouteVersion[] }
export interface DryRunResult { decisionId: number; requestId: string; routeVersionId: number; selectedBindingId: number | null; outcome: 'SELECTED' | 'NO_CANDIDATE'; routingKeyHash: string; evaluations: Array<{ target: RouteTargetConfig; eligible: boolean; health: RouteHealth; reasons: string[] }> }
export interface SaveRouteInput { healthFilterEnabled: boolean; fallbackMode: 'DISABLED' | 'ONLY_NOT_SENT'; targets: RouteTargetConfig[] }
export interface DryRunInput { versionId: number; requestId: string; routingKey: string; attributes: Record<string, unknown>; healthByBinding: Record<number, RouteHealth> }
const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }
export const serviceRouteApi = {
  get: (serviceId: number, signal?: AbortSignal) => getJson<RoutePolicyView>(`/control/v1/product-model/services/${serviceId}/route-policy`, signal),
  saveDraft: (serviceId: number, input: SaveRouteInput) => putJson<RouteVersion>(`/control/v1/product-model/services/${serviceId}/route-policy`, input, headers),
  publish: (serviceId: number, versionId: number) => postJson<RouteVersion>(`/control/v1/product-model/services/${serviceId}/route-policy/versions/${versionId}:publish`, {}, headers),
  dryRun: (serviceId: number, input: DryRunInput) => postJson<DryRunResult>(`/control/v1/product-model/services/${serviceId}/route-policy:dry-run`, input, headers)
}
