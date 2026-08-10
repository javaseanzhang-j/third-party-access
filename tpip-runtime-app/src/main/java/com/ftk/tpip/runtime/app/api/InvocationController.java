package com.ftk.tpip.runtime.app.api;

import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.runtime.BundleResolutionException;
import com.ftk.tpip.contract.InvocationResponse;
import com.ftk.tpip.runtime.RuntimePipeline;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.runtime.access.ConsumerAuthorizationException;
import com.ftk.tpip.runtime.access.ConsumerRequestAuthorizer;
import com.ftk.tpip.runtime.app.infrastructure.ConsumerInvocationAuditClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvocationController {
    private final RuntimePipeline pipeline;
    private final ConsumerRequestAuthorizer authorizer;
    private final ConsumerInvocationAuditClient audits;
    private final ObjectMapper json;

    public InvocationController(RuntimePipeline pipeline,ConsumerRequestAuthorizer authorizer,
            ConsumerInvocationAuditClient audits,ObjectMapper json) {
        this.pipeline = pipeline;this.authorizer=authorizer;this.audits=audits;this.json=json;
    }

    @PostMapping("/integration/v1/operations/{operationCode}:invoke")
    public ResponseEntity<?> invoke(
            @PathVariable String operationCode,
            @RequestHeader(value="X-TPIP-App-Key",required=false) String appKey,
            @RequestHeader(value="X-TPIP-Timestamp",required=false) String timestamp,
            @RequestHeader(value="X-TPIP-Nonce",required=false) String nonce,
            @RequestHeader(value="X-TPIP-Signature",required=false) String signature,
            @RequestHeader(value="X-TPIP-Scenario",required=false) String scenario,
            @RequestBody byte[] body,HttpServletRequest servletRequest) {
        long started=System.nanoTime();String requestId=servletRequest.getHeader("X-Request-Id");
        String path="/integration/v1/operations/"+operationCode+":invoke";
        ConsumerRequestAuthorizer.Authorization authorization;
        try { authorization=authorizer.authorize(new ConsumerRequestAuthorizer.Request(appKey,timestamp,nonce,signature,scenario,
                    "POST",path,operationCode,body,servletRequest.getRemoteAddr()));
        } catch(ConsumerAuthorizationException denied){
            audits.record(new ConsumerInvocationAuditClient.Audit(safeRequestId(requestId),null,appKey,operationCode,null,null,"DENIED",denied.code(),elapsed(started)));
            int status=denied.code().equals("SERVICE_NOT_GRANTED")||denied.code().equals("SCENARIO_NOT_ALLOWED")||denied.code().equals("SOURCE_NOT_ALLOWED")?403:401;
            return ResponseEntity.status(status).body(Map.of("operationCode",operationCode,"requestId",safeRequestId(requestId),"code",denied.code(),"message",denied.getMessage()));
        } catch(RuntimeException unavailable){
            return ResponseEntity.status(503).body(Map.of("operationCode",operationCode,"requestId",safeRequestId(requestId),"code","CONSUMER_AUTHORIZATION_UNAVAILABLE","message",unavailable.getMessage()));
        }
        InvocationRequest request;
        try{request=json.readValue(body,InvocationRequest.class);}catch(Exception invalid){return ResponseEntity.badRequest().body(Map.of("code","INVALID_INVOCATION_REQUEST","message","调用报文不是合法的InvocationRequest"));}
        audits.record(new ConsumerInvocationAuditClient.Audit(request.meta().requestId(),authorization.applicationId(),appKey,operationCode,
                authorization.grantId(),authorization.grantVersionId(),"ALLOWED",null,elapsed(started)));
        try {
            InvocationResponse response = pipeline.invoke(operationCode, request);
            return ResponseEntity.ok(response);
        } catch (BundleResolutionException exception) {
            return ResponseEntity.status(503).body(Map.of(
                    "operationCode", operationCode,
                    "requestId", request.meta().requestId(),
                    "code", "TPIP_RUNTIME_BUNDLE_UNAVAILABLE",
                    "resolutionCode", exception.code().name(),
                    "message", exception.getMessage()));
        }
    }
    private static long elapsed(long started){return Math.max(0,(System.nanoTime()-started)/1_000_000);}
    private static String safeRequestId(String value){return value==null||value.isBlank()?"unknown":value.substring(0,Math.min(128,value.length()));}
}
