import { signRuntimeRequest } from './runtimeInvokeApi'
describe('runtime consumer signature',()=>{
  it('creates deterministic HMAC SHA-256 for the exact body',async()=>{
    const value=await signRuntimeRequest('POST','/integration/v1/operations/sms.send:invoke','sms.send','1','n','{}','secret')
    expect(value).toMatch(/^[a-f0-9]{64}$/);expect(value).toBe(await signRuntimeRequest('POST','/integration/v1/operations/sms.send:invoke','sms.send','1','n','{}','secret'))
  })
})
