#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="${1:?usage: finish-bootstrap.sh EVIDENCE_DIR}"
CONTROL_BASE="${TPIP_E2E_CONTROL_BASE:-http://127.0.0.1:18080}"
OPERATOR="${TPIP_E2E_OPERATOR:-codex-e2e}"
RUN_ID="$(basename "${EVIDENCE_DIR}")"

request() {
  local method="$1" path="$2" data="$3" output="$4" status
  if [[ -n "${data}" ]]; then
    status=$(curl -sS --max-time 20 -o "${output}" -w '%{http_code}' -X "${method}" \
      "${CONTROL_BASE}${path}" -H 'Content-Type: application/json' -H "X-Operator: ${OPERATOR}" --data-binary "${data}")
  else
    status=$(curl -sS --max-time 20 -o "${output}" -w '%{http_code}' -X "${method}" \
      "${CONTROL_BASE}${path}" -H "X-Operator: ${OPERATOR}")
  fi
  if [[ "${status}" -lt 200 || "${status}" -ge 300 ]]; then
    echo "HTTP ${status}: ${method} ${path}" >&2; jq . "${output}" >&2 || true; exit 1
  fi
}
post() { request POST "$1" "$2" "$3"; }

provider_contract_id=$(jq -r '.id' "${EVIDENCE_DIR}/provider-contract.json")
provider_contract_version_id=$(jq -r '.id' "${EVIDENCE_DIR}/provider-contract-version.json")
credential_id=$(jq -r '.id' "${EVIDENCE_DIR}/credential.json")
operation_id=$(jq -r '.id' "${EVIDENCE_DIR}/operation.json")
canonical_request_version_id=$(jq -r '.id' "${EVIDENCE_DIR}/canonical-request-version.json")
canonical_response_version_id=$(jq -r '.id' "${EVIDENCE_DIR}/canonical-response-version.json")
canonical_request_id=$(jq -r '.id' "${EVIDENCE_DIR}/canonical-request.json")
canonical_response_id=$(jq -r '.id' "${EVIDENCE_DIR}/canonical-response.json")

if ! jq -e '.id > 0' "${EVIDENCE_DIR}/endpoint.json" >/dev/null 2>&1; then
  post '/control/v1/endpoints' "$(jq -nc --argjson providerContractId "${provider_contract_id}" --argjson credentialRefId "${credential_id}" --arg run "${RUN_ID}" '{providerContractId:$providerContractId,endpointCode:("e2e.customer.member-query."+$run),environmentCode:"test",protocolScheme:"HTTP",baseUrl:"http://127.0.0.1:19090",resourcePath:"/vendor/v1/members/query",httpMethod:"POST",contentType:"application/json",charsetName:"UTF-8",connectTimeoutMs:1000,readTimeoutMs:2000,totalTimeoutMs:3000,credentialRefId:$credentialRefId,networkConfig:{},tlsConfig:null}')" "${EVIDENCE_DIR}/endpoint.json"
fi
endpoint_id=$(jq -r '.id' "${EVIDENCE_DIR}/endpoint.json")
if [[ ! -s "${EVIDENCE_DIR}/endpoint-published.json" ]]; then post "/control/v1/endpoints/${endpoint_id}:publish" '' "${EVIDENCE_DIR}/endpoint-published.json"; fi
if [[ ! -s "${EVIDENCE_DIR}/endpoint-probe.json" ]]; then post "/control/v1/endpoints/${endpoint_id}:probe" '' "${EVIDENCE_DIR}/endpoint-probe.json"; fi

if ! jq -e '.id > 0' "${EVIDENCE_DIR}/binding.json" >/dev/null 2>&1; then
  post '/control/v1/bindings' "$(jq -nc --argjson operationId "${operation_id}" --argjson providerContractId "${provider_contract_id}" --arg run "${RUN_ID}" '{bindingCode:("e2e.customer.lookup.mock."+$run),bindingName:"Customer Lookup Mock Binding",operationId:$operationId,providerContractId:$providerContractId,ownerCode:"e2e"}')" "${EVIDENCE_DIR}/binding.json"
fi
binding_id=$(jq -r '.id' "${EVIDENCE_DIR}/binding.json")

