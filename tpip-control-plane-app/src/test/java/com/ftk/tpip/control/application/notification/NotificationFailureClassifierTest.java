package com.ftk.tpip.control.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ftk.tpip.release.domain.model.NotificationFailureClass;
import org.junit.jupiter.api.Test;

class NotificationFailureClassifierTest {
    private final NotificationFailureClassifier classifier = new NotificationFailureClassifier();

    @Test
    void classifiesPermanentRateLimitedCircuitAndTransientFailures() {
        assertEquals(NotificationFailureClass.PERMANENT, classifier.classify("WEBHOOK_HTTP_401"));
        assertEquals(NotificationFailureClass.PERMANENT, classifier.classify("WECOM_CREDENTIAL_REJECTED"));
        assertEquals(NotificationFailureClass.RATE_LIMITED, classifier.classify("WEBHOOK_HTTP_429"));
        assertEquals(NotificationFailureClass.CIRCUIT_OPEN, classifier.classify("ENDPOINT_CIRCUIT_OPEN"));
        assertEquals(NotificationFailureClass.TRANSIENT, classifier.classify("WEBHOOK_HTTP_503"));
        assertEquals(NotificationFailureClass.TRANSIENT, classifier.classify("WEBHOOK_IO_FAILURE"));
    }
}
