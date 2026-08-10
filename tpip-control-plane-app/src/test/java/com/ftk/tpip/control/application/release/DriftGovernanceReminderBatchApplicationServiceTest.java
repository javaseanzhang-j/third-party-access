package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DriftGovernanceReminderBatchApplicationServiceTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void requiresApprovalThenAtomicallyAdvancesBudgetAfterOutboxCreation() {
        var batches = new Batches(); var outbox = new Outbox(false);
        var service = service(batches, outbox);
        var draft = service.create(23, "local", List.of(1L), "creator");
        var approved = service.approve(draft.batch().id(), 0, "approver");

        var dispatched = service.dispatch(approved.batch().id(), 1, "dispatcher");

        assertEquals(DriftGovernanceReminderBatchStatus.DISPATCHED, dispatched.batch().status());
        assertNotNull(dispatched.batch().outboxId());
        assertEquals(1, batches.progress.getFirst().reminderCount());
        assertEquals(1, outbox.calls);
    }

    @Test
    void outboxFailureDoesNotAdvanceReminderBudget() {
        var batches = new Batches(); var outbox = new Outbox(true);
        var service = service(batches, outbox);
        var draft = service.create(23, "local", List.of(1L), "creator");
        service.approve(draft.batch().id(), 0, "approver");

        assertThrows(IllegalStateException.class, () -> service.dispatch(1, 1, "dispatcher"));
        assertTrue(batches.progress.isEmpty());
        assertEquals(DriftGovernanceReminderBatchStatus.APPROVED, batches.values.get(1L).status());
    }

    @Test
    void cancelledBatchCanBeAtomicallyReplacedWithLineage() {
        var batches = new Batches(); var service = service(batches, new Outbox(false));
        var original = service.create(23, "local", List.of(1L), "creator");

        var result = service.replace(original.batch().id(), 0, "change recipients", "local",
                List.of(1L), "operator");

        assertEquals(DriftGovernanceReminderBatchStatus.CANCELLED, result.cancelled().batch().status());
        assertEquals(result.replacement().batch().id(), result.cancelled().batch().replacedByBatchId());
        assertEquals(original.batch().id(), result.replacement().batch().replacesBatchId());
        assertEquals(DriftGovernanceReminderBatchStatus.DRAFT, result.replacement().batch().status());
    }

    private static DriftGovernanceReminderBatchApplicationService service(Batches batches, Outbox outbox) {
        DriftGovernanceExecution execution = new DriftGovernanceExecution(1L, 11, 23,
                "BUILT_IN_DEFAULT", null, null, HASH, "owner", DriftGovernanceExecutionStatus.READY,
                3, 3600, 0, Instant.now().minusSeconds(1), "{}", "{}", HASH, 0,
                "materializer", Instant.now(), Instant.now());
        DriftGovernanceExecutionRepository executions = new Executions(execution);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        return new DriftGovernanceReminderBatchApplicationService(batches, executions, outbox, json,
                new CanonicalJsonService(json));
    }

    private static final class Executions implements DriftGovernanceExecutionRepository {
        private final DriftGovernanceExecution value;
        private Executions(DriftGovernanceExecution value) { this.value = value; }
        @Override public Optional<DriftGovernanceExecution> findById(long id) { return Optional.of(value); }
        @Override public DriftGovernanceExecution materialize(DriftGovernanceExecution v,String a){throw new UnsupportedOperationException();}
        @Override public Optional<DriftGovernanceExecution> findByReportId(long id){return Optional.empty();}
        @Override public List<DriftGovernanceExecution> findByWorkspaceId(long id){return List.of(value);}
        @Override public List<DriftGovernanceExecution> findDueWithoutActiveBatch(Instant now,int limit){return List.of(value);}
        @Override public List<DriftGovernanceExecution> findDueWithoutActiveBatch(long workspaceId,Instant now,int limit){return List.of(value);}
    }

    private static final class Batches implements DriftGovernanceReminderBatchRepository {
        private final Map<Long, DriftGovernanceReminderBatch> values = new HashMap<>();
        private final Map<Long, List<DriftGovernanceReminderBatchMember>> memberValues = new HashMap<>();
        private long sequence = 1;
        private List<ReminderProgress> progress = List.of();
        @Override public DriftGovernanceReminderBatch create(DriftGovernanceReminderBatch batch,
                List<DriftGovernanceReminderBatchMember> m,String actor) {
            long id = sequence++;
            var value = copy(batch, id, DriftGovernanceReminderBatchStatus.DRAFT, 0, null,
                    null, null, null, null, null, null, null, null);
            values.put(id, value);
            memberValues.put(id, m.stream().map(x -> new DriftGovernanceReminderBatchMember(id, x.executionId(),
                    x.reminderNo(), x.evaluationChecksum())).toList()); return value;
        }
        @Override public DriftGovernanceReminderBatch approve(long id,long expected,String actor,Instant now) {
            var value = copy(values.get(id), id, DriftGovernanceReminderBatchStatus.APPROVED, expected + 1, null,
                    actor, now, null, null, null, null, null, null);
            values.put(id, value); return value;
        }
        @Override public DriftGovernanceReminderBatch lockForUpdate(long id){return values.get(id);}
        @Override public DriftGovernanceReminderBatch cancel(long id,long expected,String reason,String actor,
                Instant now,Long replacedBy) {
            var old = values.get(id);
            var value = copy(old,id,DriftGovernanceReminderBatchStatus.CANCELLED,expected+1,null,
                    old.approvedBy(),old.approvedAt(),reason,actor,now,null,null,replacedBy);
            values.put(id,value); return value;
        }
        @Override public DriftGovernanceReminderBatch linkReplacement(long id,long expected,long replacedBy,String actor) {
            var old=values.get(id); var value=copy(old,id,old.status(),expected+1,old.outboxId(),old.approvedBy(),
                    old.approvedAt(),old.cancelReason(),old.cancelledBy(),old.cancelledAt(),old.dispatchedBy(),
                    old.dispatchedAt(),replacedBy); values.put(id,value); return value;
        }
        @Override public DriftGovernanceReminderBatch markDispatched(long id,long expected,long outboxId,
                List<ReminderProgress> p,String actor,Instant now) {
            progress = p; var old=values.get(id); var value = copy(old, id,
                    DriftGovernanceReminderBatchStatus.DISPATCHED, expected+1, outboxId,
                    old.approvedBy(), old.approvedAt(), null, null, null, actor, now, old.replacedByBatchId());
            values.put(id,value); return value;
        }
        @Override public Optional<DriftGovernanceReminderBatch> findById(long id){return Optional.ofNullable(values.get(id));}
        @Override public List<DriftGovernanceReminderBatch> findByWorkspaceId(long id){return List.copyOf(values.values());}
        @Override public List<DriftGovernanceReminderBatchMember> findMembers(long id){return memberValues.get(id);}
        private static DriftGovernanceReminderBatch copy(DriftGovernanceReminderBatch b,Long id,
                DriftGovernanceReminderBatchStatus status,long version,Long outbox,String approver,Instant approved,
                String reason,String cancelledBy,Instant cancelledAt,String dispatcher,Instant dispatched,
                Long replacedBy) {
            return new DriftGovernanceReminderBatch(id,b.batchCode(),b.workspaceId(),b.aggregationKey(),
                    b.environmentCode(),b.ownerCode(),b.creationSource(),status,b.memberCount(),b.payloadDocument(),
                    b.contentChecksum(),version,outbox,b.replacesBatchId(),replacedBy,b.createdBy(),Instant.now(),
                    approver,approved,reason,cancelledBy,cancelledAt,dispatcher,dispatched);
        }
    }

    private static final class Outbox implements NotificationOutboxRepository {
        private final boolean fail; private int calls;
        private Outbox(boolean fail){this.fail=fail;}
        @Override public NotificationOutboxMessage enqueue(NotificationOutboxMessage value){calls++;if(fail)throw new IllegalStateException("outbox unavailable");return new NotificationOutboxMessage(9L,value.eventType(),value.aggregateType(),value.aggregateId(),value.environmentCode(),value.payload(),value.availableAt(),Instant.now());}
        @Override public List<NotificationDeliveryTask> claim(String a,Instant b,Instant c,int d){throw unsupported();}
        @Override public Optional<NotificationDeliveryTask> findById(long id){throw unsupported();}
        @Override public NotificationDeliveryTask markDelivered(long a,String b,Instant c){throw unsupported();}
        @Override public NotificationDeliveryTask markFailed(long a,String b,Instant c,String d,NotificationFailureClass e,long f,boolean g,Instant h){throw unsupported();}
        @Override public List<NotificationDeliveryTask> findByStatus(NotificationDeliveryStatus a,int b){throw unsupported();}
        @Override public NotificationDeliveryTask replay(long a,Instant b,String c){throw unsupported();}
        @Override public List<NotificationDeliveryTask> replayBatch(List<Long>a,Instant b,String c){throw unsupported();}
        @Override public List<NotificationDeliveryAttemptAggregate> aggregateAttempts(Instant a,Instant b,String c,String d,NotificationProviderType e,Long f){throw unsupported();}
        @Override public List<NotificationRoutingFailure> findRoutingFailures(int a){throw unsupported();}
        @Override public NotificationRoutingFailure reroute(long a,String b){throw unsupported();}
        @Override public long countRoutingFailures(){throw unsupported();}
        private static UnsupportedOperationException unsupported(){return new UnsupportedOperationException();}
    }
}
