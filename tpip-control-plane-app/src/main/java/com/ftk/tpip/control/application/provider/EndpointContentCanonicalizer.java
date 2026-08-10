package com.ftk.tpip.control.application.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.provider.domain.model.EndpointHttpMethod;
import com.ftk.tpip.provider.domain.model.EndpointScheme;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class EndpointContentCanonicalizer {
    private static final Set<String> SENSITIVE_KEYS = Set.of("password","secret","secretvalue","token",
            "accesstoken","refreshtoken","apikey","clientsecret","privatekey","credential","credentialvalue");

    private final ObjectMapper objectMapper;
    private final CanonicalJsonService canonicalJson;

    public EndpointContentCanonicalizer(ObjectMapper objectMapper, CanonicalJsonService canonicalJson) {
        this.objectMapper = objectMapper;
        this.canonicalJson = canonicalJson;
    }

    CanonicalEndpointContent canonicalize(
            long providerContractId,
            String endpointCode,
            String environmentCode,
            EndpointScheme protocolScheme,
            String baseUrl,
            String resourcePath,
            EndpointHttpMethod httpMethod,
            String contentType,
            String charsetName,
            int connectTimeoutMs,
            int readTimeoutMs,
            int totalTimeoutMs,
            Long credentialRefId,
            JsonNode networkConfig,
            JsonNode tlsConfig) {
        validateObject("networkConfig", networkConfig);
        validateObject("tlsConfig", tlsConfig);
        rejectSensitive(networkConfig,"networkConfig");
        rejectSensitive(tlsConfig,"tlsConfig");
        if (protocolScheme == EndpointScheme.HTTP && !CanonicalJsonService.isMissing(tlsConfig)) {
            throw new IllegalArgumentException("tlsConfig is only valid for HTTPS endpoints");
        }
        ObjectNode content = objectMapper.createObjectNode();
        String normalizedBaseUrl = baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        content.put("providerContractId", providerContractId);
        content.put("endpointCode", endpointCode);
        content.put("environmentCode", environmentCode.trim());
        content.put("protocolScheme", protocolScheme.value());
        content.put("baseUrl", normalizedBaseUrl);
        content.put("resourcePath", resourcePath.trim());
        content.put("httpMethod", httpMethod.name());
        if (contentType != null && !contentType.isBlank()) {
            content.put("contentType", contentType.trim());
        }
        content.put("charsetName", charsetName.trim());
        content.put("connectTimeoutMs", connectTimeoutMs);
        content.put("readTimeoutMs", readTimeoutMs);
        content.put("totalTimeoutMs", totalTimeoutMs);
        if (credentialRefId != null) {
            content.put("credentialRefId", credentialRefId);
        }
        if (!CanonicalJsonService.isMissing(networkConfig)) {
            content.set("networkConfig", canonicalJson.canonicalNode(networkConfig));
        }
        if (!CanonicalJsonService.isMissing(tlsConfig)) {
            content.set("tlsConfig", canonicalJson.canonicalNode(tlsConfig));
        }
        String canonicalContent = canonicalJson.write(canonicalJson.canonicalNode(content));
        return new CanonicalEndpointContent(
                canonicalJson.canonicalString(networkConfig),
                canonicalJson.canonicalString(tlsConfig),
                canonicalJson.sha256(canonicalContent));
    }

    private static void validateObject(String field, JsonNode value) {
        if (!CanonicalJsonService.isMissing(value) && !value.isObject()) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
    }
    private static void rejectSensitive(JsonNode node,String path){
        if(CanonicalJsonService.isMissing(node))return;
        if(node.isObject())node.properties().forEach(entry->{String next=path+"."+entry.getKey();String key=entry.getKey().toLowerCase(Locale.ROOT).replaceAll("[_\\-.]","");if(SENSITIVE_KEYS.contains(key))throw new IllegalArgumentException(next+" is sensitive; use a Secret Reference instead");rejectSensitive(entry.getValue(),next);});
        else if(node.isArray())for(int i=0;i<node.size();i++)rejectSensitive(node.get(i),path+"["+i+"]");
    }
}
