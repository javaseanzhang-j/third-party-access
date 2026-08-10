package com.ftk.tpip.worker.notification;

interface NotificationEndpointCircuitBreaker {
    void beforeDelivery(NotificationTask task);
    void recordSuccess(NotificationTask task);
    void recordFailure(NotificationTask task, String errorCode);
}
