package com.ftk.tpip.worker.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class NotificationDispatcherPropertiesTest {
    @Test
    void validatesEndpointSnapshotAtExecutionBoundary() {
        NotificationDispatcherProperties properties = enabledProperties();

        properties.validate();

        assertEquals(URI.create("http://127.0.0.1:19001/ops"),
                properties.validateEndpoint("http://127.0.0.1:19001/ops"));
    }

    @Test
    void rejectsUnsafeEndpointSnapshot() {
        NotificationDispatcherProperties properties = enabledProperties();
        assertThrows(NotificationDeliveryException.class,
                () -> properties.validateEndpoint("http://user:secret@127.0.0.1:19001/a"));
        assertThrows(NotificationDeliveryException.class,
                () -> properties.validateEndpoint("http://127.0.0.1:19001/a?token=secret"));
    }

    private static NotificationDispatcherProperties enabledProperties() {
        NotificationDispatcherProperties properties = new NotificationDispatcherProperties();
        properties.setEnabled(true);
        properties.setAutomationToken("0123456789abcdef-test");
        properties.setAllowHttpWebhooks(true);
        return properties;
    }
}
