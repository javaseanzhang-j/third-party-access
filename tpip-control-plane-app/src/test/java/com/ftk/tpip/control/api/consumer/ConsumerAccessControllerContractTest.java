package com.ftk.tpip.control.api.consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

class ConsumerAccessControllerContractTest {
    @Test void exposesConsumerAccessManagementAndPublishedSnapshot() throws Exception {
        assertArrayEquals(new String[]{"/control/v1/consumer-access"},
                ConsumerAccessController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[]{"/runtime-snapshot"},ConsumerAccessController.class
                .getDeclaredMethod("runtimeSnapshot").getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/applications/{appId}/grants"},ConsumerAccessController.class
                .getDeclaredMethod("createGrant",long.class,ConsumerAccessController.CreateGrant.class,String.class)
                .getAnnotation(PostMapping.class).value());
    }
}
