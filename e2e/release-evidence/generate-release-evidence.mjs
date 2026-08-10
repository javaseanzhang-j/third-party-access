import { createHash } from 'node:crypto'
import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs'
import { basename, join, relative, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'

function required(name) {
  const value = process.env[name]
  if (!value) throw new Error(`${name} is required`)
  return value
}

function sha256Buffer(value) {
  return createHash('sha256').update(value).digest('hex')
}

function sha256File(path) {
  return sha256Buffer(readFileSync(path))
}

function directoryFiles(root, current = root) {
  return readdirSync(current, { withFileTypes: true })
    .sort((left, right) => left.name.localeCompare(right.name))
    .flatMap(entry => {
      const path = join(current, entry.name)
      return entry.isDirectory() ? directoryFiles(root, path) : [path]
    })
}

function describeArtifact(id, kind, path, root) {
  if (!existsSync(path)) return { id, kind, path: relative(root, path), present: false }
  if (kind === 'FILE') {
    return {
      id, kind, path: relative(root, path), present: true,
      sizeBytes: statSync(path).size, checksum: { algorithm: 'SHA-256', value: sha256File(path) }
    }
  }
  const files = directoryFiles(path).map(file => ({
    path: relative(path, file), sizeBytes: statSync(file).size, checksum: sha256File(file)
  }))
  return {
    id, kind, path: relative(root, path), present: true, fileCount: files.length,
    checksum: { algorithm: 'SHA-256', value: sha256Buffer(JSON.stringify(files)) }, files
  }
}

function command(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, encoding: 'utf8' })
  return result.status === 0 ? result.stdout.trim() : null
}

function firstLine(commandName, args, cwd) {
  const result = spawnSync(commandName, args, { cwd, encoding: 'utf8' })
  const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim()
  return output.split(/\r?\n/)[0] || 'UNAVAILABLE'
}

function parseProjectVersion(projectRoot) {
  const pom = readFileSync(join(projectRoot, 'pom.xml'), 'utf8')
  return pom.match(/<artifactId>tpip-parent<\/artifactId>\s*<version>([^<]+)<\/version>/)?.[1] ?? 'UNAVAILABLE'
}

function migrationVersion(migrationDir) {
  if (!existsSync(migrationDir)) return null
  const versions = readdirSync(migrationDir)
    .map(name => name.match(/^V(\d+)__/))
    .filter(Boolean)
    .map(match => Number(match[1]))
  return versions.length ? `V${Math.max(...versions)}` : null
}

function auditChecksumValid(gate) {
  const evidence = gate?.governanceEvidence
  if (!evidence?.auditChains || !evidence?.auditChainChecksum?.value) return false
  return sha256Buffer(JSON.stringify(evidence.auditChains)) === evidence.auditChainChecksum.value
}

const releaseRoot = resolve(required('TPIP_RELEASE_ROOT'))
const projectRoot = resolve(required('TPIP_RELEASE_PROJECT_ROOT'))
const buildRoot = join(releaseRoot, 'build')
const gateReportPath = process.env.TPIP_RELEASE_GATE_REPORT || ''
const gate = gateReportPath && existsSync(gateReportPath)
  ? JSON.parse(readFileSync(gateReportPath, 'utf8')) : null
const businessEvidencePath = gate?.artifacts?.businessEvidence ?? join(releaseRoot, 'governance-gate', 'business-evidence.json')
const businessEvidence = existsSync(businessEvidencePath)
  ? JSON.parse(readFileSync(businessEvidencePath, 'utf8')) : null
const uiPackage = JSON.parse(readFileSync(join(projectRoot, 'tpip-ui/package.json'), 'utf8'))
const gitCommit = command('git', ['rev-parse', 'HEAD'], projectRoot)
const gitStatus = gitCommit ? command('git', ['status', '--porcelain'], projectRoot) : null

const artifacts = [
  describeArtifact('CONTROL_PLANE_JAR', 'FILE', join(buildRoot, 'tpip-control-plane-app.jar'), releaseRoot),
  describeArtifact('RUNTIME_APP_JAR', 'FILE', join(buildRoot, 'tpip-runtime-app.jar'), releaseRoot),
  describeArtifact('WORKER_APP_JAR', 'FILE', join(buildRoot, 'tpip-worker-app.jar'), releaseRoot),
  describeArtifact('UI_DIST', 'DIRECTORY', join(buildRoot, 'tpip-ui-dist'), releaseRoot),
  describeArtifact('DATABASE_MIGRATIONS', 'DIRECTORY', join(buildRoot, 'database-migration'), releaseRoot),
  describeArtifact('GOVERNANCE_GATE_REPORT', 'FILE', gateReportPath || join(releaseRoot, 'governance-gate/gate-report.json'), releaseRoot),
  describeArtifact('GOVERNANCE_BUSINESS_EVIDENCE', 'FILE', businessEvidencePath, releaseRoot)
]

const testsPassed = ['TPIP_RELEASE_MAVEN_TEST_STATUS', 'TPIP_RELEASE_UI_TEST_STATUS', 'TPIP_RELEASE_UI_BUILD_STATUS']
  .every(name => process.env[name] === 'PASSED')
const artifactsComplete = artifacts.every(artifact => artifact.present)
const gatePassed = gate?.status === 'PASSED'
  && gate?.cleanup?.cleanupStatus === 'CLEANED'
  && gate?.cleanup?.verifyCleanStatus === 'VERIFIED'
  && Object.values(gate?.governanceEvidence?.assertions ?? {}).every(value => value === true)
  && JSON.stringify(businessEvidence) === JSON.stringify(gate?.governanceEvidence)
  && auditChecksumValid(gate)
