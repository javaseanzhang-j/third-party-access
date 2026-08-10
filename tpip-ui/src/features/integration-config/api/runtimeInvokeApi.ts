export interface RuntimeInvocationRequest {
  meta: { requestId: string; caller: string; tenantId: string | null; idempotencyKey: string | null
    deadline: string | null; attributes: Record<string, string> }
  payload: Record<string, unknown>
}
export interface RuntimeInvocationResult { httpStatus: number; document: Record<string, unknown> }
export interface RuntimeConsumerIdentity { appKey:string;appSecret:string;scenario:string|null }

async function sha256(value:string):Promise<string>{const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value));return Array.from(new Uint8Array(bytes),v=>v.toString(16).padStart(2,'0')).join('')}
export async function signRuntimeRequest(method:string,path:string,serviceCode:string,timestamp:string,nonce:string,body:string,secret:string):Promise<string>{
  const material=[method,path,serviceCode,timestamp,nonce,await sha256(body)].join('\n')
  const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(secret),{name:'HMAC',hash:'SHA-256'},false,['sign'])
  const signature=await crypto.subtle.sign('HMAC',key,new TextEncoder().encode(material))
  return Array.from(new Uint8Array(signature),v=>v.toString(16).padStart(2,'0')).join('')
}

export const runtimeInvokeApi = {
  invoke: async (operationCode: string, request: RuntimeInvocationRequest,identity:RuntimeConsumerIdentity): Promise<RuntimeInvocationResult> => {
    const path=`/integration/v1/operations/${encodeURIComponent(operationCode)}:invoke`;const body=JSON.stringify(request)
    const timestamp=String(Date.now());const nonce=crypto.randomUUID();const signature=await signRuntimeRequest('POST',path,operationCode,timestamp,nonce,body,identity.appSecret)
    const headers:Record<string,string>={Accept:'application/json','Content-Type':'application/json','X-TPIP-App-Key':identity.appKey,
      'X-TPIP-Timestamp':timestamp,'X-TPIP-Nonce':nonce,'X-TPIP-Signature':signature,'X-Request-Id':request.meta.requestId}
    if(identity.scenario)headers['X-TPIP-Scenario']=identity.scenario
    const response = await fetch(path, {
      method: 'POST', headers,
      body
    })
    const text = await response.text()
    let document: Record<string, unknown>
    try { document = text ? JSON.parse(text) as Record<string, unknown> : {} }
    catch { document = { code: 'TPIP_RUNTIME_INVALID_RESPONSE', message: text } }
    return { httpStatus: response.status, document }
  }
}
