#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
project_dir="$(cd "$script_dir/../.." && pwd)"
ui_dir="$project_dir/tpip-ui"
fixture="$script_dir/fixture.sh"
report_generator="$script_dir/generate-report.mjs"
run_id="tpip-ui-governance-$(date -u +%Y%m%dT%H%M%SZ)-$$"
if [[ -n "${TPIP_E2E_ARTIFACT_DIR:-}" ]]; then
  mkdir -p "$TPIP_E2E_ARTIFACT_DIR"
  run_dir="$TPIP_E2E_ARTIFACT_DIR/$run_id"
  mkdir "$run_dir"
else
  run_dir="$(mktemp -d /tmp/tpip-ui-governance-gate.XXXXXX)"
fi
manifest="$run_dir/fixture.properties"
control_log="$run_dir/control-plane.log"
evidence_path="$run_dir/business-evidence.json"
playwright_output="$run_dir/playwright"
control_pid=""
workspace_id=""
seeded=false
phase="CONFIGURATION"
cleanup_status="NOT_REQUIRED"
verify_clean_status="NOT_REQUIRED"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
started_epoch_ms="$(($(date +%s) * 1000))"

finish() {
  local status=$?
  local finished_at finished_epoch_ms duration_ms gate_status failure_stage
  trap - EXIT INT TERM

  if [[ -n "$control_pid" ]] && kill -0 "$control_pid" 2>/dev/null; then
    kill "$control_pid" 2>/dev/null || true
    wait "$control_pid" 2>/dev/null || true
  fi
  if [[ "$seeded" == true ]]; then
    cleanup_status="CLEANED"
    if ! "$fixture" cleanup "$manifest"; then
      cleanup_status="FAILED"
      status=1
      phase="CLEANUP"
    fi
    verify_clean_status="VERIFIED"
    if ! "$fixture" verify-clean "$manifest"; then
      verify_clean_status="FAILED"
      status=1
      phase="VERIFY_CLEAN"
    fi
  fi

  if [[ "$status" -eq 0 ]]; then
    gate_status="PASSED"
    failure_stage=""
  else
    gate_status="FAILED"
    failure_stage="$phase"
  fi
  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  finished_epoch_ms="$(($(date +%s) * 1000))"
  duration_ms="$((finished_epoch_ms - started_epoch_ms))"

  if ! TPIP_GATE_ARTIFACT_ROOT="$run_dir" \
    TPIP_GATE_EVIDENCE_PATH="$evidence_path" \
    TPIP_GATE_RUN_ID="$run_id" \
    TPIP_GATE_STATUS="$gate_status" \
    TPIP_GATE_STARTED_AT="$started_at" \
    TPIP_GATE_FINISHED_AT="$finished_at" \
    TPIP_GATE_DURATION_MS="$duration_ms" \
    TPIP_GATE_WORKSPACE_ID="$workspace_id" \
    TPIP_GATE_BROWSER_CHANNEL="${TPIP_E2E_BROWSER_CHANNEL:-chrome}" \
    TPIP_GATE_CLEANUP_STATUS="$cleanup_status" \
    TPIP_GATE_VERIFY_CLEAN_STATUS="$verify_clean_status" \
    TPIP_GATE_FAILURE_STAGE="$failure_stage" \
    TPIP_GATE_EXIT_CODE="$status" \
    node "$report_generator"; then
    echo "Governance gate report generation failed" >&2
    status=1
    gate_status="FAILED"
  fi

  echo "gate.status=$gate_status"
  [[ -z "$workspace_id" ]] || echo "gate.workspaceId=$workspace_id"
  echo "gate.report.json=$run_dir/gate-report.json"
  echo "gate.report.markdown=$run_dir/gate-report.md"
  echo "gate.artifacts=$run_dir"
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT TERM

if [[ -z "${TPIP_MYSQL_PASSWORD:-}" ]]; then
  echo "TPIP_MYSQL_PASSWORD is required" >&2
  exit 2
fi

phase="PORT_PRECHECK"
if lsof -nP -iTCP:18082 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port 18082 is already occupied; governance gate will not reuse an unknown Control Plane" >&2
  exit 3
fi
if lsof -nP -iTCP:18100 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port 18100 is already occupied; governance gate requires an isolated UI server" >&2
  exit 3
fi

java_home="${TPIP_JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}"
jar="$project_dir/tpip-control-plane-app/target/tpip-control-plane-app-0.1.0-SNAPSHOT.jar"
phase="BUILD"
if [[ "${TPIP_E2E_SKIP_BUILD:-false}" != true ]]; then
  (
    cd "$project_dir"
    JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
      mvn -pl tpip-control-plane-app -am package -DskipTests
  )
fi
if [[ ! -f "$jar" ]]; then
  echo "Control Plane jar does not exist after build: $jar" >&2
  exit 5
fi

phase="FIXTURE_SEED"
"$fixture" seed "$manifest"
seeded=true
cleanup_status="NOT_RUN"
verify_clean_status="NOT_RUN"
workspace_id="$(awk -F= '$1=="workspaceId" {print $2}' "$manifest")"
if [[ ! "$workspace_id" =~ ^[1-9][0-9]*$ ]]; then
  echo "Fixture did not produce a valid workspaceId" >&2
  exit 4
fi

phase="CONTROL_PLANE_START"
TPIP_CONTROL_PORT=18082 "$java_home/bin/java" -jar "$jar" >"$control_log" 2>&1 &
control_pid=$!
ready=false
for _ in $(seq 1 60); do
  if curl -fsS --max-time 1 http://127.0.0.1:18082/actuator/health >/dev/null 2>&1; then
    ready=true
    break
  fi
  if ! kill -0 "$control_pid" 2>/dev/null; then
    echo "Control Plane exited before becoming healthy; see $control_log" >&2
    exit 6
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo "Control Plane did not become healthy within 60 seconds; see $control_log" >&2
  exit 6
fi

phase="PLAYWRIGHT"
(
  cd "$ui_dir"
  TPIP_E2E_UI_WORKSPACE_ID="$workspace_id" \
    TPIP_E2E_GATE_RUN_ID="$run_id" \
    TPIP_E2E_GATE_RESULT_PATH="$evidence_path" \
    TPIP_E2E_OUTPUT_DIR="$playwright_output" \
    TPIP_E2E_BROWSER_CHANNEL="${TPIP_E2E_BROWSER_CHANNEL:-chrome}" \
    npm run test:e2e:governance
)
phase="COMPLETED"