if ! jq -e '.id > 0' "${EVIDENCE_DIR}/mapping-outbound.json" >/dev/null 2>&1; then
  post '/control/v1/mappings' "$(jq -nc --argjson bindingId "${binding_id}" --arg run "${RUN_ID}" '{bindingId:$bindingId,mappingCode:("e2e.customer.lookup.outbound."+$run),mappingName:"Customer Lookup Outbound",direction:"OUTBOUND_REQUEST"}')" "${EVIDENCE_DIR}/mapping-outbound.json"
fi
outbound_id=$(jq -r '.id' "${EVIDENCE_DIR}/mapping-outbound.json")
post "/control/v1/mappings/${outbound_id}/versions" "$(jq -nc --arg sourceSchemaRef "canonical-contract-version:${canonical_request_id}:${canonical_request_version_id}" --arg targetSchemaRef "provider-contract-version:${provider_contract_id}:${provider_contract_version_id}" '{selectorProfile:"JSONPATH_1_0",sourceSchemaRef:$sourceSchemaRef,targetSchemaRef:$targetSchemaRef,mappingOptions:{},rules:[{ruleCode:"customer-id-to-member-no",ruleOrder:10,valueSource:"SELECTOR",sourceSelector:"$.customerId",targetSelector:"$.member_no",targetType:"STRING",constantValue:null,defaultValue:null,converterCode:null,converterConfig:null,conditionExpression:null,required:true,arrayStrategy:null,missingStrategy:"FAIL",errorStrategy:"FAIL",enabled:true}]}')" "${EVIDENCE_DIR}/mapping-outbound-version.json"
outbound_version_id=$(jq -r '.id' "${EVIDENCE_DIR}/mapping-outbound-version.json")
post "/control/v1/mappings/${outbound_id}/versions/${outbound_version_id}:test" "$(jq -nc '{source:{customerId:"C1001"},requestId:"fixture-outbound",traceId:"fixture-outbound",operationCode:"e2e.customer.lookup",attributes:{}}')" "${EVIDENCE_DIR}/mapping-outbound-result.json"
post "/control/v1/mappings/${outbound_id}/versions/${outbound_version_id}:publish" '' "${EVIDENCE_DIR}/mapping-outbound-published.json"

post '/control/v1/mappings' "$(jq -nc --argjson bindingId "${binding_id}" --arg run "${RUN_ID}" '{bindingId:$bindingId,mappingCode:("e2e.customer.lookup.inbound."+$run),mappingName:"Customer Lookup Inbound",direction:"INBOUND_RESPONSE"}')" "${EVIDENCE_DIR}/mapping-inbound.json"
inbound_id=$(jq -r '.id' "${EVIDENCE_DIR}/mapping-inbound.json")
post "/control/v1/mappings/${inbound_id}/versions" "$(jq -nc --arg sourceSchemaRef "provider-contract-version:${provider_contract_id}:${provider_contract_version_id}" --arg targetSchemaRef "canonical-contract-version:${canonical_response_id}:${canonical_response_version_id}" '{selectorProfile:"JSONPATH_1_0",sourceSchemaRef:$sourceSchemaRef,targetSchemaRef:$targetSchemaRef,mappingOptions:{},rules:[{ruleCode:"member-no-to-customer-id",ruleOrder:10,valueSource:"SELECTOR",sourceSelector:"$.data.member_no",targetSelector:"$.customerId",targetType:"STRING",required:true,arrayStrategy:null,missingStrategy:"FAIL",errorStrategy:"FAIL",enabled:true},{ruleCode:"member-name-to-customer-name",ruleOrder:20,valueSource:"SELECTOR",sourceSelector:"$.data.member_name",targetSelector:"$.customerName",targetType:"STRING",required:true,arrayStrategy:null,missingStrategy:"FAIL",errorStrategy:"FAIL",enabled:true},{ruleCode:"mobile-no-to-mobile",ruleOrder:30,valueSource:"SELECTOR",sourceSelector:"$.data.mobile_no",targetSelector:"$.mobile",targetType:"STRING",required:true,arrayStrategy:null,missingStrategy:"FAIL",errorStrategy:"FAIL",enabled:true},{ruleCode:"member-status-to-status",ruleOrder:40,valueSource:"SELECTOR",sourceSelector:"$.data.member_status",targetSelector:"$.status",targetType:"STRING",required:true,arrayStrategy:null,missingStrategy:"FAIL",errorStrategy:"FAIL",enabled:true}]}')" "${EVIDENCE_DIR}/mapping-inbound-version.json"
inbound_version_id=$(jq -r '.id' "${EVIDENCE_DIR}/mapping-inbound-version.json")
post "/control/v1/mappings/${inbound_id}/versions/${inbound_version_id}:test" "$(jq -nc '{source:{code:"0",data:{member_no:"C1001",member_name:"张三",mobile_no:"13800138000",member_status:"ACTIVE"}},requestId:"fixture-inbound",traceId:"fixture-inbound",operationCode:"e2e.customer.lookup",attributes:{}}')" "${EVIDENCE_DIR}/mapping-inbound-result.json"
post "/control/v1/mappings/${inbound_id}/versions/${inbound_version_id}:publish" '' "${EVIDENCE_DIR}/mapping-inbound-published.json"

