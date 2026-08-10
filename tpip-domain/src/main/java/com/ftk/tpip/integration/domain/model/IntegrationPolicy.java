package com.ftk.tpip.integration.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record IntegrationPolicy(Long id,long bindingId,AssetCode policyCode,String policyName,
        PolicyStatus status,long rowVersion,Instant createdAt,Instant updatedAt){
    public IntegrationPolicy{if(id!=null&&id<=0)throw new IllegalArgumentException("id must be positive");if(bindingId<=0)throw new IllegalArgumentException("bindingId must be positive");Objects.requireNonNull(policyCode);policyName=required(policyName);Objects.requireNonNull(status);if(rowVersion<0)throw new IllegalArgumentException("rowVersion must not be negative");}
    public static IntegrationPolicy create(long bindingId,AssetCode code,String name){return new IntegrationPolicy(null,bindingId,code,name,PolicyStatus.ACTIVE,0,null,null);}
    public IntegrationPolicy revise(String name,PolicyStatus status,long expected){if(id==null)throw new IllegalStateException("unsaved policy cannot be revised");return new IntegrationPolicy(id,bindingId,policyCode,name,status,expected,createdAt,updatedAt);}
    private static String required(String v){String x=Objects.requireNonNull(v).trim();if(x.isEmpty()||x.length()>200)throw new IllegalArgumentException("policyName is blank or too long");return x;}
}