const status = testsPassed && artifactsComplete && gatePassed
  ? 'SEALED' : gate && gate.status !== 'PASSED' ? 'REJECTED' : 'INCOMPLETE'

const report = {
  schemaVersion: '1.0',
  evidenceType: 'TPIP_RELEASE_EVIDENCE',
  releaseId: required('TPIP_RELEASE_ID'),
  status,
  signatureStatus: 'UNSIGNED',
  startedAt: required('TPIP_RELEASE_STARTED_AT'),
  finishedAt: required('TPIP_RELEASE_FINISHED_AT'),
  sourceIdentity: gitCommit ? {
    status: 'AVAILABLE', commit: gitCommit, dirty: Boolean(gitStatus), dirtyEntryCount: gitStatus ? gitStatus.split(/\r?\n/).length : 0
  } : { status: 'UNAVAILABLE', commit: null, dirty: null, dirtyEntryCount: null },
  versions: {
    platform: parseProjectVersion(projectRoot),
    ui: uiPackage.version,
    databaseMigration: migrationVersion(join(buildRoot, 'database-migration'))
  },
  buildEnvironment: {
    java: firstLine(process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin/java') : 'java', ['-version'], projectRoot),
    maven: firstLine('mvn', ['-version'], projectRoot),
    node: process.version,
    browserChannel: process.env.TPIP_RELEASE_BROWSER_CHANNEL ?? 'chrome'
  },
  verification: {
    mavenTests: process.env.TPIP_RELEASE_MAVEN_TEST_STATUS ?? 'NOT_RUN',
    uiTests: process.env.TPIP_RELEASE_UI_TEST_STATUS ?? 'NOT_RUN',
    uiBuild: process.env.TPIP_RELEASE_UI_BUILD_STATUS ?? 'NOT_RUN',
    governanceGate: gate ? {
      runId: gate.runId, status: gate.status, workspaceId: gate.workspaceId,
      auditChainChecksum: gate.governanceEvidence?.auditChainChecksum ?? null,
      cleanup: gate.cleanup
    } : null
  },
  artifacts,
  associations: { pcsAssets: [], eaAssets: [], standards: ['EESIS'] },
  failure: status === 'SEALED' ? null : {
    stage: process.env.TPIP_RELEASE_FAILURE_STAGE ?? 'EVIDENCE_GENERATION',
    exitCode: Number(process.env.TPIP_RELEASE_EXIT_CODE ?? 1)
  }
}
report.manifestChecksum = { algorithm: 'SHA-256', value: sha256Buffer(JSON.stringify(report)) }

const jsonPath = join(releaseRoot, 'release-evidence.json')
const markdownPath = join(releaseRoot, 'release-evidence.md')
const checksumsPath = join(releaseRoot, 'artifact-checksums.sha256')
writeFileSync(jsonPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8')
writeFileSync(checksumsPath, `${artifacts.filter(item => item.present)
  .map(item => `${item.checksum.value}  ${item.path}`).join('\n')}\n`, 'utf8')

const artifactLines = artifacts.map(artifact =>
  `- ${artifact.present ? 'PRESENT' : 'MISSING'} — ${artifact.id}${artifact.present ? ` — \`${artifact.checksum.value}\`` : ''}`)
const lines = [
  '# TPIP Release Evidence', '',
  `- Status: **${status}**`,
  `- Release ID: \`${report.releaseId}\``,
  `- Signature: ${report.signatureStatus}`,
  `- Source: ${report.sourceIdentity.status}${gitCommit ? ` — \`${gitCommit}\`${report.sourceIdentity.dirty ? ' (dirty)' : ''}` : ''}`,
  `- Platform/UI/Database: ${report.versions.platform} / ${report.versions.ui} / ${report.versions.databaseMigration ?? 'UNAVAILABLE'}`,
  '', '## Verification', '',
  `- Maven tests: ${report.verification.mavenTests}`,
  `- UI tests: ${report.verification.uiTests}`,
  `- UI build: ${report.verification.uiBuild}`,
  `- Governance gate: ${report.verification.governanceGate?.status ?? 'NOT_RUN'}`,
  `- Governance cleanup: ${report.verification.governanceGate?.cleanup?.cleanupStatus ?? 'NOT_RUN'} / ${report.verification.governanceGate?.cleanup?.verifyCleanStatus ?? 'NOT_RUN'}`,
  `- Audit chain checksum: ${report.verification.governanceGate?.auditChainChecksum?.value ? `\`${report.verification.governanceGate.auditChainChecksum.value}\`` : 'UNAVAILABLE'}`,
  '', '## Artifacts', '', ...artifactLines,
  '', '## Evidence identity', '',
  `- Manifest checksum: \`${report.manifestChecksum.value}\``,
  `- JSON: \`${jsonPath}\``,
  `- Checksums: \`${checksumsPath}\``
]
if (report.failure) lines.push('', '## Failure', '', `- Stage: ${report.failure.stage}`, `- Exit code: ${report.failure.exitCode}`)
writeFileSync(markdownPath, `${lines.join('\n')}\n`, 'utf8')
console.log(`release.evidence.status=${status}`)
console.log(`release.evidence.json=${jsonPath}`)
console.log(`release.evidence.markdown=${markdownPath}`)
