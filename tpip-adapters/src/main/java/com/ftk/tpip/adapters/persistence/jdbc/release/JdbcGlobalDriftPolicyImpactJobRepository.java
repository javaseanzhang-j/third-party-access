package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.exception.GlobalImpactJobCommandConflictException;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactJobRepository;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcGlobalDriftPolicyImpactJobRepository implements GlobalDriftPolicyImpactJobRepository {
    private static final String J = "job_id,candidate_policy_id,candidate_version_id,candidate_checksum,"
            + "coverage_checksum,workspace_count,succeeded_count,failed_count,job_status,snapshot_at,expires_at,"
            + "sealed_snapshot_id,row_version,created_by,created_at,updated_by,updated_at";
    private static final String I = "job_id,workspace_id,item_order,current_policy_id,current_version_id,"
            + "current_checksum,item_status,attempt_count,lease_owner,lease_until,workspace_snapshot_id,"
            + "failure_code,failure_message,started_at,finished_at";
    private final JdbcTemplate jdbc;
    public JdbcGlobalDriftPolicyImpactJobRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public GlobalDriftPolicyImpactJob create(GlobalDriftPolicyImpactJob value,
            List<GlobalDriftPolicyImpactJobItem> items) {
        if (items.size() != value.workspaceCount())
            throw new IllegalArgumentException("Global impact job item count does not match coverage");
        jdbc.update("INSERT INTO tpip_global_drift_policy_impact_job(" + J + ") VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                value.jobId(), value.candidatePolicyId(), value.candidateVersionId(), value.candidateChecksum(),
                value.coverageChecksum(), value.workspaceCount(), value.succeededCount(), value.failedCount(),
                value.status().name(), Timestamp.from(value.snapshotAt()), Timestamp.from(value.expiresAt()),
                value.sealedSnapshotId(), value.rowVersion(), value.createdBy(), Timestamp.from(value.createdAt()),
                value.updatedBy(), Timestamp.from(value.updatedAt()));
        jdbc.batchUpdate("INSERT INTO tpip_global_drift_policy_impact_job_item(" + I +
                ") VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", items, 100, (s, item) -> bind(s, item));
        audit("GLOBAL_DRIFT_POLICY_IMPACT_JOB_CREATED", value, value.createdBy(), null);
        return findById(value.jobId()).orElseThrow();
    }
    @Override public Optional<GlobalDriftPolicyImpactJob> findById(String jobId) {
        return jdbc.query("SELECT " + J + " FROM tpip_global_drift_policy_impact_job WHERE job_id=?",
                (r, n) -> job(r), jobId).stream().findFirst();
    }
    @Override public List<GlobalDriftPolicyImpactJob> findRunnable(Instant now, int limit) {
        return jdbc.query("SELECT " + J + " FROM tpip_global_drift_policy_impact_job " +
                "WHERE job_status IN ('PENDING','RUNNING') AND expires_at>? " +
                "ORDER BY created_at,job_id LIMIT ?", (r, n) -> job(r), Timestamp.from(now), limit);
    }
    @Override public List<GlobalDriftPolicyImpactJobItem> findItems(String jobId) {
        return items("WHERE job_id=? ORDER BY item_order", jobId);
    }
    @Override public List<GlobalDriftPolicyImpactJobItem> findItems(String jobId, int offset, int limit) {
        return items("WHERE job_id=? ORDER BY item_order LIMIT ? OFFSET ?", jobId, limit, offset);
    }
    @Override public List<GlobalDriftPolicyImpactJobItem> claim(String jobId, int batchSize,
            String workerId, Instant now, Instant leaseUntil) {
        List<Long> ids = jdbc.query("SELECT workspace_id FROM tpip_global_drift_policy_impact_job_item " +
                "WHERE job_id=? AND (item_status='PENDING' OR (item_status='RUNNING' AND lease_until<=?)) " +
                "AND EXISTS (SELECT 1 FROM tpip_global_drift_policy_impact_job j WHERE j.job_id=? " +
                "AND j.job_status IN ('PENDING','RUNNING') AND j.expires_at>?) " +
                "ORDER BY item_order LIMIT ? FOR UPDATE SKIP LOCKED", (r, n) -> r.getLong(1),
                jobId, Timestamp.from(now), jobId, Timestamp.from(now), batchSize);
        if (ids.isEmpty()) return List.of();
        jdbc.batchUpdate("UPDATE tpip_global_drift_policy_impact_job_item SET item_status='RUNNING',"
                + "attempt_count=attempt_count+1,lease_owner=?,lease_until=?,failure_code=NULL,failure_message=NULL,"
                + "started_at=COALESCE(started_at,?),finished_at=NULL WHERE job_id=? AND workspace_id=?",
                ids, 100, (s, workspaceId) -> { s.setString(1, workerId);
                    s.setTimestamp(2, Timestamp.from(leaseUntil)); s.setTimestamp(3, Timestamp.from(now));
                    s.setString(4, jobId); s.setLong(5, workspaceId); });
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] parameters = new Object[ids.size() + 1]; parameters[0] = jobId;
        for (int i = 0; i < ids.size(); i++) parameters[i + 1] = ids.get(i);
        return jdbc.query("SELECT " + I + " FROM tpip_global_drift_policy_impact_job_item WHERE job_id=? " +
                "AND workspace_id IN (" + placeholders + ") ORDER BY item_order", (r, n) -> item(r), parameters);
    }
    @Override public void markSucceeded(String jobId, long workspaceId, String workerId,
            String snapshotId, Instant now) {
        int changed = jdbc.update("UPDATE tpip_global_drift_policy_impact_job_item SET item_status='SUCCEEDED',"
                + "workspace_snapshot_id=?,lease_owner=NULL,lease_until=NULL,finished_at=? " +
                "WHERE job_id=? AND workspace_id=? AND item_status='RUNNING' AND lease_owner=?", snapshotId,
                Timestamp.from(now), jobId, workspaceId, workerId);
        if (changed == 0) throw new IllegalStateException("Global impact job item is not RUNNING");
        jdbc.update("UPDATE tpip_global_drift_policy_impact_job SET last_progress_at=? WHERE job_id=?",
                Timestamp.from(now),jobId);
    }
    @Override public void markFailed(String jobId, long workspaceId, String workerId,
            String code, String message, Instant now) {
        int changed = jdbc.update("UPDATE tpip_global_drift_policy_impact_job_item SET item_status='FAILED',"
                + "failure_code=?,failure_message=?,lease_owner=NULL,lease_until=NULL,finished_at=? " +
                "WHERE job_id=? AND workspace_id=? AND item_status='RUNNING' AND lease_owner=?", code, message,
                Timestamp.from(now), jobId, workspaceId, workerId);
        if (changed == 0) throw new IllegalStateException("Global impact job item is not RUNNING");
        jdbc.update("UPDATE tpip_global_drift_policy_impact_job SET last_progress_at=? WHERE job_id=?",
                Timestamp.from(now),jobId);
    }
    @Override public GlobalDriftPolicyImpactJob refresh(String jobId, String actor, Instant now) {
        int[] counts = jdbc.queryForObject("SELECT COUNT(*),SUM(item_status='SUCCEEDED'),SUM(item_status='FAILED'),"
                + "SUM(item_status IN ('PENDING','RUNNING')) FROM tpip_global_drift_policy_impact_job_item WHERE job_id=?",
                (r, n) -> new int[] {r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4)}, jobId);
        var current = findById(jobId).orElseThrow();
        GlobalDriftPolicyImpactJobStatus status;
        if (current.status() == GlobalDriftPolicyImpactJobStatus.SEALED
                || current.status() == GlobalDriftPolicyImpactJobStatus.CANCELLED) return current;
        if (current.expired(now)) status = GlobalDriftPolicyImpactJobStatus.EXPIRED;
        else if (counts[1] == counts[0]) status = GlobalDriftPolicyImpactJobStatus.READY;
        else if (counts[3] == 0 && counts[2] > 0) status = GlobalDriftPolicyImpactJobStatus.FAILED;
        else if (counts[1] > 0 || counts[2] > 0) status = GlobalDriftPolicyImpactJobStatus.RUNNING;
        else status = GlobalDriftPolicyImpactJobStatus.PENDING;
        jdbc.update("UPDATE tpip_global_drift_policy_impact_job SET succeeded_count=?,failed_count=?,job_status=?,"
                + "row_version=row_version+1,updated_by=?,updated_at=? WHERE job_id=?",
                counts[1], counts[2], status.name(), actor, Timestamp.from(now), jobId);
        return findById(jobId).orElseThrow();
    }
    @Override public GlobalDriftPolicyImpactJob retryFailed(String jobId, long rowVersion,
            String actor, String reason, Instant now) {
        var job = findById(jobId).orElseThrow();
        if (job.status() != GlobalDriftPolicyImpactJobStatus.FAILED || job.expired(now))
            throw new GlobalImpactJobCommandConflictException("Only a non-expired FAILED Global impact job can be retried");
        int changed = jdbc.update("UPDATE tpip_global_drift_policy_impact_job SET job_status='PENDING',failed_count=0,"
                + "row_version=row_version+1,updated_by=?,updated_at=? WHERE job_id=? AND row_version=? AND job_status='FAILED'",
                actor, Timestamp.from(now), jobId, rowVersion);
        if (changed == 0) throw new GlobalImpactJobCommandConflictException("Global impact job changed concurrently");
        jdbc.update("UPDATE tpip_global_drift_policy_impact_job_item SET item_status='PENDING',failure_code=NULL,"
                + "failure_message=NULL,finished_at=NULL WHERE job_id=? AND item_status='FAILED'", jobId);
        var saved = findById(jobId).orElseThrow(); audit("GLOBAL_DRIFT_POLICY_IMPACT_JOB_RETRIED", saved, actor, reason);
        return saved;
    }
    @Override public GlobalDriftPolicyImpactJob seal(String jobId, long rowVersion, String snapshotId,
            String actor, Instant now) {
        int changed = jdbc.update("UPDATE tpip_global_drift_policy_impact_job SET job_status='SEALED',"
                + "sealed_snapshot_id=?,row_version=row_version+1,updated_by=?,updated_at=? " +
                "WHERE job_id=? AND row_version=? AND job_status='READY'", snapshotId, actor,
                Timestamp.from(now), jobId, rowVersion);
        if (changed == 0) throw new GlobalImpactJobCommandConflictException("Global impact job is not READY or changed concurrently");
        var saved = findById(jobId).orElseThrow(); audit("GLOBAL_DRIFT_POLICY_IMPACT_JOB_SEALED", saved, actor, snapshotId);
        return saved;
    }
    @Override public GlobalDriftPolicyImpactJob cancel(String jobId, long rowVersion, String actor,
            String reason, Instant now) {
        int changed = jdbc.update("UPDATE tpip_global_drift_policy_impact_job SET job_status='CANCELLED',"
                + "row_version=row_version+1,updated_by=?,updated_at=? WHERE job_id=? AND row_version=? "
                + "AND job_status IN ('PENDING','RUNNING','FAILED','READY') AND expires_at>?",
                actor, Timestamp.from(now), jobId, rowVersion, Timestamp.from(now));
        if (changed == 0) throw new GlobalImpactJobCommandConflictException("Global impact job cannot be cancelled or changed concurrently");
        var saved = findById(jobId).orElseThrow();
        audit("GLOBAL_DRIFT_POLICY_IMPACT_JOB_CANCELLED", saved, actor, reason); return saved;
    }
    @Override public List<GlobalDriftPolicyImpactJob> expireDue(Instant now, int limit, String actor) {
        List<String> ids = jdbc.query("SELECT job_id FROM tpip_global_drift_policy_impact_job WHERE "
                + "job_status IN ('PENDING','RUNNING','FAILED','READY') AND expires_at<=? "
                + "ORDER BY expires_at,job_id LIMIT ? FOR UPDATE SKIP LOCKED", (r,n)->r.getString(1),
                Timestamp.from(now), limit);
        return ids.stream().map(id -> {
            jdbc.update("UPDATE tpip_global_drift_policy_impact_job SET job_status='EXPIRED',"
                    + "row_version=row_version+1,updated_by=?,updated_at=? WHERE job_id=?",
                    actor, Timestamp.from(now), id);
            var saved = findById(id).orElseThrow();
            audit("GLOBAL_DRIFT_POLICY_IMPACT_JOB_EXPIRED", saved, actor, null); return saved;
        }).toList();
    }
    @Override public List<GlobalDriftPolicyImpactJob> findPurgeCandidates(Instant before, int limit) {
        return jdbc.query("SELECT " + J + " FROM tpip_global_drift_policy_impact_job WHERE "
                + "job_status IN ('EXPIRED','CANCELLED') AND updated_at<=? ORDER BY updated_at,job_id LIMIT ?",
                (r,n)->job(r), Timestamp.from(before), limit);
    }
    @Override public GlobalDriftPolicyImpactJobPurgeReceipt purge(String jobId, long rowVersion,
            Instant before, boolean deleteOrphans, String actor, String reason, Instant now) {
        var value = findById(jobId).orElseThrow();
        if ((value.status()!=GlobalDriftPolicyImpactJobStatus.EXPIRED
                && value.status()!=GlobalDriftPolicyImpactJobStatus.CANCELLED)
                || value.rowVersion()!=rowVersion || value.updatedAt().isAfter(before))
            throw new IllegalArgumentException("Global impact job is not eligible for purge");
        int itemCount = jdbc.queryForObject("SELECT COUNT(*) FROM tpip_global_drift_policy_impact_job_item WHERE job_id=?",
                Integer.class, jobId);
        List<String> orphanIds = jdbc.query("SELECT s.snapshot_id FROM tpip_drift_policy_impact_snapshot s "
                + "JOIN tpip_global_drift_policy_impact_job_item i ON i.workspace_snapshot_id=s.snapshot_id "
                + "LEFT JOIN tpip_global_drift_policy_impact_snapshot_item g ON g.workspace_snapshot_id=s.snapshot_id "
                + "WHERE i.job_id=? AND s.expires_at<=? AND s.publish_used_at IS NULL "
                + "AND s.activation_used_at IS NULL AND g.workspace_snapshot_id IS NULL",
                (r,n)->r.getString(1), jobId, Timestamp.from(now));
        jdbc.update("DELETE FROM tpip_global_drift_policy_impact_job_item WHERE job_id=?", jobId);
        int deletedSnapshots = 0;
        if (deleteOrphans && !orphanIds.isEmpty()) {
            String marks = String.join(",", java.util.Collections.nCopies(orphanIds.size(), "?"));
            deletedSnapshots = jdbc.update("DELETE FROM tpip_drift_policy_impact_snapshot WHERE snapshot_id IN ("+marks+")",
                    orphanIds.toArray());
        }
        int deleted = jdbc.update("DELETE FROM tpip_global_drift_policy_impact_job WHERE job_id=? AND row_version=?",
                jobId, rowVersion);
        if (deleted == 0) throw new IllegalArgumentException("Global impact job changed concurrently");
        var receipt = new GlobalDriftPolicyImpactJobPurgeReceipt(java.util.UUID.randomUUID().toString(), jobId,
                value.candidatePolicyId(), value.candidateVersionId(), value.candidateChecksum(), value.coverageChecksum(),
                value.status(), value.workspaceCount(), value.succeededCount(), value.failedCount(), itemCount,
                orphanIds.size(), deletedSnapshots, reason, actor, now);
        jdbc.update("INSERT INTO tpip_global_drift_policy_impact_job_purge_receipt("+
                "receipt_id,job_id,candidate_policy_id,candidate_version_id,candidate_checksum,coverage_checksum,"
                + "terminal_status,workspace_count,succeeded_count,failed_count,item_count,orphan_snapshot_count,"
                + "deleted_snapshot_count,purge_reason,purged_by,purged_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                receipt.receiptId(), receipt.jobId(), receipt.candidatePolicyId(), receipt.candidateVersionId(),
                receipt.candidateChecksum(), receipt.coverageChecksum(), receipt.terminalStatus().name(),
                receipt.workspaceCount(), receipt.succeededCount(), receipt.failedCount(), receipt.itemCount(),
                receipt.orphanSnapshotCount(), receipt.deletedSnapshotCount(), receipt.reason(), receipt.purgedBy(),
                Timestamp.from(receipt.purgedAt()));
        audit("GLOBAL_DRIFT_POLICY_IMPACT_JOB_PURGED", value, actor, receipt.receiptId()); return receipt;
    }
    @Override public Optional<GlobalDriftPolicyImpactJobPurgeReceipt> findPurgeReceipt(String jobId) {
        return jdbc.query("SELECT receipt_id,job_id,candidate_policy_id,candidate_version_id,candidate_checksum,"
                + "coverage_checksum,terminal_status,workspace_count,succeeded_count,failed_count,item_count,"
                + "orphan_snapshot_count,deleted_snapshot_count,purge_reason,purged_by,purged_at "
                + "FROM tpip_global_drift_policy_impact_job_purge_receipt WHERE job_id=?",(r,n)->
                new GlobalDriftPolicyImpactJobPurgeReceipt(r.getString("receipt_id"),r.getString("job_id"),
                        r.getLong("candidate_policy_id"),r.getLong("candidate_version_id"),r.getString("candidate_checksum"),
                        r.getString("coverage_checksum"),GlobalDriftPolicyImpactJobStatus.valueOf(r.getString("terminal_status")),
                        r.getInt("workspace_count"),r.getInt("succeeded_count"),r.getInt("failed_count"),
                        r.getInt("item_count"),r.getInt("orphan_snapshot_count"),r.getInt("deleted_snapshot_count"),
                        r.getString("purge_reason"),r.getString("purged_by"),r.getTimestamp("purged_at").toInstant()),jobId)
                .stream().findFirst();
    }
    @Override public List<GlobalImpactJobDispatch> claimRunnableDispatches(Instant now,String workerId,
            Instant leaseUntil,int limit,int maximumActive,int minimumBatch,int maximumBatch){
        Integer active=jdbc.queryForObject("SELECT COUNT(*) FROM tpip_global_drift_policy_impact_job WHERE "
                + "dispatch_lease_until>? AND dispatch_lease_owner<>?",Integer.class,Timestamp.from(now),workerId);
        int capacity=Math.max(0,maximumActive-(active==null?0:active));if(capacity==0)return List.of();
        List<String> ids=jdbc.query("SELECT job_id FROM tpip_global_drift_policy_impact_job WHERE "
                + "job_status IN ('PENDING','RUNNING') AND expires_at>? AND "
                + "(dispatch_lease_until IS NULL OR dispatch_lease_until<=? OR dispatch_lease_owner=?) "
                + "ORDER BY (CASE job_priority WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'NORMAL' THEN 2 ELSE 1 END "
                + "+ LEAST(FLOOR(TIMESTAMPDIFF(SECOND,COALESCE(last_dispatched_at,created_at),?)/300),4)) DESC,"
                + "COALESCE(last_dispatched_at,created_at),created_at,job_id LIMIT ? FOR UPDATE SKIP LOCKED",
                (r,n)->r.getString(1),Timestamp.from(now),Timestamp.from(now),workerId,Timestamp.from(now),Math.min(limit,capacity));
        List<GlobalImpactJobDispatch> result=new java.util.ArrayList<>();
        for(String id:ids){int changed=jdbc.update("UPDATE tpip_global_drift_policy_impact_job SET dispatch_lease_owner=?,"
                + "dispatch_lease_until=?,dispatch_count=dispatch_count+1,last_dispatched_at=? WHERE job_id=? AND "
                + "(dispatch_lease_until IS NULL OR dispatch_lease_until<=? OR dispatch_lease_owner=?)",workerId,
                Timestamp.from(leaseUntil),Timestamp.from(now),id,Timestamp.from(now),workerId);
            if(changed>0)result.add(dispatch(id,leaseUntil,minimumBatch,maximumBatch));}
        return List.copyOf(result);
    }
    @Override public void releaseDispatch(String jobId,String workerId,Instant now){
        jdbc.update("UPDATE tpip_global_drift_policy_impact_job SET dispatch_lease_owner=NULL,dispatch_lease_until=NULL "
                + "WHERE job_id=? AND dispatch_lease_owner=?",jobId,workerId);
    }
    @Override public int renewItemLeases(String jobId,List<Long> workspaceIds,String workerId,Instant leaseUntil){
        if(workspaceIds.isEmpty())return 0;String marks=String.join(",",java.util.Collections.nCopies(workspaceIds.size(),"?"));
        Object[] args=new Object[workspaceIds.size()+3];args[0]=Timestamp.from(leaseUntil);args[1]=jobId;args[2]=workerId;
        for(int i=0;i<workspaceIds.size();i++)args[i+3]=workspaceIds.get(i);
        return jdbc.update("UPDATE tpip_global_drift_policy_impact_job_item SET lease_until=? WHERE job_id=? "
                + "AND lease_owner=? AND item_status='RUNNING' AND workspace_id IN ("+marks+")",args);
    }
    @Override public GlobalImpactJobRuntimeState reprioritize(String jobId,long rowVersion,GlobalImpactJobPriority priority,
            String actor,String reason,Instant now){int changed=jdbc.update("UPDATE tpip_global_drift_policy_impact_job SET "
                    + "job_priority=?,row_version=row_version+1,updated_by=?,updated_at=? WHERE job_id=? AND row_version=? "
                    + "AND job_status IN ('PENDING','RUNNING','FAILED','READY') AND expires_at>?",priority.name(),actor,
                    Timestamp.from(now),jobId,rowVersion,Timestamp.from(now));
        if(changed==0)throw new GlobalImpactJobCommandConflictException("Global impact job priority cannot change or changed concurrently");
        var job=findById(jobId).orElseThrow();audit("GLOBAL_DRIFT_POLICY_IMPACT_JOB_REPRIORITIZED",job,actor,reason);
        return findRuntimeState(jobId,now).orElseThrow();}
    @Override public List<GlobalImpactJobRuntimeState> findStalled(Instant now,Instant before,int limit){
        return runtimeStates("WHERE j.job_status IN ('PENDING','RUNNING') AND j.expires_at>? AND j.last_progress_at<=? "
                + "ORDER BY j.last_progress_at,j.job_id LIMIT ?",now,Timestamp.from(now),Timestamp.from(before),limit);}
    @Override public Optional<GlobalImpactJobRuntimeState> findRuntimeState(String jobId,Instant now){
        return runtimeStates("WHERE j.job_id=?",now,jobId).stream().findFirst();}
    @Override public int countActiveDispatches(Instant now){Integer value=jdbc.queryForObject(
            "SELECT COUNT(*) FROM tpip_global_drift_policy_impact_job WHERE dispatch_lease_until>?",Integer.class,
            Timestamp.from(now));return value==null?0:value;}
    private GlobalImpactJobDispatch dispatch(String jobId,Instant leaseUntil,int minimumBatch,int maximumBatch){
        var job=findById(jobId).orElseThrow();Object[] profile=jdbc.queryForObject("SELECT "
                + "SUM(i.item_status IN ('PENDING','RUNNING')),COALESCE(MAX(CASE w.risk_level WHEN 'CRITICAL' THEN 4 "
                + "WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END),1),COALESCE(AVG(CASE WHEN i.finished_at IS NOT NULL "
                + "THEN TIMESTAMPDIFF(MICROSECOND,i.started_at,i.finished_at)/1000 END),0),j.job_priority,j.dispatch_count "
                + "FROM tpip_global_drift_policy_impact_job j JOIN tpip_global_drift_policy_impact_job_item i ON i.job_id=j.job_id "
                + "JOIN tpip_workspace w ON w.id=i.workspace_id WHERE j.job_id=? GROUP BY j.job_priority,j.dispatch_count",
                (r,n)->new Object[]{r.getInt(1),r.getInt(2),r.getLong(3),r.getString(4),r.getLong(5)},jobId);
        int pending=(int)profile[0],risk=(int)profile[1];long average=(long)profile[2];int recommended=Math.min(maximumBatch,Math.max(1,pending));
        if(risk>=3)recommended=Math.max(minimumBatch,recommended/2);if(average>=2000)recommended=Math.max(minimumBatch,recommended/2);
        recommended=Math.max(1,Math.min(recommended,Math.max(1,pending)));
        return new GlobalImpactJobDispatch(job,GlobalImpactJobPriority.valueOf((String)profile[3]),pending,risk,average,recommended,(long)profile[4],leaseUntil);
    }
    private List<GlobalImpactJobRuntimeState> runtimeStates(String suffix,Instant now,Object...args){
        return jdbc.query("SELECT "+J+",j.job_priority,j.dispatch_count,j.last_dispatched_at,j.last_progress_at,"
                + "j.dispatch_lease_owner,j.dispatch_lease_until FROM tpip_global_drift_policy_impact_job j "+suffix,(r,n)->{
            var job=job(r);Instant progress=r.getTimestamp("last_progress_at").toInstant();Timestamp dispatched=r.getTimestamp("last_dispatched_at");
            Timestamp lease=r.getTimestamp("dispatch_lease_until");String owner=r.getString("dispatch_lease_owner");
            String recovery=switch(job.status()){
                case READY->"READY_FOR_MANUAL_SEAL";case FAILED->"REVIEW_FAILURE_AND_RETRY";
                case SEALED,EXPIRED,CANCELLED->"NO_ACTION_TERMINAL";
                default->r.getLong("dispatch_count")==0?"START_OR_ENABLE_WORKER":
                        lease!=null&&lease.toInstant().isAfter(now)?"WAIT_FOR_ACTIVE_WORKER":"REDISPATCH_AFTER_LEASE_EXPIRY";};
            return new GlobalImpactJobRuntimeState(job,GlobalImpactJobPriority.valueOf(r.getString("job_priority")),
                    r.getLong("dispatch_count"),dispatched==null?null:dispatched.toInstant(),progress,owner,
                    lease==null?null:lease.toInstant(),Math.max(0,java.time.Duration.between(progress,now).toSeconds()),recovery);
        },args);
    }
    private List<GlobalDriftPolicyImpactJobItem> items(String suffix, Object... args) {
        return jdbc.query("SELECT " + I + " FROM tpip_global_drift_policy_impact_job_item " + suffix,
                (r, n) -> item(r), args);
    }
    private void audit(String type, GlobalDriftPolicyImpactJob value, String actor, String detail) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,"
                + "event_summary,event_detail) VALUES(UUID(),?,'USER',?,'GLOBAL_DRIFT_POLICY_IMPACT_JOB',?,?,"
                + "JSON_OBJECT('candidatePolicyId',?,'candidateVersionId',?,'workspaceCount',?,'detail',?))",
                type, actor, value.jobId(), type.replace('_', ' '), value.candidatePolicyId(),
                value.candidateVersionId(), value.workspaceCount(), detail);
    }
    private static GlobalDriftPolicyImpactJob job(ResultSet r) throws SQLException {
        return new GlobalDriftPolicyImpactJob(r.getString("job_id"), r.getLong("candidate_policy_id"),
                r.getLong("candidate_version_id"), r.getString("candidate_checksum"),
                r.getString("coverage_checksum"), r.getInt("workspace_count"), r.getInt("succeeded_count"),
                r.getInt("failed_count"), GlobalDriftPolicyImpactJobStatus.valueOf(r.getString("job_status")),
                r.getTimestamp("snapshot_at").toInstant(), r.getTimestamp("expires_at").toInstant(),
                r.getString("sealed_snapshot_id"), r.getLong("row_version"), r.getString("created_by"),
                r.getTimestamp("created_at").toInstant(), r.getString("updated_by"),
                r.getTimestamp("updated_at").toInstant());
    }
    private static GlobalDriftPolicyImpactJobItem item(ResultSet r) throws SQLException {
        return new GlobalDriftPolicyImpactJobItem(r.getString("job_id"), r.getLong("workspace_id"),
                r.getInt("item_order"), nullable(r, "current_policy_id"), nullable(r, "current_version_id"),
                r.getString("current_checksum"), GlobalDriftPolicyImpactJobItemStatus.valueOf(r.getString("item_status")),
                r.getInt("attempt_count"), r.getString("lease_owner"), instant(r.getTimestamp("lease_until")),
                r.getString("workspace_snapshot_id"), r.getString("failure_code"), r.getString("failure_message"),
                instant(r.getTimestamp("started_at")), instant(r.getTimestamp("finished_at")));
    }
    private static void bind(PreparedStatement s, GlobalDriftPolicyImpactJobItem item) throws SQLException {
        int i=1; s.setString(i++, item.jobId()); s.setLong(i++, item.workspaceId()); s.setInt(i++, item.itemOrder());
        nullable(s,i++,item.currentPolicyId()); nullable(s,i++,item.currentVersionId()); s.setString(i++,item.currentChecksum());
        s.setString(i++,item.status().name()); s.setInt(i++,item.attemptCount()); s.setString(i++,item.leaseOwner());
        s.setTimestamp(i++,timestamp(item.leaseUntil())); s.setString(i++,item.workspaceSnapshotId());
        s.setString(i++,item.failureCode()); s.setString(i++,item.failureMessage());
        s.setTimestamp(i++,timestamp(item.startedAt())); s.setTimestamp(i,timestamp(item.finishedAt()));
    }
    private static void nullable(PreparedStatement s,int index,Long value)throws SQLException{
        if(value==null)s.setNull(index,Types.BIGINT);else s.setLong(index,value);
    }
    private static Long nullable(ResultSet r,String column)throws SQLException{long v=r.getLong(column);return r.wasNull()?null:v;}
    private static Timestamp timestamp(Instant v){return v==null?null:Timestamp.from(v);}
    private static Instant instant(Timestamp v){return v==null?null:v.toInstant();}
}
