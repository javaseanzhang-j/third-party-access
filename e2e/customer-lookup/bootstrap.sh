#!/usr/bin/env bash
set -euo pipefail

CONTROL_BASE="${TPIP_E2E_CONTROL_BASE:-http://127.0.0.1:18080}"
OPERATOR="${TPIP_E2E_OPERATOR:-codex-e2e}"
RUN_ID="${TPIP_E2E_RUN_ID:-$(date +%Y%m%d%H%M%S)}"
EVIDENCE_DIR="e2e/customer-lookup/evidence/${RUN_ID}"
mkdir -p "${EVIDENCE_DIR}"

request() {
  local method="$1" path="$2" data="$3" output="$4"
  local status
  if [[ -n "${data}" ]]; then
    status=$(curl -sS --max-time 20 -o "${output}" -w '%{http_code}' \
      -X "${method}" "${CONTROL_BASE}${path}" \
      -H 'Content-Type: application/json' -H "X-Operator: ${OPERATOR}" \
      --data-binary "${data}")
  else
    status=$(curl -sS --max-time 20 -o "${output}" -w '%{http_code}' \
      -X "${method}" "${CONTROL_BASE}${path}" -H "X-Operator: ${OPERATOR}")
  fi
  if [[ "${status}" -lt 200 || "${status}" -ge 300 ]]; then
    echo "HTTP ${status}: ${method} ${path}" >&2
    jq . "${output}" >&2 || true
    exit 1
  fi
}

post() { request POST "$1" "$2" "$3"; }

health_file="${EVIDENCE_DIR}/control-plane-health.json"
request GET '/actuator/health' '' "${health_file}"

provider_file="${EVIDENCE_DIR}/provider.json"
post '/control/v1/providers' "$(jq -nc --arg run "${RUN_ID}" '{providerCode:("e2e.mock-provider."+$run),providerName:"E2E Mock Provider",providerType:"SUPPLIER",description:"Local deterministic acceptance provider",ownerCode:"e2e"}')" "${provider_file}"
provider_id=$(jq -r '.id' "${provider_file}")

credential_file="${EVIDENCE_DIR}/credential.json"
post '/control/v1/credentials' "$(jq -nc --argjson providerId "${provider_id}" --arg run "${RUN_ID}" '{providerId:$providerId,credentialCode:("e2e.mock-provider.api-key."+$run),environmentCode:"test",credentialType:"API_KEY",secretUri:"env://TPIP_SECRET_E2E_PROVIDER_API_KEY",secretMetadata:{purpose:"local-e2e"}}')" "${credential_file}"
credential_id=$(jq -r '.id' "${credential_file}")

operation_file="${EVIDENCE_DIR}/operation.json"
operation_search_file="${EVIDENCE_DIR}/operation-search.json"
request GET '/control/v1/operations?keyword=e2e.customer.lookup&size=200' '' "${operation_search_file}"
operation_id=$(jq -r '[.items[] | select(.operationCode == "e2e.customer.lookup")][0].id // empty' "${operation_search_file}")
if [[ -n "${operation_id}" ]]; then
  request GET "/control/v1/operations/${operation_id}" '' "${operation_file}"
else
  domain_file="${EVIDENCE_DIR}/domain.json"
  post '/control/v1/domains' "$(jq -nc --arg run "${RUN_ID}" '{domainCode:("e2e.customer."+$run),domainName:"E2E Customer",description:"Local acceptance domain",ownerCode:"e2e"}')" "${domain_file}"
  domain_id=$(jq -r '.id' "${domain_file}")
  capability_file="${EVIDENCE_DIR}/capability.json"
  post '/control/v1/capabilities' "$(jq -nc --argjson domainId "${domain_id}" --arg run "${RUN_ID}" '{domainId:$domainId,capabilityCode:("e2e.customer.profile."+$run),capabilityName:"Customer Profile",description:"Local acceptance capability",ownerCode:"e2e"}')" "${capability_file}"
  capability_id=$(jq -r '.id' "${capability_file}")
  post '/control/v1/operations' "$(jq -nc --argjson capabilityId "${capability_id}" '{capabilityId:$capabilityId,operationCode:"e2e.customer.lookup",operationName:"E2E Customer Lookup",description:"JSONPath mapping acceptance operation",invocationMode:"SYNC",idempotencyClass:"IDEMPOTENT",dataClassification:"INTERNAL",ownerCode:"e2e"}')" "${operation_file}"
  operation_id=$(jq -r '.id' "${operation_file}")
