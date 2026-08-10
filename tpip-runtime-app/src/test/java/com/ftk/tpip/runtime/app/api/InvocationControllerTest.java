package com.ftk.tpip.runtime.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.runtime.*;
import com.ftk.tpip.runtime.access.*;
import com.ftk.tpip.runtime.app.infrastructure.ConsumerInvocationAuditClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InvocationControllerTest {
    @Test void authorizesBeforeReturningExplicitBundleUnavailableResult() throws Exception {
        Instant now=Instant.parse("2026-08-10T10:00:00Z");ObjectMapper json=new ObjectMapper().findAndRegisterModules();
        byte[] body="{\"meta\":{\"requestId\":\"req-1\",\"caller\":\"test\",\"tenantId\":null,\"idempotencyKey\":null,\"deadline\":null,\"attributes\":{}},\"payload\":{}}".getBytes(StandardCharsets.UTF_8);
        String path="/integration/v1/operations/customer.identity.verify:invoke",timestamp=String.valueOf(now.toEpochMilli()),nonce="nonce-1";
        var entry=new ConsumerAccessSnapshot.Entry(1,"test-app","tpip_test","env://TPIP_SECRET_TEST",now.minusSeconds(1),null,2,3,
                "customer.identity.verify",now.minusSeconds(1),null,List.of(),Set.of());
        SecretResolver secrets=new SecretResolver(){public boolean supports(String ref){return true;}public SecretValue resolve(String ref){return SecretValue.of("secret".toCharArray());}};
        var authorizer=new ConsumerRequestAuthorizer(()->new ConsumerAccessSnapshot("tpip.consumer-access/v1",now,"x",List.of(entry)),secrets,
                (app,value,ttl)->true,Clock.fixed(now,ZoneOffset.UTC),Duration.ofMinutes(5));
        RuntimePipeline pipeline=(operation,invocation)->{throw new BundleResolutionException(BundleResolutionCode.REFERENCE_NOT_FOUND,"No active Bundle reference");};
        var audits=new ConsumerInvocationAuditClient(URI.create("http://127.0.0.1:1"),Duration.ofMillis(5),Duration.ofMillis(5),json);
        HttpServletRequest servlet=(HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletRequest.class},
                (proxy,method,args)->switch(method.getName()){case "getRemoteAddr"->"127.0.0.1";case "getHeader"->"req-1";default->defaultValue(method.getReturnType());});
        var response=new InvocationController(pipeline,authorizer,audits,json).invoke("customer.identity.verify","tpip_test",timestamp,nonce,
                signature(path,timestamp,nonce,body),null,body,servlet);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,response.getStatusCode());
        @SuppressWarnings("unchecked") Map<String,Object> document=(Map<String,Object>)response.getBody();
        assertEquals("TPIP_RUNTIME_BUNDLE_UNAVAILABLE",document.get("code"));
    }
    private static String signature(String path,String timestamp,String nonce,byte[] body)throws Exception{
        String material=String.join("\n","POST",path,"customer.identity.verify",timestamp,nonce,ConsumerRequestAuthorizer.sha256(body));
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
    }
    private static Object defaultValue(Class<?> type){if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==char.class)return '\0';return 0;}
}
