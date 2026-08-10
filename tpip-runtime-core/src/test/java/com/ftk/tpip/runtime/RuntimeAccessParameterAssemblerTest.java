package com.ftk.tpip.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeAccessParameterAssemblerTest {
    private final ObjectMapper json=new ObjectMapper();
    private final SecretResolver secrets=new SecretResolver(){public boolean supports(String ref){return true;}public SecretValue resolve(String ref){return SecretValue.of("secret-value".toCharArray());}};
    private final RuntimeAccessParameterAssembler assembler=new RuntimeAccessParameterAssembler(json,secrets,
            Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"),ZoneOffset.UTC),()->UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test void assemblesFrozenParametersAcrossTransportLocations()throws Exception{
        var context=new InvocationContext("r1","t1","customer.lookup",json.readTree("{\"customerId\":\"C 1\"}"));
        context.providerRequest(json.readTree("{\"name\":\"Sean\"}"));
        var endpoint=json.readTree("""
          {"baseUrl":"https://api.example","resourcePath":"/customers/{customerId}","accessParameterPlan":{"parameters":[
            {"code":"customerId","location":"PATH","source":"REQUEST","sourceSelector":"$.customerId","required":true},
            {"code":"appKey","location":"QUERY","source":"FIXED","value":"demo"},
            {"code":"X-App-Secret","location":"HEADER","source":"SECRET_REF","secretReference":"secret://demo/key","sensitive":true},
            {"code":"requestTime","location":"BODY","source":"SYSTEM_TIME"},
            {"code":"nonce","location":"SIGNATURE","source":"UUID"}
          ]}}
          """);
        var prepared=assembler.assemble(endpoint,context);
        assertEquals("/customers/C%201?appKey=demo",prepared.path("resourcePath").asText());
        assertEquals("secret-value",context.transportHeaders().get("X-App-Secret").getFirst());
        assertEquals("2026-08-10T00:00:00Z",context.providerRequest().path("requestTime").asText());
        assertEquals("00000000-0000-0000-0000-000000000001",context.attributes().get("signature_nonce"));
        context.clearSensitiveValues();
        assertFalse(context.transportHeaders().containsKey("X-App-Secret"));
    }

    @Test void rejectsMissingRequiredSource()throws Exception{
        var context=new InvocationContext("r1","t1","op",json.readTree("{}"));context.providerRequest(json.readTree("{}"));
        var endpoint=json.readTree("{\"accessParameterPlan\":{\"parameters\":[{\"code\":\"tenant\",\"location\":\"HEADER\",\"source\":\"REQUEST\",\"sourceSelector\":\"$.tenant\",\"required\":true}]}}");
        assertThrows(IllegalArgumentException.class,()->assembler.assemble(endpoint,context));
    }
}
