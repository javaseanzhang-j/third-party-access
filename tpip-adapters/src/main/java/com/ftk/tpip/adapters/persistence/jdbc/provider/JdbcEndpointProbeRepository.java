package com.ftk.tpip.adapters.persistence.jdbc.provider;

import com.ftk.tpip.provider.domain.model.EndpointProbeResult;
import com.ftk.tpip.provider.domain.repository.EndpointProbeRepository;
import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcEndpointProbeRepository implements EndpointProbeRepository {
    private final JdbcTemplate jdbc;
    public JdbcEndpointProbeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public EndpointProbeResult save(EndpointProbeResult value) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO tpip_endpoint_probe_result(endpoint_id,outcome,reason_code,latency_ms,actor_code) VALUES(?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, value.endpointId()); statement.setString(2, value.outcome());
            statement.setString(3, value.reasonCode()); statement.setLong(4, value.latencyMs());
            statement.setString(5, value.actorCode()); return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return endpoint probe id");
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,asset_version,event_summary) VALUES(UUID(),'ENDPOINT_CONNECTIVITY_PROBED','USER',?,'ENDPOINT',?,'probe',?)",
                value.actorCode(), Long.toString(value.endpointId()), value.outcome() + ":" + value.reasonCode());
        return findById(keys.getKey().longValue());
    }

    @Override
    public List<EndpointProbeResult> findByEndpoint(long endpointId, int limit) {
        return jdbc.query("SELECT id,endpoint_id,outcome,reason_code,latency_ms,actor_code,created_at FROM tpip_endpoint_probe_result WHERE endpoint_id=? ORDER BY id DESC LIMIT ?",
                (r, n) -> map(r), endpointId, limit);
    }

    private EndpointProbeResult findById(long id) {
        return jdbc.query("SELECT id,endpoint_id,outcome,reason_code,latency_ms,actor_code,created_at FROM tpip_endpoint_probe_result WHERE id=?",
                (r, n) -> map(r), id).stream().findFirst().orElseThrow();
    }
    private static EndpointProbeResult map(java.sql.ResultSet r) throws java.sql.SQLException {
        return new EndpointProbeResult(r.getLong("id"), r.getLong("endpoint_id"), r.getString("outcome"),
                r.getString("reason_code"), r.getLong("latency_ms"), r.getString("actor_code"),
                r.getTimestamp("created_at").toInstant());
    }
}
