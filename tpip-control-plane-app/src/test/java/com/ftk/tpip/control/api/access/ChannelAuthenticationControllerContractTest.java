package com.ftk.tpip.control.api.access;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

class ChannelAuthenticationControllerContractTest {
    @Test void exposesTemplateDrivenChannelAuthenticationVersions() throws Exception {
        assertArrayEquals(new String[] {"/control/v1/business-integration/channels/{channelId}/authentication-versions"},
                ChannelAuthenticationController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[] {"/{versionId}:publish"}, ChannelAuthenticationController.class
                .getDeclaredMethod("publish", long.class, long.class, String.class)
                .getAnnotation(PostMapping.class).value());
    }
}
