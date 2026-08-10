package com.ftk.tpip.adapters.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.GetObjectLockConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectLockConfiguration;
import software.amazon.awssdk.services.s3.model.ObjectLockEnabled;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3NotificationAttemptArchiveStoreTest {
    @Test
    void writesWithRetentionBindsVersionAndReadsExactVersionBack() {
        byte[] content = "{\"id\":1}\n".getBytes(StandardCharsets.UTF_8);
        FakeS3 fake = new FakeS3(content, true);
        var store = new S3NotificationAttemptArchiveStore(fake.client, "archive-bucket", "tpip/", 1024,
                true, true, Duration.ofDays(365), "COMPLIANCE");

        var artifact = store.write("123e4567-e89b-12d3-a456-426614174000", content);

        assertEquals("s3://archive-bucket/tpip/123e4567-e89b-12d3-a456-426614174000.ndjson?versionId=version-1",
                artifact.artifactUri());
        assertArrayEquals(content, store.read(artifact.artifactUri()));
        assertEquals("*", fake.put.ifNoneMatch());
        assertEquals(ObjectLockMode.COMPLIANCE, fake.put.objectLockMode());
        assertEquals("version-1", fake.get.versionId());
    }

    @Test
    void refusesWritesWhenRequiredObjectLockCapabilityIsMissing() {
        FakeS3 fake = new FakeS3("value\n".getBytes(StandardCharsets.UTF_8), false);
        var store = new S3NotificationAttemptArchiveStore(fake.client, "archive-bucket", "tpip/", 1024,
                true, true, Duration.ofDays(365), "GOVERNANCE");

        assertFalse(store.probe().ready());
        assertThrows(IllegalStateException.class, () -> store.write(
                "123e4567-e89b-12d3-a456-426614174000", "value\n".getBytes(StandardCharsets.UTF_8)));
    }

    private static final class FakeS3 {
        private final byte[] content;
        private final boolean objectLock;
        private PutObjectRequest put;
        private GetObjectRequest get;
        private final S3Client client;

        private FakeS3(byte[] content, boolean objectLock) {
            this.content = content; this.objectLock = objectLock;
            this.client = (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(),
                    new Class<?>[] {S3Client.class}, (proxy, method, arguments) -> invoke(method.getName(), arguments));
        }

        private Object invoke(String method, Object[] arguments) {
            return switch (method) {
                case "headBucket" -> HeadBucketResponse.builder().build();
                case "getBucketVersioning" -> GetBucketVersioningResponse.builder()
                        .status(BucketVersioningStatus.ENABLED).build();
                case "getObjectLockConfiguration" -> GetObjectLockConfigurationResponse.builder()
                        .objectLockConfiguration(ObjectLockConfiguration.builder().objectLockEnabled(objectLock
                                ? ObjectLockEnabled.ENABLED : ObjectLockEnabled.UNKNOWN_TO_SDK_VERSION).build()).build();
                case "putObject" -> {
                    put = (PutObjectRequest) arguments[0]; yield PutObjectResponse.builder().versionId("version-1").build();
                }
                case "headObject" -> HeadObjectResponse.builder().contentLength((long) content.length).build();
                case "getObject" -> {
                    get = (GetObjectRequest) arguments[0];
                    yield ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content);
                }
                case "serviceName" -> "s3";
                case "close" -> null;
                case "toString" -> "FakeS3Client";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> arguments != null && arguments.length == 1 && arguments[0] == client;
                default -> throw new UnsupportedOperationException(method);
            };
        }
    }
}
