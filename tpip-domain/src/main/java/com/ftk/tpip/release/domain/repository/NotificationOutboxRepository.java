package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.NotificationOutboxMessage;
import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import com.ftk.tpip.release.domain.model.NotificationDeliveryTask;
import com.ftk.tpip.release.domain.model.NotificationFailureClass;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptAggregate;
import com.ftk.tpip.release.domain.model.NotificationProviderType;

public interface NotificationOutboxRepository {
    NotificationOutboxMessage enqueue(NotificationOutboxMessage message);
    List<NotificationDeliveryTask> claim(String workerId, Instant now, Instant expiredBefore, int batchSize);
    Optional<NotificationDeliveryTask> findById(long id);
    NotificationDeliveryTask markDelivered(long id, String workerId, Instant deliveredAt);
    NotificationDeliveryTask markFailed(long id, String workerId, Instant availableAt,
            String error, NotificationFailureClass failureClass, long retryDelayMillis, boolean deadLetter,
            Instant failedAt);
    List<NotificationDeliveryTask> findByStatus(NotificationDeliveryStatus status, int limit);
    NotificationDeliveryTask replay(long id, Instant availableAt, String actor);
    List<NotificationDeliveryTask> replayBatch(List<Long> ids, Instant availableAt, String actor);
    List<NotificationDeliveryAttemptAggregate> aggregateAttempts(Instant fromInclusive, Instant toExclusive,
            String environmentCode, String channelCode, NotificationProviderType providerType, Long endpointRevisionId);
    List<com.ftk.tpip.release.domain.model.NotificationRoutingFailure> findRoutingFailures(int limit);
    com.ftk.tpip.release.domain.model.NotificationRoutingFailure reroute(long eventId, String actor);
    long countRoutingFailures();
}
