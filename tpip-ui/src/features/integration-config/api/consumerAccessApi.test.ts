import { consumerAccessApi } from './consumerAccessApi'
describe('consumer access api',()=>{
  afterEach(()=>vi.unstubAllGlobals())
  it('publishes a service grant version',async()=>{
    const fetchMock=vi.fn(async()=>new Response(JSON.stringify({id:9,lifecycleStatus:'PUBLISHED'}),{status:200,headers:{'Content-Type':'application/json'}}))
    vi.stubGlobal('fetch',fetchMock);await consumerAccessApi.publishGrant(3,9)
    const calls=fetchMock.mock.calls as unknown as Array<[string,RequestInit]>
    expect(calls[0]?.[0]).toBe('/control/v1/consumer-access/grants/3/versions/9:publish')
  })
})
