package com.ftk.tpip.control.application.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.provider.domain.model.EndpointHttpMethod;
import com.ftk.tpip.provider.domain.model.EndpointScheme;
import org.junit.jupiter.api.Test;

class EndpointContentCanonicalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalJsonService canonicalJson = new CanonicalJsonService(objectMapper);
    private final EndpointContentCanonicalizer canonicalizer =
            new EndpointContentCanonicalizer(objectMapper, canonicalJson);

    @Test
    void normalizesJsonAndBaseUrlBeforeHashing() throws Exception {
        var firstNetwork = objectMapper.readTree("{\"proxy\":{\"port\":8080,\"host\":\"proxy\"}}");
        var secondNetwork = objectMapper.readTree("{\"proxy\":{\"host\":\"proxy\",\"port\":8080}}");

        CanonicalEndpointContent first = canonicalizer.canonicalize(
                1, "payment.query", "dev", EndpointScheme.HTTPS,
                "https://api.example.com/", "/v1/query", EndpointHttpMethod.POST,
                "application/json", "UTF-8", 1000, 3000, 5000,
                null, firstNetwork, objectMapper.readTree("{\"verifyHostname\":true}"));
        CanonicalEndpointContent second = canonicalizer.canonicalize(
                1, "payment.query", "dev", EndpointScheme.HTTPS,
                "https://api.example.com", "/v1/query", EndpointHttpMethod.POST,
                "application/json", "UTF-8", 1000, 3000, 5000,
                null, secondNetwork, objectMapper.readTree("{\"verifyHostname\":true}"));

        assertEquals(first.networkConfig(), second.networkConfig());
        assertEquals(first.checksum(), second.checksum());
    }

    @Test
    void rejectsTlsConfigForHttp() throws Exception {
        var tls = objectMapper.readTree("{\"verifyHostname\":true}");
        assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize(
                1, "payment.query", "dev", EndpointScheme.HTTP,
                "http://api.example.com", "/v1/query", EndpointHttpMethod.GET,
                null, "UTF-8", 1000, 3000, 5000,
                null, null, tls));
    }

    @Test
    void rejectsRawSecretsInEndpointMetadata() throws Exception {
        var network = objectMapper.readTree("{\"proxy\":{\"password\":\"raw-secret\"}}");
        assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize(
                1, "payment.query", "dev", EndpointScheme.HTTPS,
                "https://api.example.com", "/v1/query", EndpointHttpMethod.POST,
                "application/json", "UTF-8", 1000, 3000, 5000,
                null, network, null));
    }
}
