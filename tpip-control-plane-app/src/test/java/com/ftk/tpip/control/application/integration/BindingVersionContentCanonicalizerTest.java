package com.ftk.tpip.control.application.integration;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.catalog.domain.model.IdempotencyClass;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import org.junit.jupiter.api.Test;

class BindingVersionContentCanonicalizerTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final BindingVersionContentCanonicalizer canonicalizer=new BindingVersionContentCanonicalizer(new CanonicalJsonService(mapper));
    @Test void producesStableChecksumForMetadataKeyOrder()throws Exception{var a=canonicalizer.canonicalize(1,2,3,4,5L,6L,7L,IdempotencyClass.IDEMPOTENT,mapper.readTree("{\"b\":2,\"a\":1}"),mapper.readTree("{\"region\":\"cn\"}"));var b=canonicalizer.canonicalize(1,2,3,4,5L,6L,7L,IdempotencyClass.IDEMPOTENT,mapper.readTree("{\"a\":1,\"b\":2}"),mapper.readTree("{\"region\":\"cn\"}"));assertEquals(a.checksum(),b.checksum());assertEquals("{\"a\":1,\"b\":2}",a.complianceMetadata());}
    @Test void rejectsSecretLikeMetadata()throws Exception{assertThrows(IllegalArgumentException.class,()->canonicalizer.canonicalize(1,2,3,4,5L,6L,null,IdempotencyClass.UNKNOWN,mapper.readTree("{\"clientSecret\":\"raw\"}"),null));}
}
