#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="${1:?usage: verify-rollback.sh EVIDENCE_DIR}"
CONTROL_BASE="${TPIP_E2E_CONTROL_BASE:-http://127.0.0.1:18080}"
RUNTIME_BASE="${TPIP_E2E_RUNTIME_BASE:-http://127.0.0.1:18081}"
OPERATOR="${TPIP_E2E_OPERATOR:-codex-e2e}"
RUN_ID=$(jq -r '.runId' "${EVIDENCE_DIR}/asset-ids.json")
BINDING_ID=$(jq -r '.bindingId' "${EVIDENCE_DIR}/asset-ids.json")
BINDING_VERSION_ID=$(jq -r '.bindingVersionId' "${EVIDENCE_DIR}/asset-ids.json")
FIXTURE_SUITE_VERSION_ID=$(jq -r '.fixtureSuiteVersionId' "${EVIDENCE_DIR}/asset-ids.json")
BASE_BUNDLE_ID=$(jq -r '.bundleId' "${EVIDENCE_DIR}/asset-ids.json")

request() {
  local method="$1" path="$2" data="$3" output="$4" status
  if [[ -n "${data}" ]]; then
    status=$(curl -sS --max-time 20 -o "${output}" -w '%{http_code}' -X "${method}" "${CONTROL_BASE}${path}" \
      -H 'Content-Type: application/json' -H "X-Operator: ${OPERATOR}" --data-binary "${data}")
  else
    status=$(curl -sS --max-time 20 -o "${output}" -w '%{http_code}' -X "${method}" "${CONTROL_BASE}${path}" -H "X-Operator: ${OPERATOR}")
  fi
  if [[ "${status}" -lt 200 || "${status}" -ge 300 ]]; then
    echo "HTTP ${status}: ${method} ${path}" >&2; jq . "${output}" >&2 || true; exit 1
  fi
}
post() { request POST "$1" "$2" "$3"; }

post '/control/v1/workspaces' "$(jq -nc --arg run "${RUN_ID}" --argjson baseBundleId "${BASE_BUNDLE_ID}" '{workspaceCode:("e2e.customer.lookup.rollback."+$run),workspaceName:"Customer Lookup Rollback Verification",baseBundleId:$baseBundleId,environmentCode:"test",riskLevel:"LOW",ownerCode:"e2e-owner"}')" "${EVIDENCE_DIR}/rollback-workspace.json"
workspace_id=$(jq -r '.id' "${EVIDENCE_DIR}/rollback-workspace.json")
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/rollback-workspace.json")
post "/control/v1/workspaces/${workspace_id}/assets/binding-version" "$(jq -nc --argjson bindingId "${BINDING_ID}" --argjson bindingVersionId "${BINDING_VERSION_ID}" '{bindingId:$bindingId,bindingVersionId:$bindingVersionId,changeType:"REFERENCE",dependencyMetadata:{purpose:"rollback-e2e"}}')" "${EVIDENCE_DIR}/rollback-workspace-asset.json"
post "/control/v1/workspaces/${workspace_id}:verify" "$(jq -nc --argjson fixtureSuiteVersionId "${FIXTURE_SUITE_VERSION_ID}" --argjson rowVersion "${row_version}" '{fixtureSuiteVersionId:$fixtureSuiteVersionId,rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/rollback-workspace-verification.json"
request GET "/control/v1/workspaces/${workspace_id}" '' "${EVIDENCE_DIR}/rollback-workspace-verified.json"
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/rollback-workspace-verified.json")
post "/control/v1/workspaces/${workspace_id}:submit-review" "$(jq -nc --argjson rowVersion "${row_version}" '{rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/rollback-workspace-review.json"
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/rollback-workspace-review.json")
post "/control/v1/workspaces/${workspace_id}/approvals" "$(jq -nc --argjson rowVersion "${row_version}" '{approvalStage:"RELEASE",decision:"APPROVED",decisionComment:"Rollback E2E candidate",evidenceSnapshot:{verified:true},rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/rollback-workspace-approval.json"
request GET "/control/v1/workspaces/${workspace_id}" '' "${EVIDENCE_DIR}/rollback-workspace-approved.json"
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/rollback-workspace-approved.json")
post "/control/v1/workspaces/${workspace_id}/bundles" "$(jq -nc --arg run "${RUN_ID}" --argjson rowVersion "${row_version}" '{bundleCode:("e2e.customer.lookup.rollback-bundle."+$run),bundleVersion:"1.0.1",rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/rollback-bundle-compiled.json"
bundle_id=$(jq -r '.id' "${EVIDENCE_DIR}/rollback-bundle-compiled.json")
post "/control/v1/bundles/${bundle_id}:publish" '' "${EVIDENCE_DIR}/rollback-bundle-published.json"

post '/control/v1/deployments' "$(jq -nc --arg run "${RUN_ID}" --argjson bundleId "${bundle_id}" '{deploymentCode:("e2e.customer.lookup.canary."+$run),bundleId:$bundleId,rolloutMetadata:{strategy:"CANARY",purpose:"rollback-e2e"}}')" "${EVIDENCE_DIR}/rollback-deployment-created.json"
deployment_id=$(jq -r '.id' "${EVIDENCE_DIR}/rollback-deployment-created.json")
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/rollback-deployment-created.json")
post "/control/v1/deployments/${deployment_id}:preheat" "$(jq -nc --argjson rowVersion "${row_version}" '{rowVersion:$rowVersion}')" "${EVIDENCE_DIR}/rollback-preheat.json"
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/rollback-preheat.json")
post "/control/v1/deployments/${deployment_id}:activate" "$(jq -nc --argjson rowVersion "${row_version}" '{rowVersion:$rowVersion,initialTraffic:10.00}')" "${EVIDENCE_DIR}/rollback-canary-active.json"
row_version=$(jq -r '.rowVersion' "${EVIDENCE_DIR}/rollback-canary-active.json")
post "/control/v1/deployments/${deployment_id}:rollback" "$(jq -nc --arg run "${RUN_ID}" --argjson rowVersion "${row_version}" '{rowVersion:$rowVersion,rollbackDeploymentCode:("e2e.customer.lookup.restored."+$run),reason:"Automated local E2E rollback verification"}')" "${EVIDENCE_DIR}/E2E-014.json"
jq -e '.deploymentStatus == "ACTIVE" and .trafficPercentage == 100' "${EVIDENCE_DIR}/E2E-014.json" >/dev/null
request GET '/runtime-config/v1/routes/e2e.customer.lookup/environments/test' '' "${EVIDENCE_DIR}/rollback-route.json"
jq -e --arg run "${RUN_ID}" '.targets | length == 1 and .[0].deploymentCode == ("e2e.customer.lookup.restored."+$run) and .[0].trafficPercentage == 100' "${EVIDENCE_DIR}/rollback-route.json" >/dev/null

sleep 6
status=$(curl -sS --max-time 20 -o "${EVIDENCE_DIR}/rollback-invocation.json" -w '%{http_code}' -X POST \
  "${RUNTIME_BASE}/integration/v1/operations/e2e.customer.lookup:invoke" -H 'Content-Type: application/json' \
  --data-binary "$(jq -nc --arg run "${RUN_ID}" '{meta:{requestId:("E2E-014-"+$run),caller:"local-e2e",attributes:{traceId:("trace-E2E-014-"+$run)}},payload:{customerId:"C1001"}}')")
[[ "${status}" == "200" ]]
jq -e '.result.code == "SUCCESS"' "${EVIDENCE_DIR}/rollback-invocation.json" >/dev/null
echo "${EVIDENCE_DIR}/E2E-014.json"