fi

request_schema='{"type":"object","required":["customerId"],"properties":{"customerId":{"type":"string","minLength":1,"maxLength":40}},"additionalProperties":false}'
response_schema='{"type":"object","required":["customerId","customerName","mobile","status"],"properties":{"customerId":{"type":"string"},"customerName":{"type":"string"},"mobile":{"type":"string"},"status":{"type":"string","enum":["ACTIVE","DISABLED"]}},"additionalProperties":false}'
provider_request_schema='{"type":"object","required":["member_no"],"properties":{"member_no":{"type":"string"}},"additionalProperties":false}'
provider_response_schema='{"type":"object","required":["code","data"],"properties":{"code":{"type":"string","const":"0"},"data":{"type":"object","required":["member_no","member_name","mobile_no","member_status"],"properties":{"member_no":{"type":"string"},"member_name":{"type":"string"},"mobile_no":{"type":"string"},"member_status":{"type":"string"}}}},"additionalProperties":false}'

canonical_request_file="${EVIDENCE_DIR}/canonical-request.json"
post '/control/v1/contracts' "$(jq -nc --argjson operationId "${operation_id}" --arg run "${RUN_ID}" '{operationId:$operationId,contractCode:("e2e.customer.lookup.request."+$run),contractName:"Customer Lookup Request",contractKind:"REQUEST",description:"Canonical request"}')" "${canonical_request_file}"
canonical_request_id=$(jq -r '.id' "${canonical_request_file}")
canonical_request_version_file="${EVIDENCE_DIR}/canonical-request-version.json"
post "/control/v1/contracts/${canonical_request_id}/versions" "$(jq -nc --argjson schema "${request_schema}" '{semanticVersion:"1.0.0",schemaStandard:"JSON_SCHEMA_2020_12",schemaDocument:$schema,exampleDocument:{customerId:"C1001"},compatibilityMode:"BACKWARD"}')" "${canonical_request_version_file}"
canonical_request_version_id=$(jq -r '.id' "${canonical_request_version_file}")
post "/control/v1/contracts/${canonical_request_id}/versions/${canonical_request_version_id}:publish" '' "${EVIDENCE_DIR}/canonical-request-published.json"

canonical_response_file="${EVIDENCE_DIR}/canonical-response.json"
post '/control/v1/contracts' "$(jq -nc --argjson operationId "${operation_id}" --arg run "${RUN_ID}" '{operationId:$operationId,contractCode:("e2e.customer.lookup.response."+$run),contractName:"Customer Lookup Response",contractKind:"RESPONSE",description:"Canonical response"}')" "${canonical_response_file}"
canonical_response_id=$(jq -r '.id' "${canonical_response_file}")
canonical_response_version_file="${EVIDENCE_DIR}/canonical-response-version.json"
post "/control/v1/contracts/${canonical_response_id}/versions" "$(jq -nc --argjson schema "${response_schema}" '{semanticVersion:"1.0.0",schemaStandard:"JSON_SCHEMA_2020_12",schemaDocument:$schema,exampleDocument:{customerId:"C1001",customerName:"张三",mobile:"13800138000",status:"ACTIVE"},compatibilityMode:"BACKWARD"}')" "${canonical_response_version_file}"
canonical_response_version_id=$(jq -r '.id' "${canonical_response_version_file}")
post "/control/v1/contracts/${canonical_response_id}/versions/${canonical_response_version_id}:publish" '' "${EVIDENCE_DIR}/canonical-response-published.json"

