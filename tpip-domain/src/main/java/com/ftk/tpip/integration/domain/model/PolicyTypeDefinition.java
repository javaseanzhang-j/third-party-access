package com.ftk.tpip.integration.domain.model;

import com.ftk.tpip.shared.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public record PolicyTypeDefinition(Long id, AssetCode policyTypeCode, SemanticVersion semanticVersion,
        PolicyImplementationKind implementationKind, Set<PolicyMountStage> allowedStages,
        String configurationSchema, String runtimeCompatibility,
        PolicySecurityClassification securityClassification, boolean deterministic, boolean sideEffect,
        PolicyIdempotencyRequirement idempotencyRequirement, String implementationRef,
        String artifactChecksum, PolicyTypeStatus status, Instant createdAt) {
    private static final Pattern SHA=Pattern.compile("^[a-f0-9]{64}$");
    public PolicyTypeDefinition {
        if(id!=null&&id<=0)throw new IllegalArgumentException("id must be positive");
        Objects.requireNonNull(policyTypeCode);Objects.requireNonNull(semanticVersion);Objects.requireNonNull(implementationKind);
        allowedStages=Set.copyOf(Objects.requireNonNull(allowedStages));if(allowedStages.isEmpty())throw new IllegalArgumentException("allowedStages must not be empty");
        if(configurationSchema==null||configurationSchema.isBlank())throw new IllegalArgumentException("configurationSchema must not be blank");
        runtimeCompatibility=required(runtimeCompatibility,"runtimeCompatibility",100);Objects.requireNonNull(securityClassification);Objects.requireNonNull(status);
        if(artifactChecksum!=null&&!SHA.matcher(artifactChecksum).matches())throw new IllegalArgumentException("artifactChecksum must be lowercase SHA-256");
        if(implementationKind==PolicyImplementationKind.PLUGIN&&(implementationRef==null||implementationRef.isBlank()||artifactChecksum==null))throw new IllegalArgumentException("PLUGIN requires implementationRef and artifactChecksum");
    }
    public static PolicyTypeDefinition create(AssetCode code,SemanticVersion version,PolicyImplementationKind kind,
            Set<PolicyMountStage> stages,String schema,String compatibility,PolicySecurityClassification security,
            boolean deterministic,boolean sideEffect,PolicyIdempotencyRequirement idempotency,String ref,String checksum){
        return new PolicyTypeDefinition(null,code,version,kind,stages,schema,compatibility,security,deterministic,sideEffect,idempotency,ref,checksum,PolicyTypeStatus.ACTIVE,null);
    }
    private static String required(String v,String f,int max){String x=Objects.requireNonNull(v,f).trim();if(x.isEmpty()||x.length()>max)throw new IllegalArgumentException(f+" is blank or too long");return x;}
}
