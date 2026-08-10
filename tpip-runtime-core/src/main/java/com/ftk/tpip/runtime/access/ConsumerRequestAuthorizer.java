package com.ftk.tpip.runtime.access;

import com.ftk.tpip.runtime.SecretResolver;
import com.ftk.tpip.runtime.SecretValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ConsumerRequestAuthorizer {
    private final ConsumerAccessSnapshotSource snapshots;
    private final SecretResolver secrets;
    private final ConsumerNonceStore nonces;
    private final Clock clock;
    private final Duration allowedSkew;
    public ConsumerRequestAuthorizer(ConsumerAccessSnapshotSource snapshots,SecretResolver secrets,
            ConsumerNonceStore nonces,Clock clock,Duration allowedSkew){this.snapshots=snapshots;this.secrets=secrets;this.nonces=nonces;this.clock=clock;this.allowedSkew=allowedSkew;}

    public Authorization authorize(Request request){
        required(request.appKey(),"CONSUMER_UNKNOWN","缺少X-TPIP-App-Key");
        required(request.timestamp(),"SIGNATURE_INVALID","缺少X-TPIP-Timestamp");
        required(request.nonce(),"SIGNATURE_INVALID","缺少X-TPIP-Nonce");
        required(request.signature(),"SIGNATURE_INVALID","缺少X-TPIP-Signature");
        Instant requestTime;
        try{requestTime=Instant.ofEpochMilli(Long.parseLong(request.timestamp()));}
        catch(Exception failure){throw denied("SIGNATURE_INVALID","调用时间戳不合法");}
        Instant now=clock.instant();
        if(Duration.between(requestTime,now).abs().compareTo(allowedSkew)>0)throw denied("SIGNATURE_INVALID","调用时间戳超过允许窗口");
        List<ConsumerAccessSnapshot.Entry> identities=snapshots.fetch().entries().stream()
                .filter(item->item.appKey().equals(request.appKey())).toList();
        if(identities.isEmpty())throw denied("CONSUMER_UNKNOWN","调用方身份不存在或凭据未发布");
        ConsumerAccessSnapshot.Entry identity=identities.getFirst();
        if(!effective(now,identity.credentialValidFrom(),identity.credentialValidUntil()))throw denied("CREDENTIAL_EXPIRED","调用凭据不在有效期内");
        verifySignature(identity.secretReference(),request);
        if(!nonces.claim(request.appKey(),request.nonce(),allowedSkew.multipliedBy(2)))throw denied("REQUEST_REPLAYED","请求nonce已使用");
        ConsumerAccessSnapshot.Entry grant=identities.stream().filter(item->item.serviceCode().equals(request.serviceCode()))
                .filter(item->effective(now,item.grantValidFrom(),item.grantValidUntil())).findFirst()
                .orElseThrow(()->denied("SERVICE_NOT_GRANTED","当前应用未获得该业务服务授权"));
        if(!grant.allowedScenarios().isEmpty()&&(request.scenario()==null||!grant.allowedScenarios().contains(request.scenario())))
            throw denied("SCENARIO_NOT_ALLOWED","当前业务场景未获得授权");
        if(!sourceAllowed(request.sourceAddress(),grant.allowedCidrs()))throw denied("SOURCE_NOT_ALLOWED","调用来源不在允许范围内");
        return new Authorization(grant.applicationId(),grant.appCode(),grant.grantId(),grant.grantVersionId());
    }
    private void verifySignature(String reference,Request request){
        if(!secrets.supports(reference))throw denied("CREDENTIAL_UNAVAILABLE","调用凭据Secret不可用");
        String bodyHash=sha256(request.body());
        String material=String.join("\n",request.method(),request.path(),request.serviceCode(),request.timestamp(),request.nonce(),bodyHash);
        byte[] expected;
        try(SecretValue secret=secrets.resolve(reference)){
            char[] characters=secret.copy();java.nio.ByteBuffer encoded=StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(characters));
            byte[] key=new byte[encoded.remaining()];encoded.get(key);java.util.Arrays.fill(characters,'\0');
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));java.util.Arrays.fill(key,(byte)0);
            expected=HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
        }catch(ConsumerAuthorizationException failure){throw failure;}catch(Exception failure){throw denied("CREDENTIAL_UNAVAILABLE","调用凭据无法完成签名校验");}
        byte[] actual=request.signature().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        if(!MessageDigest.isEqual(expected,actual))throw denied("SIGNATURE_INVALID","请求签名不正确");
    }
    public static String sha256(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean effective(Instant now,Instant from,Instant until){return !now.isBefore(from)&&(until==null||now.isBefore(until));}
    private static boolean sourceAllowed(String source,List<String> cidrs){
        if(cidrs==null||cidrs.isEmpty())return true;if(source==null)return false;
        try{byte[] address=java.net.InetAddress.getByName(source).getAddress();return cidrs.stream().anyMatch(cidr->contains(address,cidr));}
        catch(Exception invalid){return false;}
    }
    private static boolean contains(byte[] address,String cidr){
        try{String[] parts=cidr.split("/",-1);byte[] network=java.net.InetAddress.getByName(parts[0]).getAddress();if(network.length!=address.length)return false;
            int prefix=parts.length==1?network.length*8:Integer.parseInt(parts[1]);if(prefix<0||prefix>network.length*8)return false;
            for(int i=0;i<network.length;i++){int bits=Math.min(8,Math.max(0,prefix-i*8));int mask=bits==0?0:(0xff<<(8-bits))&0xff;if((address[i]&mask)!=(network[i]&mask))return false;}return true;
        }catch(Exception invalid){return false;}
    }
    private static void required(String value,String code,String message){if(value==null||value.isBlank())throw denied(code,message);}
    private static ConsumerAuthorizationException denied(String code,String message){return new ConsumerAuthorizationException(code,message);}
    public record Request(String appKey,String timestamp,String nonce,String signature,String scenario,String method,
            String path,String serviceCode,byte[] body,String sourceAddress){}
    public record Authorization(long applicationId,String appCode,long grantId,long grantVersionId){}
}