provider_contract_file="${EVIDENCE_DIR}/provider-contract.json"
post '/control/v1/provider-contracts' "$(jq -nc --argjson providerId "${provider_id}" --arg run "${RUN_ID}" '{providerId:$providerId,contractCode:("e2e.mock.customer.lookup."+$run),contractName:"Mock Customer Lookup",protocolType:"HTTP",description:"Local mock contract"}')" "${provider_contract_file}"
provider_contract_id=$(jq -r '.id' "${provider_contract_file}")
provider_contract_version_file="${EVIDENCE_DIR}/provider-contract-version.json"
post "/control/v1/provider-contracts/${provider_contract_id}/versions" "$(jq -nc --argjson requestSchema "${provider_request_schema}" --argjson responseSchema "${provider_response_schema}" '{semanticVersion:"1.0.0",requestSchema:$requestSchema,responseSchema:$responseSchema,errorSchema:null,callbackSchema:null,examples:{request:{member_no:"C1001"}}}')" "${provider_contract_version_file}"
provider_contract_version_id=$(jq -r '.id' "${provider_contract_version_file}")
post "/control/v1/provider-contracts/${provider_contract_id}/versions/${provider_contract_version_id}:publish" '' "${EVIDENCE_DIR}/provider-contract-published.json"

endpoint_file="${EVIDENCE_DIR}/endpoint.json"
post '/control/v1/endpoints' "$(jq -nc --argjson providerContractId "${provider_contract_id}" --argjson credentialRefId "${credential_id}" --arg run "${RUN_ID}" '{providerContractId:$providerContractId,endpointCode:("e2e.customer.member-query."+$run),environmentCode:"test",protocolScheme:"HTTP",baseUrl:"http://127.0.0.1:19090",resourcePath:"/vendor/v1/members/query",httpMethod:"POST",contentType:"application/json",charsetName:"UTF-8",connectTimeoutMs:1000,readTimeoutMs:2000,totalTimeoutMs:3000,credentialRefId:$credentialRefId,networkConfig:{},tlsConfig:null}')" "${endpoint_file}"
endpoint_id=$(jq -r '.id' "${endpoint_file}")
post "/control/v1/endpoints/${endpoint_id}:publish" '' "${EVIDENCE_DIR}/endpoint-published.json"
post "/control/v1/endpoints/${endpoint_id}:probe" '' "${EVIDENCE_DIR}/endpoint-probe.json"

binding_file="${EVIDENCE_DIR}/binding.json"
post '/control/v1/bindings' "$(jq -nc --argjson operationId "${operation_id}" --argjson providerContractId "${provider_contract_id}" --arg run "${RUN_ID}" '{bindingCode:("e2e.customer.lookup.mock."+$run),bindingName:"Customer Lookup Mock Binding",operationId:$operationId,providerContractId:$providerContractId,ownerCode:"e2e"}')" "${binding_file}"
binding_id=$(jq -r '.id' "${binding_file}")

