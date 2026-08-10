package com.ftk.tpip.control.api.access;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AccessChannelControllerContractTest {
    @Test
    void exposesProductChannelAndEffectiveConfigurationEndpoints() throws Exception {
        assertArrayEquals(new String[] {"/control/v1/product-model/channels"},
                AccessChannelController.class.getAnnotation(RequestMapping.class).value());
        GetMapping effective = AccessChannelController.class
                .getDeclaredMethod("effective", long.class, long.class).getAnnotation(GetMapping.class);
        assertArrayEquals(new String[] {"/{channelId}/effective-configuration"}, effective.value());
    }
}
