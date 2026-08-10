#!/usr/bin/env bash
set -euo pipefail

CONTROL_BASE="${TPIP_E2E_CONTROL_BASE:-http://127.0.0.1:18082}"
OPERATOR="${TPIP_E2E_OPERATOR:-codex-e2e}"
WORKER_ID="${TPIP_E2E_WORKER_ID:-codex-e2e-worker}"
RUN_ID="${TPIP_E2E_RUN_ID:-$(date +%Y%m%d%H%M%S)}"
EVIDENCE_DIR="e2e/global-impact-seal/evidence/${RUN_ID}"
mkdir -p "${EVIDENCE_DIR}"

request() {
  local method="$1" path="$2" data="$3" output="$4" identity_header="${5:-X-Operator: ${OPERATOR}}"
  local status
  if [[ -n "${data}" ]]; then
    status=$(curl -sS --max-time 60 -o "${output}" -w '%{http_code}' -X "${method}" \
      "${CONTROL_BASE}${path}" -H 'Content-Type: application/json' -H "${identity_header}" \
      --data-binary "${data}")
  else
    status=$(curl -sS --max-time 20 -o "${output}" -w '%{http_code}' -X "${method}" \
      "${CONTROL_BASE}${path}" -H "${identity_header}")
  fi
  if [[ "${status}" -lt 200 || "${status}" -ge 300 ]]; then
    echo "HTTP ${status}: ${method} ${path}" >&2
    jq . "${output}" >&2 || true
    exit 1
  fi
}

request GET '/actuator/health' '' "${EVIDENCE_DIR}/health.json"
jq -e '.status == "UP"' "${EVIDENCE_DIR}/health.json" >/dev/null

policy_file="${EVIDENCE_DIR}/policy.json"
request POST '/control/v1/drift-governance-policies' "$(jq -nc --arg run "${RUN_ID}" \
  '{policyCode:("drift.global.ui.e2e."+$run),policyName:("UI Sealed Snapshot E2E "+$run),scope:"GLOBAL",workspaceId:null}')" "${policy_file}"
policy_id=$(jq -r '.id' "${policy_file}")

version_file="${EVIDENCE_DIR}/policy-version.json"
request POST "/control/v1/drift-governance-policies/${policy_id}/versions" \
  '{"overdueAfterSeconds":86400,"aggregationWindowSeconds":3600,"reminderIntervalSeconds":7200,"maximumReminders":3,"ownerCode":"codex-e2e","suppressedDriftKinds":[],"suppressedCheckCodes":[]}' \
  "${version_file}"
version_id=$(jq -r '.id' "${version_file}")

job_file="${EVIDENCE_DIR}/job-created.json"
request POST '/control/v1/verification-drift-workbench/global-governance-policy-impact-jobs' \
  "$(jq -nc --argjson policyId "${policy_id}" --argjson versionId "${version_id}" \
    '{candidatePolicyId:$policyId,candidateVersionId:$versionId,ttlSeconds:3600}')" "${job_file}"
job_id=$(jq -r '.jobId' "${job_file}")

ready_file="${EVIDENCE_DIR}/job-ready.json"
request POST "/control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/${job_id}:run-batch" \
  '{"batchSize":100}' "${ready_file}" "X-Worker-Id: ${WORKER_ID}"
jq -e '.job.status == "READY" and .job.workspaceCount == .job.succeededCount and .job.failedCount == 0' \
  "${ready_file}" >/dev/null
row_version=$(jq -r '.job.rowVersion' "${ready_file}")

request GET "/control/v1/verification-drift-workbench/global-governance-policy-impact-job-views/${job_id}" '' \
  "${EVIDENCE_DIR}/job-ready-view.json"
jq -e '[.summary.allowedActions[] | select(.action == "SEAL")][0].enabled == true' \
  "${EVIDENCE_DIR}/job-ready-view.json" >/dev/null

sealed_file="${EVIDENCE_DIR}/job-sealed.json"
request POST "/control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/${job_id}:seal" \
  "$(jq -nc --argjson rowVersion "${row_version}" '{rowVersion:$rowVersion}')" "${sealed_file}"
jq -e '.status == "SEALED" and (.sealedSnapshotId | length) == 36' "${sealed_file}" >/dev/null
snapshot_id=$(jq -r '.sealedSnapshotId' "${sealed_file}")

snapshot_file="${EVIDENCE_DIR}/snapshot-view.json"
request GET "/control/v1/verification-drift-workbench/global-governance-policy-impact-snapshot-views/${snapshot_id}" '' \
  "${snapshot_file}"
workspace_file="${EVIDENCE_DIR}/snapshot-workspaces.json"
request GET "/control/v1/verification-drift-workbench/global-governance-policy-impact-snapshot-views/${snapshot_id}/workspace-snapshots?page=0&size=100" '' \
  "${workspace_file}"
timeline_file="${EVIDENCE_DIR}/timeline.json"
request GET "/control/v1/verification-drift-workbench/global-governance-policy-impact-job-views/${job_id}/timeline?limit=100" '' \
  "${timeline_file}"

jq -e 'has("impactDocument") | not' "${snapshot_file}" >/dev/null
jq -e '.totalElements == (.items | length) and all(.items[]; .impact != null)' "${workspace_file}" >/dev/null
jq -e 'any(.items[]; .eventType == "GLOBAL_DRIFT_POLICY_IMPACT_JOB_SEALED")' "${timeline_file}" >/dev/null

jq -n --arg runId "${RUN_ID}" --arg jobId "${job_id}" --arg snapshotId "${snapshot_id}" \
  --argjson workspaceCount "$(jq '.workspaceCount' "${snapshot_file}")" \
  '{runId:$runId,status:"PASSED",jobId:$jobId,snapshotId:$snapshotId,workspaceCount:$workspaceCount,
    assertions:["READY_ALL_SUCCEEDED","SEAL_ALLOWED","SEALED","STABLE_VIEW_NO_RAW_DOCUMENT","WORKSPACE_IMPACT_STRUCTURED","SEAL_AUDIT_PRESENT"]}' \
  > "${EVIDENCE_DIR}/acceptance-summary.json"

echo "Global impact seal acceptance PASSED: ${EVIDENCE_DIR}"