outbound_file="${EVIDENCE_DIR}/mapping-outbound.json"
post '/control/v1/mappings' "$(jq -nc --argjson bindingId "${binding_id}" --arg run "${RUN_ID}" '{bindingId:$bindingId,mappingCode:("e2e.customer.lookup.outbound."+$run),mappingName:"Customer Lookup Outbound",direction:"OUTBOUND_REQUEST"}')" "${outbound_file}"
outbound_id=$(jq -r '.id' "${outbound_file}")
outbound_version_file="${EVIDENCE_DIR}/mapping-outbound-version.json"
post "/control/v1/mappings/${outbound_id}/versions" "$(jq -nc --arg sourceSchemaRef "canonical-contract-version:${canonical_request_id}:${canonical_request_version_id}" --arg targetSchemaRef "provider-contract-version:${provider_contract_id}:${provider_contract_version_id}" '{selectorProfile:"JSONPATH_1_0",sourceSchemaRef:$sourceSchemaRef,targetSchemaRef:$targetSchemaRef,mappingOptions:{},rules:[{ruleCode:"customer-id-to-member-no",ruleOrder:10,valueSource:"SELECTOR",sourceSelector:"$.customerId",targetSelector:"$.member_no",targetType:"STRING",constantValue:null,defaultValue:null,converterCode:null,converterConfig:null,conditionExpression:null,required:true,arrayStrategy:null,missingStrategy:"FAIL",errorStrategy:"FAIL",enabled:true}]}')" "${outbound_version_file}"
outbound_version_id=$(jq -r '.id' "${outbound_version_file}")
post "/control/v1/mappings/${outbound_id}/versions/${outbound_version_id}:test" "$(jq -nc '{source:{customerId:"C1001"},requestId:"fixture-outbound",traceId:"fixture-outbound",operationCode:"e2e.customer.lookup",attributes:{}}')" "${EVIDENCE_DIR}/mapping-outbound-result.json"
post "/control/v1/mappings/${outbound_id}/versions/${outbound_version_id}:publish" '' "${EVIDENCE_DIR}/mapping-outbound-published.json"

inbound_file="${EVIDENCE_DIR}/mapping-inbound.json"
post '/control/v1/mappings' "$(jq -nc --argjson bindingId "${binding_id}" --arg run "${RUN_ID}" '{bindingId:$bindingId,mappingCode:("e2e.customer.lookup.inbound."+$run),mappingName:"Customer Lookup Inbound",direction:"INBOUND_RESPONSE"}')" "${inbound_file}"
inbound_id=$(jq -r '.id' "${inbound_file}")
inbound_version_file="${EVIDENCE_DIR}/mapping-inbound-version.json"
post "/control/v1/mappings/${inbound_id}/versions" "$(jq -nc --arg sourceSchemaRef "provider-contract-version:${provider_contract_id}:${provider_contract_version_id}" --arg targetSchemaRef "canonical-contract-version:${canonical_response_id}:${canonical_response_version_id}" '{selectorProfile:"JSONPATH_1_0",sourceSchemaRef:$sourceSchemaRef,targetSchemaRef:$targetSchemaRef,mappingOptions:{},rules:[{ruleCode:"member-no-to-customer-id",ruleOrder:10,valueSource:"SELECTOR",sourceSelector:"$.data.member_no",targetSelector:"$.customerId",targetType:"STRING",required:true,arrayStrategy:null,missingStrategy:"FAIL",errorStrategy:"FAIL",enabled:true},{ruleCode:"member-name-to-customer-name",ruleOrder:20,valueSource:"SELECTOR",sourceSelector:"$.data.member_name",targetSelector:"$.customerName",targetType:"STRING",required:true,arrayStrategy:null,missingStrategy:"FAIL",errorStrategy:"FAIL",enabled:true},{ruleCode:"mobile-no-to-mobile",ruleOrder:30,valueSource:"SELECTOR",sourceSelector:"$.data.mobile_no",targetSelector:"$.mobile",targetType:"STRING",required:true,arrayStrategy:null,missingStrategy:"FAIL",errorStrategy:"FAIL",enabled:true},{ruleCode:"member-status-to-status",ruleOrder:40,valueSource:"SELECTOR",sourceSelector:"$.data.member_status",targetSelector:"$.status",targetType:"STRING",required:true,arrayStrategy:null,missingStrategy:"FAIL",errorStrategy:"FAIL",enabled:true}]}')" "${inbound_version_file}"
inbound_version_id=$(jq -r '.id' "${inbound_version_file}")
post "/control/v1/mappings/${inbound_id}/versions/${inbound_version_id}:test" "$(jq -nc '{source:{code:"0",data:{member_no:"C1001",member_name:"张三",mobile_no:"13800138000",member_status:"ACTIVE"}},requestId:"fixture-inbound",traceId:"fixture-inbound",operationCode:"e2e.customer.lookup",attributes:{}}')" "${EVIDENCE_DIR}/mapping-inbound-result.json"
post "/control/v1/mappings/${inbound_id}/versions/${inbound_version_id}:publish" '' "${EVIDENCE_DIR}/mapping-inbound-published.json"

