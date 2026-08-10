package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.integration.domain.model.MappingAssetDirection;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.FixtureSuiteRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcFixtureSuiteRepository implements FixtureSuiteRepository {
    private static final String S = "id,binding_id,suite_code,suite_name,description,status,row_version,created_at,updated_at";
    private static final String V = "id,suite_id,version_no,content_checksum,lifecycle_status,published_at,created_at";
    private final JdbcTemplate jdbc;

    public JdbcFixtureSuiteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<FixtureSuite> findById(long id) {
        return jdbc.query("SELECT " + S + " FROM tpip_fixture_suite WHERE id=?", this::suite, id).stream().findFirst();
    }

    @Override public Optional<FixtureSuite> findByCode(AssetCode code) {
        return jdbc.query("SELECT " + S + " FROM tpip_fixture_suite WHERE suite_code=?", this::suite, code.value()).stream().findFirst();
    }

    @Override public List<FixtureSuite> findByBinding(long bindingId) {
        return jdbc.query("SELECT " + S + " FROM tpip_fixture_suite WHERE binding_id=? ORDER BY id DESC", this::suite, bindingId);
    }

    @Override public FixtureSuite create(FixtureSuite suite, String actor) {
        var keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO tpip_fixture_suite(binding_id,suite_code,suite_name,description,status,row_version,created_by,updated_by) VALUES(?,?,?,?,?,0,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, suite.bindingId()); statement.setString(2, suite.suiteCode().value());
                statement.setString(3, suite.suiteName()); statement.setString(4, suite.description());
                statement.setString(5, suite.status().name()); statement.setString(6, actor); statement.setString(7, actor);
                return statement;
            }, keys);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("FixtureSuite code already exists: " + suite.suiteCode().value());
        }
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return FixtureSuite id");
        FixtureSuite saved = findById(keys.getKey().longValue()).orElseThrow();
        audit("FIXTURE_SUITE_CREATED", saved.suiteCode().value(), null, actor);
        return saved;
    }

    @Override public Optional<FixtureSuiteVersion> findVersion(long suiteId, long versionId) {
        return jdbc.query("SELECT " + V + " FROM tpip_fixture_suite_version WHERE suite_id=? AND id=?",
                (r,n) -> version(r, cases(r.getLong("id"))), suiteId, versionId).stream().findFirst();
    }

    @Override public Optional<FixtureSuiteVersion> findVersionById(long versionId) {
        return jdbc.query("SELECT " + V + " FROM tpip_fixture_suite_version WHERE id=?",
                (r,n) -> version(r, cases(r.getLong("id"))), versionId).stream().findFirst();
    }

    @Override public List<FixtureSuiteVersion> findVersions(long suiteId) {
        return jdbc.query("SELECT " + V + " FROM tpip_fixture_suite_version WHERE suite_id=? ORDER BY version_no DESC",
                (r,n) -> version(r, cases(r.getLong("id"))), suiteId);
    }

    @Override public FixtureSuiteVersion createVersion(FixtureSuiteVersion version, String actor) {
        jdbc.queryForObject("SELECT id FROM tpip_fixture_suite WHERE id=? FOR UPDATE", Long.class, version.suiteId());
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_fixture_suite_version WHERE suite_id=?", Integer.class, version.suiteId());
        var keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO tpip_fixture_suite_version(suite_id,version_no,content_checksum,lifecycle_status,created_by) VALUES(?,?,?,'DRAFT',?)", Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, version.suiteId()); statement.setInt(2, next == null ? 1 : next);
                statement.setString(3, version.contentChecksum()); statement.setString(4, actor); return statement;
            }, keys);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("An identical FixtureSuite version already exists");
        }
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return FixtureSuiteVersion id");
        long versionId = keys.getKey().longValue();
        for (FixtureCase fixture : version.cases()) insertCase(versionId, fixture, actor);
        FixtureSuiteVersion saved = findVersion(version.suiteId(), versionId).orElseThrow();
        audit("FIXTURE_SUITE_VERSION_CREATED", findById(version.suiteId()).orElseThrow().suiteCode().value(), Integer.toString(saved.versionNo()), actor);
        return saved;
    }

    @Override public FixtureSuiteVersion publishVersion(long suiteId, long versionId, String actor) {
        int updated = jdbc.update("UPDATE tpip_fixture_suite_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3) WHERE suite_id=? AND id=? AND lifecycle_status='DRAFT'", suiteId, versionId);
        if (updated == 0) throw new IllegalArgumentException("only a DRAFT FixtureSuite version can be published");
        FixtureSuiteVersion saved = findVersion(suiteId, versionId).orElseThrow();
        audit("FIXTURE_SUITE_VERSION_PUBLISHED", findById(suiteId).orElseThrow().suiteCode().value(), Integer.toString(saved.versionNo()), actor);
        return saved;
    }

    private void insertCase(long versionId, FixtureCase fixture, String actor) {
        jdbc.update("INSERT INTO tpip_fixture_case(suite_version_id,case_code,case_name,case_order,execution_mode,direction,source_document,expected_document,expected_success,expected_diagnostic_code,assertion_document,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                versionId, fixture.caseCode(), fixture.caseName(), fixture.caseOrder(), fixture.executionMode().name(), fixture.direction().name(),
                fixture.sourceDocument(), fixture.expectedDocument(), fixture.expectedSuccess(),
                fixture.expectedDiagnosticCode(), fixture.assertionDocument(), actor);
    }

    private List<FixtureCase> cases(long versionId) {
        return jdbc.query("SELECT id,suite_version_id,case_code,case_name,case_order,execution_mode,direction,source_document,expected_document,expected_success,expected_diagnostic_code,assertion_document,created_at FROM tpip_fixture_case WHERE suite_version_id=? ORDER BY case_order,id",
                (r,n) -> new FixtureCase(r.getLong("id"), r.getLong("suite_version_id"), r.getString("case_code"),
                        r.getString("case_name"), r.getInt("case_order"), FixtureExecutionMode.valueOf(r.getString("execution_mode")),
                        MappingAssetDirection.valueOf(r.getString("direction")),
                        r.getString("source_document"), r.getString("expected_document"), r.getBoolean("expected_success"),
                        r.getString("expected_diagnostic_code"), r.getString("assertion_document"),
                        r.getTimestamp("created_at").toInstant()), versionId);
    }

    private FixtureSuite suite(ResultSet r, int row) throws SQLException {
        return new FixtureSuite(r.getLong("id"), r.getLong("binding_id"), AssetCode.of(r.getString("suite_code")),
                r.getString("suite_name"), r.getString("description"), FixtureSuiteStatus.valueOf(r.getString("status")),
                r.getLong("row_version"), r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    }

    private FixtureSuiteVersion version(ResultSet r, List<FixtureCase> cases) throws SQLException {
        Timestamp published = r.getTimestamp("published_at");
        return new FixtureSuiteVersion(r.getLong("id"), r.getLong("suite_id"), r.getInt("version_no"),
                r.getString("content_checksum"), FixtureSuiteVersionStatus.valueOf(r.getString("lifecycle_status")),
                published == null ? null : published.toInstant(), r.getTimestamp("created_at").toInstant(), cases);
    }

    private void audit(String eventType, String code, String version, String actor) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,asset_version,event_summary) VALUES(UUID(),?,'USER',?,'FIXTURE_SUITE',?,?,?)",
                eventType, actor, code, version, eventType.replace('_', ' '));
    }
}
