package com.ftk.tpip.adapters.archive;

import com.ftk.tpip.release.domain.service.NotificationAttemptArchiveStore;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetObjectLockConfigurationRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectLockEnabled;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public final class S3NotificationAttemptArchiveStore implements NotificationAttemptArchiveStore, AutoCloseable {
    private final S3Client client;
    private final String bucket;
    private final String prefix;
    private final long maximumBytes;
    private final boolean requireVersioning;
    private final boolean requireObjectLock;
    private final Duration retention;
    private final ObjectLockMode lockMode;

    public S3NotificationAttemptArchiveStore(S3Client client, String bucket, String prefix, long maximumBytes,
            boolean requireVersioning, boolean requireObjectLock, Duration retention, String lockMode) {
        if (client == null || bucket == null || bucket.isBlank() || maximumBytes < 1 || retention == null
                || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("S3 archive store configuration is invalid");
        }
        this.client = client; this.bucket = bucket.trim(); this.prefix = normalizePrefix(prefix);
        this.maximumBytes = maximumBytes; this.requireVersioning = requireVersioning;
        this.requireObjectLock = requireObjectLock;
        this.retention = retention;
        this.lockMode = ObjectLockMode.fromValue(required(lockMode, "objectLockMode").toUpperCase(Locale.ROOT));
    }

    @Override public StoredArchive write(String batchCode, byte[] content) {
        if (batchCode == null || !batchCode.matches("[0-9a-f-]{36}") || content == null || content.length == 0) {
            throw new IllegalArgumentException("archive content or batchCode is invalid");
        }
        if (content.length > maximumBytes) throw new IllegalArgumentException("archive content is too large");
        StorageHealth health = probe();
        if (!health.ready()) throw new IllegalStateException("S3 archive storage is not ready: " + health.detail());
        String key = prefix + batchCode + ".ndjson";
        String hexChecksum = sha256Hex(content);
        var request = PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/x-ndjson")
                .contentLength((long) content.length).ifNoneMatch("*")
                .checksumSHA256(Base64.getEncoder().encodeToString(sha256(content)))
                .metadata(Map.of("tpip-sha256", hexChecksum, "tpip-format", "notification-attempt-archive-v1"));
        if (requireObjectLock) {
            request.objectLockMode(lockMode).objectLockRetainUntilDate(Instant.now().plus(retention));
        }
        try {
            var response = client.putObject(request.build(), RequestBody.fromBytes(content));
            return new StoredArchive(uri(key, response.versionId()), content.length);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 409 || exception.statusCode() == 412) {
                String existingUri = uri(key, null);
                if (Arrays.equals(read(existingUri), content)) return new StoredArchive(existingUri, content.length);
                throw new IllegalStateException("S3 archive artifact is immutable and already exists", exception);
            }
            throw new IllegalStateException("S3 archive artifact cannot be written", exception);
        }
    }

    @Override public byte[] read(String artifactUri) {
        Location location = location(artifactUri);
        try {
            var headBuilder = HeadObjectRequest.builder().bucket(bucket).key(location.key());
            if (location.versionId() != null) headBuilder.versionId(location.versionId());
            var head = client.headObject(headBuilder.build());
            if (head.contentLength() == null || head.contentLength() < 1 || head.contentLength() > maximumBytes) {
                throw new IllegalStateException("S3 archive artifact size is invalid");
            }
            var getBuilder = GetObjectRequest.builder().bucket(bucket).key(location.key());
            if (location.versionId() != null) getBuilder.versionId(location.versionId());
            byte[] content = client.getObject(getBuilder.build(), ResponseTransformer.toBytes()).asByteArray();
            if (content.length != head.contentLength()) throw new IllegalStateException("S3 archive read size mismatch");
            return content;
        } catch (S3Exception exception) {
            throw new IllegalStateException("S3 archive artifact cannot be read", exception);
        }
    }

    @Override public StorageHealth probe() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            boolean versioning;
            try {
                versioning = client.getBucketVersioning(
                                GetBucketVersioningRequest.builder().bucket(bucket).build()).status()
                        == BucketVersioningStatus.ENABLED;
            } catch (RuntimeException exception) {
                versioning = false;
            }
            boolean objectLock;
            try {
                objectLock = client.getObjectLockConfiguration(
                                GetObjectLockConfigurationRequest.builder().bucket(bucket).build())
                        .objectLockConfiguration().objectLockEnabled() == ObjectLockEnabled.ENABLED;
            } catch (RuntimeException exception) {
                objectLock = false;
            }
            boolean ready = (!requireVersioning || versioning) && (!requireObjectLock || objectLock);
            String detail = ready ? "S3 bucket capabilities satisfy archive policy"
                    : "S3 bucket is missing required Versioning or Object Lock";
            return new StorageHealth("S3", true, ready, versioning, objectLock, detail);
        } catch (RuntimeException exception) {
            return new StorageHealth("S3", false, false, false, false,
                    "S3 archive probe failed: " + exception.getClass().getSimpleName());
        }
    }

    @Override public void close() { client.close(); }

    private Location location(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("artifactUri is required");
        URI uri = URI.create(value);
        String key = uri.getPath() == null ? null : uri.getPath().replaceFirst("^/", "");
        if (!"s3".equals(uri.getScheme()) || !bucket.equals(uri.getHost()) || key == null
                || !key.startsWith(prefix) || !key.endsWith(".ndjson")) {
            throw new IllegalArgumentException("artifactUri is outside the configured S3 archive prefix");
        }
        String versionId = null;
        if (uri.getQuery() != null && uri.getQuery().startsWith("versionId=")) {
            versionId = uri.getQuery().substring(10);
        }
        return new Location(key, versionId == null || versionId.isBlank() ? null : versionId);
    }

    private String uri(String key, String versionId) {
        try {
            return new URI("s3", bucket, "/" + key,
                    versionId == null || versionId.isBlank() ? null : "versionId=" + versionId, null).toString();
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalStateException("S3 archive URI cannot be created", exception);
        }
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) return "notification-attempts/";
        String result = value.trim().replaceFirst("^/+", "");
        if (result.contains("..")) throw new IllegalArgumentException("S3 archive prefix is invalid");
        return result.endsWith("/") ? result : result + "/";
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private static String sha256Hex(byte[] value) { return HexFormat.of().formatHex(sha256(value)); }
    private record Location(String key, String versionId) { }
}
