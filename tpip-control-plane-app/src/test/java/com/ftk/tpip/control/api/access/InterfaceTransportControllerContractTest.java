package com.ftk.tpip.control.api.access;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

class InterfaceTransportControllerContractTest {
    @Test void exposesVersionedInterfaceTransportWithoutBaseUrl() throws Exception {
        assertArrayEquals(new String[] {"/control/v1/business-integration/third-party-interfaces/{interfaceId}/transport-versions"},
                InterfaceTransportController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[] {"/{versionId}:publish"}, InterfaceTransportController.class
                .getDeclaredMethod("publish", long.class, long.class, String.class)
                .getAnnotation(PostMapping.class).value());
    }
}
