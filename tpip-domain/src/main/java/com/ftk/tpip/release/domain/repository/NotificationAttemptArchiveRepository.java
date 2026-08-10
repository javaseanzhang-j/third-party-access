package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.NotificationAttemptArchiveBatch;
import com.ftk.tpip.release.domain.model.NotificationAttemptArchiveVerification;
import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationAttemptArchiveRepository {
    List<NotificationDeliveryAttemptEvidence> findEvidence(String environmentCode, Instant start, Instant end,
            int limit);
    NotificationAttemptArchiveBatch createBatch(NotificationAttemptArchiveBatch value);
    Optional<NotificationAttemptArchiveBatch> findBatch(long id);
    Optional<NotificationAttemptArchiveBatch> findBatch(String environmentCode, Instant start, Instant end);
    boolean hasOverlappingBatch(String environmentCode, Instant start, Instant end);
    List<NotificationAttemptArchiveBatch> findBatches(String environmentCode, int limit);
    boolean hasEvidence(String environmentCode, Instant start, Instant end);
    List<NotificationAttemptArchiveBatch> findVerificationCandidates(Instant lastSuccessfulVerificationBefore,
            int limit);
    NotificationAttemptArchiveVerification recordVerification(NotificationAttemptArchiveVerification value);
    List<NotificationAttemptArchiveVerification> findVerifications(long archiveBatchId, int limit);
    long countBatches(String status);
    long countVerificationFailuresSince(Instant since);
    boolean tryAcquireLease(String leaseName, String ownerCode, Instant acquiredAt, Instant lockedUntil);
    void releaseLease(String leaseName, String ownerCode, Instant releasedAt);
    NotificationAttemptArchiveBatch markStored(long id, String artifactUri, String artifactChecksum,
            long artifactSizeBytes, String manifestDocument, String manifestChecksum, String actor);
    NotificationAttemptArchiveBatch markVerified(long id, String actor, Instant at);
    NotificationAttemptArchiveBatch markFailed(long id, String reason);
    NotificationAttemptArchiveBatch changeLegalHold(long id, boolean hold, String reason, String actor, Instant at);
    long deleteVerifiedEvidence(NotificationAttemptArchiveBatch batch);
    NotificationAttemptArchiveBatch markPurged(long id, long count, String actor, Instant at);
}
