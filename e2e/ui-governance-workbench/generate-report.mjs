import { existsSync, readFileSync, writeFileSync } from 'node:fs'

function required(name) {
  const value = process.env[name]
  if (!value) throw new Error(`${name} is required`)
  return value
}

function optionalJson(path) {
  return path && existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : null
}

function nullableNumber(value) {
  if (!value) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const artifactRoot = required('TPIP_GATE_ARTIFACT_ROOT')
const evidencePath = process.env.TPIP_GATE_EVIDENCE_PATH ?? `${artifactRoot}/business-evidence.json`
const jsonPath = `${artifactRoot}/gate-report.json`
const markdownPath = `${artifactRoot}/gate-report.md`
const evidence = optionalJson(evidencePath)
const status = required('TPIP_GATE_STATUS')
const finishedAt = required('TPIP_GATE_FINISHED_AT')
const report = {
  schemaVersion: '1.0',
  reportType: 'TPIP_UI_GOVERNANCE_GATE',
  runId: required('TPIP_GATE_RUN_ID'),
  status,
  startedAt: required('TPIP_GATE_STARTED_AT'),
  finishedAt,
  durationMs: nullableNumber(process.env.TPIP_GATE_DURATION_MS),
  workspaceId: nullableNumber(process.env.TPIP_GATE_WORKSPACE_ID),
  environment: {
    browserChannel: process.env.TPIP_GATE_BROWSER_CHANNEL ?? 'chrome',
    controlPort: 18082,
    uiPort: 18100
  },
  governanceEvidence: evidence,
  cleanup: {
    attempted: process.env.TPIP_GATE_CLEANUP_STATUS !== 'NOT_REQUIRED',
    cleanupStatus: process.env.TPIP_GATE_CLEANUP_STATUS ?? 'NOT_RUN',
    verifyCleanStatus: process.env.TPIP_GATE_VERIFY_CLEAN_STATUS ?? 'NOT_RUN'
  },
  artifacts: {
    root: artifactRoot,
    controlLog: `${artifactRoot}/control-plane.log`,
    playwrightOutput: `${artifactRoot}/playwright`,
    businessEvidence: existsSync(evidencePath) ? evidencePath : null,
    jsonReport: jsonPath,
    markdownReport: markdownPath
  },
  failure: status === 'PASSED' ? null : {
    stage: process.env.TPIP_GATE_FAILURE_STAGE ?? 'UNKNOWN',
    exitCode: nullableNumber(process.env.TPIP_GATE_EXIT_CODE),
    traceDirectory: `${artifactRoot}/playwright`
  }
}

writeFileSync(jsonPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8')

const assets = evidence?.assets
const checksum = evidence?.auditChainChecksum
const assertions = evidence?.assertions ?? {}
const assertionLines = Object.entries(assertions)
  .map(([name, passed]) => `- ${passed ? 'PASS' : 'FAIL'} — ${name}`)
const lines = [
  '# TPIP UI Governance Gate Report',
  '',
  `- Status: **${status}**`,
  `- Run ID: \`${report.runId}\``,
  `- Started: ${report.startedAt}`,
  `- Finished: ${finishedAt}`,
  `- Duration: ${report.durationMs ?? 'unknown'} ms`,
  `- Workspace ID: ${report.workspaceId ?? 'not created'}`,
  '',
  '## Governance evidence',
  '',
  `- Original batch: ${assets?.originalBatchId ?? 'not produced'}`,
  `- Replacement batch: ${assets?.replacementBatchId ?? 'not produced'}`,
  `- Outbox: ${assets?.outboxId ?? 'not produced'}`,
  `- Audit chain checksum: ${checksum ? `\`${checksum.algorithm}:${checksum.value}\`` : 'not produced'}`,
  '',
  '## Assertions',
  '',
  ...(assertionLines.length ? assertionLines : ['- No complete business evidence was produced.']),
  '',
  '## Cleanup',
  '',
  `- Cleanup: ${report.cleanup.cleanupStatus}`,
  `- Verify clean: ${report.cleanup.verifyCleanStatus}`,
  '',
  '## Artifacts',
  '',
  `- JSON report: \`${jsonPath}\``,
  `- Business evidence: ${report.artifacts.businessEvidence ? `\`${report.artifacts.businessEvidence}\`` : 'not produced'}`,
  `- Playwright output / traces: \`${report.artifacts.playwrightOutput}\``,
  `- Control Plane log: \`${report.artifacts.controlLog}\``
]
if (report.failure) {
  lines.push('', '## Failure', '', `- Stage: ${report.failure.stage}`,
    `- Exit code: ${report.failure.exitCode ?? 'unknown'}`,
    `- Trace directory: \`${report.failure.traceDirectory}\``)
}
writeFileSync(markdownPath, `${lines.join('\n')}\n`, 'utf8')
