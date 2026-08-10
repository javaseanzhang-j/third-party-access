package com.ftk.tpip.release.domain.model;

import java.util.List;

public record NotificationProviderCapability(NotificationProviderType providerType, String capabilityVersion,
        String contentType, List<String> messageTypes, String credentialPlacement, boolean signingSupported,
        int defaultRateLimitPerSecond) {}
