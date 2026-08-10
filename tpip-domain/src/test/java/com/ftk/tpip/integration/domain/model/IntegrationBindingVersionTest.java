package com.ftk.tpip.integration.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import com.ftk.tpip.catalog.domain.model.IdempotencyClass;
import org.junit.jupiter.api.Test;

class IntegrationBindingVersionTest {
    private static final String SHA="a".repeat(64);
    @Test void createsDraftWithFrozenReferences(){var v=IntegrationBindingVersion.draft(1,2,3,4,5,6L,7L,null,8L,IdempotencyClass.IDEMPOTENT,"{}","{}",SHA);assertEquals(BindingVersionLifecycleStatus.DRAFT,v.lifecycleStatus());assertEquals(6,v.requestMappingVersionId());assertEquals(IdempotencyClass.IDEMPOTENT,v.idempotencyClass());}
    @Test void rejectsInvalidChecksum(){assertThrows(IllegalArgumentException.class,()->IntegrationBindingVersion.draft(1,2,3,4,5,6L,7L,null,null,IdempotencyClass.UNKNOWN,"{}","{}","bad"));}
    @Test void freezesOptionalAccessChannel(){var v=IntegrationBindingVersion.draft(1,2,3,4,5,9L,6L,7L,null,null,IdempotencyClass.UNKNOWN,"{}","{}",SHA);assertEquals(9,v.accessChannelId());}
}