policy_file="${EVIDENCE_DIR}/policy.json"
post '/control/v1/policies' "$(jq -nc --argjson bindingId "${binding_id}" --arg run "${RUN_ID}" '{bindingId:$bindingId,policyCode:("e2e.customer.lookup.policy."+$run),policyName:"Customer Lookup Runtime Policy"}')" "${policy_file}"
policy_id=$(jq -r '.id' "${policy_file}")
policy_version_file="${EVIDENCE_DIR}/policy-version.json"
post "/control/v1/policies/${policy_id}/versions" "$(jq -nc '{document:{apiVersion:"tpip.policy/v1alpha1",kind:"PolicyChain",stages:{AFTER_REQUEST_MAPPING:[{id:"inject-request-id",use:"builtin.transport.inject@1.0.0",with:{headers:{"X-Request-Id":"${context.requestId}"}},onFailure:"FAIL"}],BEFORE_TRANSPORT:[{id:"provider-api-key",use:"builtin.auth.api-key@1.0.0",with:{secretRef:"env://TPIP_SECRET_E2E_PROVIDER_API_KEY",headerName:"X-API-Key",prefix:"ApiKey "},onFailure:"FAIL"}]}}}')" "${policy_version_file}"
policy_version_id=$(jq -r '.id' "${policy_version_file}")
post "/control/v1/policies/${policy_id}/versions/${policy_version_id}:publish" '' "${EVIDENCE_DIR}/policy-published.json"

binding_version_file="${EVIDENCE_DIR}/binding-version.json"
post "/control/v1/bindings/${binding_id}/versions" "$(jq -nc --argjson canonicalRequestContractVersionId "${canonical_request_version_id}" --argjson canonicalResponseContractVersionId "${canonical_response_version_id}" --argjson providerContractVersionId "${provider_contract_version_id}" --argjson endpointId "${endpoint_id}" --argjson requestMappingVersionId "${outbound_version_id}" --argjson responseMappingVersionId "${inbound_version_id}" --argjson policyVersionId "${policy_version_id}" '{canonicalRequestContractVersionId:$canonicalRequestContractVersionId,canonicalResponseContractVersionId:$canonicalResponseContractVersionId,providerContractVersionId:$providerContractVersionId,endpointId:$endpointId,requestMappingVersionId:$requestMappingVersionId,responseMappingVersionId:$responseMappingVersionId,callbackMappingVersionId:null,policyVersionId:$policyVersionId,errorMappingVersionId:null,complianceMetadata:{acceptance:"local-e2e"},routingAttributes:{}}')" "${binding_version_file}"
binding_version_id=$(jq -r '.id' "${binding_version_file}")
post "/control/v1/bindings/${binding_id}/versions/${binding_version_id}:publish" '' "${EVIDENCE_DIR}/binding-version-published.json"

