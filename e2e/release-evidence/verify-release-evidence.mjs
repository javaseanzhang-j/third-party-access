import { createHash } from 'node:crypto'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

function files(root, current = root) {
  return readdirSync(current, { withFileTypes: true })
    .sort((left, right) => left.name.localeCompare(right.name))
    .flatMap(entry => entry.isDirectory() ? files(root, join(current, entry.name)) : [join(current, entry.name)])
}

function fail(message) {
  console.error(`release.evidence.verify=FAILED: ${message}`)
  process.exit(1)
}

const reportPath = resolve(process.argv[2] ?? '')
if (!reportPath || !existsSync(reportPath)) fail('release-evidence.json does not exist')
const root = dirname(reportPath)
const report = JSON.parse(readFileSync(reportPath, 'utf8'))
if (report.schemaVersion !== '1.0' || report.evidenceType !== 'TPIP_RELEASE_EVIDENCE') fail('unsupported evidence contract')
if (!['SEALED', 'REJECTED', 'INCOMPLETE'].includes(report.status)) fail('invalid release status')

const manifest = { ...report }
delete manifest.manifestChecksum
if (report.manifestChecksum?.algorithm !== 'SHA-256'
  || sha256(JSON.stringify(manifest)) !== report.manifestChecksum?.value) fail('manifest checksum mismatch')

for (const artifact of report.artifacts ?? []) {
  if (!artifact.present) continue
  const path = resolve(root, artifact.path)
  if (path !== root && !path.startsWith(`${root}${sep}`)) fail(`artifact escapes release root: ${artifact.id}`)
  if (!existsSync(path)) fail(`artifact missing: ${artifact.id}`)
  if (artifact.kind === 'FILE') {
    if (sha256(readFileSync(path)) !== artifact.checksum?.value) fail(`artifact checksum mismatch: ${artifact.id}`)
  } else if (artifact.kind === 'DIRECTORY') {
    const actualFiles = files(path).map(file => ({
      path: relative(path, file), sizeBytes: statSync(file).size, checksum: sha256(readFileSync(file))
    }))
    if (sha256(JSON.stringify(actualFiles)) !== artifact.checksum?.value) fail(`directory checksum mismatch: ${artifact.id}`)
  } else fail(`invalid artifact kind: ${artifact.id}`)
}

if (report.status === 'SEALED') {
  if (!report.artifacts?.every(artifact => artifact.present)) fail('sealed evidence contains missing artifacts')
  if (report.verification?.mavenTests !== 'PASSED' || report.verification?.uiTests !== 'PASSED'
    || report.verification?.uiBuild !== 'PASSED') fail('sealed evidence contains incomplete build verification')
  const gateArtifact = report.artifacts.find(artifact => artifact.id === 'GOVERNANCE_GATE_REPORT')
  const businessArtifact = report.artifacts.find(artifact => artifact.id === 'GOVERNANCE_BUSINESS_EVIDENCE')
  const gate = JSON.parse(readFileSync(resolve(root, gateArtifact.path), 'utf8'))
  const businessEvidence = JSON.parse(readFileSync(resolve(root, businessArtifact.path), 'utf8'))
  if (gate.status !== 'PASSED' || gate.cleanup?.cleanupStatus !== 'CLEANED'
    || gate.cleanup?.verifyCleanStatus !== 'VERIFIED') fail('governance gate is not cleanly passed')
  if (JSON.stringify(businessEvidence) !== JSON.stringify(gate.governanceEvidence)) fail('business evidence differs from gate report')
  if (report.verification?.governanceGate?.runId !== gate.runId
    || report.verification?.governanceGate?.workspaceId !== gate.workspaceId) fail('release-to-gate identity mismatch')
  const expectedAuditChecksum = sha256(JSON.stringify(gate.governanceEvidence?.auditChains))
  if (expectedAuditChecksum !== gate.governanceEvidence?.auditChainChecksum?.value) fail('audit chain checksum mismatch')
  if (!Object.values(gate.governanceEvidence?.assertions ?? {}).every(value => value === true)) fail('governance assertion failed')
}

console.log(`release.evidence.verify=PASSED`)
console.log(`release.evidence.status=${report.status}`)
console.log(`release.evidence.releaseId=${report.releaseId}`)
