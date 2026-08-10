package com.ftk.tpip.worker.notification;

final class EnvironmentNotificationSecretResolver implements NotificationSecretResolver {
    private static final String PREFIX = "env://";

    @Override
    public String resolve(String reference) {
        if (reference == null || !reference.matches("env://TPIP_SECRET_[A-Z0-9_]{1,200}")) {
            throw new NotificationDeliveryException("SECRET_REFERENCE_NOT_ALLOWED");
        }
        String value = System.getenv(reference.substring(PREFIX.length()));
        if (value == null || value.isBlank()) {
            throw new NotificationDeliveryException("SECRET_NOT_AVAILABLE");
        }
        return value.trim();
    }
}
