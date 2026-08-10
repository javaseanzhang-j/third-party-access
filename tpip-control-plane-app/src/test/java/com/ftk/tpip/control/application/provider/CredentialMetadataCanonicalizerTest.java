package com.ftk.tpip.control.application.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CredentialMetadataCanonicalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CredentialMetadataCanonicalizer canonicalizer =
            new CredentialMetadataCanonicalizer(new CanonicalJsonService(objectMapper));

    @Test
    void canonicalizesNonSecretOperationalHints() throws Exception {
        String result = canonicalizer.canonicalize(objectMapper.readTree("""
                {"scopes":["read"],"headerName":"X-API-Key","keyId":"key-v1"}
                """));

        assertEquals(
                "{\"headerName\":\"X-API-Key\",\"keyId\":\"key-v1\",\"scopes\":[\"read\"]}",
                result);
    }

    @Test
    void rejectsSensitiveKeysAtAnyDepth() throws Exception {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> canonicalizer.canonicalize(objectMapper.readTree("""
                {"oauth":{"client_secret":"must-not-be-here"}}
                """)));
        assertTrue(exception.getMessage().contains("client_secret"));
        assertTrue(exception.getMessage().contains("Secret Manager"));
    }

    @Test
    void permitsTokenEndpointBecauseItIsConfigurationNotASecret() throws Exception {
        String result = canonicalizer.canonicalize(objectMapper.readTree("""
                {"tokenEndpoint":"https://identity.example.test/oauth/token"}
                """));

        assertTrue(result.contains("tokenEndpoint"));
    }

    @Test
    void rejectsNonObjectMetadata() throws Exception {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> canonicalizer.canonicalize(objectMapper.readTree("[1,2]")));
        assertTrue(exception.getMessage().contains("JSON object"));
    }
}
