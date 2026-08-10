package com.ftk.tpip.runtime.app.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.runtime.access.ConsumerAccessSnapshot;
import com.ftk.tpip.runtime.access.ConsumerAccessSnapshotSource;
import java.net.URI;
import java.net.http.*;
import java.time.*;

public final class HttpConsumerAccessSnapshotSource implements ConsumerAccessSnapshotSource {
    private final URI uri;private final Duration timeout,ttl,maxStale;private final HttpClient client;private final ObjectMapper json;private final Clock clock;
    private volatile Cached cached;
    public HttpConsumerAccessSnapshotSource(URI base,Duration connect,Duration timeout,Duration ttl,Duration maxStale,ObjectMapper json,Clock clock){
        this.uri=base.resolve("/control/v1/consumer-access/runtime-snapshot");this.timeout=timeout;this.ttl=ttl;this.maxStale=maxStale;this.json=json;this.clock=clock;
        this.client=HttpClient.newBuilder().connectTimeout(connect).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override public ConsumerAccessSnapshot fetch(){Cached value=cached;Instant now=clock.instant();if(value!=null&&now.isBefore(value.loadedAt.plus(ttl)))return value.snapshot;
        synchronized(this){value=cached;now=clock.instant();if(value!=null&&now.isBefore(value.loadedAt.plus(ttl)))return value.snapshot;
            try{HttpRequest request=HttpRequest.newBuilder(uri).timeout(timeout).header("Accept","application/json").GET().build();
                HttpResponse<byte[]> response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());if(response.statusCode()!=200)throw new IllegalStateException("授权快照返回HTTP "+response.statusCode());
                ConsumerAccessSnapshot snapshot=json.readValue(response.body(),ConsumerAccessSnapshot.class);if(!"tpip.consumer-access/v1".equals(snapshot.apiVersion()))throw new IllegalStateException("不支持的授权快照版本");
                cached=new Cached(snapshot,now);return snapshot;
            }catch(InterruptedException e){Thread.currentThread().interrupt();return staleOrFail(value,now,e);}catch(Exception e){return staleOrFail(value,now,e);}}
    }
    private ConsumerAccessSnapshot staleOrFail(Cached value,Instant now,Exception failure){if(value!=null&&now.isBefore(value.loadedAt.plus(maxStale)))return value.snapshot;throw new IllegalStateException("调用方授权快照不可用",failure);}
    private record Cached(ConsumerAccessSnapshot snapshot,Instant loadedAt){}
}