post '/control/v1/policies' "$(jq -nc --argjson bindingId "${binding_id}" --arg run "${RUN_ID}" '{bindingId:$bindingId,policyCode:("e2e.customer.lookup.policy."+$run),policyName:"Customer Lookup Runtime Policy"}')" "${EVIDENCE_DIR}/policy.json"
policy_id=$(jq -r '.id' "${EVIDENCE_DIR}/policy.json")
post "/control/v1/policies/${policy_id}/versions" "$(jq -nc '{document:{apiVersion:"tpip.policy/v1alpha1",kind:"PolicyChain",stages:{AFTER_REQUEST_MAPPING:[{id:"inject-request-id",use:"builtin.transport.inject@1.0.0",with:{headers:{"X-Request-Id":"${context.requestId}"}},onFailure:"FAIL"}],BEFORE_TRANSPORT:[{id:"provider-api-key",use:"builtin.auth.api-key@1.0.0",with:{secretRef:"env://TPIP_SECRET_E2E_PROVIDER_API_KEY",headerName:"X-API-Key",prefix:"ApiKey "},onFailure:"FAIL"}]}}}')" "${EVIDENCE_DIR}/policy-version.json"
policy_version_id=$(jq -r '.id' "${EVIDENCE_DIR}/policy-version.json")
post "/control/v1/policies/${policy_id}/versions/${policy_version_id}:publish" '' "${EVIDENCE_DIR}/policy-published.json"

post "/control/v1/bindings/${binding_id}/versions" "$(jq -nc --argjson canonicalRequestContractVersionId "${canonical_request_version_id}" --argjson canonicalResponseContractVersionId "${canonical_response_version_id}" --argjson providerContractVersionId "${provider_contract_version_id}" --argjson endpointId "${endpoint_id}" --argjson requestMappingVersionId "${outbound_version_id}" --argjson responseMappingVersionId "${inbound_version_id}" --argjson policyVersionId "${policy_version_id}" '{canonicalRequestContractVersionId:$canonicalRequestContractVersionId,canonicalResponseContractVersionId:$canonicalResponseContractVersionId,providerContractVersionId:$providerContractVersionId,endpointId:$endpointId,requestMappingVersionId:$requestMappingVersionId,responseMappingVersionId:$responseMappingVersionId,callbackMappingVersionId:null,policyVersionId:$policyVersionId,errorMappingVersionId:null,complianceMetadata:{acceptance:"local-e2e"},routingAttributes:{}}')" "${EVIDENCE_DIR}/binding-version.json"
binding_version_id=$(jq -r '.id' "${EVIDENCE_DIR}/binding-version.json")
post "/control/v1/bindings/${binding_id}/versions/${binding_version_id}:publish" '' "${EVIDENCE_DIR}/binding-version-published.json"
post '/control/v1/fixture-suites' "$(jq -nc --argjson bindingId "${binding_id}" --arg run "${RUN_ID}" '{bindingId:$bindingId,suiteCode:("e2e.customer.lookup.fixtures."+$run),suiteName:"Customer Lookup Verification Fixtures",description:"Immutable server-side E2E fixtures"}')" "${EVIDENCE_DIR}/fixture-suite.json"
fixture_suite_id=$(jq -r '.id' "${EVIDENCE_DIR}/fixture-suite.json")
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
post "/control/v1/fixture-suites/${fixture_suite_id}/versions" "${fixture_cases}" "${EVIDENCE_DIR}/fixture-suite-version.json"
fixture_suite_version_id=$(jq -r '.id' "${EVIDENCE_DIR}/fixture-suite-version.json")
post "/control/v1/fixture-suites/${fixture_suite_id}/versions/${fixture_suite_version_id}:publish" '' "${EVIDENCE_DIR}/fixture-suite-version-published.json"
request GET "/control/v1/bindings/${binding_id}/versions/${binding_version_id}/bundle-preview?bundleCode=e2e.customer.lookup.bundle.${RUN_ID}&bundleVersion=1.0.0" '' "${EVIDENCE_DIR}/bundle-preview.json"

