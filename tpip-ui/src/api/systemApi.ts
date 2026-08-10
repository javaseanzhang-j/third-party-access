import { getJson } from './http'

export interface ControlPlaneHealth { status: string }

export const systemApi = {
  health: (signal?: AbortSignal) => getJson<ControlPlaneHealth>('/actuator/health', signal)
}
