package com.ftk.tpip.runtime.app.infrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;import java.net.http.*;import java.time.Duration;
public final class ConsumerInvocationAuditClient {
    private final URI uri;private final HttpClient client;private final ObjectMapper json;private final Duration timeout;
    public ConsumerInvocationAuditClient(URI base,Duration connect,Duration timeout,ObjectMapper json){this.uri=base.resolve("/control/v1/consumer-access/invocation-audits");this.timeout=timeout;this.json=json;this.client=HttpClient.newBuilder().connectTimeout(connect).build();}
    public void record(Audit value){try{HttpRequest request=HttpRequest.newBuilder(uri).timeout(timeout).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(value))).build();client.send(request,HttpResponse.BodyHandlers.discarding());}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception ignored){/* authorization result must not depend on audit availability */}}
    public record Audit(String requestId,Long applicationId,String appKey,String serviceCode,Long grantId,Long grantVersionId,String result,String rejectReason,long durationMs){}
}
