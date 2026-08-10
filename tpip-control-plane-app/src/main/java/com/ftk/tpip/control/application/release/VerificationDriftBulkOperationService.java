package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.VerificationBaselineRepository;
import com.ftk.tpip.release.domain.repository.VerificationDriftBulkOperationRepository;
import com.ftk.tpip.release.domain.repository.VerificationDriftWorkbenchRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationDriftBulkOperationService {
    private final VerificationBaselineApplicationService decisions;
    private final VerificationBaselineRepository reviews;
    private final VerificationDriftBulkOperationRepository operations;
    private final VerificationDriftWorkbenchRepository workbench;
    private final ObjectMapper json;

    public VerificationDriftBulkOperationService(VerificationBaselineApplicationService decisions,
            VerificationBaselineRepository reviews, VerificationDriftBulkOperationRepository operations,
            VerificationDriftWorkbenchRepository workbench, ObjectMapper json) {
        this.decisions = decisions; this.reviews = reviews; this.operations = operations;
        this.workbench = workbench; this.json = json;
    }

    @Transactional
    public BulkResult assign(String commandKey, long workspaceId, boolean dryRun, String assigneeCode, String reason,
            List<BulkItem> items, String actor) {
        return execute(commandKey, workspaceId, VerificationDriftBulkOperationType.ASSIGN, dryRun,
                text(assigneeCode, 100, "assigneeCode"), reason, items, actor);
    }

    @Transactional
    public BulkResult acknowledge(String commandKey, long workspaceId, boolean dryRun, String reason,
            List<BulkItem> items, String actor) {
        return execute(commandKey, workspaceId, VerificationDriftBulkOperationType.ACKNOWLEDGE, dryRun,
                null, reason, items, actor);
    }

    @Transactional
    public BulkResult dispose(String commandKey, long workspaceId, VerificationDriftReviewStatus resolution, boolean dryRun,
            String reason, List<BulkItem> items, String actor) {
        if (resolution != VerificationDriftReviewStatus.ACCEPTED
                && resolution != VerificationDriftReviewStatus.DISMISSED)
            throw new IllegalArgumentException("resolution must be ACCEPTED or DISMISSED");
        return execute(commandKey, workspaceId, resolution == VerificationDriftReviewStatus.ACCEPTED
                        ? VerificationDriftBulkOperationType.ACCEPT : VerificationDriftBulkOperationType.DISMISS,
                dryRun, null, reason, items, actor);
    }

    @Transactional(readOnly = true)
    public OperationEvidence get(String commandKey) {
        String key = text(commandKey, 100, "commandKey");
        return operations.findByCommandKey(key).map(value -> new OperationEvidence(value.commandKey(),
                        value.workspaceId(), value.operationType(), value.status(), value.dryRun(),
                        value.requestChecksum(), value.actorCode(), tree(value.requestDocument()),
                        read(value.resultDocument()), value.createdAt()))
                .orElseThrow(() -> new IllegalArgumentException("Bulk operation does not exist: " + key));
    }

    private BulkResult execute(String commandKey, long workspaceId, VerificationDriftBulkOperationType type, boolean dryRun,
            String assigneeCode, String reason, List<BulkItem> items, String actor) {
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        String key = text(commandKey, 100, "Idempotency-Key");
        String operator = text(actor, 100, "X-Operator");
        String note = text(reason, 1000, "reason");
        List<BulkItem> targets = targets(items);
        RequestEvidence request = new RequestEvidence(workspaceId, type, dryRun, assigneeCode, note, targets);
        String requestDocument = write(request);
        String checksum = sha256(requestDocument);
        var existing = operations.findByCommandKey(key);
        if (existing.isPresent()) {
            if (!existing.get().requestChecksum().equals(checksum)
                    || !existing.get().actorCode().equals(operator))
                throw new IllegalArgumentException("Idempotency key was already used for another request");
            BulkResult value = read(existing.get().resultDocument());
            return value.replayed();
        }

        List<ItemResult> inspected = targets.stream().map(item -> inspect(workspaceId, type, item)).toList();
        int eligible = (int) inspected.stream().filter(ItemResult::eligible).count();
        int rejected = inspected.size() - eligible;
        if (dryRun || rejected > 0) {
            VerificationDriftBulkOperationStatus status = dryRun
                    ? VerificationDriftBulkOperationStatus.PREVIEWED
                    : VerificationDriftBulkOperationStatus.REJECTED;
            BulkResult result = new BulkResult(key, workspaceId, type, status, dryRun, false, inspected.size(), eligible,
                    0, rejected, inspected, Instant.now());
            save(result, checksum, requestDocument, operator);
            return result;
        }

        List<ItemResult> applied = new ArrayList<>();
        Instant now = Instant.now();
        for (BulkItem item : targets) applied.add(apply(type, assigneeCode, note, item, operator, now));
        BulkResult result = new BulkResult(key, workspaceId, type, VerificationDriftBulkOperationStatus.APPLIED, false, false,
                applied.size(), applied.size(), applied.size(), 0, List.copyOf(applied), now);
        save(result, checksum, requestDocument, operator);
        return result;
    }

    private ItemResult inspect(long workspaceId, VerificationDriftBulkOperationType type, BulkItem item) {
        try {
            var row = workbench.findReport(item.reportId()).orElseThrow(() ->
                    new IllegalArgumentException("VerificationDriftReport does not exist: " + item.reportId()));
            if (row.workspaceId() != workspaceId)
                throw new IllegalArgumentException("Drift report belongs to another Workspace");
            VerificationDriftReview review = switch (type) {
                case ASSIGN -> assignable(item);
                case ACKNOWLEDGE -> decisions.previewAcknowledge(item.reportId(), item.rowVersion());
                case ACCEPT -> decisions.previewAccept(item.reportId(), item.rowVersion());
                case DISMISS -> decisions.previewDismiss(item.reportId(), item.rowVersion());
            };
            return item(review, true, "ELIGIBLE", null, target(type), null);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            VerificationDriftReview current = safeReview(item.reportId());
            return new ItemResult(item.reportId(), item.rowVersion(), current == null ? null : current.status(),
                    current == null ? null : current.rowVersion(), current == null ? null : current.assigneeCode(),
                    false, reasonCode(failure.getMessage()), safeMessage(failure.getMessage()), target(type),
                    null, null);
        }
    }

    private VerificationDriftReview assignable(BulkItem item) {
        VerificationDriftReview value = decisions.review(item.reportId());
        if (value.rowVersion() != item.rowVersion())
            throw new IllegalArgumentException("Drift review rowVersion is stale");
        if (value.status() != VerificationDriftReviewStatus.OPEN
                && value.status() != VerificationDriftReviewStatus.ACKNOWLEDGED)
            throw new IllegalArgumentException("Only actionable drift can be assigned");
        return value;
    }

    private ItemResult apply(VerificationDriftBulkOperationType type, String assigneeCode, String reason,
            BulkItem item, String actor, Instant now) {
        VerificationDriftReview before = decisions.review(item.reportId());
        VerificationDriftReview value;
        Long successor = null;
        switch (type) {
            case ASSIGN -> value = reviews.assignReview(item.reportId(), item.rowVersion(), assigneeCode,
                    reason, actor, now);
            case ACKNOWLEDGE -> value = decisions.acknowledge(item.reportId(), item.rowVersion(), reason, actor);
            case DISMISS -> value = decisions.dismiss(item.reportId(), item.rowVersion(), reason, actor);
            case ACCEPT -> {
                var accepted = decisions.accept(item.reportId(), item.rowVersion(), reason, actor);
                value = accepted.review(); successor = accepted.successorBaseline().id();
            }
            default -> throw new IllegalStateException("Unsupported bulk operation");
        }
        return new ItemResult(item.reportId(), item.rowVersion(), before.status(), before.rowVersion(),
                before.assigneeCode(),
                true, "APPLIED", null, value.status(), value.rowVersion(), successor);
    }

    private void save(BulkResult result, String checksum, String requestDocument, String actor) {
        operations.save(new VerificationDriftBulkOperation(result.commandKey(), result.workspaceId(), result.operationType(),
                result.dryRun(), checksum, result.status(), actor, result.itemCount(), result.eligibleCount(),
                result.appliedCount(), result.rejectedCount(), requestDocument, write(result), result.completedAt()));
    }

    private VerificationDriftReview safeReview(long reportId) {
        try { return decisions.review(reportId); }
        catch (IllegalArgumentException | IllegalStateException ignored) { return null; }
    }

    private static List<BulkItem> targets(List<BulkItem> items) {
        if (items == null || items.isEmpty() || items.size() > 100)
            throw new IllegalArgumentException("items must contain 1 to 100 entries");
        var ids = new HashSet<Long>();
        for (BulkItem item : items) {
            if (item == null || item.reportId() <= 0 || item.rowVersion() < 0)
                throw new IllegalArgumentException("items contain an invalid target");
            if (!ids.add(item.reportId())) throw new IllegalArgumentException("items contain duplicate reportId");
        }
        return List.copyOf(items);
    }

    private static ItemResult item(VerificationDriftReview value, boolean eligible, String code, String message,
            VerificationDriftReviewStatus target, Long successor) {
        return new ItemResult(value.driftReportId(), value.rowVersion(), value.status(), value.rowVersion(),
                value.assigneeCode(), eligible, code, message, target, null, successor);
    }
    private static VerificationDriftReviewStatus target(VerificationDriftBulkOperationType type) {
        return switch (type) {
            case ACKNOWLEDGE -> VerificationDriftReviewStatus.ACKNOWLEDGED;
            case ACCEPT -> VerificationDriftReviewStatus.ACCEPTED;
            case DISMISS -> VerificationDriftReviewStatus.DISMISSED;
            case ASSIGN -> null;
        };
    }
    private static String reasonCode(String message) {
        if (message == null) return "NOT_ELIGIBLE";
        if (message.contains("does not exist")) return "REPORT_NOT_FOUND";
        if (message.contains("another Workspace")) return "WORKSPACE_MISMATCH";
        if (message.contains("rowVersion") || message.contains("concurrently")) return "ROW_VERSION_MISMATCH";
        if (message.contains("OPEN") || message.contains("acknowledged") || message.contains("actionable"))
            return "INVALID_REVIEW_STATUS";
        if (message.contains("REGRESSION") || message.contains("compatible")) return "INCOMPATIBLE_REGRESSION";
        return "NOT_ELIGIBLE";
    }
    private static String safeMessage(String value) {
        if (value == null || value.isBlank()) return "Target is not eligible";
        return value.length() <= 300 ? value : value.substring(0, 300);
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("Bulk operation evidence cannot be serialized", failure); }
    }
    private BulkResult read(String value) {
        try { return json.readValue(value, BulkResult.class); }
        catch (Exception failure) { throw new IllegalStateException("Stored bulk operation evidence is invalid", failure); }
    }
    private com.fasterxml.jackson.databind.JsonNode tree(String value) {
        try { return json.readTree(value); }
        catch (Exception failure) { throw new IllegalStateException("Stored bulk operation request is invalid", failure); }
    }
    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException("SHA-256 is unavailable", failure); }
    }
    private static String text(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.trim().length() > maximum)
            throw new IllegalArgumentException(field + " must contain 1 to " + maximum + " characters");
        return value.trim();
    }

    private record RequestEvidence(long workspaceId, VerificationDriftBulkOperationType operationType, boolean dryRun,
            String assigneeCode, String reason, List<BulkItem> items) {}
    public record BulkItem(long reportId, long rowVersion) {}
    public record ItemResult(long reportId, long expectedRowVersion,
            VerificationDriftReviewStatus previousStatus, Long previousRowVersion, String assigneeCode,
            boolean eligible, String reasonCode, String reasonMessage, VerificationDriftReviewStatus targetStatus,
            Long resultingRowVersion, Long successorBaselineId) {}
    public record BulkResult(String commandKey, long workspaceId, VerificationDriftBulkOperationType operationType,
            VerificationDriftBulkOperationStatus status, boolean dryRun, boolean idempotentReplay,
            int itemCount, int eligibleCount, int appliedCount, int rejectedCount,
            List<ItemResult> items, Instant completedAt) {
        BulkResult replayed() { return new BulkResult(commandKey, workspaceId, operationType, status, dryRun, true,
                itemCount, eligibleCount, appliedCount, rejectedCount, items, completedAt); }
    }
    public record OperationEvidence(String commandKey, long workspaceId,
            VerificationDriftBulkOperationType operationType, VerificationDriftBulkOperationStatus status,
            boolean dryRun, String requestChecksum, String actorCode,
            com.fasterxml.jackson.databind.JsonNode request, BulkResult result, Instant createdAt) {}
}
