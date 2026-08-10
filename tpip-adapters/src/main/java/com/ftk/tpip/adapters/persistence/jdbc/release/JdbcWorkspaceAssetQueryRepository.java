package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.WorkspaceAssetQueryRepository;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcWorkspaceAssetQueryRepository implements WorkspaceAssetQueryRepository {
    private static final String FROM = " FROM tpip_workspace w "
            + "LEFT JOIN tpip_drift_governance_policy wp ON wp.workspace_id=w.id AND wp.policy_scope='WORKSPACE' AND wp.policy_status='ACTIVE' "
            + "LEFT JOIN tpip_drift_governance_policy gp ON gp.policy_scope='GLOBAL' AND gp.policy_status='ACTIVE' "
            + "LEFT JOIN tpip_drift_governance_policy_version epv ON epv.id=COALESCE(wp.current_version_id,gp.current_version_id) "
            + "LEFT JOIN (SELECT workspace_id,MAX(id) latest_baseline_id,COUNT(*) baseline_count FROM tpip_verification_baseline GROUP BY workspace_id) bc ON bc.workspace_id=w.id "
            + "LEFT JOIN tpip_verification_baseline lb ON lb.id=bc.latest_baseline_id "
            + "LEFT JOIN (SELECT b.workspace_id,COUNT(*) drift_report_count,"
            + "SUM(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') THEN 1 ELSE 0 END) actionable_drift_count,"
            + "COALESCE(SUM(r.drift_count),0) changed_item_count FROM tpip_verification_drift_report r "
            + "JOIN tpip_verification_baseline b ON b.id=r.baseline_id LEFT JOIN tpip_verification_drift_review rv ON rv.drift_report_id=r.id GROUP BY b.workspace_id) dc ON dc.workspace_id=w.id ";
    private static final String SELECT = "SELECT w.id,w.workspace_code,w.workspace_name,w.base_bundle_id,w.environment_code,w.lifecycle_status,w.risk_level,w.owner_code,"
            + "COALESCE(wp.id,gp.id) policy_id,COALESCE(wp.policy_code,gp.policy_code) policy_code,COALESCE(wp.policy_name,gp.policy_name) policy_name,epv.id policy_version_id,epv.version_no,"
            + "CASE WHEN wp.id IS NOT NULL THEN 'WORKSPACE' WHEN gp.id IS NOT NULL THEN 'GLOBAL' ELSE 'DEFAULT' END resolution_source,"
            + "lb.id baseline_id,lb.fixture_suite_version_id,lb.source_verification_run_id,lb.baseline_checksum,lb.created_at baseline_created_at,"
            + "COALESCE(bc.baseline_count,0) baseline_count,COALESCE(dc.drift_report_count,0) drift_report_count,COALESCE(dc.actionable_drift_count,0) actionable_drift_count,COALESCE(dc.changed_item_count,0) changed_item_count,"
            + "w.row_version,w.created_at,w.updated_at ";
    private final JdbcTemplate jdbc;
    public JdbcWorkspaceAssetQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public WorkspacePage findWorkspaces(WorkspaceQuery query, int offset, int limit) {
        var where = new StringBuilder(" WHERE 1=1"); var args = new ArrayList<Object>();
        in(where,args,"w.lifecycle_status",query.lifecycleStatuses()); in(where,args,"w.risk_level",query.riskLevels());
        if(query.environmentCode()!=null){where.append(" AND w.environment_code=?");args.add(query.environmentCode());}
        if(query.keyword()!=null){where.append(" AND (w.workspace_code LIKE ? OR w.workspace_name LIKE ? OR w.owner_code LIKE ?)");String p="%"+query.keyword()+"%";args.add(p);args.add(p);args.add(p);}
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+FROM+where,Long.class,args.toArray());
        var pageArgs=new ArrayList<>(args);pageArgs.add(limit);pageArgs.add(offset);
        var items=jdbc.query(SELECT+FROM+where+" ORDER BY w.updated_at DESC,w.id DESC LIMIT ? OFFSET ?",(r,n)->workspace(r),pageArgs.toArray());
        return new WorkspacePage(List.copyOf(items),total==null?0:total);
    }
    @Override public Optional<WorkspaceRow> findWorkspace(long id){return jdbc.query(SELECT+FROM+" WHERE w.id=?",(r,n)->workspace(r),id).stream().findFirst();}
    @Override public List<BaselineRow> findBaselines(long id,int limit){return jdbc.query("SELECT id,fixture_suite_version_id,source_verification_run_id,baseline_checksum,predecessor_baseline_id,accepted_drift_report_id,created_at FROM tpip_verification_baseline WHERE workspace_id=? ORDER BY id DESC LIMIT ?",(r,n)->new BaselineRow(r.getLong("id"),r.getLong("fixture_suite_version_id"),r.getLong("source_verification_run_id"),r.getString("baseline_checksum"),nullableLong(r,"predecessor_baseline_id"),nullableLong(r,"accepted_drift_report_id"),r.getTimestamp("created_at").toInstant()),id,limit);}
    @Override public List<DriftReportRow> findDriftReports(long id,int limit){return jdbc.query("SELECT r.id,r.baseline_id,r.verification_run_id,r.drift_status,r.compared_check_count,r.drift_count,rv.review_status,rv.assignee_code,rv.successor_baseline_id,r.created_at,COALESCE(rv.updated_at,r.created_at) updated_at FROM tpip_verification_drift_report r JOIN tpip_verification_baseline b ON b.id=r.baseline_id LEFT JOIN tpip_verification_drift_review rv ON rv.drift_report_id=r.id WHERE b.workspace_id=? ORDER BY r.id DESC LIMIT ?",(r,n)->new DriftReportRow(r.getLong("id"),r.getLong("baseline_id"),r.getLong("verification_run_id"),VerificationDriftStatus.valueOf(r.getString("drift_status")),r.getInt("compared_check_count"),r.getInt("drift_count"),enumOrNull(VerificationDriftReviewStatus.class,r.getString("review_status")),r.getString("assignee_code"),nullableLong(r,"successor_baseline_id"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant()),id,limit);}
    @Override public List<ImpactEvidenceRow> findImpactEvidence(long id,int limit){return jdbc.query("SELECT j.job_id,j.candidate_policy_id,p.policy_code,p.policy_name,j.candidate_version_id,v.version_no,j.job_status,j.job_priority,i.item_status,i.attempt_count,i.workspace_snapshot_id,s.impact_checksum,j.sealed_snapshot_id,j.created_at,i.finished_at FROM tpip_global_drift_policy_impact_job_item i JOIN tpip_global_drift_policy_impact_job j ON j.job_id=i.job_id JOIN tpip_drift_governance_policy p ON p.id=j.candidate_policy_id JOIN tpip_drift_governance_policy_version v ON v.id=j.candidate_version_id LEFT JOIN tpip_drift_policy_impact_snapshot s ON s.snapshot_id=i.workspace_snapshot_id WHERE i.workspace_id=? ORDER BY j.created_at DESC,j.job_id DESC LIMIT ?",(r,n)->new ImpactEvidenceRow(r.getString("job_id"),r.getLong("candidate_policy_id"),r.getString("policy_code"),r.getString("policy_name"),r.getLong("candidate_version_id"),r.getInt("version_no"),GlobalDriftPolicyImpactJobStatus.valueOf(r.getString("job_status")),GlobalImpactJobPriority.valueOf(r.getString("job_priority")),GlobalDriftPolicyImpactJobItemStatus.valueOf(r.getString("item_status")),r.getInt("attempt_count"),r.getString("workspace_snapshot_id"),r.getString("impact_checksum"),r.getString("sealed_snapshot_id"),r.getTimestamp("created_at").toInstant(),instant(r,"finished_at")),id,limit);}
    private static WorkspaceRow workspace(ResultSet r)throws SQLException{Long pid=nullableLong(r,"policy_id");EffectivePolicy policy=pid==null?null:new EffectivePolicy(pid,r.getString("policy_code"),r.getString("policy_name"),nullableLong(r,"policy_version_id"),nullableInt(r,"version_no"),r.getString("resolution_source"));Long bid=nullableLong(r,"baseline_id");LatestBaseline baseline=bid==null?null:new LatestBaseline(bid,r.getLong("fixture_suite_version_id"),r.getLong("source_verification_run_id"),r.getString("baseline_checksum"),r.getTimestamp("baseline_created_at").toInstant());return new WorkspaceRow(r.getLong("id"),r.getString("workspace_code"),r.getString("workspace_name"),nullableLong(r,"base_bundle_id"),r.getString("environment_code"),WorkspaceLifecycleStatus.valueOf(r.getString("lifecycle_status")),WorkspaceRiskLevel.valueOf(r.getString("risk_level")),r.getString("owner_code"),policy,baseline,r.getLong("baseline_count"),r.getLong("drift_report_count"),r.getLong("actionable_drift_count"),r.getLong("changed_item_count"),r.getLong("row_version"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());}
    private static void in(StringBuilder w,List<Object>a,String c,Collection<?>v){if(v==null||v.isEmpty())return;w.append(" AND ").append(c).append(" IN (").append(String.join(",",Collections.nCopies(v.size(),"?"))).append(')');v.forEach(x->a.add(x instanceof Enum<?>e?e.name():x));}
    private static Long nullableLong(ResultSet r,String c)throws SQLException{long v=r.getLong(c);return r.wasNull()?null:v;}private static Integer nullableInt(ResultSet r,String c)throws SQLException{int v=r.getInt(c);return r.wasNull()?null:v;}private static java.time.Instant instant(ResultSet r,String c)throws SQLException{Timestamp v=r.getTimestamp(c);return v==null?null:v.toInstant();}private static <E extends Enum<E>>E enumOrNull(Class<E>t,String v){return v==null?null:Enum.valueOf(t,v);}
}
