package com.ftk.tpip.bundle;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.mapping.api.MappingDirection;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DefaultBundleCompilerTest {
    private final ObjectMapper json=new ObjectMapper();private final Clock clock=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);private final DefaultBundleCompiler compiler=new DefaultBundleCompiler(json,clock);
    @Test void compilesDeterministicRuntimeManifest()throws Exception{var first=compiler.compile(request(List.of(response(),request()),List.of("secret://p/prod/key","secret://p/prod/key"),"{\"b\":2,\"a\":1}"));var second=compiler.compile(request(List.of(request(),response()),List.of("secret://p/prod/key"),"{\"a\":1,\"b\":2}"));assertEquals(first.checksum(),second.checksum());assertEquals(64,first.checksum().length());assertTrue(BundleIntegrity.contentChecksumMatches(json,first));assertEquals(1,first.secretReferences().size());assertEquals(Instant.parse("2026-01-01T00:00:00Z"),first.compiledAt());}
    @Test void requiresBothSynchronousMappingDirections()throws Exception{var ex=assertThrows(IllegalArgumentException.class,()->compiler.compile(request(List.of(request()),List.of(),"{}")));assertTrue(ex.getMessage().contains("INBOUND_RESPONSE"));}
    @Test void rejectsNonSecretReference()throws Exception{assertThrows(IllegalArgumentException.class,()->compiler.compile(request(List.of(request(),response()),List.of("https://example.test/key"),"{}")));}
    private BundleCompilationRequest request(List<CompiledMappingPlan> plans,List<String> secrets,String endpoint)throws Exception{return new BundleCompilationRequest("refund.provider-a.prod","1.0.0","payment.refund","prod","payment.refund.provider-a@1",json.readTree("{\"type\":\"object\"}"),json.readTree("{\"type\":\"object\"}"),json.readTree("{\"semanticVersion\":\"1.0.0\"}"),plans,null,json.readTree(endpoint),secrets,">=0.1 <1.0");}
    private CompiledMappingPlan request(){return new CompiledMappingPlan("request.mapping",1,MappingDirection.OUTBOUND_REQUEST,List.of(),"a".repeat(64));}
    private CompiledMappingPlan response(){return new CompiledMappingPlan("response.mapping",1,MappingDirection.INBOUND_RESPONSE,List.of(),"b".repeat(64));}
}
