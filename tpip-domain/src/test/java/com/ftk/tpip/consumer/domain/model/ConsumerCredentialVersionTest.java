package com.ftk.tpip.consumer.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConsumerCredentialVersionTest {
    private static final String SHA="a".repeat(64);
    @Test void acceptsEnvironmentSecretReferenceWithoutSecretValue(){
        var value=ConsumerCredentialVersion.draft(1,"tpip_123456789012345678901234","env://TPIP_SECRET_MEMBER_CENTER",
                Instant.parse("2026-01-01T00:00:00Z"),null,SHA);
        assertEquals("env://TPIP_SECRET_MEMBER_CENTER",value.secretReference());
        assertEquals(ConsumerCredentialVersion.LifecycleStatus.DRAFT,value.lifecycleStatus());
    }
    @Test void rejectsNonReferenceSecret(){
        assertThrows(IllegalArgumentException.class,()->ConsumerCredentialVersion.draft(1,
                "tpip_123456789012345678901234","plain-secret",Instant.now(),null,SHA));
    }
}
