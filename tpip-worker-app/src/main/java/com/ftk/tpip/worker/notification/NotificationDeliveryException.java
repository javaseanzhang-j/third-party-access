package com.ftk.tpip.worker.notification;

import java.time.Duration;

public final class NotificationDeliveryException extends RuntimeException {
    private final String errorCode;
    private final Duration retryAfter;
    public NotificationDeliveryException(String errorCode) {
        this(errorCode, null, null);
    }
    public NotificationDeliveryException(String errorCode, Throwable cause) {
        this(errorCode, null, cause);
    }
    public NotificationDeliveryException(String errorCode, Duration retryAfter) {
        this(errorCode, retryAfter, null);
    }
    private NotificationDeliveryException(String errorCode, Duration retryAfter, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.retryAfter = retryAfter;
    }
    public String errorCode() { return errorCode; }
    public Duration retryAfter() { return retryAfter; }
}
