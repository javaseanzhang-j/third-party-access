package com.ftk.tpip.control.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.configuration.NotificationAttemptArchiveProperties;
import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.NotificationAttemptArchiveBatch;
import com.ftk.tpip.release.domain.model.NotificationAttemptArchiveVerification;
import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptEvidence;
import com.ftk.tpip.release.domain.repository.NotificationAttemptArchiveRepository;
import com.ftk.tpip.release.domain.service.NotificationAttemptArchiveStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationAttemptArchiveApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private static final Instant START = NOW.minus(Duration.ofDays(101));
    private static final Instant END = START.plus(Duration.ofHours(1));

    @Test
    void archivesDeterministicEvidenceAndVerifiesByReadingArtifactBack() {
        Fixture fixture = fixture(false);

        NotificationAttemptArchiveBatch stored = fixture.service.archive("default", START, END, "operator-a");
        NotificationAttemptArchiveBatch verified = fixture.service.verify(stored.id(), "reviewer-b");

        assertEquals("VERIFIED", verified.status());
        assertEquals(2, verified.recordCount());
        assertEquals(64, verified.artifactChecksum().length());
        assertEquals(64, verified.manifestChecksum().length());
        assertNotNull(verified.verifiedAt());
        assertEquals("PASSED", fixture.service.verifications(verified.id(), 10).getFirst().verificationResult());
        assertThrows(IllegalStateException.class, () -> fixture.service.purge(verified.id(), "operator-a"));
    }

    @Test
    void rejectsTamperedArtifactAndHonorsLegalHoldBeforeControlledPurge() {
        Fixture tampered = fixture(true);
        NotificationAttemptArchiveBatch stored = tampered.service.archive("default", START, END, "operator-a");
        tampered.store.content = "tampered\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> tampered.service.verify(stored.id(), "reviewer-b"));

        Fixture fixture = fixture(true);
        NotificationAttemptArchiveBatch verified = fixture.service.verify(
                fixture.service.archive("default", START, END, "operator-a").id(), "reviewer-b");
        fixture.service.changeLegalHold(verified.id(), true, "regulatory investigation", "legal-a");
        assertThrows(IllegalArgumentException.class, () -> fixture.service.purge(verified.id(), "operator-a"));
        fixture.service.changeLegalHold(verified.id(), false, null, "legal-a");

        NotificationAttemptArchiveBatch purged = fixture.service.purge(verified.id(), "operator-a");
        assertEquals("PURGED", purged.status());
        assertEquals(2L, purged.purgedRecordCount());
        assertEquals(0, fixture.repository.evidence.size());
    }

    @Test
    void rejectsManifestWhoseValidChecksumNoLongerMatchesBatchMetadata() {
        Fixture fixture = fixture(false);
        NotificationAttemptArchiveBatch stored = fixture.service.archive("default", START, END, "operator-a");
        String changedManifest = stored.manifestDocument().replace("\"environmentCode\":\"default\"",
                "\"environmentCode\":\"other\"");
        fixture.repository.batch = new NotificationAttemptArchiveBatch(stored.id(), stored.batchCode(),
                stored.environmentCode(), stored.windowStart(), stored.windowEnd(), stored.firstAttemptId(),
                stored.lastAttemptId(), stored.recordCount(), stored.artifactUri(), stored.artifactChecksum(),
                stored.artifactSizeBytes(), changedManifest, sha256(changedManifest), stored.status(),
                stored.legalHold(), stored.holdReason(), stored.heldBy(), stored.heldAt(), stored.verifiedBy(),
                stored.verifiedAt(), stored.purgedBy(), stored.purgedAt(), stored.purgedRecordCount(),
                stored.failureReason(), stored.createdBy(), stored.createdAt(), stored.updatedAt());

        assertThrows(IllegalStateException.class, () -> fixture.service.verify(stored.id(), "reviewer-b"));
        assertEquals("FAILED", fixture.repository.verifications.getFirst().verificationResult());
    }

    @Test
    void automaticallyArchivesOnceAndSupportsIndependentManualAudit() {
        Fixture fixture = fixture(false);

        NotificationAttemptArchiveBatch batch = fixture.service.archiveCompletedWindowIfNecessary(
                "default", START, END, "system-archive").orElseThrow();
        assertEquals("VERIFIED", batch.status());
        assertTrue(fixture.service.archiveCompletedWindowIfNecessary(
                "default", START, END, "system-archive").isEmpty());

        NotificationAttemptArchiveVerification audit = fixture.service.audit(batch.id(), "MANUAL", "reviewer-b");
        assertEquals("PASSED", audit.verificationResult());
        assertEquals(2, fixture.service.verifications(batch.id(), 10).size());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Fixture fixture(boolean purgeEnabled) {
        var repository = new FakeRepository();
        repository.evidence.add(new NotificationDeliveryAttemptEvidence(11, 101, "default", 1,
                "WEBHOOK", "ops", 7L, "SUCCESS", null, null, null, false, START.plusSeconds(1)));
        repository.evidence.add(new NotificationDeliveryAttemptEvidence(12, 102, "default", 2,
                "WEBHOOK", "ops", 7L, "FAILURE", "HTTP_503", "TRANSIENT", 5000L, true,
                START.plusSeconds(2)));
        var store = new FakeStore();
        var archive = new NotificationAttemptArchiveProperties(); archive.setPurgeEnabled(purgeEnabled);
        var delivery = new NotificationDeliveryProperties();
        ObjectMapper json = new ObjectMapper();
        var service = new NotificationAttemptArchiveApplicationService(repository, store, archive, delivery,
                new CanonicalJsonService(json), json, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, repository, store);
    }

    private record Fixture(NotificationAttemptArchiveApplicationService service, FakeRepository repository,
            FakeStore store) { }

    static final class FakeStore implements NotificationAttemptArchiveStore {
        byte[] content;
        @Override public StoredArchive write(String batchCode, byte[] value) {
            content = value.clone(); return new StoredArchive("memory://" + batchCode, value.length);
        }
        @Override public byte[] read(String artifactUri) { return content.clone(); }
    }

    static final class FakeRepository implements NotificationAttemptArchiveRepository {
        final List<NotificationDeliveryAttemptEvidence> evidence = new ArrayList<>();
        final List<NotificationAttemptArchiveVerification> verifications = new ArrayList<>();
        NotificationAttemptArchiveBatch batch;
        @Override public List<NotificationDeliveryAttemptEvidence> findEvidence(String environment, Instant start,
                Instant end, int limit) {
            return evidence.stream().filter(v -> v.environmentCode().equals(environment)
                    && !v.occurredAt().isBefore(start) && v.occurredAt().isBefore(end)).limit(limit).toList();
        }
        @Override public NotificationAttemptArchiveBatch createBatch(NotificationAttemptArchiveBatch value) {
            batch = copy(value, 1L, "CREATED", false, null, null, null, null, null, null, null, null,
                    null, null, NOW, NOW); return batch;
        }
        @Override public Optional<NotificationAttemptArchiveBatch> findBatch(long id) { return Optional.ofNullable(batch); }
        @Override public Optional<NotificationAttemptArchiveBatch> findBatch(String environment, Instant start,
                Instant end) {
            return Optional.ofNullable(batch).filter(value -> value.environmentCode().equals(environment)
                    && value.windowStart().equals(start) && value.windowEnd().equals(end));
        }
        @Override public boolean hasOverlappingBatch(String environment, Instant start, Instant end) {
            return batch != null && batch.environmentCode().equals(environment)
                    && batch.windowStart().isBefore(end) && batch.windowEnd().isAfter(start);
        }
        @Override public List<NotificationAttemptArchiveBatch> findBatches(String environment, int limit) { return List.of(batch); }
        @Override public boolean hasEvidence(String environment, Instant start, Instant end) {
            return !findEvidence(environment, start, end, 1).isEmpty();
        }
        @Override public List<NotificationAttemptArchiveBatch> findVerificationCandidates(Instant cutoff, int limit) {
            return batch == null ? List.of() : List.of(batch);
        }
        @Override public NotificationAttemptArchiveVerification recordVerification(
                NotificationAttemptArchiveVerification value) {
            var recorded = new NotificationAttemptArchiveVerification((long) verifications.size() + 1,
                    value.archiveBatchId(), value.verificationType(), value.verificationResult(),
                    value.artifactChecksum(), value.failureReason(), value.verifiedBy(), value.verifiedAt(),
                    value.durationMillis());
            verifications.add(recorded); return recorded;
        }
        @Override public List<NotificationAttemptArchiveVerification> findVerifications(long batchId, int limit) {
            return verifications.stream().filter(value -> value.archiveBatchId() == batchId).limit(limit).toList();
        }
        @Override public long countBatches(String status) { return batch != null && status.equals(batch.status()) ? 1 : 0; }
        @Override public long countVerificationFailuresSince(Instant since) {
            return verifications.stream().filter(value -> "FAILED".equals(value.verificationResult())
                    && !value.verifiedAt().isBefore(since)).count();
        }
        @Override public boolean tryAcquireLease(String leaseName, String ownerCode, Instant acquiredAt,
                Instant lockedUntil) { return true; }
        @Override public void releaseLease(String leaseName, String ownerCode, Instant releasedAt) { }
        @Override public NotificationAttemptArchiveBatch markStored(long id, String uri, String checksum, long bytes,
                String manifest, String manifestChecksum, String actor) {
            batch = new NotificationAttemptArchiveBatch(batch.id(), batch.batchCode(), batch.environmentCode(),
                    batch.windowStart(), batch.windowEnd(), batch.firstAttemptId(), batch.lastAttemptId(),
                    batch.recordCount(), uri, checksum, bytes, manifest, manifestChecksum, "STORED", batch.legalHold(),
                    batch.holdReason(), batch.heldBy(), batch.heldAt(), null, null, null, null, null, null,
                    batch.createdBy(), batch.createdAt(), NOW); return batch;
        }
        @Override public NotificationAttemptArchiveBatch markVerified(long id, String actor, Instant at) {
            batch = copy(batch, id, "VERIFIED", batch.legalHold(), batch.holdReason(), batch.heldBy(), batch.heldAt(),
                    actor, at, null, null, null, null, null, batch.createdAt(), at); return batch;
        }
        @Override public NotificationAttemptArchiveBatch markFailed(long id, String reason) {
            batch = copy(batch, id, "FAILED", batch.legalHold(), batch.holdReason(), batch.heldBy(), batch.heldAt(),
                    batch.verifiedBy(), batch.verifiedAt(), null, null, null, reason, null, batch.createdAt(), NOW);
            return batch;
        }
        @Override public NotificationAttemptArchiveBatch changeLegalHold(long id, boolean hold, String reason,
                String actor, Instant at) {
            batch = copy(batch, id, batch.status(), hold, reason, actor, at, batch.verifiedBy(), batch.verifiedAt(),
                    batch.purgedBy(), batch.purgedAt(), batch.purgedRecordCount(), batch.failureReason(), null,
                    batch.createdAt(), at); return batch;
        }
        @Override public long deleteVerifiedEvidence(NotificationAttemptArchiveBatch value) {
            int count = evidence.size(); evidence.clear(); return count;
        }
        @Override public NotificationAttemptArchiveBatch markPurged(long id, long count, String actor, Instant at) {
            batch = copy(batch, id, "PURGED", false, batch.holdReason(), batch.heldBy(), batch.heldAt(),
                    batch.verifiedBy(), batch.verifiedAt(), actor, at, count, null, null, batch.createdAt(), at);
            return batch;
        }
        private static NotificationAttemptArchiveBatch copy(NotificationAttemptArchiveBatch v, Long id, String status,
                boolean hold, String holdReason, String heldBy, Instant heldAt, String verifiedBy, Instant verifiedAt,
                String purgedBy, Instant purgedAt, Long purgedCount, String failure, String ignored,
                Instant createdAt, Instant updatedAt) {
            return new NotificationAttemptArchiveBatch(id, v.batchCode(), v.environmentCode(), v.windowStart(),
                    v.windowEnd(), v.firstAttemptId(), v.lastAttemptId(), v.recordCount(), v.artifactUri(),
                    v.artifactChecksum(), v.artifactSizeBytes(), v.manifestDocument(), v.manifestChecksum(), status,
                    hold, holdReason, heldBy, heldAt, verifiedBy, verifiedAt, purgedBy, purgedAt, purgedCount,
                    failure, v.createdBy(), createdAt, updatedAt);
        }
    }
}
