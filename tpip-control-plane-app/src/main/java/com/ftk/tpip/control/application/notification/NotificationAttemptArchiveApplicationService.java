package com.ftk.tpip.control.application.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.configuration.NotificationAttemptArchiveProperties;
import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.NotificationAttemptArchiveBatch;
import com.ftk.tpip.release.domain.model.NotificationAttemptArchiveVerification;
import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptEvidence;
import com.ftk.tpip.release.domain.repository.NotificationAttemptArchiveRepository;
import com.ftk.tpip.release.domain.service.NotificationAttemptArchiveStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationAttemptArchiveApplicationService {
    private static final String FORMAT = "tpip.notification-attempt-archive/v1";
    private static final Duration MAXIMUM_WINDOW = Duration.ofDays(31);
    private final NotificationAttemptArchiveRepository repository;
    private final NotificationAttemptArchiveStore store;
    private final NotificationAttemptArchiveProperties archiveProperties;
    private final NotificationDeliveryProperties deliveryProperties;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public NotificationAttemptArchiveApplicationService(NotificationAttemptArchiveRepository repository,
            NotificationAttemptArchiveStore store, NotificationAttemptArchiveProperties archiveProperties,
            NotificationDeliveryProperties deliveryProperties, CanonicalJsonService canonicalJson,
            ObjectMapper json) {
        this(repository, store, archiveProperties, deliveryProperties, canonicalJson, json, Clock.systemUTC());
    }

    NotificationAttemptArchiveApplicationService(NotificationAttemptArchiveRepository repository,
            NotificationAttemptArchiveStore store, NotificationAttemptArchiveProperties archiveProperties,
            NotificationDeliveryProperties deliveryProperties, CanonicalJsonService canonicalJson,
            ObjectMapper json, Clock clock) {
        this.repository = repository; this.store = store; this.archiveProperties = archiveProperties;
        this.deliveryProperties = deliveryProperties; this.canonicalJson = canonicalJson;
        this.json = json; this.clock = clock; archiveProperties.validate(); deliveryProperties.validate();
    }

    public NotificationAttemptArchiveBatch archive(String environmentCode, Instant start, Instant end, String actor) {
        String environment = environment(environmentCode); String operator = actor(actor); validateWindow(start, end);
        if (repository.hasOverlappingBatch(environment, start, end)) {
            throw new IllegalArgumentException("archive window overlaps an existing batch");
        }
        List<NotificationDeliveryAttemptEvidence> evidence = repository.findEvidence(environment, start, end,
                archiveProperties.getMaximumRecordsPerBatch() + 1);
        if (evidence.isEmpty()) throw new IllegalArgumentException("archive window contains no attempt evidence");
        if (evidence.size() > archiveProperties.getMaximumRecordsPerBatch()) {
            throw new IllegalArgumentException("archive window exceeds maximumRecordsPerBatch");
        }
        String code = UUID.randomUUID().toString();
        NotificationAttemptArchiveBatch batch = repository.createBatch(new NotificationAttemptArchiveBatch(null,
                code, environment, start, end, evidence.getFirst().id(), evidence.getLast().id(), evidence.size(),
                null, null, null, null, null, "CREATED", false,
                null, null, null, null, null, null, null, null, null, operator, null, null));
        return store(batch, evidence, operator);
    }

    public NotificationAttemptArchiveBatch retry(long id, String actor) {
        NotificationAttemptArchiveBatch batch = batch(id);
        if (!"FAILED".equals(batch.status())) throw new IllegalArgumentException("only FAILED archive can be retried");
        List<NotificationDeliveryAttemptEvidence> evidence = repository.findEvidence(batch.environmentCode(),
                batch.windowStart(), batch.windowEnd(), archiveProperties.getMaximumRecordsPerBatch() + 1).stream()
                .filter(value -> value.id() >= batch.firstAttemptId() && value.id() <= batch.lastAttemptId()).toList();
        if (evidence.size() != batch.recordCount()) {
            throw new IllegalStateException("online evidence no longer matches failed archive batch watermark");
        }
        return store(batch, evidence, actor(actor));
    }

    public NotificationAttemptArchiveBatch verify(long id, String actor) {
        NotificationAttemptArchiveBatch batch = batch(id);
        if (!"STORED".equals(batch.status())) throw new IllegalArgumentException("only STORED archive can be verified");
        String operator = actor(actor); long started = System.nanoTime();
        try {
            verifyArtifact(batch);
            NotificationAttemptArchiveBatch verified = repository.markVerified(id, operator, clock.instant());
            recordVerification(verified, "INITIAL", "PASSED", null, operator, started);
            return verified;
        } catch (RuntimeException exception) {
            recordVerification(batch, "INITIAL", "FAILED", safeFailure(exception), operator, started);
            throw exception;
        }
    }

    public Optional<NotificationAttemptArchiveBatch> archiveCompletedWindowIfNecessary(String environmentCode,
            Instant start, Instant end, String actor) {
        String environment = environment(environmentCode); String operator = actor(actor); validateWindow(start, end);
        Optional<NotificationAttemptArchiveBatch> existing = repository.findBatch(environment, start, end);
        if (existing.isPresent()) {
            NotificationAttemptArchiveBatch batch = existing.get();
            if ("STORED".equals(batch.status())) return Optional.of(verify(batch.id(), operator));
            if ("FAILED".equals(batch.status())) {
                NotificationAttemptArchiveBatch retried = retry(batch.id(), operator);
                return Optional.of("STORED".equals(retried.status()) ? verify(retried.id(), operator) : retried);
            }
            return Optional.empty();
        }
        if (repository.hasOverlappingBatch(environment, start, end)) return Optional.empty();
        if (!repository.hasEvidence(environment, start, end)) return Optional.empty();
        NotificationAttemptArchiveBatch archived = archive(environment, start, end, operator);
        return Optional.of("STORED".equals(archived.status()) ? verify(archived.id(), operator) : archived);
    }

    public NotificationAttemptArchiveVerification audit(long id, String verificationType, String actor) {
        NotificationAttemptArchiveBatch batch = batch(id);
        if (!("VERIFIED".equals(batch.status()) || "PURGED".equals(batch.status()))) {
            throw new IllegalArgumentException("only VERIFIED or PURGED archive can be audited");
        }
        String type = required(verificationType, "verificationType", 20).toUpperCase(java.util.Locale.ROOT);
        if (!("MANUAL".equals(type) || "DRILL".equals(type))) {
            throw new IllegalArgumentException("verificationType must be MANUAL or DRILL");
        }
        String operator = actor(actor); long started = System.nanoTime();
        try {
            verifyArtifact(batch);
            return recordVerification(batch, type, "PASSED", null, operator, started);
        } catch (RuntimeException exception) {
            recordVerification(batch, type, "FAILED", safeFailure(exception), operator, started);
            throw exception;
        }
    }

    @Transactional
    public NotificationAttemptArchiveBatch purge(long id, String actor) {
        if (!archiveProperties.isPurgeEnabled()) {
            throw new IllegalStateException("notification attempt purge is disabled");
        }
        NotificationAttemptArchiveBatch batch = batch(id);
        if (!"VERIFIED".equals(batch.status()) || batch.legalHold()) {
            throw new IllegalArgumentException("archive must be VERIFIED and not under legal hold");
        }
        Instant cutoff = clock.instant().minus(deliveryProperties.getAttemptOnlineRetention());
        if (batch.windowEnd().isAfter(cutoff)) {
            throw new IllegalArgumentException("archive window is still inside online retention");
        }
        verifyArtifact(batch);
        long deleted = repository.deleteVerifiedEvidence(batch);
        if (deleted != batch.recordCount()) {
            throw new IllegalStateException("purged record count does not match verified archive manifest");
        }
        return repository.markPurged(id, deleted, actor(actor), clock.instant());
    }

    public NotificationAttemptArchiveBatch changeLegalHold(long id, boolean hold, String reason, String actor) {
        String normalizedReason = hold ? required(reason, "holdReason", 500) : null;
        return repository.changeLegalHold(id, hold, normalizedReason, actor(actor), clock.instant());
    }

    @Transactional(readOnly = true)
    public NotificationAttemptArchiveBatch batch(long id) {
        if (id <= 0) throw new IllegalArgumentException("archiveBatchId must be positive");
        return repository.findBatch(id)
                .orElseThrow(() -> new IllegalArgumentException("notification attempt archive batch does not exist"));
    }

    @Transactional(readOnly = true)
    public List<NotificationAttemptArchiveBatch> batches(String environmentCode, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        return repository.findBatches(environment(environmentCode), limit);
    }

    @Transactional(readOnly = true)
    public List<NotificationAttemptArchiveVerification> verifications(long id, int limit) {
        batch(id);
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        return repository.findVerifications(id, limit);
    }

    @Transactional(readOnly = true)
    public List<NotificationAttemptArchiveBatch> verificationCandidates(Instant cutoff, int limit) {
        if (cutoff == null || cutoff.isAfter(clock.instant()) || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("verification candidate query is invalid");
        }
        return repository.findVerificationCandidates(cutoff, limit);
    }

    public NotificationAttemptArchiveStore.StorageHealth storageHealth() { return store.probe(); }

    private NotificationAttemptArchiveBatch store(NotificationAttemptArchiveBatch batch,
            List<NotificationDeliveryAttemptEvidence> evidence, String actor) {
        try {
            byte[] content = content(evidence);
            if (content.length > archiveProperties.getMaximumArtifactBytes()) {
                throw new IllegalArgumentException("archive artifact exceeds maximumArtifactBytes");
            }
            String checksum = sha256(content);
            var stored = store.write(batch.batchCode(), content);
            if (stored.sizeBytes() != content.length) throw new IllegalStateException("archive store size mismatch");
            String manifest = manifest(batch, checksum, content.length);
            return repository.markStored(batch.id(), stored.artifactUri(), checksum, content.length,
                    manifest, sha256(manifest.getBytes(StandardCharsets.UTF_8)), actor);
        } catch (RuntimeException exception) {
            return repository.markFailed(batch.id(), safeFailure(exception));
        }
    }

    private void verifyArtifact(NotificationAttemptArchiveBatch batch) {
        byte[] content = store.read(batch.artifactUri());
        if (content.length > archiveProperties.getMaximumArtifactBytes()
                || batch.artifactSizeBytes() == null || content.length != batch.artifactSizeBytes()
                || !sha256(content).equals(batch.artifactChecksum())) {
            throw new IllegalStateException("archive artifact size or checksum verification failed");
        }
        String[] lines = new String(content, StandardCharsets.UTF_8).split("\\n");
        if (lines.length != batch.recordCount()) throw new IllegalStateException("archive record count verification failed");
        long first = -1; long last = -1;
        for (int index = 0; index < lines.length; index++) {
            try {
                JsonNode value = json.readTree(lines[index]);
                if (!FORMAT.equals(value.path("format").asText())
                        || !batch.environmentCode().equals(value.path("environmentCode").asText())) {
                    throw new IllegalStateException("archive record identity verification failed");
                }
                long id = value.path("id").asLong(-1);
                Instant occurred = Instant.parse(value.path("occurredAt").asText());
                if (id <= 0 || occurred.isBefore(batch.windowStart()) || !occurred.isBefore(batch.windowEnd())) {
                    throw new IllegalStateException("archive record watermark verification failed");
                }
                if (index == 0) first = id; last = id;
            } catch (java.io.IOException | java.time.format.DateTimeParseException exception) {
                throw new IllegalStateException("archive record cannot be parsed", exception);
            }
        }
        if (first != batch.firstAttemptId() || last != batch.lastAttemptId()) {
            throw new IllegalStateException("archive first/last attempt id verification failed");
        }
        String manifestChecksum = sha256(batch.manifestDocument().getBytes(StandardCharsets.UTF_8));
        if (!manifestChecksum.equals(batch.manifestChecksum())) {
            throw new IllegalStateException("archive manifest checksum verification failed");
        }
        verifyManifest(batch);
    }

    private void verifyManifest(NotificationAttemptArchiveBatch batch) {
        try {
            JsonNode manifest = json.readTree(batch.manifestDocument());
            if (!FORMAT.equals(manifest.path("format").asText())
                    || !batch.batchCode().equals(manifest.path("batchCode").asText())
                    || !batch.environmentCode().equals(manifest.path("environmentCode").asText())
                    || !batch.windowStart().equals(Instant.parse(manifest.path("windowStart").asText()))
                    || !batch.windowEnd().equals(Instant.parse(manifest.path("windowEnd").asText()))
                    || batch.firstAttemptId() != manifest.path("firstAttemptId").asLong(-1)
                    || batch.lastAttemptId() != manifest.path("lastAttemptId").asLong(-1)
                    || batch.recordCount() != manifest.path("recordCount").asLong(-1)
                    || !batch.artifactChecksum().equals(manifest.path("artifactChecksum").asText())
                    || batch.artifactSizeBytes() != manifest.path("artifactSizeBytes").asLong(-1)) {
                throw new IllegalStateException("archive manifest does not match batch metadata");
            }
        } catch (java.io.IOException | java.time.format.DateTimeParseException exception) {
            throw new IllegalStateException("archive manifest cannot be parsed", exception);
        }
    }

    private byte[] content(List<NotificationDeliveryAttemptEvidence> evidence) {
        StringBuilder result = new StringBuilder();
        for (NotificationDeliveryAttemptEvidence value : evidence) {
            ObjectNode node = json.createObjectNode();
            node.put("format", FORMAT).put("id", value.id()).put("deliveryId", value.deliveryId())
                    .put("environmentCode", value.environmentCode()).put("attemptNo", value.attemptNo())
                    .put("providerType", value.providerType()).put("channelCode", value.channelCode());
            if (value.endpointRevisionId() != null) node.put("endpointRevisionId", value.endpointRevisionId());
            node.put("outcome", value.outcome());
            if (value.errorCode() != null) node.put("errorCode", value.errorCode());
            if (value.failureClass() != null) node.put("failureClass", value.failureClass());
            if (value.retryDelayMillis() != null) node.put("retryDelayMillis", value.retryDelayMillis());
            node.put("terminalFailure", value.terminalFailure()).put("occurredAt", value.occurredAt().toString());
            result.append(canonicalJson.canonicalString(node)).append('\n');
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String manifest(NotificationAttemptArchiveBatch batch, String checksum, long bytes) {
        ObjectNode value = json.createObjectNode().put("format", FORMAT).put("batchCode", batch.batchCode())
                .put("environmentCode", batch.environmentCode()).put("windowStart", batch.windowStart().toString())
                .put("windowEnd", batch.windowEnd().toString()).put("firstAttemptId", batch.firstAttemptId())
                .put("lastAttemptId", batch.lastAttemptId()).put("recordCount", batch.recordCount())
                .put("artifactChecksum", checksum).put("artifactSizeBytes", bytes);
        return canonicalJson.canonicalString(value);
    }

    private NotificationAttemptArchiveVerification recordVerification(NotificationAttemptArchiveBatch batch,
            String type, String result, String failure, String operator, long startedNanos) {
        long duration = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
        return repository.recordVerification(new NotificationAttemptArchiveVerification(null, batch.id(), type,
                result, batch.artifactChecksum(), failure, operator, clock.instant(), duration));
    }

    private void validateWindow(Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end) || end.isAfter(clock.instant())
                || Duration.between(start, end).compareTo(MAXIMUM_WINDOW) > 0) {
            throw new IllegalArgumentException("archive window must be completed and no longer than 31 days");
        }
    }
    private static String environment(String value) {
        String normalized = NotificationOperationsApplicationService.environment(value);
        if (normalized == null) throw new IllegalArgumentException("environmentCode must not be blank");
        return normalized;
    }
    private static String actor(String value) { return required(value, "X-Operator", 100); }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String result = value.trim(); if (result.length() > max) throw new IllegalArgumentException(field + " is too long");
        return result;
    }
    private static String safeFailure(RuntimeException exception) {
        String value = exception.getClass().getSimpleName() + ": "
                + (exception.getMessage() == null ? "archive operation failed" : exception.getMessage());
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}
