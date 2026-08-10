package com.ftk.tpip.release.domain.service;

public interface NotificationAttemptArchiveStore {
    StoredArchive write(String batchCode, byte[] content);
    byte[] read(String artifactUri);
    default StorageHealth probe() {
        return new StorageHealth("UNSPECIFIED", true, true, false, false, "No capability probe provided");
    }
    record StoredArchive(String artifactUri, long sizeBytes) { }
    record StorageHealth(String provider, boolean reachable, boolean ready, boolean versioningEnabled,
            boolean objectLockEnabled, String detail) { }
}