post '/control/v1/workspaces' "$(jq -nc --arg run "${RUN_ID}" '{workspaceCode:("e2e.customer.lookup.workspace."+$run),workspaceName:"Customer Lookup E2E Workspace",baseBundleId:null,environmentCode:"test",riskLevel:"LOW",ownerCode:"e2e-owner"}')" "${EVIDENCE_DIR}/workspace.json"
workspace_id=$(jq -r '.id' "${EVIDENCE_DIR}/workspace.json")
post "/control/v1/workspaces/${workspace_id}/assets/binding-version" "$(jq -nc --argjson bindingId "${binding_id}" --argjson bindingVersionId "${binding_version_id}" '{bindingId:$bindingId,bindingVersionId:$bindingVersionId,changeType:"ADD",dependencyMetadata:{acceptance:"local-e2e"}}')" "${EVIDENCE_DIR}/workspace-asset.json"
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/workspace.json")
post "/control/v1/workspaces/${workspace_id}:verify" "$(jq -nc --argjson fixtureSuiteVersionId "${fixture_suite_version_id}" --argjson rowVersion "${row_version}" '{fixtureSuiteVersionId:$fixtureSuiteVersionId,rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/workspace-verification.json"
verification_job_id=$(jq -r '.id' "${EVIDENCE_DIR}/workspace-verification.json")
request GET "/control/v1/verification-jobs/${verification_job_id}/checks" '' "${EVIDENCE_DIR}/workspace-verification-checks.json"
request GET "/control/v1/workspaces/${workspace_id}" '' "${EVIDENCE_DIR}/workspace-verified.json"
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/workspace-verified.json")
post "/control/v1/workspaces/${workspace_id}:submit-review" "$(jq -nc --argjson rowVersion "${row_version}" '{rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/workspace-review.json"
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/workspace-review.json")
post "/control/v1/workspaces/${workspace_id}/approvals" "$(jq -nc --argjson rowVersion "${row_version}" '{approvalStage:"RELEASE",decision:"APPROVED",decisionComment:"Local automated E2E acceptance",evidenceSnapshot:{fixtures:"PASSED"},rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/workspace-approval.json"
request GET "/control/v1/workspaces/${workspace_id}" '' "${EVIDENCE_DIR}/workspace-approved.json"
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/workspace-approved.json")
post "/control/v1/workspaces/${workspace_id}/bundles" "$(jq -nc --arg run "${RUN_ID}" --argjson rowVersion "${row_version}" '{bundleCode:("e2e.customer.lookup.bundle."+$run),bundleVersion:"1.0.0",rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/bundle-compiled.json"
bundle_id=$(jq -r '.id' "${EVIDENCE_DIR}/bundle-compiled.json")
post "/control/v1/bundles/${bundle_id}:publish" '' "${EVIDENCE_DIR}/bundle-published.json"

jq -n --arg runId "${RUN_ID}" --arg evidenceDir "${EVIDENCE_DIR}" --argjson operationId "${operation_id}" \
  --argjson bindingId "${binding_id}" --argjson bindingVersionId "${binding_version_id}" --argjson fixtureSuiteVersionId "${fixture_suite_version_id}" \
  --argjson workspaceId "${workspace_id}" --argjson bundleId "${bundle_id}" \
  --arg bundleCode "$(jq -r '.bundleCode' "${EVIDENCE_DIR}/bundle-published.json")" \
  --arg bundleVersion "$(jq -r '.bundleVersion' "${EVIDENCE_DIR}/bundle-published.json")" \
  --arg artifactChecksum "$(jq -r '.artifactChecksum' "${EVIDENCE_DIR}/bundle-published.json")" \
  '{runId:$runId,evidenceDir:$evidenceDir,operationCode:"e2e.customer.lookup",operationId:$operationId,bindingId:$bindingId,bindingVersionId:$bindingVersionId,fixtureSuiteVersionId:$fixtureSuiteVersionId,workspaceId:$workspaceId,bundleId:$bundleId,bundleCode:$bundleCode,bundleVersion:$bundleVersion,artifactChecksum:$artifactChecksum}' > "${EVIDENCE_DIR}/asset-ids.json"
echo "${EVIDENCE_DIR}"
