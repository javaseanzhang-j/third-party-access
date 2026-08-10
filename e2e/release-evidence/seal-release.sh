#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
project_dir="$(cd "$script_dir/../.." && pwd)"
generator="$script_dir/generate-release-evidence.mjs"
verifier="$script_dir/verify-release-evidence.mjs"
gate="$project_dir/e2e/ui-governance-workbench/verify.sh"
release_id="tpip-release-$(date -u +%Y%m%dT%H%M%SZ)-$$"
if [[ -n "${TPIP_RELEASE_EVIDENCE_DIR:-}" ]]; then
  mkdir -p "$TPIP_RELEASE_EVIDENCE_DIR"
  release_root="$TPIP_RELEASE_EVIDENCE_DIR/$release_id"
  mkdir "$release_root"
else
  release_root="$(mktemp -d /tmp/tpip-release-evidence.XXXXXX)"
fi
build_root="$release_root/build"
gate_root="$release_root/governance-gate"
gate_log="$release_root/governance-gate.log"
evidence_json="$release_root/release-evidence.json"
mkdir -p "$build_root" "$gate_root"

phase="CONFIGURATION"
maven_test_status="NOT_RUN"
ui_test_status="NOT_RUN"
ui_build_status="NOT_RUN"
gate_report=""
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

generate_evidence() {
  local exit_code="$1" finished_at
  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  TPIP_RELEASE_ROOT="$release_root" \
    TPIP_RELEASE_PROJECT_ROOT="$project_dir" \
    TPIP_RELEASE_ID="$release_id" \
    TPIP_RELEASE_STARTED_AT="$started_at" \
    TPIP_RELEASE_FINISHED_AT="$finished_at" \
    TPIP_RELEASE_MAVEN_TEST_STATUS="$maven_test_status" \
    TPIP_RELEASE_UI_TEST_STATUS="$ui_test_status" \
    TPIP_RELEASE_UI_BUILD_STATUS="$ui_build_status" \
    TPIP_RELEASE_GATE_REPORT="$gate_report" \
    TPIP_RELEASE_BROWSER_CHANNEL="${TPIP_E2E_BROWSER_CHANNEL:-chrome}" \
    TPIP_RELEASE_FAILURE_STAGE="$phase" \
    TPIP_RELEASE_EXIT_CODE="$exit_code" \
    node "$generator"
}

finish() {
  local status=$?
  trap - EXIT INT TERM
  if [[ ! -f "$evidence_json" ]]; then
    if ! generate_evidence "$status"; then
      echo "Release evidence generation failed" >&2
      status=1
    fi
  fi
  if [[ -f "$evidence_json" ]]; then
    if ! node "$verifier" "$evidence_json"; then status=1; fi
  fi
  echo "release.id=$release_id"
  echo "release.evidence.json=$evidence_json"
  echo "release.evidence.markdown=$release_root/release-evidence.md"
  echo "release.artifacts=$release_root"
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT TERM

if [[ -z "${TPIP_MYSQL_PASSWORD:-}" ]]; then
  echo "TPIP_MYSQL_PASSWORD is required" >&2
  exit 2
fi

java_home="${TPIP_JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}"
export JAVA_HOME="$java_home"
export PATH="$java_home/bin:$PATH"

phase="MAVEN_TEST_AND_PACKAGE"
(
  cd "$project_dir"
  mvn clean package
)
maven_test_status="PASSED"

phase="UI_TEST"
(
  cd "$project_dir/tpip-ui"
  CI=true npm test
)
ui_test_status="PASSED"

phase="UI_BUILD"
(
  cd "$project_dir/tpip-ui"
  CI=true npm run build
)
ui_build_status="PASSED"

phase="ARTIFACT_COLLECTION"
cp "$project_dir/tpip-control-plane-app/target/tpip-control-plane-app-0.1.0-SNAPSHOT.jar" \
  "$build_root/tpip-control-plane-app.jar"
cp "$project_dir/tpip-runtime-app/target/tpip-runtime-app-0.1.0-SNAPSHOT.jar" \
  "$build_root/tpip-runtime-app.jar"
cp "$project_dir/tpip-worker-app/target/tpip-worker-app-0.1.0-SNAPSHOT.jar" \
  "$build_root/tpip-worker-app.jar"
cp -R "$project_dir/tpip-ui/dist" "$build_root/tpip-ui-dist"
mkdir "$build_root/database-migration"
cp "$project_dir"/database/migration/*.sql "$build_root/database-migration/"

phase="GOVERNANCE_GATE"
gate_exit=0
TPIP_E2E_ARTIFACT_DIR="$gate_root" \
  TPIP_MYSQL_PASSWORD="$TPIP_MYSQL_PASSWORD" \
  TPIP_E2E_SKIP_BUILD=true \
  TPIP_E2E_BROWSER_CHANNEL="${TPIP_E2E_BROWSER_CHANNEL:-chrome}" \
  "$gate" >"$gate_log" 2>&1 || gate_exit=$?
cat "$gate_log"
gate_report="$(awk -F= '$1=="gate.report.json" {print $2}' "$gate_log" | tail -n 1)"
if [[ "$gate_exit" -ne 0 ]]; then exit "$gate_exit"; fi
if [[ -z "$gate_report" || ! -f "$gate_report" ]]; then
  echo "Governance gate did not produce gate-report.json" >&2
  exit 7
fi

phase="EVIDENCE_GENERATION"
generate_evidence 0

phase="EVIDENCE_VERIFICATION"
node "$verifier" "$evidence_json"
phase="COMPLETED"
