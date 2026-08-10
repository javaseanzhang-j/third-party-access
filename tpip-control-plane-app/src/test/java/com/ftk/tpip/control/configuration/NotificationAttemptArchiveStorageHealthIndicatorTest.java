package com.ftk.tpip.control.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ftk.tpip.release.domain.service.NotificationAttemptArchiveStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class NotificationAttemptArchiveStorageHealthIndicatorTest {
    @Test
    void mapsStoragePolicyReadinessToActuatorStatus() {
        NotificationAttemptArchiveStore store = new NotificationAttemptArchiveStore() {
            @Override public StoredArchive write(String batchCode, byte[] content) { throw new UnsupportedOperationException(); }
            @Override public byte[] read(String artifactUri) { throw new UnsupportedOperationException(); }
            @Override public StorageHealth probe() {
                return new StorageHealth("S3", true, false, true, false, "Object Lock is required");
            }
        };

        var health = new NotificationAttemptArchiveStorageHealthIndicator(store).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(false, health.getDetails().get("objectLockEnabled"));
    }
}