fixture_suite_file="${EVIDENCE_DIR}/fixture-suite.json"
post '/control/v1/fixture-suites' "$(jq -nc --argjson bindingId "${binding_id}" --arg run "${RUN_ID}" '{bindingId:$bindingId,suiteCode:("e2e.customer.lookup.fixtures."+$run),suiteName:"Customer Lookup Verification Fixtures",description:"Immutable server-side E2E fixtures"}')" "${fixture_suite_file}"
fixture_suite_id=$(jq -r '.id' "${fixture_suite_file}")
fixture_version_file="${EVIDENCE_DIR}/fixture-suite-version.json"
fixture_cases=$(jq -nc '{cases:[
  {caseCode:"outbound.success",caseName:"Canonical request to provider request",caseOrder:10,executionMode:"MAPPING",direction:"OUTBOUND_REQUEST",source:{customerId:"C1001"},expectedSuccess:true,assertions:[
    {code:"mapping-success",type:"SUCCESS",expected:true},
    {code:"member-number",type:"JSON_PATH",path:"$.member_no",operator:"EQUALS",expected:"C1001"},
    {code:"provider-request-schema",type:"JSON_SCHEMA",schema:{type:"object",required:["member_no"],properties:{member_no:{type:"string"}},additionalProperties:false}},
    {code:"member-policy",type:"POLICY_EXPRESSION",expression:"$.member_no == \"C1001\""}
  ]},
  {caseCode:"inbound.success",caseName:"Provider response to canonical response",caseOrder:20,executionMode:"MAPPING",direction:"INBOUND_RESPONSE",source:{code:"0",data:{member_no:"C1001",member_name:"张三",mobile_no:"13800138000",member_status:"ACTIVE"}},expectedSuccess:true,assertions:[
    {code:"mapping-success",type:"SUCCESS",expected:true},
    {code:"customer-id",type:"JSON_PATH",path:"$.customerId",operator:"EQUALS",expected:"C1001"},
    {code:"mobile-format",type:"JSON_PATH",path:"$.mobile",operator:"MATCHES",expected:"^138[0-9]{8}$"},
    {code:"canonical-response-schema",type:"JSON_SCHEMA",schema:{type:"object",required:["customerId","customerName","mobile","status"],properties:{customerId:{type:"string"},customerName:{type:"string"},mobile:{type:"string"},status:{type:"string"}},additionalProperties:false}},
    {code:"active-policy",type:"POLICY_EXPRESSION",expression:"$.status == \"ACTIVE\""}
  ]},
  {caseCode:"remote.success",caseName:"Canonical request through candidate runtime pipeline",caseOrder:30,executionMode:"REMOTE_CALL",direction:"OUTBOUND_REQUEST",source:{customerId:"C1001"},expectedSuccess:true,assertions:[
    {code:"runtime-success",type:"SUCCESS",expected:true},
    {code:"http-status",type:"HTTP_STATUS",expected:200},
    {code:"content-type",type:"HTTP_HEADER",name:"Content-Type",operator:"CONTAINS",expected:"application/json"},
    {code:"provider-member-number",type:"JSON_PATH",path:"$.data.member_no",operator:"EQUALS",expected:"C1001"},
    {code:"provider-response-schema",type:"JSON_SCHEMA",schema:{type:"object",required:["code","data"],properties:{code:{const:"0"},data:{type:"object",required:["member_no"],properties:{member_no:{type:"string"}}}}}},
    {code:"status-policy",type:"POLICY_EXPRESSION",expression:"http.status == 200"}
  ]}
]}')
post "/control/v1/fixture-suites/${fixture_suite_id}/versions" "${fixture_cases}" "${fixture_version_file}"
fixture_suite_version_id=$(jq -r '.id' "${fixture_version_file}")
post "/control/v1/fixture-suites/${fixture_suite_id}/versions/${fixture_suite_version_id}:publish" '' "${EVIDENCE_DIR}/fixture-suite-version-published.json"

preview_file="${EVIDENCE_DIR}/bundle-preview.json"
request GET "/control/v1/bindings/${binding_id}/versions/${binding_version_id}/bundle-preview?bundleCode=e2e.customer.lookup.bundle.${RUN_ID}&bundleVersion=1.0.0" '' "${preview_file}"

