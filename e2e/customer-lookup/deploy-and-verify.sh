#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="${1:?usage: deploy-and-verify.sh EVIDENCE_DIR}"
CONTROL_BASE="${TPIP_E2E_CONTROL_BASE:-http://127.0.0.1:18080}"
RUNTIME_BASE="${TPIP_E2E_RUNTIME_BASE:-http://127.0.0.1:18081}"
MOCK_BASE="${TPIP_E2E_MOCK_BASE:-http://127.0.0.1:19090}"
OPERATOR="${TPIP_E2E_OPERATOR:-codex-e2e}"
RUN_ID=$(jq -r '.runId' "${EVIDENCE_DIR}/asset-ids.json")
BUNDLE_ID=$(jq -r '.bundleId' "${EVIDENCE_DIR}/asset-ids.json")

request() {
  local method="$1" url="$2" data="$3" output="$4" status
  if [[ -n "${data}" ]]; then
    status=$(curl -sS --max-time 20 -o "${output}" -w '%{http_code}' -X "${method}" "${url}" \
      -H 'Content-Type: application/json' -H "X-Operator: ${OPERATOR}" --data-binary "${data}")
  else
    status=$(curl -sS --max-time 20 -o "${output}" -w '%{http_code}' -X "${method}" "${url}" -H "X-Operator: ${OPERATOR}")
  fi
  if [[ "${status}" -lt 200 || "${status}" -ge 300 ]]; then
    echo "HTTP ${status}: ${method} ${url}" >&2; jq . "${output}" >&2 || true; exit 1
  fi
}

request GET "${RUNTIME_BASE}/actuator/health" '' "${EVIDENCE_DIR}/runtime-health-before-deploy.json"
request POST "${MOCK_BASE}/mock/admin/reset" '{}' "${EVIDENCE_DIR}/mock-reset.json"

if ! jq -e '.id > 0' "${EVIDENCE_DIR}/deployment-created.json" >/dev/null 2>&1; then
  request POST "${CONTROL_BASE}/control/v1/deployments" \
    "$(jq -nc --arg run "${RUN_ID}" --argjson bundleId "${BUNDLE_ID}" '{deploymentCode:("e2e.customer.lookup.deployment."+$run),bundleId:$bundleId,rolloutMetadata:{strategy:"LOCAL_E2E"}}')" \
    "${EVIDENCE_DIR}/deployment-created.json"
fi
deployment_id=$(jq -r '.id' "${EVIDENCE_DIR}/deployment-created.json")
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/deployment-created.json")

if ! jq -e '.deploymentStatus == "READY"' "${EVIDENCE_DIR}/preheat-result.json" >/dev/null 2>&1; then
  request POST "${CONTROL_BASE}/control/v1/deployments/${deployment_id}:preheat" \
    "$(jq -nc --argjson rowVersion "${row_version}" '{rowVersion:$rowVersion}')" \
    "${EVIDENCE_DIR}/preheat-result.json"
fi
jq -e '.deploymentStatus == "READY"' "${EVIDENCE_DIR}/preheat-result.json" >/dev/null
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/preheat-result.json")

if ! jq -e '.deploymentStatus == "ACTIVE"' "${EVIDENCE_DIR}/deployment-result.json" >/dev/null 2>&1; then
  request POST "${CONTROL_BASE}/control/v1/deployments/${deployment_id}:activate" \
    "$(jq -nc --argjson rowVersion "${row_version}" '{rowVersion:$rowVersion,initialTraffic:100.00}')" \
    "${EVIDENCE_DIR}/deployment-result.json"
fi
jq -e '.deploymentStatus == "ACTIVE" and .trafficPercentage == 100' "${EVIDENCE_DIR}/deployment-result.json" >/dev/null
request GET "${CONTROL_BASE}/runtime-config/v1/routes/e2e.customer.lookup/environments/test" '' "${EVIDENCE_DIR}/runtime-route.json"

invoke() {
  local case_id="$1" customer_payload="$2" expected_code="$3"
  local output="${EVIDENCE_DIR}/${case_id}.json"
  local request_id="${case_id}-${RUN_ID}"
  request POST "${RUNTIME_BASE}/integration/v1/operations/e2e.customer.lookup:invoke" \
    "$(jq -nc --arg requestId "${request_id}" --argjson payload "${customer_payload}" '{meta:{requestId:$requestId,caller:"local-e2e",tenantId:null,idempotencyKey:null,deadline:null,attributes:{traceId:("trace-"+$requestId)}},payload:$payload}')" \
    "${output}"
  jq -e --arg expected "${expected_code}" '.result.code == $expected' "${output}" >/dev/null
}

invoke E2E-001 '{"customerId":"C1001"}' SUCCESS
jq -e '.result.success == true and .payload == {"customerId":"C1001","customerName":"张三","mobile":"13800138000","status":"ACTIVE"}' "${EVIDENCE_DIR}/E2E-001.json" >/dev/null
requests_after_success=$(curl -sS --max-time 10 "${MOCK_BASE}/mock/admin/stats" | jq -r '.total')

invoke E2E-002 '{}' CANONICAL_REQUEST_INVALID
invoke E2E-003 '{"customerId":"C1001","illegal":"field"}' CANONICAL_REQUEST_INVALID
requests_after_invalid=$(curl -sS --max-time 10 "${MOCK_BASE}/mock/admin/stats" | jq -r '.total')
[[ "${requests_after_success}" == "${requests_after_invalid}" ]]

invoke E2E-006 '{"customerId":"C404"}' PROVIDER_HTTP_ERROR
invoke E2E-007 '{"customerId":"C500"}' PROVIDER_HTTP_ERROR
invoke E2E-008 '{"customerId":"CSLOW"}' TRANSPORT_FAILED
invoke E2E-009 '{"customerId":"CBAD"}' PROVIDER_RESPONSE_INVALID

request GET "${MOCK_BASE}/mock/admin/stats" '' "${EVIDENCE_DIR}/mock-provider-stats.json"
jq -e --arg requestId "E2E-001-${RUN_ID}" '.requests[] | select(.request_id == $requestId and .authorized == true)' "${EVIDENCE_DIR}/mock-provider-stats.json" >/dev/null
request GET "${RUNTIME_BASE}/actuator/prometheus" '' "${EVIDENCE_DIR}/runtime-metrics.txt"

success_count=$(jq -s '[.[] | select(.result.code == "SUCCESS")] | length' "${EVIDENCE_DIR}"/E2E-*.json)
failure_count=$(jq -s '[.[] | select(.result.code != "SUCCESS")] | length' "${EVIDENCE_DIR}"/E2E-*.json)
jq -n --arg runId "${RUN_ID}" --argjson deploymentId "${deployment_id}" \
  --argjson successCount "${success_count}" --argjson failureCount "${failure_count}" \
  --arg routeRevision "$(jq -r '.revision' "${EVIDENCE_DIR}/runtime-route.json")" \
  '{runId:$runId,deploymentId:$deploymentId,executedCases:($successCount+$failureCount),successfulBusinessCases:$successCount,expectedFailureCases:$failureCount,routeRevision:$routeRevision,status:"CORE_E2E_PASSED"}' \
  > "${EVIDENCE_DIR}/acceptance-summary.json"

echo "${EVIDENCE_DIR}/acceptance-summary.json"
