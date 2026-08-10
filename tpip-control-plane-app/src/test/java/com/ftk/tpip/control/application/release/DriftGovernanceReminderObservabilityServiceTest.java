package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.*;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DriftGovernanceReminderObservabilityServiceTest {
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);

    @Test
    void comparesReplacementMembersWithoutReadingPayloads() {
        var batches = new Batches();
        batches.values.put(1L, batch(1, DriftGovernanceReminderBatchStatus.CANCELLED, null, 2L, null));
        batches.values.put(2L, batch(2, DriftGovernanceReminderBatchStatus.DRAFT, 1L, null, null));
        batches.members.put(1L, List.of(member(1, 10, A), member(1, 20, A), member(1, 30, A)));
        batches.members.put(2L, List.of(member(2, 10, A), member(2, 30, B), member(2, 40, A)));
        var service = new DriftGovernanceReminderObservabilityService(batches, new Observability());

        var result = service.diff(2);

        assertEquals(1, result.addedCount());
        assertEquals(1, result.removedCount());
        assertEquals(1, result.unchangedCount());
        assertEquals(1, result.modifiedCount());
        assertEquals(List.of(10L, 20L, 30L, 40L),
                result.members().stream().map(value -> value.executionId()).toList());
    }

    @Test
    void calculatesSuccessRateFromTerminalChannelDeliveries() {
        var service = new DriftGovernanceReminderObservabilityService(new Batches(), new Observability());

        var result = service.metrics(23);

        assertEquals("75.00", result.terminalDeliverySuccessRate().toPlainString());
        assertEquals(5, result.dueUnbatchedExecutions());
    }

    @Test
    void reportsDraftAsNotSubmittedWithoutLookingUpOutbox() {
        var batches = new Batches();
        batches.values.put(2L, batch(2, DriftGovernanceReminderBatchStatus.DRAFT, null, null, null));
        var service = new DriftGovernanceReminderObservabilityService(batches, new Observability());

        var result = service.delivery(2);

        assertFalse(result.submitted());
        assertNull(result.overview());
    }

    @Test
    void returnsImmutableBatchAuditTimelineWithCurrentVersionContext() {
        var batches = new Batches();
        batches.values.put(2L, batch(2, DriftGovernanceReminderBatchStatus.DRAFT, null, null, null));
        var service = new DriftGovernanceReminderObservabilityService(batches, new Observability());

        var result = service.timeline(2);

        assertEquals(2, result.batchId());
        assertEquals("batch-2", result.batchCode());
        assertEquals(1, result.events().size());
        assertEquals("operator", result.events().getFirst().actorCode());
    }

    private static DriftGovernanceReminderBatch batch(long id, DriftGovernanceReminderBatchStatus status,
            Long replaces, Long replacedBy, Long outbox) {
        boolean cancelled = status == DriftGovernanceReminderBatchStatus.CANCELLED;
        return new DriftGovernanceReminderBatch(id, "batch-" + id, 23, A, "local", "owner",
                DriftGovernanceReminderBatchSource.MANUAL, status, 1, "{}", A, 0, outbox, replaces, replacedBy,
                "creator", Instant.now(), null, null, cancelled ? "replaced" : null,
                cancelled ? "operator" : null, cancelled ? Instant.now() : null, null, null);
    }
    private static DriftGovernanceReminderBatchMember member(long batch, long execution, String checksum) {
        return new DriftGovernanceReminderBatchMember(batch, execution, 1, checksum);
    }

    private static final class Batches implements DriftGovernanceReminderBatchRepository {
        private final Map<Long, DriftGovernanceReminderBatch> values = new HashMap<>();
        private final Map<Long, List<DriftGovernanceReminderBatchMember>> members = new HashMap<>();
        @Override public Optional<DriftGovernanceReminderBatch> findById(long id){return Optional.ofNullable(values.get(id));}
        @Override public List<DriftGovernanceReminderBatchMember> findMembers(long id){return members.getOrDefault(id,List.of());}
        @Override public List<DriftGovernanceReminderBatch> findByWorkspaceId(long id){return List.copyOf(values.values());}
        @Override public DriftGovernanceReminderBatch create(DriftGovernanceReminderBatch b,List<DriftGovernanceReminderBatchMember>m,String a){throw unsupported();}
        @Override public DriftGovernanceReminderBatch approve(long a,long b,String c,Instant d){throw unsupported();}
        @Override public DriftGovernanceReminderBatch lockForUpdate(long id){throw unsupported();}
        @Override public DriftGovernanceReminderBatch cancel(long a,long b,String c,String d,Instant e,Long f){throw unsupported();}
        @Override public DriftGovernanceReminderBatch linkReplacement(long a,long b,long c,String d){throw unsupported();}
        @Override public DriftGovernanceReminderBatch markDispatched(long a,long b,long c,List<ReminderProgress>d,String e,Instant f){throw unsupported();}
        private static UnsupportedOperationException unsupported(){return new UnsupportedOperationException();}
    }

    private static final class Observability implements DriftGovernanceReminderObservabilityRepository {
        @Override public Optional<DeliveryOverview> findDeliveryOverview(long id){throw new AssertionError("not expected");}
        @Override public List<BatchAuditEvent> findBatchTimeline(String code,int limit){
            return List.of(new BatchAuditEvent("event-1","DRIFT_GOVERNANCE_REMINDER_BATCH_CREATED",
                    "operator","created",0L,"DRAFT",null,null,null,null,Instant.now()));
        }
        @Override public MetricsSnapshot summarize(long workspace,Instant now){
            return new MetricsSnapshot(7,1,1,4,1,5,2,Instant.parse("2026-08-09T00:00:00Z"),
                    1,2,1,0,2,1,3,1);
        }
    }
}
