package com.ftk.tpip.worker.notification;

public interface NotificationProvider {
    boolean supports(NotificationTask task);
    void deliver(NotificationTask task);
}
