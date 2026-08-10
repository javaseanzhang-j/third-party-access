package com.ftk.tpip.runtime.access;

import static org.junit.jupiter.api.Assertions.*;
import com.ftk.tpip.runtime.SecretValue;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class ConsumerRequestAuthorizerTest {
    private static final Instant NOW=Instant.parse("2026-08-10T10:00:00Z");
    @Test void acceptsSignedRequestForPublishedServiceGrant() throws Exception {
        byte[] body="{}".getBytes(StandardCharsets.UTF_8);String timestamp=String.valueOf(NOW.toEpochMilli());
        String signature=signature("POST","/integration/v1/operations/sms.send:invoke","sms.send",timestamp,"n-1",body);
        var result=authorizer().authorize(new ConsumerRequestAuthorizer.Request("tpip_app",timestamp,"n-1",signature,
                "login","POST","/integration/v1/operations/sms.send:invoke","sms.send",body,"127.0.0.1"));
        assertEquals(7,result.applicationId());
    }
    @Test void rejectsServiceOutsideWhitelist() throws Exception {
        byte[] body="{}".getBytes(StandardCharsets.UTF_8);String timestamp=String.valueOf(NOW.toEpochMilli());
        String signature=signature("POST","/integration/v1/operations/face.verify:invoke","face.verify",timestamp,"n-2",body);
        var failure=assertThrows(ConsumerAuthorizationException.class,()->authorizer().authorize(new ConsumerRequestAuthorizer.Request(
                "tpip_app",timestamp,"n-2",signature,"login","POST","/integration/v1/operations/face.verify:invoke","face.verify",body,"127.0.0.1")));
        assertEquals("SERVICE_NOT_GRANTED",failure.code());
    }
    private ConsumerRequestAuthorizer authorizer(){
        var entry=new ConsumerAccessSnapshot.Entry(7,"member","tpip_app","env://TPIP_SECRET_TEST",NOW.minusSeconds(60),null,
                8,9,"sms.send",NOW.minusSeconds(60),null,List.of(),Set.of("login"));
        return new ConsumerRequestAuthorizer(()->new ConsumerAccessSnapshot("v1",NOW,"x",List.of(entry)),new com.ftk.tpip.runtime.SecretResolver(){
            public boolean supports(String ref){return true;}public SecretValue resolve(String ref){return SecretValue.of("secret".toCharArray());}},
                (app,nonce,ttl)->true,Clock.fixed(NOW,ZoneOffset.UTC),Duration.ofMinutes(5));
    }
    private static String signature(String method,String path,String service,String timestamp,String nonce,byte[] body)throws Exception{
        String material=String.join("\n",method,path,service,timestamp,nonce,ConsumerRequestAuthorizer.sha256(body));
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
    }
}
