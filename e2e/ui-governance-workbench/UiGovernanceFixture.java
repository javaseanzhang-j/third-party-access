import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Isolated, manifest-driven fixture for governance UI browser acceptance. */
public final class UiGovernanceFixture {
    private static final String ACTOR = "ui-v016-fixture";
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);
    private static final DateTimeFormatter RUN_ID = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    private UiGovernanceFixture() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !(args[0].equals("seed") || args[0].equals("cleanup")
                || args[0].equals("verify-clean"))) {
            throw new IllegalArgumentException(
                    "Usage: UiGovernanceFixture <seed|cleanup|verify-clean> <manifest.properties>");
        }
        String password = requiredEnvironment("TPIP_MYSQL_PASSWORD");
        String url = environment("TPIP_MYSQL_URL",
                "jdbc:mysql://127.0.0.1:3306/tpip_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        String user = environment("TPIP_MYSQL_USER", "root");
        Path manifest = Path.of(args[1]).toAbsolutePath().normalize();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            if (args[0].equals("seed")) seed(connection, manifest);
            else if (args[0].equals("cleanup")) {
                Properties ids = load(manifest);
                cleanup(connection, ids);
                verifyClean(connection, ids);
                connection.commit();
                System.out.println("fixture.cleanup=complete");
            } else {
                verifyClean(connection, load(manifest));
                connection.rollback();
                System.out.println("fixture.verifyClean=complete");
            }
        }
    }

    private static void seed(Connection connection, Path manifestPath) throws Exception {
        if (Files.exists(manifestPath)) throw new IllegalStateException("Manifest already exists: " + manifestPath);
        Properties ids = new Properties();
        String runId = RUN_ID.format(Instant.now());
        Instant now = Instant.now();
        Instant old = now.minusSeconds(5L * 24 * 3600);
        long fixtureSuiteVersionId = requiredLong(connection,
                "SELECT id FROM tpip_fixture_suite_version ORDER BY id DESC LIMIT 1");
        try {
            long workspaceId = insert(connection,
                    "INSERT INTO tpip_workspace(workspace_code,workspace_name,environment_code,lifecycle_status," +
                            "risk_level,owner_code,created_by,updated_by) VALUES(?,?,?,'DRAFT','MEDIUM',?,?,?)",
                    "ui-v016-" + runId, "UI v0.16 governance acceptance fixture", "local",
                    "ui-v016-owner", ACTOR, ACTOR);
            put(ids, "workspaceId", workspaceId);

            long policyId = insert(connection,
                    "INSERT INTO tpip_drift_governance_policy(policy_code,policy_name,policy_scope,workspace_id," +
                            "policy_status,created_by,updated_by) VALUES(?,?,'WORKSPACE',?,'DRAFT',?,?)",
                    "ui-v016-policy-" + runId, "UI v0.16 deterministic policy", workspaceId, ACTOR, ACTOR);
            put(ids, "policyId", policyId);
            long policyVersionId = insert(connection,
                    "INSERT INTO tpip_drift_governance_policy_version(policy_id,version_no," +
                            "overdue_after_seconds,aggregation_window_seconds,reminder_interval_seconds," +
                            "maximum_reminders,owner_code,suppressed_drift_kinds,suppressed_check_codes," +
                            "content_checksum,lifecycle_status,published_at,created_by) " +
                            "VALUES(?,1,3600,86400,86400,3,?,'[]','[]',?,'PUBLISHED',?,?)",
                    policyId, "ui-v016-owner", HASH_C, timestamp(old), ACTOR);
            put(ids, "policyVersionId", policyVersionId);
            update(connection, "UPDATE tpip_drift_governance_policy SET policy_status='ACTIVE'," +
                            "current_version_id=?,row_version=1,updated_by=? WHERE id=?",
                    policyVersionId, ACTOR, policyId);

            long baselineRunId = verificationRun(connection, workspaceId, 1, old.minusSeconds(60));
            long driftRunId = verificationRun(connection, workspaceId, 2, old);
            long commandRunId = verificationRun(connection, workspaceId, 3, old.plusSeconds(60));
            put(ids, "baselineRunId", baselineRunId);
            put(ids, "driftRunId", driftRunId);
            put(ids, "commandRunId", commandRunId);
            long baselineId = insert(connection,
                    "INSERT INTO tpip_verification_baseline(workspace_id,fixture_suite_version_id," +
                            "source_verification_run_id,baseline_checksum,snapshot_document,created_by,created_at) " +
                            "VALUES(?,?,?,?,?,?,?)", workspaceId, fixtureSuiteVersionId, baselineRunId, HASH_A,
                    "{\"checks\":[{\"checkCode\":\"UI_V016_FIXTURE\",\"status\":\"PASSED\"}]}", ACTOR,
                    timestamp(old.minusSeconds(30)));
            put(ids, "baselineId", baselineId);

            String report = "{\"drifts\":[{\"checkCode\":\"UI_V016_FIXTURE\",\"kind\":\"EVIDENCE_CHANGED\"}]}";
            long driftReportId = insert(connection,
                    "INSERT INTO tpip_verification_drift_report(baseline_id,verification_run_id,drift_status," +
                            "compared_check_count,drift_count,report_document,created_by,created_at) " +
                            "VALUES(?,?,'DRIFTED',1,1,?,?,?)",
                    baselineId, driftRunId, report, ACTOR, timestamp(old));
            put(ids, "driftReportId", driftReportId);
            update(connection, "INSERT INTO tpip_verification_drift_review(drift_report_id,review_status," +
                    "row_version,created_at) VALUES(?,'OPEN',0,?)", driftReportId, timestamp(old));
            update(connection, "INSERT INTO tpip_verification_drift_item(drift_report_id,item_no,check_code," +
                            "drift_kind,created_at) VALUES(?,1,'UI_V016_FIXTURE','EVIDENCE_CHANGED',?)",
                    driftReportId, timestamp(old));
            long commandDriftReportId = insert(connection,
                    "INSERT INTO tpip_verification_drift_report(baseline_id,verification_run_id,drift_status," +
                            "compared_check_count,drift_count,report_document,created_by,created_at) " +
                            "VALUES(?,?,'DRIFTED',1,1,?,?,?)",
                    baselineId, commandRunId,
                    "{\"drifts\":[{\"checkCode\":\"UI_V017_COMMAND\",\"kind\":\"RESULT_CHANGED\"}]}",
                    ACTOR, timestamp(old.plusSeconds(60)));
            put(ids, "commandDriftReportId", commandDriftReportId);
            update(connection, "INSERT INTO tpip_verification_drift_review(drift_report_id,review_status," +
                    "row_version,created_at) VALUES(?,'OPEN',0,?)", commandDriftReportId, timestamp(old.plusSeconds(60)));
            update(connection, "INSERT INTO tpip_verification_drift_item(drift_report_id,item_no,check_code," +
                            "drift_kind,created_at) VALUES(?,1,'UI_V017_COMMAND','RESULT_CHANGED',?)",
                    commandDriftReportId, timestamp(old.plusSeconds(60)));

            String policySnapshot = "{\"source\":\"WORKSPACE_POLICY\",\"overdueAfterSeconds\":3600," +
                    "\"aggregationWindowSeconds\":86400,\"reminderIntervalSeconds\":86400," +
                    "\"maximumReminders\":3,\"ownerCode\":\"ui-v016-owner\"}";
            String evaluation = "{\"reportId\":" + driftReportId + ",\"reminderCandidate\":true," +
                    "\"drifts\":[{\"checkCode\":\"UI_V016_FIXTURE\",\"kind\":\"EVIDENCE_CHANGED\"}]}";
            long executionId = insert(connection,
                    "INSERT INTO tpip_drift_governance_execution(drift_report_id,workspace_id,policy_source," +
                            "policy_id,policy_version_id,aggregation_key,owner_code,execution_status," +
                            "maximum_reminders,reminder_interval_seconds,reminder_count,next_reminder_at," +
                            "last_reminder_at,policy_snapshot_document,evaluation_document,evaluation_checksum," +
                            "row_version,materialized_by,materialized_at) " +
                            "VALUES(?,?,'WORKSPACE_POLICY',?,?,?,?,'READY',3,86400,1,?,?,?,?,?,1,?,?)",
                    driftReportId, workspaceId, policyId, policyVersionId, HASH_A, "ui-v016-owner",
                    timestamp(now.minusSeconds(3600)), timestamp(now.minusSeconds(90000)), policySnapshot,
                    evaluation, HASH_B, ACTOR, timestamp(old));
            put(ids, "executionId", executionId);

            long outboxId = insert(connection,
                    "INSERT INTO tpip_notification_outbox(event_type,aggregate_type,aggregate_id,environment_code," +
                            "payload,delivery_status,routing_status,routing_attempted_at,attempt_count,available_at," +
                            "delivered_at,created_at) VALUES('DRIFT_GOVERNANCE_REMINDER','DRIFT_GOVERNANCE_REMINDER_BATCH'," +
                            "?,'local',?,'DELIVERED','ROUTED',?,1,?,?,?)",
                    "ui-v016-outbox-" + runId,
                    "{\"workspaceId\":" + workspaceId + ",\"fixture\":\"ui-v016\"}",
                    timestamp(now.minusSeconds(50)), timestamp(now.minusSeconds(60)),
                    timestamp(now.minusSeconds(40)), timestamp(now.minusSeconds(60)));
            put(ids, "outboxId", outboxId);
            long deliveryId = insert(connection,
                    "INSERT INTO tpip_notification_delivery(outbox_id,channel_code,delivery_status,attempt_count," +
                            "available_at,delivered_at) VALUES(?,'ui-v016-simulated','DELIVERED',1,?,?)",
                    outboxId, timestamp(now.minusSeconds(60)), timestamp(now.minusSeconds(40)));
            put(ids, "deliveryId", deliveryId);

            long originalBatchId = batch(connection, runId + "-original", workspaceId, "CANCELLED", HASH_A,
                    null, null, null, old, now.minusSeconds(120));
            put(ids, "originalBatchId", originalBatchId);
            member(connection, originalBatchId, executionId, 1, HASH_B, now.minusSeconds(120));
            long replacementBatchId = batch(connection, runId + "-replacement", workspaceId, "DISPATCHED", HASH_B,
                    outboxId, originalBatchId, null, old.plusSeconds(60), null);
            put(ids, "replacementBatchId", replacementBatchId);
            member(connection, replacementBatchId, executionId, 1, HASH_B, now.minusSeconds(40));
            update(connection, "UPDATE tpip_drift_governance_reminder_batch SET replaced_by_batch_id=? WHERE id=?",
                    replacementBatchId, originalBatchId);
            update(connection, "UPDATE tpip_drift_governance_execution SET last_outbox_id=? WHERE id=?",
                    outboxId, executionId);
            long draftBatchId = batch(connection, runId + "-draft", workspaceId, "DRAFT", HASH_C,
                    null, null, null, now.minusSeconds(20), null);
            put(ids, "draftBatchId", draftBatchId);
            member(connection, draftBatchId, executionId, 2, HASH_B, null);

            ids.setProperty("runId", runId);
            ids.setProperty("workspaceCode", "ui-v016-" + runId);
            Files.createDirectories(manifestPath.getParent());
            try (OutputStream output = Files.newOutputStream(manifestPath)) {
                ids.store(output, "TPIP UI v0.16 isolated fixture - generated, safe to delete after cleanup");
            }
            connection.commit();
            System.out.println("fixture.seed=complete");
            System.out.println("fixture.workspaceId=" + workspaceId);
            System.out.println("fixture.manifest=" + manifestPath);
        } catch (Exception failure) {
            connection.rollback();
            throw failure;
        }
    }

    private static long verificationRun(Connection connection, long workspaceId, long runNo, Instant at)
            throws SQLException {
        return insert(connection, "INSERT INTO tpip_verification_run(workspace_id,run_no,run_type,status," +
                        "total_count,passed_count,failed_count,result_summary,started_at,finished_at,created_by) " +
                        "VALUES(?,?,'FULL','PASSED',1,1,0,?,?,?,?)",
                workspaceId, runNo, "{\"fixture\":\"ui-v016\"}", timestamp(at),
                timestamp(at.plusSeconds(10)), ACTOR);
    }

    private static long batch(Connection connection, String suffix, long workspaceId, String status,
            String checksum, Long outboxId, Long replacesId, Long replacedById, Instant createdAt,
            Instant cancelledAt) throws SQLException {
        String batchCode = "ui-v016-batch-" + suffix;
        String payload = "{\"fixture\":\"ui-v016\",\"batchCode\":\"" + batchCode + "\"}";
        boolean cancelled = status.equals("CANCELLED");
        boolean dispatched = status.equals("DISPATCHED");
        return insert(connection,
                "INSERT INTO tpip_drift_governance_reminder_batch(batch_code,workspace_id,aggregation_key," +
                        "environment_code,owner_code,creation_source,batch_status,member_count,payload_document," +
                        "content_checksum,row_version,outbox_id,replaces_batch_id,replaced_by_batch_id,created_by," +
                        "created_at,approved_by,approved_at,cancel_reason,cancelled_by,cancelled_at,dispatched_by," +
                        "dispatched_at) VALUES(?,?,?,'local','ui-v016-owner','MANUAL',?,1,?,?,1,?,?,?,?,?,?,?,?,?,?,?,?)",
                batchCode, workspaceId, HASH_A, status, payload, checksum, outboxId, replacesId, replacedById,
                ACTOR, timestamp(createdAt), dispatched ? ACTOR : null,
                dispatched ? timestamp(createdAt.plusSeconds(10)) : null,
                cancelled ? "v0.16 replacement lineage acceptance" : null,
                cancelled ? ACTOR : null, timestamp(cancelledAt), dispatched ? ACTOR : null,
                dispatched ? timestamp(createdAt.plusSeconds(20)) : null);
    }

    private static void member(Connection connection, long batchId, long executionId, int reminderNo,
            String checksum, Instant releasedAt) throws SQLException {
        update(connection, "INSERT INTO tpip_drift_governance_reminder_batch_member(batch_id,execution_id," +
                        "reminder_no,evaluation_checksum,reservation_released_at) VALUES(?,?,?,?,?)",
                batchId, executionId, reminderNo, checksum, timestamp(releasedAt));
    }

    private static void cleanup(Connection connection, Properties ids) throws SQLException {
        long policyId = id(ids, "policyId");
        long workspaceId = id(ids, "workspaceId");
        requireIsolatedWorkspace(connection, workspaceId, ids.getProperty("workspaceCode"));
        List<Long> outboxIds = queryLongs(connection, "SELECT outbox_id FROM tpip_drift_governance_reminder_batch " +
                "WHERE workspace_id=? AND outbox_id IS NOT NULL", workspaceId);
        List<String> batchCodes = queryStrings(connection, "SELECT batch_code FROM tpip_drift_governance_reminder_batch " +
                "WHERE workspace_id=?", workspaceId);
        List<String> reportCodes = queryStrings(connection, "SELECT CAST(r.id AS CHAR) FROM tpip_verification_drift_report r " +
                "JOIN tpip_verification_run v ON v.id=r.verification_run_id WHERE v.workspace_id=?", workspaceId);
        deleteAudit(connection, "DRIFT_GOVERNANCE_REMINDER_BATCH", batchCodes);
        deleteAudit(connection, "DRIFT_GOVERNANCE_EXECUTION", reportCodes);
        if (!outboxIds.isEmpty()) update(connection, "DELETE FROM tpip_notification_delivery WHERE outbox_id IN (" +
                placeholders(outboxIds.size()) + ")", outboxIds.toArray());
        update(connection, "UPDATE tpip_drift_governance_execution SET last_outbox_id=NULL WHERE workspace_id=?", workspaceId);
        update(connection, "DELETE m FROM tpip_drift_governance_reminder_batch_member m JOIN " +
                "tpip_drift_governance_reminder_batch b ON b.id=m.batch_id WHERE b.workspace_id=?", workspaceId);
        update(connection, "UPDATE tpip_drift_governance_reminder_batch SET replaces_batch_id=NULL," +
                "replaced_by_batch_id=NULL WHERE workspace_id=?", workspaceId);
        update(connection, "DELETE FROM tpip_drift_governance_reminder_batch WHERE workspace_id=?", workspaceId);
        update(connection, "DELETE FROM tpip_drift_governance_execution WHERE workspace_id=?", workspaceId);
        if (!outboxIds.isEmpty()) update(connection, "DELETE FROM tpip_notification_outbox WHERE id IN (" +
                placeholders(outboxIds.size()) + ")", outboxIds.toArray());
        update(connection, "DELETE i FROM tpip_verification_drift_item i JOIN tpip_verification_drift_report r " +
                "ON r.id=i.drift_report_id JOIN tpip_verification_run v ON v.id=r.verification_run_id WHERE v.workspace_id=?", workspaceId);
        update(connection, "DELETE d FROM tpip_verification_drift_review d JOIN tpip_verification_drift_report r " +
                "ON r.id=d.drift_report_id JOIN tpip_verification_run v ON v.id=r.verification_run_id WHERE v.workspace_id=?", workspaceId);
        update(connection, "DELETE r FROM tpip_verification_drift_report r JOIN tpip_verification_run v " +
                "ON v.id=r.verification_run_id WHERE v.workspace_id=?", workspaceId);
        update(connection, "DELETE FROM tpip_verification_baseline WHERE id=?", id(ids, "baselineId"));
        update(connection, "DELETE FROM tpip_verification_run WHERE workspace_id=?", workspaceId);
        update(connection, "UPDATE tpip_drift_governance_policy SET current_version_id=NULL,policy_status='PAUSED' WHERE id=?", policyId);
        update(connection, "DELETE FROM tpip_drift_governance_policy_version WHERE id=?", id(ids, "policyVersionId"));
        update(connection, "DELETE FROM tpip_drift_governance_policy WHERE id=?", policyId);
        update(connection, "DELETE FROM tpip_workspace WHERE id=?", id(ids, "workspaceId"));
    }

    private static void verifyClean(Connection connection, Properties ids) throws SQLException {
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("workspaceId", "tpip_workspace"); checks.put("policyId", "tpip_drift_governance_policy");
        checks.put("policyVersionId", "tpip_drift_governance_policy_version");
        checks.put("baselineRunId", "tpip_verification_run"); checks.put("driftRunId", "tpip_verification_run");
        checks.put("commandRunId", "tpip_verification_run");
        checks.put("baselineId", "tpip_verification_baseline"); checks.put("driftReportId", "tpip_verification_drift_report");
        checks.put("commandDriftReportId", "tpip_verification_drift_report");
        checks.put("executionId", "tpip_drift_governance_execution");
        checks.put("originalBatchId", "tpip_drift_governance_reminder_batch");
        checks.put("replacementBatchId", "tpip_drift_governance_reminder_batch");
        checks.put("draftBatchId", "tpip_drift_governance_reminder_batch");
        checks.put("outboxId", "tpip_notification_outbox"); checks.put("deliveryId", "tpip_notification_delivery");
        for (var check : checks.entrySet()) {
            long count = count(connection, "SELECT COUNT(*) FROM " + check.getValue() + " WHERE id=?",
                    id(ids, check.getKey()));
            if (count != 0) throw new IllegalStateException("Fixture residue remains for " + check.getKey() + ": " + count);
        }
    }

    private static long insert(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(s, values); if (s.executeUpdate() != 1) throw new SQLException("Expected one inserted row");
            try (ResultSet keys = s.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Database did not return a generated key");
                return keys.getLong(1);
            }
        }
    }
    private static void update(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) { bind(s, values); s.executeUpdate(); }
    }
    private static void bind(PreparedStatement s, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
    }
    private static long requiredLong(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            if (!r.next()) throw new IllegalStateException("At least one FixtureSuite version is required before UI acceptance");
            return r.getLong(1);
        }
    }
    private static long count(Connection c, String sql, long id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setLong(1, id); try (ResultSet r = s.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
    private static void requireIsolatedWorkspace(Connection c, long workspaceId, String expectedCode) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT workspace_code,created_by FROM tpip_workspace WHERE id=?")) {
            s.setLong(1, workspaceId);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next() || !Objects.equals(expectedCode, r.getString(1)) || !ACTOR.equals(r.getString(2))
                        || !expectedCode.startsWith("ui-v016-"))
                    throw new IllegalStateException("Refusing cleanup: isolated Workspace identity does not match manifest");
            }
        }
    }
    private static List<Long> queryLongs(Connection c, String sql, long value) throws SQLException {
        List<Long> result = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement(sql)) { s.setLong(1, value);
            try (ResultSet r = s.executeQuery()) { while (r.next()) result.add(r.getLong(1)); } }
        return result;
    }
    private static List<String> queryStrings(Connection c, String sql, long value) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement(sql)) { s.setLong(1, value);
            try (ResultSet r = s.executeQuery()) { while (r.next()) result.add(r.getString(1)); } }
        return result;
    }
    private static void deleteAudit(Connection c, String assetType, List<String> assetCodes) throws SQLException {
        if (assetCodes.isEmpty()) return;
        List<Object> values = new ArrayList<>(); values.add(assetType); values.add("local-ui"); values.addAll(assetCodes);
        update(c, "DELETE FROM tpip_audit_event WHERE asset_type=? AND actor_code=? AND asset_code IN (" +
                placeholders(assetCodes.size()) + ")", values.toArray());
    }
    private static String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Properties load(Path path) throws IOException {
        Properties p = new Properties(); try (InputStream in = Files.newInputStream(path)) { p.load(in); } return p;
    }
    private static long id(Properties p, String name) {
        String value = p.getProperty(name);
        if (value == null || !value.matches("[1-9][0-9]*")) throw new IllegalArgumentException("Manifest has no valid " + name);
        return Long.parseLong(value);
    }
    private static void put(Properties p, String name, long value) { p.setProperty(name, Long.toString(value)); }
    private static String environment(String name, String fallback) {
        String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value;
    }
    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
