package com.ftk.tpip.control.application.notification;

public final class NotificationOutboxNotFoundException extends RuntimeException {
    public NotificationOutboxNotFoundException(long id) { super("Notification outbox task not found: " + id); }
}
