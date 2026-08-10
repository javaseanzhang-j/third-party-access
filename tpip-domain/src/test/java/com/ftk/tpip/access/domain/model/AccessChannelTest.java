package com.ftk.tpip.access.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import org.junit.jupiter.api.Test;

class AccessChannelTest {
    @Test
    void normalizesTrailingSlash() {
        AccessChannel channel = AccessChannel.create(1, AssetCode.of("aliyun.sms"), "阿里云短信",
                "https://dysmsapi.aliyuncs.com/", 2L, null);
        assertEquals("https://dysmsapi.aliyuncs.com", channel.baseUrl());
    }

    @Test
    void rejectsUrlContainingQuery() {
        assertThrows(IllegalArgumentException.class, () -> AccessChannel.create(1,
                AssetCode.of("aliyun.sms"), "阿里云短信", "https://example.com?token=x", null, null));
    }
}
