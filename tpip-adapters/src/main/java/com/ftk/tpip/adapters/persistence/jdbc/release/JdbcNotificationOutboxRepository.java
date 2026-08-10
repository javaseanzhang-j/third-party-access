package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.release.domain.model.NotificationOutboxMessage;
import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import com.ftk.tpip.release.domain.model.NotificationDeliveryTask;
import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptAggregate;
import com.ftk.tpip.release.domain.model.NotificationFailureClass;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import com.ftk.tpip.release.domain.model.NotificationRoutingFailure;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import com.ftk.tpip.release.domain.service.NotificationTemplateEngine;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcNotificationOutboxRepository implements NotificationOutboxRepository {
    private static final String COLUMNS = "d.id,d.outbox_id,d.channel_code,d.channel_version_id,d.provider_type,"
            + "d.endpoint_uri,d.endpoint_revision_id,d.authorization_secret_ref,d.provider_configuration,d.template_version_id,d.message_content_type,d.message_payload,o.event_type,o.aggregate_type,"
            + "o.aggregate_id,o.payload,d.delivery_status,d.attempt_count,d.available_at,d.claimed_by,"
            + "d.claimed_at,d.delivered_at,d.last_error,d.failure_class,d.last_retry_delay_ms,"
            + "d.dead_lettered_at,d.created_at,d.updated_at";
    private static final String FROM = " FROM tpip_notification_delivery d JOIN tpip_notification_outbox o "
            + "ON o.id=d.outbox_id ";
    private static final RowMapper<NotificationDeliveryTask> MAPPER = (result, row) -> new NotificationDeliveryTask(
            result.getLong("id"), result.getLong("outbox_id"), result.getString("channel_code"),
            result.getLong("channel_version_id"),
            NotificationProviderType.valueOf(result.getString("provider_type")),
            result.getString("endpoint_uri"), nullableLong(result, "endpoint_revision_id"), result.getString("authorization_secret_ref"),
            result.getString("provider_configuration"),
            nullableLong(result, "template_version_id"), result.getString("message_content_type"),
            result.getString("message_payload"),
            result.getString("event_type"), result.getString("aggregate_type"),
            result.getString("aggregate_id"), result.getString("payload"),
            NotificationDeliveryStatus.valueOf(result.getString("delivery_status")),
            result.getInt("attempt_count"), result.getTimestamp("available_at").toInstant(),
            result.getString("claimed_by"), instant(result.getTimestamp("claimed_at")),
            instant(result.getTimestamp("delivered_at")), result.getString("last_error"),
            nullableEnum(result.getString("failure_class")), nullableLong(result, "last_retry_delay_ms"),
            instant(result.getTimestamp("dead_lettered_at")),
            result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final NotificationTemplateEngine templates;
    public JdbcNotificationOutboxRepository(JdbcTemplate jdbc, ObjectMapper json,
            NotificationTemplateEngine templates) { this.jdbc = jdbc; this.json = json; this.templates = templates; }

    @Override
    public NotificationOutboxMessage enqueue(NotificationOutboxMessage message) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO tpip_notification_outbox("
                    + "event_type,aggregate_type,aggregate_id,environment_code,payload,available_at) VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, message.eventType());
            statement.setString(2, message.aggregateType());
            statement.setString(3, message.aggregateId());
            statement.setString(4, message.environmentCode());
            statement.setString(5, message.payload());
            statement.setTimestamp(6, Timestamp.from(message.availableAt()));
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return an outbox id");
        return new NotificationOutboxMessage(keys.getKey().longValue(), message.eventType(),
                message.aggregateType(), message.aggregateId(), message.environmentCode(), message.payload(),
                message.availableAt(), null);
    }

    @Override
    public List<NotificationDeliveryTask> claim(String workerId, Instant now, Instant expiredBefore, int batchSize) {
        List<Long> unrouted = jdbc.query("SELECT o.id FROM tpip_notification_outbox o WHERE "
                        + "o.delivery_status='PENDING' AND o.routing_status='UNROUTED' AND o.available_at<=? "
                        + "ORDER BY o.id LIMIT ? FOR UPDATE SKIP LOCKED",
                (result, row) -> result.getLong(1), Timestamp.from(now), batchSize);
        for (Long outboxId : unrouted) {
            NotificationOutboxMessage event = findOutbox(outboxId);
            java.util.LinkedHashMap<String, RouteTarget> targets = new java.util.LinkedHashMap<>();
            jdbc.query("SELECT rv.priority,c.channel_code,cv.id channel_version_id,cv.provider_type,"
                            + "cv.endpoint_uri,cv.endpoint_revision_id,cv.authorization_secret_ref,cv.configuration provider_configuration,cv.template_version_id,"
                            + "tv.content_type,tv.template_document,tv.variable_schema FROM tpip_notification_route r "
                            + "JOIN tpip_notification_route_version rv ON rv.id=r.current_version_id "
                            + "AND rv.lifecycle_status='PUBLISHED' "
                            + "JOIN JSON_TABLE(rv.event_types,'$[*]' COLUMNS(event_type VARCHAR(100) PATH '$')) et "
                            + "JOIN JSON_TABLE(rv.channel_version_ids,'$[*]' COLUMNS(channel_version_id BIGINT PATH '$')) rc "
                            + "JOIN tpip_notification_channel_version cv ON cv.id=rc.channel_version_id "
                            + "AND cv.lifecycle_status='PUBLISHED' "
                            + "JOIN tpip_notification_channel c ON c.id=cv.channel_id AND c.status='ACTIVE' "
                            + "LEFT JOIN tpip_notification_template_version tv ON tv.id=cv.template_version_id "
                            + "AND tv.lifecycle_status='PUBLISHED' "
                            + "LEFT JOIN tpip_notification_template t ON t.id=tv.template_id AND t.status='ACTIVE' "
                            + "AND t.environment_code=? "
                            + "WHERE r.status='ACTIVE' AND r.environment_code=? "
                            + "AND c.environment_code=? AND (et.event_type=? OR et.event_type='*') "
                            + "AND (cv.template_version_id IS NULL OR t.id IS NOT NULL) "
                            + "ORDER BY rv.priority,rv.id,c.channel_code,cv.version_no DESC",
                    result -> {
                        RouteTarget target = new RouteTarget(result.getString("channel_code"),
                                result.getLong("channel_version_id"), result.getString("provider_type"),
                                result.getString("endpoint_uri"), nullableLong(result,"endpoint_revision_id"), result.getString("authorization_secret_ref"),
                                result.getString("provider_configuration"),
                                nullableLong(result, "template_version_id"), result.getString("content_type"),
                                result.getString("template_document"), result.getString("variable_schema"));
                        targets.putIfAbsent(target.channelCode(), target);
                    }, event.environmentCode(), event.environmentCode(), event.environmentCode(), event.eventType());
            if (targets.isEmpty()) {
                jdbc.update("UPDATE tpip_notification_outbox SET routing_status='NO_MATCH',routing_attempted_at=?,routing_error=NULL WHERE id=?",
                        Timestamp.from(now), outboxId);
                continue;
            }
            List<MaterializedTarget> materialized;
            try {
                String context = context(event);
                materialized = targets.values().stream().map(target -> materialize(target, context)).toList();
            } catch (IllegalArgumentException exception) {
                jdbc.update("UPDATE tpip_notification_outbox SET routing_status='RENDER_FAILED',routing_attempted_at=?,routing_error=? WHERE id=?",
                        Timestamp.from(now), safeError(exception), outboxId);
                continue;
            }
            for (MaterializedTarget target : materialized) {
                jdbc.update("INSERT INTO tpip_notification_delivery(outbox_id,channel_code,channel_version_id,"
                                + "provider_type,endpoint_uri,endpoint_revision_id,authorization_secret_ref,template_version_id,"
                                + "provider_configuration,message_content_type,message_payload,available_at) "
                                + "SELECT id,?,?,?,?,?,?,?,?,?,?,available_at FROM tpip_notification_outbox WHERE id=?",
                        target.route().channelCode(), target.route().channelVersionId(), target.route().providerType(),
                        target.route().endpointUri(), target.route().endpointRevisionId(), target.route().authorizationSecretRef(),
                        target.route().templateVersionId(), target.route().providerConfiguration(),
                        target.route().contentType(), target.messagePayload(), outboxId);
            }
            jdbc.update("UPDATE tpip_notification_outbox SET routing_status='ROUTED',routing_attempted_at=?,routing_error=NULL WHERE id=?",
                    Timestamp.from(now), outboxId);
        }
        List<Long> ids = jdbc.query("SELECT id FROM tpip_notification_delivery WHERE ((delivery_status='PENDING' "
                        + "AND available_at<=?) OR (delivery_status='CLAIMED' AND claimed_at<?)) "
                        + "ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
                (result, row) -> result.getLong(1), Timestamp.from(now), Timestamp.from(expiredBefore), batchSize);
        for (Long id : ids) {
            jdbc.update("UPDATE tpip_notification_delivery SET delivery_status='CLAIMED',claimed_by=?,claimed_at=?,"
                    + "attempt_count=attempt_count+1,last_error=NULL WHERE id=?", workerId, Timestamp.from(now), id);
        }
        return ids.stream().map(id -> findById(id).orElseThrow()).toList();
    }

    @Override
    public Optional<NotificationDeliveryTask> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + FROM + "WHERE d.id=?", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public NotificationDeliveryTask markDelivered(long id, String workerId, Instant deliveredAt) {
        NotificationDeliveryTask current = findById(id).orElseThrow(
                () -> new IllegalArgumentException("notification task does not exist"));
        int updated = jdbc.update("UPDATE tpip_notification_delivery SET delivery_status='DELIVERED',delivered_at=?,"
                + "claimed_by=NULL,claimed_at=NULL,last_error=NULL,failure_class=NULL,last_retry_delay_ms=NULL,"
                + "dead_lettered_at=NULL WHERE id=? AND delivery_status='CLAIMED' "
                + "AND claimed_by=?", Timestamp.from(deliveredAt), id, workerId);
        if (updated == 0) throw new IllegalArgumentException("notification task is not claimed by this worker");
        recordAttempt(current, "SUCCESS", null, null, null, false, deliveredAt);
        NotificationDeliveryTask result = findById(id).orElseThrow();
        refreshOutbox(result.eventId(), deliveredAt);
        return result;
    }

    @Override
    public NotificationDeliveryTask markFailed(long id, String workerId, Instant availableAt,
            String error, NotificationFailureClass failureClass, long retryDelayMillis, boolean deadLetter,
            Instant failedAt) {
        NotificationDeliveryTask current = findById(id).orElseThrow(
                () -> new IllegalArgumentException("notification task does not exist"));
        if (current.status() != NotificationDeliveryStatus.CLAIMED || !workerId.equals(current.claimedBy())) {
            throw new IllegalArgumentException("notification task is not claimed by this worker");
        }
        int updated = jdbc.update("UPDATE tpip_notification_delivery SET delivery_status=?,available_at=?,claimed_by=NULL,"
                        + "claimed_at=NULL,last_error=?,failure_class=?,last_retry_delay_ms=?,dead_lettered_at=? "
                        + "WHERE id=? AND delivery_status='CLAIMED' AND claimed_by=?",
                deadLetter ? "DEAD_LETTER" : "PENDING", Timestamp.from(availableAt), error, failureClass.name(),
                retryDelayMillis, deadLetter ? Timestamp.from(failedAt) : null, id, workerId);
        if (updated == 0) throw new IllegalArgumentException("notification task claim is no longer owned by this worker");
        recordAttempt(current, "FAILURE", error, failureClass, retryDelayMillis, deadLetter, failedAt);
        NotificationDeliveryTask result = findById(id).orElseThrow();
        refreshOutbox(result.eventId(), availableAt);
        return result;
    }

    @Override
    public List<NotificationDeliveryTask> findByStatus(NotificationDeliveryStatus status, int limit) {
        return jdbc.query("SELECT " + COLUMNS + FROM + "WHERE d.delivery_status=? "
                + "ORDER BY d.id DESC LIMIT ?", MAPPER, status.name(), limit);
    }

    @Override
    public NotificationDeliveryTask replay(long id, Instant availableAt, String actor) {
        NotificationDeliveryTask current = findById(id).orElseThrow(
                () -> new IllegalArgumentException("notification delivery does not exist"));
        int updated = jdbc.update("UPDATE tpip_notification_delivery SET delivery_status='PENDING',attempt_count=0,"
                + "available_at=?,claimed_by=NULL,claimed_at=NULL,delivered_at=NULL,last_error=NULL,"
                + "failure_class=NULL,last_retry_delay_ms=NULL,dead_lettered_at=NULL "
                + "WHERE id=? AND delivery_status='DEAD_LETTER'", Timestamp.from(availableAt), id);
        if (updated == 0) throw new IllegalArgumentException("only DEAD_LETTER notification can be replayed");
        jdbc.update("UPDATE tpip_notification_outbox SET delivery_status='PENDING',delivered_at=NULL,last_error=NULL "
                + "WHERE id=?", current.eventId());
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,"
                + "asset_code,asset_version,event_summary) VALUES(UUID(),'NOTIFICATION_REPLAYED','USER',?,"
                + "'NOTIFICATION_DELIVERY',?,'1','Replayed dead-letter channel delivery')", actor, Long.toString(id));
        return findById(id).orElseThrow();
    }

    @Override
    public List<NotificationDeliveryTask> replayBatch(List<Long> ids, Instant availableAt, String actor) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("delivery ids must not be empty");
        List<NotificationDeliveryTask> replayed = ids.stream()
                .map(id -> replay(id, availableAt, actor)).toList();
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,"
                        + "asset_code,asset_version,event_summary) VALUES(UUID(),'NOTIFICATION_BATCH_REPLAYED',"
                        + "'USER',?,'NOTIFICATION_DELIVERY_BATCH',?,'1',?)",
                actor, Integer.toString(ids.size()), "Replayed " + ids.size() + " dead-letter deliveries");
        return replayed;
    }

    @Override
    public List<NotificationDeliveryAttemptAggregate> aggregateAttempts(Instant fromInclusive, Instant toExclusive,
            String environmentCode, String channelCode, NotificationProviderType providerType, Long endpointRevisionId) {
        StringBuilder sql = new StringBuilder("SELECT environment_code,provider_type,channel_code,endpoint_revision_id,COUNT(*) attempts,"
                + "SUM(outcome='SUCCESS') successes,SUM(outcome='FAILURE') failures,"
                + "SUM(terminal_failure=TRUE) dead_letters FROM tpip_notification_delivery_attempt "
                + "WHERE occurred_at>=? AND occurred_at<?");
        List<Object> arguments = new java.util.ArrayList<>();
        arguments.add(Timestamp.from(fromInclusive));
        arguments.add(Timestamp.from(toExclusive));
        if (environmentCode != null) { sql.append(" AND environment_code=?"); arguments.add(environmentCode); }
        if (channelCode != null) { sql.append(" AND channel_code=?"); arguments.add(channelCode); }
        if (providerType != null) { sql.append(" AND provider_type=?"); arguments.add(providerType.name()); }
        if (endpointRevisionId != null) {
            sql.append(" AND endpoint_revision_id=?"); arguments.add(endpointRevisionId);
        }
        sql.append(" GROUP BY environment_code,provider_type,channel_code,endpoint_revision_id "
                + "ORDER BY environment_code,provider_type,channel_code,endpoint_revision_id");
        return jdbc.query(sql.toString(), (result, row) -> new NotificationDeliveryAttemptAggregate(
                result.getString("environment_code"),
                NotificationProviderType.valueOf(result.getString("provider_type")),
                result.getString("channel_code"), nullableLong(result, "endpoint_revision_id"),
                result.getLong("attempts"), result.getLong("successes"), result.getLong("failures"),
                result.getLong("dead_letters")), arguments.toArray());
    }

    @Override
    public List<NotificationRoutingFailure> findRoutingFailures(int limit) {
        return jdbc.query("SELECT id,event_type,aggregate_type,aggregate_id,environment_code,payload,available_at,"
                        + "routing_status,routing_error,routing_attempted_at,created_at FROM tpip_notification_outbox WHERE routing_status IN ('NO_MATCH','RENDER_FAILED') "
                        + "ORDER BY routing_attempted_at,id LIMIT ?", ROUTING_FAILURE_MAPPER, limit);
    }

    @Override
    public NotificationRoutingFailure reroute(long eventId, String actor) {
        NotificationRoutingFailure current = jdbc.query("SELECT id,event_type,aggregate_type,aggregate_id,"
                        + "environment_code,payload,available_at,routing_status,routing_error,routing_attempted_at,created_at "
                        + "FROM tpip_notification_outbox WHERE id=? AND routing_status IN ('NO_MATCH','RENDER_FAILED')",
                ROUTING_FAILURE_MAPPER, eventId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("notification event is not a routing failure"));
        jdbc.update("UPDATE tpip_notification_outbox SET routing_status='UNROUTED',routing_attempted_at=NULL,routing_error=NULL "
                + "WHERE id=? AND routing_status IN ('NO_MATCH','RENDER_FAILED')", eventId);
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,"
                + "asset_version,event_summary) VALUES(UUID(),'NOTIFICATION_REROUTE_REQUESTED','USER',?,"
                + "'NOTIFICATION_OUTBOX',?,'1','Requested notification event reroute')", actor, Long.toString(eventId));
        return current;
    }

    @Override
    public long countRoutingFailures() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM tpip_notification_outbox WHERE routing_status IN ('NO_MATCH','RENDER_FAILED')",
                Long.class);
        return value == null ? 0 : value;
    }

    private void refreshOutbox(long outboxId, Instant changedAt) {
        Integer unfinished = jdbc.queryForObject("SELECT COUNT(*) FROM tpip_notification_delivery WHERE outbox_id=? "
                + "AND delivery_status IN ('PENDING','CLAIMED')", Integer.class, outboxId);
        Integer dead = jdbc.queryForObject("SELECT COUNT(*) FROM tpip_notification_delivery WHERE outbox_id=? "
                + "AND delivery_status='DEAD_LETTER'", Integer.class, outboxId);
        String aggregateStatus = unfinished != null && unfinished > 0 ? "PENDING"
                : dead != null && dead > 0 ? "DEAD_LETTER" : "DELIVERED";
        jdbc.update("UPDATE tpip_notification_outbox SET delivery_status=?,delivered_at=?,last_error=? WHERE id=?",
                aggregateStatus, "DELIVERED".equals(aggregateStatus) ? Timestamp.from(changedAt) : null,
                "DEAD_LETTER".equals(aggregateStatus) ? "CHANNEL_DELIVERY_DEAD_LETTER" : null, outboxId);
    }

    private NotificationOutboxMessage findOutbox(long id) {
        return jdbc.query("SELECT id,event_type,aggregate_type,aggregate_id,environment_code,payload,available_at,delivered_at "
                        + "FROM tpip_notification_outbox WHERE id=?",
                (result, row) -> new NotificationOutboxMessage(result.getLong("id"),
                        result.getString("event_type"), result.getString("aggregate_type"),
                        result.getString("aggregate_id"), result.getString("environment_code"), result.getString("payload"),
                        result.getTimestamp("available_at").toInstant(), instant(result.getTimestamp("delivered_at"))),
                id).stream().findFirst().orElseThrow();
    }

    private void recordAttempt(NotificationDeliveryTask task, String outcome, String error,
            NotificationFailureClass failureClass, Long retryDelayMillis, boolean terminal, Instant occurredAt) {
        jdbc.update("INSERT INTO tpip_notification_delivery_attempt(delivery_id,environment_code,attempt_no,provider_type,"
                        + "channel_code,endpoint_revision_id,outcome,error_code,failure_class,retry_delay_ms,"
                        + "terminal_failure,occurred_at) SELECT ?,environment_code,?,?,?,?,?,?,?,?,?,? "
                        + "FROM tpip_notification_outbox WHERE id=?",
                task.id(), task.attemptCount(), task.providerType().name(), task.channelCode(),
                task.endpointRevisionId(), outcome, error, failureClass == null ? null : failureClass.name(),
                retryDelayMillis, terminal, Timestamp.from(occurredAt), task.eventId());
    }

    private MaterializedTarget materialize(RouteTarget target, String context) {
        String payload = target.templateVersionId() == null ? null
                : templates.render(target.templateDocument(), target.variableSchema(), context);
        return new MaterializedTarget(target, payload);
    }

    private String context(NotificationOutboxMessage event) {
        try {
            ObjectNode context = json.createObjectNode();
            context.put("eventId", event.id()).put("eventType", event.eventType())
                    .put("aggregateType", event.aggregateType()).put("aggregateId", event.aggregateId())
                    .put("environmentCode", event.environmentCode());
            context.set("payload", json.readTree(event.payload()));
            return json.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("event payload is not valid JSON");
        }
    }

    private static String safeError(IllegalArgumentException exception) {
        String value = exception.getMessage() == null ? "TEMPLATE_RENDER_FAILED" : exception.getMessage();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private record RouteTarget(String channelCode, long channelVersionId, String providerType,
            String endpointUri, Long endpointRevisionId, String authorizationSecretRef, String providerConfiguration,
            Long templateVersionId, String contentType,
            String templateDocument, String variableSchema) {}
    private record MaterializedTarget(RouteTarget route, String messagePayload) {}

    private static final RowMapper<NotificationRoutingFailure> ROUTING_FAILURE_MAPPER = (result, row) ->
            new NotificationRoutingFailure(result.getLong("id"), result.getString("event_type"),
                    result.getString("aggregate_type"), result.getString("aggregate_id"),
                    result.getString("environment_code"), result.getString("payload"),
                    result.getTimestamp("available_at").toInstant(),
                    result.getString("routing_status"), result.getString("routing_error"),
                    instant(result.getTimestamp("routing_attempted_at")),
                    result.getTimestamp("created_at").toInstant());

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static Long nullableLong(java.sql.ResultSet result, String column) throws java.sql.SQLException {
        long value = result.getLong(column); return result.wasNull() ? null : value;
    }
    private static NotificationFailureClass nullableEnum(String value) {
        return value == null ? null : NotificationFailureClass.valueOf(value);
    }
}
