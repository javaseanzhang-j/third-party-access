import { getJson, postJson } from '@/api/http'

export interface ConsumerProject { id:number; projectCode:{value:string}|string; projectName:string; ownerCode:string; description:string|null; status:'ACTIVE'|'INACTIVE'; rowVersion:number }
export interface ConsumerApplication { id:number; projectId:number; appCode:{value:string}|string; appName:string; ownerCode:string; description:string|null; status:'ACTIVE'|'INACTIVE'; rowVersion:number }
export interface ConsumerCredential { id:number; applicationId:number; versionNo:number; appKey:string; secretReference:string; algorithm:string; validFrom:string; validUntil:string|null; lifecycleStatus:'DRAFT'|'PUBLISHED'|'REVOKED' }
export interface GrantVersion { id:number; grantId:number; versionNo:number; validFrom:string; validUntil:string|null; qpsLimit:number|null; burstLimit:number|null; dailyQuota:number|null; allowedCidrs:string[]; allowedScenarios:string[]; lifecycleStatus:'DRAFT'|'PUBLISHED' }
export interface GrantView { grant:{id:number;applicationId:number;operationId:number;grantCode:{value:string}|string;status:'ACTIVE'|'INACTIVE'}; serviceCode:string; serviceName:string; versions:GrantVersion[] }
export interface ConsumerApplicationDetail { application:ConsumerApplication; credentials:ConsumerCredential[]; grants:GrantView[] }
export interface GrantInput { operationId:number;ownerCode:string;validFrom:string|null;validUntil:string|null;qpsLimit:number|null;burstLimit:number|null;dailyQuota:number|null;allowedCidrs:string[];allowedScenarios:string[] }

const operator=(import.meta.env.VITE_TPIP_OPERATOR as string|undefined)?.trim()||'local-ui'
const headers={'X-Operator':operator}
export const consumerAccessApi={
  projects:(signal?:AbortSignal)=>getJson<ConsumerProject[]>('/control/v1/consumer-access/projects',signal),
  createProject:(input:{projectCode:string;projectName:string;ownerCode:string;description:string|null})=>postJson<ConsumerProject>('/control/v1/consumer-access/projects',input,headers),
  applications:(projectId?:number,signal?:AbortSignal)=>getJson<ConsumerApplication[]>(`/control/v1/consumer-access/applications${projectId?`?projectId=${projectId}`:''}`,signal),
  createApplication:(projectId:number,input:{appCode:string;appName:string;ownerCode:string;description:string|null})=>postJson<ConsumerApplication>(`/control/v1/consumer-access/projects/${projectId}/applications`,input,headers),
  application:(appId:number,signal?:AbortSignal)=>getJson<ConsumerApplicationDetail>(`/control/v1/consumer-access/applications/${appId}`,signal),
  createCredential:(appId:number,input:{secretReference:string;validFrom:string|null;validUntil:string|null})=>postJson<ConsumerCredential>(`/control/v1/consumer-access/applications/${appId}/credential-versions`,input,headers),
  publishCredential:(appId:number,id:number)=>postJson<ConsumerCredential>(`/control/v1/consumer-access/applications/${appId}/credential-versions/${id}:publish`,{},headers),
  revokeCredential:(appId:number,id:number)=>postJson<ConsumerCredential>(`/control/v1/consumer-access/applications/${appId}/credential-versions/${id}:revoke`,{},headers),
  createGrant:(appId:number,input:GrantInput)=>postJson<GrantView>(`/control/v1/consumer-access/applications/${appId}/grants`,input,headers),
  publishGrant:(grantId:number,id:number)=>postJson<GrantVersion>(`/control/v1/consumer-access/grants/${grantId}/versions/${id}:publish`,{},headers)
}
