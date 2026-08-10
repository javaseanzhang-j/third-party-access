package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DriftGovernanceReminderObservabilityRepository {
    Optional<DeliveryOverview> findDeliveryOverview(long outboxId);
    List<BatchAuditEvent> findBatchTimeline(String batchCode, int limit);
    MetricsSnapshot summarize(long workspaceId, Instant now);

    record DeliveryOverview(long outboxId, NotificationDeliveryStatus outboxStatus, String routingStatus,
            Instant routingAttemptedAt, Instant deliveredAt, Instant createdAt, List<ChannelDelivery> deliveries) {}
    record ChannelDelivery(long deliveryId, String channelCode, NotificationDeliveryStatus status,
            int attemptCount, Instant deliveredAt, Instant deadLetteredAt) {}
    record BatchAuditEvent(String eventId, String eventType, String actorCode, String summary,
            Long rowVersion, String status, String reason, Long replacesBatchId,
            Long replacedByBatchId, Long outboxId, Instant occurredAt) {}
    record MetricsSnapshot(long totalBatches, long draftBatches, long approvedBatches, long dispatchedBatches,
            long cancelledBatches, long dueUnbatchedExecutions, long activeReservedExecutions,
            Instant oldestDueAt, long unroutedOutboxes, long routedOutboxes, long noMatchOutboxes,
            long renderFailedOutboxes, long pendingDeliveries, long claimedDeliveries,
            long deliveredDeliveries, long deadLetterDeliveries) {}
}
