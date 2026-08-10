package com.ftk.tpip.control.api.access;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

class BusinessRequestPreviewControllerContractTest {
    @Test void exposesBusinessRequestPreviewUnderSelectedChannelAndInterface() {
        assertArrayEquals(new String[] {
                "/control/v1/business-integration/channels/{channelId}/interfaces/{interfaceId}/request-preview"
        }, BusinessRequestPreviewController.class.getAnnotation(RequestMapping.class).value());
    }
}
