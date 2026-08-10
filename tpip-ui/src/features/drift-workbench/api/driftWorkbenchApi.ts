import{getJson,postJson}from'@/api/http'
export type Scope='ACTIONABLE'|'RESOLVED'|'ALL';export type ReviewStatus='OPEN'|'ACKNOWLEDGED'|'ACCEPTED'|'DISMISSED';export type DriftKind='NEW_CHECK'|'MISSING_CHECK'|'STATUS_CHANGED'|'RESULT_CHANGED'|'EVIDENCE_CHANGED';export type Operation='ASSIGN'|'ACKNOWLEDGE'|'ACCEPT'|'DISMISS'
export interface DriftSignature{itemNo:number;checkCode:string;driftKind:DriftKind}export interface WorkItem{reportId:number;baselineId:number;workspaceId:number;fixtureSuiteVersionId:number;verificationRunId:number;comparedCheckCount:number;driftCount:number;reviewStatus:ReviewStatus;rowVersion:number;successorBaselineId:number|null;assigneeCode:string|null;assignedBy:string|null;assignedAt:string|null;assignmentNote:string|null;createdAt:string;updatedAt:string;ageHours:number;dueAt:string;overdue:boolean;drifts:DriftSignature[]}
export interface Page{items:WorkItem[];page:number;size:number;totalElements:number}export interface Summary{totalReports:number;actionableReports:number;assignedActionableReports:number;unassignedActionableReports:number;overdueReports:number;acceptedReports:number;dismissedReports:number;affectedWorkspaces:number;changedItems:number}
export interface Filters{workspaceId?:number;scope?:Scope;driftKind?:DriftKind|'';checkCode?:string;assigneeCode?:string;overdueOnly?:boolean;overdueAfterHours?:number;page?:number;size?:number}
export interface Target{reportId:number;rowVersion:number}export interface ItemResult{reportId:number;expectedRowVersion:number;previousStatus:ReviewStatus|null;previousRowVersion:number|null;assigneeCode:string|null;eligible:boolean;reasonCode:string;reasonMessage:string|null;targetStatus:ReviewStatus|null;resultingRowVersion:number|null;successorBaselineId:number|null}export interface BulkResult{commandKey:string;workspaceId:number;operationType:Operation;status:'PREVIEWED'|'REJECTED'|'APPLIED';dryRun:boolean;idempotentReplay:boolean;itemCount:number;eligibleCount:number;appliedCount:number;rejectedCount:number;items:ItemResult[];completedAt:string}
const base='/control/v1/verification-drift-workbench';function query(f:Filters,summary=false){const q=new URLSearchParams();if(f.workspaceId)q.set('workspaceId',String(f.workspaceId));q.set('scope',f.scope??(summary?'ALL':'ACTIONABLE'));if(f.driftKind)q.set('driftKind',f.driftKind);if(f.checkCode?.trim())q.set('checkCode',f.checkCode.trim());if(f.assigneeCode?.trim())q.set('assigneeCode',f.assigneeCode.trim());q.set('overdueOnly',String(f.overdueOnly??false));q.set('overdueAfterHours',String(f.overdueAfterHours??72));if(!summary){q.set('page',String(f.page??0));q.set('size',String(f.size??20))}return q.toString()}
function key(){return globalThis.crypto?.randomUUID?.()??`local-${Date.now()}-${Math.random().toString(16).slice(2)}`}
export const driftWorkbenchApi={reports:(f:Filters,s?:AbortSignal)=>getJson<Page>(`${base}/reports?${query(f)}`,s),summary:(f:Filters,s?:AbortSignal)=>getJson<Summary>(`${base}/summary?${query(f,true)}`,s),bulk:(operation:Operation,input:{workspaceId:number;dryRun:boolean;reason:string;assigneeCode?:string;items:Target[]},s?:AbortSignal)=>{const path=operation==='ASSIGN'?'assign':operation==='ACKNOWLEDGE'?'acknowledge':'dispose';const body=path==='dispose'?{...input,resolution:operation==='ACCEPT'?'ACCEPTED':'DISMISSED'}:input;return postJson<BulkResult>(`${base}/governance-reviews:${path}`,body,{'Idempotency-Key':key(),'X-Operator':'local-operator'},s)}}

export interface OperationRequestEvidence {
  workspaceId: number
  operationType: Operation
  dryRun: boolean
  assigneeCode: string | null
  reason: string
  items: Target[]
}

export interface OperationEvidence {
  commandKey: string
  workspaceId: number
  operationType: Operation
  status: BulkResult['status']
  dryRun: boolean
  requestChecksum: string
  actorCode: string
  request: OperationRequestEvidence
  result: BulkResult
  createdAt: string
}

export function getDriftOperationEvidence(commandKey: string, signal?: AbortSignal): Promise<OperationEvidence> {
  return getJson<OperationEvidence>(`${base}/governance-operations/${encodeURIComponent(commandKey)}`, signal)
}
