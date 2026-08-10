package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriftGovernanceReminderBatchApplicationService {
    private static final String EVENT_TYPE = "TPIP_VERIFICATION_DRIFT_GOVERNANCE_REMINDER";
    private static final String AGGREGATE_TYPE = "DRIFT_GOVERNANCE_REMINDER_BATCH";
    private final DriftGovernanceReminderBatchRepository batches;
    private final DriftGovernanceExecutionRepository executions;
    private final NotificationOutboxRepository outbox;
    private final ObjectMapper json;
    private final CanonicalJsonService canonical;

    public DriftGovernanceReminderBatchApplicationService(DriftGovernanceReminderBatchRepository batches,
            DriftGovernanceExecutionRepository executions, NotificationOutboxRepository outbox,
            ObjectMapper json, CanonicalJsonService canonical) {
        this.batches = batches; this.executions = executions; this.outbox = outbox;
        this.json = json; this.canonical = canonical;
    }

    @Transactional
    public BatchDetail create(long workspaceId, String environmentCode, List<Long> executionIds, String actor) {
        return createBatch(workspaceId, environmentCode, executionIds, actor,
                DriftGovernanceReminderBatchSource.MANUAL, null, Instant.now());
    }

    @Transactional
    public BatchDetail createAutomated(long workspaceId, String environmentCode, List<Long> executionIds,
            String actor) {
        return createBatch(workspaceId, environmentCode, executionIds, actor,
                DriftGovernanceReminderBatchSource.AUTOMATION, null, Instant.now());
    }

    private BatchDetail createBatch(long workspaceId, String environmentCode, List<Long> executionIds, String actor,
            DriftGovernanceReminderBatchSource source, Long replacesBatchId, Instant now) {
        String operator = required(actor, "X-Operator", 100);
        String environment = environment(environmentCode);
        List<Long> ids = ids(executionIds);
        List<DriftGovernanceExecution> values = ids.stream().map(id -> executions.findById(id).orElseThrow(() ->
                new IllegalArgumentException("DriftGovernanceExecution does not exist: " + id))).toList();
        validate(workspaceId, values, now);
        String aggregationKey = values.getFirst().aggregationKey();
        String owner = values.getFirst().ownerCode();
        String code = "drift-reminder-" + UUID.randomUUID();
        var membersNode = json.createArrayNode();
        List<DriftGovernanceReminderBatchMember> members = values.stream().map(value -> {
            int reminderNo = value.reminderCount() + 1;
            membersNode.add(json.createObjectNode().put("executionId", value.id())
                    .put("driftReportId", value.driftReportId()).put("reminderNo", reminderNo)
                    .put("evaluationChecksum", value.evaluationChecksum()));
            return new DriftGovernanceReminderBatchMember(0, value.id(), reminderNo, value.evaluationChecksum());
        }).toList();
        var payload = json.createObjectNode().put("batchCode", code).put("workspaceId", workspaceId)
                .put("aggregationKey", aggregationKey).put("ownerCode", owner)
                .put("environmentCode", environment).put("creationSource", source.name())
                .put("memberCount", members.size());
        if (replacesBatchId != null) payload.put("replacesBatchId", replacesBatchId);
        payload.set("members", membersNode);
        String document = canonical.canonicalString(payload);
        var batch = new DriftGovernanceReminderBatch(null, code, workspaceId, aggregationKey, environment, owner,
                source, DriftGovernanceReminderBatchStatus.DRAFT, members.size(), document, canonical.sha256(document),
                0, null, replacesBatchId, null, operator, null, null, null, null, null, null, null, null);
        var saved = batches.create(batch, members, operator);
        return new BatchDetail(saved, batches.findMembers(saved.id()));
    }

    @Transactional
    public BatchDetail approve(long id, long rowVersion, String actor) {
        var saved = batches.approve(id, rowVersion, required(actor, "X-Operator", 100), Instant.now());
        return new BatchDetail(saved, batches.findMembers(id));
    }

    @Transactional
    public BatchDetail cancel(long id, long rowVersion, String reason, String actor) {
        String explanation = required(reason, "reason", 500);
        var saved = batches.cancel(id, rowVersion, explanation, required(actor, "X-Operator", 100),
                Instant.now(), null);
        return new BatchDetail(saved, batches.findMembers(id));
    }

    @Transactional
    public ReplacementResult replace(long id, long rowVersion, String reason, String environmentCode,
            List<Long> executionIds, String actor) {
        String operator = required(actor, "X-Operator", 100);
        String explanation = required(reason, "reason", 500);
        var original = batches.lockForUpdate(id);
        if (original.rowVersion() != rowVersion || (original.status() != DriftGovernanceReminderBatchStatus.DRAFT
                && original.status() != DriftGovernanceReminderBatchStatus.APPROVED))
            throw new IllegalArgumentException("Only the current DRAFT or APPROVED reminder batch can be replaced");
        List<Long> replacementIds = ids(executionIds);
        List<DriftGovernanceExecution> values = replacementIds.stream().map(executionId ->
                executions.findById(executionId).orElseThrow(() -> new IllegalArgumentException(
                        "DriftGovernanceExecution does not exist: " + executionId))).toList();
        validate(original.workspaceId(), values, Instant.now());
        if (!original.aggregationKey().equals(values.getFirst().aggregationKey())
                || !original.ownerCode().equals(values.getFirst().ownerCode()))
            throw new IllegalArgumentException("Replacement must preserve aggregation key and owner");
        var cancelled = batches.cancel(id, rowVersion, explanation, operator, Instant.now(), null);
        var replacement = createBatch(original.workspaceId(), environmentCode, replacementIds, operator,
                DriftGovernanceReminderBatchSource.MANUAL, id, Instant.now());
        var linked = batches.linkReplacement(id, cancelled.rowVersion(), replacement.batch().id(), operator);
        return new ReplacementResult(new BatchDetail(linked, batches.findMembers(id)), replacement);
    }

    @Transactional
    public BatchDetail dispatch(long id, long rowVersion, String actor) {
        String operator = required(actor, "X-Operator", 100);
        var batch = batches.lockForUpdate(id);
        if (batch.status() == DriftGovernanceReminderBatchStatus.DISPATCHED)
            return new BatchDetail(batch, batches.findMembers(id));
        if (batch.status() != DriftGovernanceReminderBatchStatus.APPROVED || batch.rowVersion() != rowVersion)
            throw new IllegalArgumentException("Only the current APPROVED reminder batch can be dispatched");
        List<DriftGovernanceReminderBatchMember> members = batches.findMembers(id);
        Instant now = Instant.now();
        List<DriftGovernanceReminderBatchRepository.ReminderProgress> progress = members.stream().map(member -> {
            var execution = executions.findById(member.executionId()).orElseThrow(() ->
                    new IllegalArgumentException("Governance execution does not exist: " + member.executionId()));
            if (execution.status() != DriftGovernanceExecutionStatus.READY
                    || execution.reminderCount() + 1 != member.reminderNo()
                    || !execution.evaluationChecksum().equals(member.evaluationChecksum())
                    || now.isBefore(execution.nextReminderAt()))
                throw new IllegalArgumentException("Governance execution is no longer dispatchable");
            var status = member.reminderNo() >= execution.maximumReminders()
                    ? DriftGovernanceExecutionStatus.EXHAUSTED : DriftGovernanceExecutionStatus.READY;
            return new DriftGovernanceReminderBatchRepository.ReminderProgress(execution.id(),
                    execution.reminderCount(), member.reminderNo(), status,
                    now.plusSeconds(execution.reminderIntervalSeconds()));
        }).toList();
        var event = outbox.enqueue(new NotificationOutboxMessage(null, EVENT_TYPE, AGGREGATE_TYPE,
                batch.batchCode(), batch.environmentCode(), batch.payloadDocument(), now, null));
        if (event.id() == null) throw new IllegalStateException("Notification outbox did not return an id");
        var saved = batches.markDispatched(id, rowVersion, event.id(), progress, operator, now);
        return new BatchDetail(saved, members);
    }

    @Transactional(readOnly = true)
    public BatchDetail get(long id) {
        var batch = batches.findById(id).orElseThrow(() ->
                new IllegalArgumentException("DriftGovernanceReminderBatch does not exist: " + id));
        return new BatchDetail(batch, batches.findMembers(id));
    }
    @Transactional(readOnly = true)
    public List<DriftGovernanceReminderBatch> list(long workspaceId) {
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        return batches.findByWorkspaceId(workspaceId);
    }

    private static void validate(long workspaceId, List<DriftGovernanceExecution> values, Instant now) {
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        String aggregation = values.getFirst().aggregationKey();
        String owner = values.getFirst().ownerCode();
        for (var value : values) {
            if (value.workspaceId() != workspaceId || value.status() != DriftGovernanceExecutionStatus.READY
                    || value.reminderCount() >= value.maximumReminders() || now.isBefore(value.nextReminderAt())
                    || !aggregation.equals(value.aggregationKey()) || !owner.equals(value.ownerCode()))
                throw new IllegalArgumentException("Executions are not eligible for the same reminder batch");
        }
    }
    private static List<Long> ids(List<Long> values) {
        if (values == null || values.isEmpty() || values.size() > 100
                || values.stream().anyMatch(value -> value == null || value <= 0)
                || values.stream().distinct().count() != values.size())
            throw new IllegalArgumentException("executionIds must contain 1 to 100 distinct positive ids");
        return List.copyOf(values);
    }
    private static String environment(String value) {
        String result = required(value, "environmentCode", 32);
        if (!result.matches("[a-z][a-z0-9_-]{0,31}"))
            throw new IllegalArgumentException("environmentCode is invalid");
        return result;
    }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(field + " is invalid");
        return value.trim();
    }
    public record BatchDetail(DriftGovernanceReminderBatch batch,
            List<DriftGovernanceReminderBatchMember> members) {}
    public record ReplacementResult(BatchDetail cancelled, BatchDetail replacement) {}
}
