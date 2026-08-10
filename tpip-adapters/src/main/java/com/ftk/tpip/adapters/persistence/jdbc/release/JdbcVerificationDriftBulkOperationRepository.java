package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.VerificationDriftBulkOperation;
import com.ftk.tpip.release.domain.model.VerificationDriftBulkOperationStatus;
import com.ftk.tpip.release.domain.model.VerificationDriftBulkOperationType;
import com.ftk.tpip.release.domain.repository.VerificationDriftBulkOperationRepository;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcVerificationDriftBulkOperationRepository
        implements VerificationDriftBulkOperationRepository {
    private static final String COLUMNS = "command_key,workspace_id,operation_type,dry_run,request_checksum," +
            "operation_status,actor_code,item_count,eligible_count,applied_count,rejected_count," +
            "request_document,result_document,created_at";
    private final JdbcTemplate jdbc;

    public JdbcVerificationDriftBulkOperationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<VerificationDriftBulkOperation> findByCommandKey(String commandKey) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_verification_drift_bulk_operation WHERE command_key=?",
                (r, n) -> new VerificationDriftBulkOperation(r.getString("command_key"), r.getLong("workspace_id"),
                        VerificationDriftBulkOperationType.valueOf(r.getString("operation_type")),
                        r.getBoolean("dry_run"), r.getString("request_checksum"),
                        VerificationDriftBulkOperationStatus.valueOf(r.getString("operation_status")),
                        r.getString("actor_code"), r.getInt("item_count"), r.getInt("eligible_count"),
                        r.getInt("applied_count"), r.getInt("rejected_count"),
                        r.getString("request_document"), r.getString("result_document"),
                        r.getTimestamp("created_at").toInstant()), commandKey).stream().findFirst();
    }

    @Override
    public VerificationDriftBulkOperation save(VerificationDriftBulkOperation value) {
        try {
            jdbc.update("INSERT INTO tpip_verification_drift_bulk_operation(" + COLUMNS.substring(0,
                            COLUMNS.lastIndexOf(",created_at")) + ") VALUES(?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON))",
                    value.commandKey(), value.workspaceId(), value.operationType().name(), value.dryRun(), value.requestChecksum(),
                    value.status().name(), value.actorCode(), value.itemCount(), value.eligibleCount(),
                    value.appliedCount(), value.rejectedCount(), value.requestDocument(), value.resultDocument());
        } catch (DuplicateKeyException failure) {
            VerificationDriftBulkOperation existing = findByCommandKey(value.commandKey()).orElseThrow();
            if (!existing.requestChecksum().equals(value.requestChecksum())
                    || !existing.actorCode().equals(value.actorCode()))
                throw new IllegalArgumentException("Idempotency key was already used for another request", failure);
            return existing;
        }
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type," +
                        "asset_code,event_summary,event_detail) VALUES(UUID(),'VERIFICATION_DRIFT_BULK_OPERATION'," +
                        "'USER',?,'VERIFICATION_DRIFT_BULK_OPERATION',?,?,JSON_OBJECT('operationType',?," +
                        "'dryRun',?,'status',?,'itemCount',?,'eligibleCount',?,'appliedCount',?,'rejectedCount',?))",
                value.actorCode(), value.commandKey(), "Recorded governed drift bulk operation", value.operationType().name(),
                value.dryRun(), value.status().name(), value.itemCount(), value.eligibleCount(), value.appliedCount(),
                value.rejectedCount());
        return findByCommandKey(value.commandKey()).orElseThrow();
    }
}