workspace_file="${EVIDENCE_DIR}/workspace.json"
post '/control/v1/workspaces' "$(jq -nc --arg run "${RUN_ID}" '{workspaceCode:("e2e.customer.lookup.workspace."+$run),workspaceName:"Customer Lookup E2E Workspace",baseBundleId:null,environmentCode:"test",riskLevel:"LOW",ownerCode:"e2e-owner"}')" "${workspace_file}"
workspace_id=$(jq -r '.id' "${workspace_file}")
post "/control/v1/workspaces/${workspace_id}/assets/binding-version" "$(jq -nc --argjson bindingId "${binding_id}" --argjson bindingVersionId "${binding_version_id}" '{bindingId:$bindingId,bindingVersionId:$bindingVersionId,changeType:"ADD",dependencyMetadata:{acceptance:"local-e2e"}}')" "${EVIDENCE_DIR}/workspace-asset.json"
workspace_row_version=$(jq -r '.rowVersion' "${workspace_file}")
post "/control/v1/workspaces/${workspace_id}:verify" "$(jq -nc --argjson fixtureSuiteVersionId "${fixture_suite_version_id}" --argjson rowVersion "${workspace_row_version}" '{fixtureSuiteVersionId:$fixtureSuiteVersionId,rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/workspace-verification.json"
verification_job_id=$(jq -r '.id' "${EVIDENCE_DIR}/workspace-verification.json")
request GET "/control/v1/verification-jobs/${verification_job_id}/checks" '' "${EVIDENCE_DIR}/workspace-verification-checks.json"
request GET "/control/v1/workspaces/${workspace_id}" '' "${EVIDENCE_DIR}/workspace-verified.json"
workspace_row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/workspace-verified.json")
post "/control/v1/workspaces/${workspace_id}:submit-review" "$(jq -nc --argjson rowVersion "${workspace_row_version}" '{rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/workspace-review.json"
workspace_row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/workspace-review.json")
post "/control/v1/workspaces/${workspace_id}/approvals" "$(jq -nc --argjson rowVersion "${workspace_row_version}" '{approvalStage:"RELEASE",decision:"APPROVED",decisionComment:"Local automated E2E acceptance",evidenceSnapshot:{fixtures:"PASSED"},rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/workspace-approval.json"
request GET "/control/v1/workspaces/${workspace_id}" '' "${EVIDENCE_DIR}/workspace-approved.json"
workspace_row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/workspace-approved.json")
bundle_file="${EVIDENCE_DIR}/bundle-compiled.json"
post "/control/v1/workspaces/${workspace_id}/bundles" "$(jq -nc --arg run "${RUN_ID}" --argjson rowVersion "${workspace_row_version}" '{bundleCode:("e2e.customer.lookup.bundle."+$run),bundleVersion:"1.0.0",rowVersion:$rowVersion}')" "${bundle_file}"
bundle_id=$(jq -r '.id' "${bundle_file}")
post "/control/v1/bundles/${bundle_id}:publish" '' "${EVIDENCE_DIR}/bundle-published.json"

jq -n --arg runId "${RUN_ID}" --arg evidenceDir "${EVIDENCE_DIR}" \
  --argjson operationId "${operation_id}" --argjson bindingId "${binding_id}" \
  --argjson bindingVersionId "${binding_version_id}" --argjson fixtureSuiteVersionId "${fixture_suite_version_id}" --argjson workspaceId "${workspace_id}" \
  --argjson bundleId "${bundle_id}" \
  --arg bundleCode "$(jq -r '.bundleCode' "${EVIDENCE_DIR}/bundle-published.json")" \
  --arg bundleVersion "$(jq -r '.bundleVersion' "${EVIDENCE_DIR}/bundle-published.json")" \
  --arg artifactChecksum "$(jq -r '.artifactChecksum' "${EVIDENCE_DIR}/bundle-published.json")" \
  '{runId:$runId,evidenceDir:$evidenceDir,operationCode:"e2e.customer.lookup",operationId:$operationId,bindingId:$bindingId,bindingVersionId:$bindingVersionId,fixtureSuiteVersionId:$fixtureSuiteVersionId,workspaceId:$workspaceId,bundleId:$bundleId,bundleCode:$bundleCode,bundleVersion:$bundleVersion,artifactChecksum:$artifactChecksum}' \
  > "${EVIDENCE_DIR}/asset-ids.json"

echo "${EVIDENCE_DIR}"
