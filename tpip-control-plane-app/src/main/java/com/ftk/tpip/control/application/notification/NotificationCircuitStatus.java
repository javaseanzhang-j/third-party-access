package com.ftk.tpip.control.application.notification;

public record NotificationCircuitStatus(String assetType, long assetId, String state,
        long consecutiveFailures, Long remainingOpenMillis, Long remainingProbeLeaseMillis) {}
