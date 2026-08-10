package com.ftk.tpip.control.configuration;

import java.nio.file.Path;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.notification-attempt-archive")
public class NotificationAttemptArchiveProperties {
    private Path directory = Path.of("./var/archive/notification-attempts");
    private String storageProvider = "FILESYSTEM";
    private int maximumRecordsPerBatch = 100_000;
    private int maximumArtifactBytes = 100 * 1024 * 1024;
    private boolean purgeEnabled;
    private boolean automationEnabled;
    private List<String> automationEnvironments = new ArrayList<>(List.of("default"));
    private Duration automationWindow = Duration.ofDays(1);
    private Duration automationCompletionDelay = Duration.ofDays(1);
    private Duration automationLookback = Duration.ofDays(365);
    private int automationMaximumBatchesPerCycle = 3;
    private Duration automationLease = Duration.ofMinutes(30);
    private boolean verificationDrillEnabled = true;
    private Duration verificationDrillInterval = Duration.ofDays(30);
    private int verificationMaximumBatchesPerCycle = 10;
    private final S3 s3 = new S3();
    public Path getDirectory() { return directory; }
    public void setDirectory(Path value) { directory = value; }
    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String value) { storageProvider = value; }
    public S3 getS3() { return s3; }
    public int getMaximumRecordsPerBatch() { return maximumRecordsPerBatch; }
    public void setMaximumRecordsPerBatch(int value) { maximumRecordsPerBatch = value; }
    public int getMaximumArtifactBytes() { return maximumArtifactBytes; }
    public void setMaximumArtifactBytes(int value) { maximumArtifactBytes = value; }
    public boolean isPurgeEnabled() { return purgeEnabled; }
    public void setPurgeEnabled(boolean value) { purgeEnabled = value; }
    public boolean isAutomationEnabled() { return automationEnabled; }
    public void setAutomationEnabled(boolean value) { automationEnabled = value; }
    public List<String> getAutomationEnvironments() { return List.copyOf(automationEnvironments); }
    public void setAutomationEnvironments(List<String> value) {
        automationEnvironments = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public Duration getAutomationWindow() { return automationWindow; }
    public void setAutomationWindow(Duration value) { automationWindow = value; }
    public Duration getAutomationCompletionDelay() { return automationCompletionDelay; }
    public void setAutomationCompletionDelay(Duration value) { automationCompletionDelay = value; }
    public Duration getAutomationLookback() { return automationLookback; }
    public void setAutomationLookback(Duration value) { automationLookback = value; }
    public int getAutomationMaximumBatchesPerCycle() { return automationMaximumBatchesPerCycle; }
    public void setAutomationMaximumBatchesPerCycle(int value) { automationMaximumBatchesPerCycle = value; }
    public Duration getAutomationLease() { return automationLease; }
    public void setAutomationLease(Duration value) { automationLease = value; }
    public boolean isVerificationDrillEnabled() { return verificationDrillEnabled; }
    public void setVerificationDrillEnabled(boolean value) { verificationDrillEnabled = value; }
    public Duration getVerificationDrillInterval() { return verificationDrillInterval; }
    public void setVerificationDrillInterval(Duration value) { verificationDrillInterval = value; }
    public int getVerificationMaximumBatchesPerCycle() { return verificationMaximumBatchesPerCycle; }
    public void setVerificationMaximumBatchesPerCycle(int value) { verificationMaximumBatchesPerCycle = value; }
    public void validate() {
        if (storageProvider == null || !("FILESYSTEM".equalsIgnoreCase(storageProvider)
                || "S3".equalsIgnoreCase(storageProvider)) || directory == null
                || maximumRecordsPerBatch < 1 || maximumRecordsPerBatch > 1_000_000
                || maximumArtifactBytes < 1024 || maximumArtifactBytes > 1024 * 1024 * 1024
                || automationEnvironments.isEmpty() || automationEnvironments.stream().anyMatch(v -> v == null || v.isBlank())
                || invalid(automationWindow) || automationWindow.compareTo(Duration.ofHours(1)) < 0
                || automationWindow.compareTo(Duration.ofDays(31)) > 0
                || invalid(automationCompletionDelay) || invalid(automationLookback)
                || automationLookback.compareTo(Duration.ofDays(3650)) > 0
                || automationLookback.toMillis() / automationWindow.toMillis() > 10_000
                || automationMaximumBatchesPerCycle < 1 || automationMaximumBatchesPerCycle > 100
                || invalid(automationLease)
                || invalid(verificationDrillInterval) || verificationMaximumBatchesPerCycle < 1
                || verificationMaximumBatchesPerCycle > 100) {
            throw new IllegalArgumentException("notification attempt archive properties are invalid");
        }
        if ("S3".equalsIgnoreCase(storageProvider)) s3.validate();
    }
    private static boolean invalid(Duration value) { return value == null || value.isZero() || value.isNegative(); }

    public static final class S3 {
        private String bucket;
        private String prefix = "notification-attempts/";
        private String region = "us-east-1";
        private URI endpoint;
        private boolean pathStyleAccess;
        private String accessKey;
        private String secretKey;
        private String sessionToken;
        private boolean requireVersioning = true;
        private boolean requireObjectLock = true;
        private Duration retention = Duration.ofDays(3650);
        private String objectLockMode = "COMPLIANCE";
        private Duration connectionTimeout = Duration.ofSeconds(3);
        private Duration socketTimeout = Duration.ofSeconds(30);
        public String getBucket() { return bucket; }
        public void setBucket(String value) { bucket = value; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String value) { prefix = value; }
        public String getRegion() { return region; }
        public void setRegion(String value) { region = value; }
        public URI getEndpoint() { return endpoint; }
        public void setEndpoint(URI value) { endpoint = value; }
        public boolean isPathStyleAccess() { return pathStyleAccess; }
        public void setPathStyleAccess(boolean value) { pathStyleAccess = value; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String value) { accessKey = value; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String value) { secretKey = value; }
        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String value) { sessionToken = value; }
        public boolean isRequireVersioning() { return requireVersioning; }
        public void setRequireVersioning(boolean value) { requireVersioning = value; }
        public boolean isRequireObjectLock() { return requireObjectLock; }
        public void setRequireObjectLock(boolean value) { requireObjectLock = value; }
        public Duration getRetention() { return retention; }
        public void setRetention(Duration value) { retention = value; }
        public String getObjectLockMode() { return objectLockMode; }
        public void setObjectLockMode(String value) { objectLockMode = value; }
        public Duration getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(Duration value) { connectionTimeout = value; }
        public Duration getSocketTimeout() { return socketTimeout; }
        public void setSocketTimeout(Duration value) { socketTimeout = value; }
        private void validate() {
            boolean accessPresent = accessKey != null && !accessKey.isBlank();
            boolean secretPresent = secretKey != null && !secretKey.isBlank();
            if (bucket == null || bucket.isBlank() || region == null || region.isBlank()
                    || prefix == null || prefix.isBlank() || accessPresent != secretPresent
                    || invalid(retention) || objectLockMode == null
                    || !("GOVERNANCE".equalsIgnoreCase(objectLockMode)
                    || "COMPLIANCE".equalsIgnoreCase(objectLockMode))
                    || invalid(connectionTimeout) || invalid(socketTimeout)) {
                throw new IllegalArgumentException("notification attempt S3 archive properties are invalid");
            }
        }
    }
}
