package com.ftk.tpip.control.configuration;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.notification-delivery")
public class NotificationDeliveryProperties {
    private Duration claimLease = Duration.ofSeconds(30);
    private Duration retryBaseDelay = Duration.ofSeconds(5);
    private Duration retryMaximumDelay = Duration.ofMinutes(5);
    private Duration maximumRetryAfter = Duration.ofHours(1);
    private int maximumAttempts = 5;
    private int maximumBatchSize = 100;
    private boolean allowHttpChannelEndpoints;
    private int minimumOperationalAttempts = 20;
    private java.math.BigDecimal warningMinimumSuccessRate = new java.math.BigDecimal("99.00");
    private java.math.BigDecimal criticalMinimumSuccessRate = new java.math.BigDecimal("95.00");
    private boolean operationsAutomationEnabled;
    private Duration operationsAutomationPollInterval = Duration.ofMinutes(1);
    private Duration operationsEvaluationWindow = Duration.ofMinutes(5);
    private Duration operationsEvaluationDelay = Duration.ofSeconds(30);
    private List<String> operationsEnvironments = List.of("default");
    private Duration attemptOnlineRetention = Duration.ofDays(90);

    public Duration getClaimLease() { return claimLease; }
    public void setClaimLease(Duration value) { claimLease = value; }
    public Duration getRetryBaseDelay() { return retryBaseDelay; }
    public void setRetryBaseDelay(Duration value) { retryBaseDelay = value; }
    public Duration getRetryMaximumDelay() { return retryMaximumDelay; }
    public void setRetryMaximumDelay(Duration value) { retryMaximumDelay = value; }
    public Duration getMaximumRetryAfter() { return maximumRetryAfter; }
    public void setMaximumRetryAfter(Duration value) { maximumRetryAfter = value; }
    public int getMaximumAttempts() { return maximumAttempts; }
    public void setMaximumAttempts(int value) { maximumAttempts = value; }
    public int getMaximumBatchSize() { return maximumBatchSize; }
    public void setMaximumBatchSize(int value) { maximumBatchSize = value; }
    public boolean isAllowHttpChannelEndpoints() { return allowHttpChannelEndpoints; }
    public void setAllowHttpChannelEndpoints(boolean value) { allowHttpChannelEndpoints = value; }
    public int getMinimumOperationalAttempts() { return minimumOperationalAttempts; }
    public void setMinimumOperationalAttempts(int value) { minimumOperationalAttempts = value; }
    public java.math.BigDecimal getWarningMinimumSuccessRate() { return warningMinimumSuccessRate; }
    public void setWarningMinimumSuccessRate(java.math.BigDecimal value) { warningMinimumSuccessRate = value; }
    public java.math.BigDecimal getCriticalMinimumSuccessRate() { return criticalMinimumSuccessRate; }
    public void setCriticalMinimumSuccessRate(java.math.BigDecimal value) { criticalMinimumSuccessRate = value; }
    public boolean isOperationsAutomationEnabled() { return operationsAutomationEnabled; }
    public void setOperationsAutomationEnabled(boolean value) { operationsAutomationEnabled = value; }
    public Duration getOperationsAutomationPollInterval() { return operationsAutomationPollInterval; }
    public void setOperationsAutomationPollInterval(Duration value) { operationsAutomationPollInterval = value; }
    public Duration getOperationsEvaluationWindow() { return operationsEvaluationWindow; }
    public void setOperationsEvaluationWindow(Duration value) { operationsEvaluationWindow = value; }
    public Duration getOperationsEvaluationDelay() { return operationsEvaluationDelay; }
    public void setOperationsEvaluationDelay(Duration value) { operationsEvaluationDelay = value; }
    public List<String> getOperationsEnvironments() { return operationsEnvironments; }
    public void setOperationsEnvironments(List<String> value) { operationsEnvironments = value; }
    public Duration getAttemptOnlineRetention() { return attemptOnlineRetention; }
    public void setAttemptOnlineRetention(Duration value) { attemptOnlineRetention = value; }

    public void validate() {
        if (claimLease == null || claimLease.isZero() || claimLease.isNegative()
                || retryBaseDelay == null || retryBaseDelay.isZero() || retryBaseDelay.isNegative()
                || retryMaximumDelay == null || retryMaximumDelay.compareTo(retryBaseDelay) < 0
                || maximumRetryAfter == null || maximumRetryAfter.isZero() || maximumRetryAfter.isNegative()
                || maximumAttempts < 1 || maximumBatchSize < 1 || maximumBatchSize > 1000
                || minimumOperationalAttempts < 1 || warningMinimumSuccessRate == null
                || criticalMinimumSuccessRate == null
                || warningMinimumSuccessRate.compareTo(java.math.BigDecimal.ZERO) < 0
                || warningMinimumSuccessRate.compareTo(new java.math.BigDecimal("100")) > 0
                || criticalMinimumSuccessRate.compareTo(java.math.BigDecimal.ZERO) < 0
                || criticalMinimumSuccessRate.compareTo(warningMinimumSuccessRate) > 0
                || operationsAutomationPollInterval == null || operationsAutomationPollInterval.isZero()
                || operationsAutomationPollInterval.isNegative()
                || operationsEvaluationWindow == null || operationsEvaluationWindow.isZero()
                || operationsEvaluationWindow.isNegative() || operationsEvaluationWindow.compareTo(Duration.ofDays(31)) > 0
                || operationsEvaluationDelay == null || operationsEvaluationDelay.isNegative()
                || operationsEnvironments == null || operationsEnvironments.isEmpty()
                || operationsEnvironments.stream().anyMatch(value -> value == null
                        || !value.trim().matches("[a-z][a-z0-9_-]{0,31}"))
                || attemptOnlineRetention == null || attemptOnlineRetention.isZero()
                || attemptOnlineRetention.isNegative()) {
            throw new IllegalArgumentException("notification delivery properties are invalid");
        }
    }
}
