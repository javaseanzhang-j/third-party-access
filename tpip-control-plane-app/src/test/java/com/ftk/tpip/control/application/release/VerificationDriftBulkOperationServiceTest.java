package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.*;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class VerificationDriftBulkOperationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void previewsAssignmentWithoutMutationAndReplaysByIdempotencyKey() {
        Fixture fixture = new Fixture();

        var preview = fixture.service.assign("assign-1", 23, true, "owner-b", "rebalance",
                List.of(item(1, 0), item(2, 1)), "operator-a");
        var replay = fixture.service.assign("assign-1", 23, true, "owner-b", "rebalance",
                List.of(item(1, 0), item(2, 1)), "operator-a");

        assertEquals(VerificationDriftBulkOperationStatus.PREVIEWED, preview.status());
        assertEquals(2, preview.eligibleCount());
        assertEquals(0, preview.appliedCount());
        assertNull(fixture.reviews.get(1L).assigneeCode());
        assertTrue(replay.idempotentReplay());
        assertThrows(IllegalArgumentException.class, () -> fixture.service.assign("assign-1", 23, true,
                "another-owner", "rebalance", List.of(item(1, 0), item(2, 1)), "operator-a"));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.assign("assign-1", 23, true,
                "owner-b", "rebalance", List.of(item(1, 0), item(2, 1)), "operator-b"));
    }

    @Test
    void rejectsWholeBatchWhenOneTargetIsNotEligible() {
        Fixture fixture = new Fixture();

        var result = fixture.service.acknowledge("ack-1", 23, false, "investigating",
                List.of(item(1, 0), item(2, 1)), "operator-a");

        assertEquals(VerificationDriftBulkOperationStatus.REJECTED, result.status());
        assertEquals(0, result.appliedCount());
        assertEquals(VerificationDriftReviewStatus.OPEN, fixture.reviews.get(1L).status());
        assertEquals("INVALID_REVIEW_STATUS", result.items().get(1).reasonCode());
    }

    @Test
    void appliesAssignmentAndDismissalWithBeforeAfterEvidence() {
        Fixture fixture = new Fixture();
        var assigned = fixture.service.assign("assign-2", 23, false, "owner-b", "rebalance",
                List.of(item(1, 0)), "operator-a");
        var dismissed = fixture.service.dispose("dismiss-1", 23, VerificationDriftReviewStatus.DISMISSED,
                false, "expected provider change", List.of(item(2, 1)), "operator-a");

        assertEquals(VerificationDriftBulkOperationStatus.APPLIED, assigned.status());
        assertEquals("owner-b", fixture.reviews.get(1L).assigneeCode());
        assertEquals(1, assigned.items().getFirst().resultingRowVersion());
        assertEquals(VerificationDriftReviewStatus.ACKNOWLEDGED,
                dismissed.items().getFirst().previousStatus());
        assertEquals(VerificationDriftReviewStatus.DISMISSED,
                dismissed.items().getFirst().targetStatus());
        assertEquals(VerificationDriftReviewStatus.DISMISSED, fixture.reviews.get(2L).status());
        assertEquals(dismissed.commandKey(), fixture.service.get("dismiss-1").result().commandKey());
        assertEquals("operator-a", fixture.service.get("dismiss-1").actorCode());
    }

    @Test
    void appliesAcceptanceAndRecordsSuccessorBaseline() {
        Fixture fixture = new Fixture();
        var result = fixture.service.dispose("accept-1", 23, VerificationDriftReviewStatus.ACCEPTED,
                false, "verified expected evolution", List.of(item(2, 1)), "operator-a");

        assertEquals(VerificationDriftBulkOperationStatus.APPLIED, result.status());
        assertEquals(902L, result.items().getFirst().successorBaselineId());
        assertEquals(VerificationDriftReviewStatus.ACCEPTED, fixture.reviews.get(2L).status());
    }

    @Test
    void rejectsCrossWorkspaceSelection() {
        Fixture fixture = new Fixture();
        var result = fixture.service.acknowledge("ack-cross", 24, true, "inspect",
                List.of(item(1, 0)), "operator-a");
        assertEquals(0, result.eligibleCount());
        assertEquals("WORKSPACE_MISMATCH", result.items().getFirst().reasonCode());
    }

    private static VerificationDriftBulkOperationService.BulkItem item(long id, long version) {
        return new VerificationDriftBulkOperationService.BulkItem(id, version);
    }

    private static final class Fixture {
        final Map<Long, VerificationDriftReview> reviews = new HashMap<>();
        final Map<String, VerificationDriftBulkOperation> operations = new HashMap<>();
        final VerificationDriftBulkOperationService service;

        Fixture() {
            reviews.put(1L, open(1));
            reviews.put(2L, acknowledged(2, 1));
            FakeDecisions decisions = new FakeDecisions(reviews);
            VerificationBaselineRepository baselineRepository = (VerificationBaselineRepository) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {VerificationBaselineRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "assignReview" -> assign((long) args[0], (long) args[1], (String) args[2],
                                (String) args[3], (String) args[4], (Instant) args[5]);
                        case "toString" -> "BulkReviewRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            VerificationDriftBulkOperationRepository operationRepository = new VerificationDriftBulkOperationRepository() {
                @Override public Optional<VerificationDriftBulkOperation> findByCommandKey(String key) {
                    return Optional.ofNullable(operations.get(key));
                }
                @Override public VerificationDriftBulkOperation save(VerificationDriftBulkOperation value) {
                    operations.put(value.commandKey(), value); return value;
                }
            };
            VerificationDriftWorkbenchRepository workbench = workbench();
            service = new VerificationDriftBulkOperationService(decisions, baselineRepository,
                    operationRepository, workbench, new ObjectMapper().findAndRegisterModules());
        }

        private VerificationDriftReview assign(long id, long version, String assignee, String note,
                String actor, Instant at) {
            VerificationDriftReview before = reviews.get(id);
            if (before.rowVersion() != version) throw new IllegalArgumentException("stale");
            VerificationDriftReview after = copy(before, before.status(), version + 1, assignee, actor, at, note,
                    before.acknowledgedBy(), before.acknowledgedAt(), before.acknowledgmentNote(),
                    before.resolvedBy(), before.resolvedAt(), before.resolutionReason(), before.successorBaselineId());
            reviews.put(id, after); return after;
        }

        private VerificationDriftWorkbenchRepository workbench() {
            return (VerificationDriftWorkbenchRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {VerificationDriftWorkbenchRepository.class}, (proxy, method, args) -> {
                        if (method.getName().equals("findReport")) {
                            long id = (long) args[0];
                            if (!reviews.containsKey(id)) return Optional.empty();
                            VerificationDriftReview review = reviews.get(id);
                            return Optional.of(new VerificationDriftWorkbenchRepository.ReportRow(id, 10, 23, 6,
                                    100 + id, 1, 1, review.status(), review.rowVersion(), review.successorBaselineId(),
                                    NOW, NOW));
                        }
                        if (method.getName().equals("toString")) return "Workbench";
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class FakeDecisions extends VerificationBaselineApplicationService {
        private final Map<Long, VerificationDriftReview> reviews;
        FakeDecisions(Map<Long, VerificationDriftReview> reviews) { super(null, null, null, null, null); this.reviews = reviews; }
        @Override public VerificationDriftReview review(long id) {
            VerificationDriftReview value = reviews.get(id);
            if (value == null) throw new IllegalArgumentException("VerificationDriftReport does not exist: " + id);
            return value;
        }
        @Override public VerificationDriftReview previewAcknowledge(long id, long version) {
            var value = review(id); version(value, version);
            if (value.status() != VerificationDriftReviewStatus.OPEN)
                throw new IllegalArgumentException("Only OPEN drift can be acknowledged");
            return value;
        }
        @Override public VerificationDriftReview previewDismiss(long id, long version) {
            var value = review(id); version(value, version);
            if (value.status() != VerificationDriftReviewStatus.ACKNOWLEDGED)
                throw new IllegalArgumentException("Drift must be acknowledged before dismissal");
            return value;
        }
        @Override public VerificationDriftReview previewAccept(long id, long version) {
            return previewDismiss(id, version);
        }
        @Override public VerificationDriftReview acknowledge(long id, long version, String note, String actor) {
            VerificationDriftReview before = previewAcknowledge(id, version);
            VerificationDriftReview after = copy(before, VerificationDriftReviewStatus.ACKNOWLEDGED, version + 1,
                    before.assigneeCode(), before.assignedBy(), before.assignedAt(), before.assignmentNote(),
                    actor, NOW, note, null, null, null, null);
            reviews.put(id, after); return after;
        }
        @Override public VerificationDriftReview dismiss(long id, long version, String reason, String actor) {
            VerificationDriftReview before = previewDismiss(id, version);
            VerificationDriftReview after = copy(before, VerificationDriftReviewStatus.DISMISSED, version + 1,
                    before.assigneeCode(), before.assignedBy(), before.assignedAt(), before.assignmentNote(),
                    before.acknowledgedBy(), before.acknowledgedAt(), before.acknowledgmentNote(),
                    actor, NOW, reason, null);
            reviews.put(id, after); return after;
        }
        @Override public DriftAcceptance accept(long id, long version, String reason, String actor) {
            VerificationDriftReview before = previewAccept(id, version);
            long successorId = 900 + id;
            VerificationDriftReview after = copy(before, VerificationDriftReviewStatus.ACCEPTED, version + 1,
                    before.assigneeCode(), before.assignedBy(), before.assignedAt(), before.assignmentNote(),
                    before.acknowledgedBy(), before.acknowledgedAt(), before.acknowledgmentNote(),
                    actor, NOW, reason, successorId);
            reviews.put(id, after);
            VerificationBaseline successor = new VerificationBaseline(successorId, 23, 6, 100 + id,
                    "a".repeat(64), "{}", 10L, id, NOW);
            return new DriftAcceptance(after, successor);
        }
        private static void version(VerificationDriftReview value, long version) {
            if (value.rowVersion() != version) throw new IllegalArgumentException("Drift review rowVersion is stale");
        }
    }

    private static VerificationDriftReview open(long id) {
        return new VerificationDriftReview(id, VerificationDriftReviewStatus.OPEN, 0,
                null, null, null, null, null, null, null, null, null, null, null, NOW, NOW);
    }
    private static VerificationDriftReview acknowledged(long id, long version) {
        return new VerificationDriftReview(id, VerificationDriftReviewStatus.ACKNOWLEDGED, version,
                null, null, null, null, "operator", NOW, "investigating", null, null, null, null, NOW, NOW);
    }
    private static VerificationDriftReview copy(VerificationDriftReview before,
            VerificationDriftReviewStatus status, long version, String assignee, String assignedBy,
            Instant assignedAt, String assignmentNote, String acknowledgedBy, Instant acknowledgedAt,
            String acknowledgmentNote, String resolvedBy, Instant resolvedAt, String resolutionReason,
            Long successor) {
        return new VerificationDriftReview(before.driftReportId(), status, version, assignee, assignedBy, assignedAt,
                assignmentNote, acknowledgedBy, acknowledgedAt, acknowledgmentNote, resolvedBy, resolvedAt,
                resolutionReason, successor, before.createdAt(), NOW);
    }
}
