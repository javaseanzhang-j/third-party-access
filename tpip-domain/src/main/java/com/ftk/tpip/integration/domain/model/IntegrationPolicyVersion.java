package com.ftk.tpip.integration.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record IntegrationPolicyVersion(Long id,long policyId,int versionNo,String dslApiVersion,
        String normalizedDocument,String compilerVersion,PolicyCompileStatus compileStatus,
        String compileDiagnostics,String contentChecksum,PolicyLifecycleStatus lifecycleStatus,
        Instant publishedAt,Instant createdAt){
    private static final Pattern SHA=Pattern.compile("^[a-f0-9]{64}$");
    public IntegrationPolicyVersion{if(id!=null&&id<=0)throw new IllegalArgumentException("id must be positive");if(policyId<=0)throw new IllegalArgumentException("policyId must be positive");if(versionNo<0)throw new IllegalArgumentException("versionNo must not be negative");if(dslApiVersion==null||dslApiVersion.isBlank())throw new IllegalArgumentException("dslApiVersion must not be blank");if(normalizedDocument==null||normalizedDocument.isBlank())throw new IllegalArgumentException("normalizedDocument must not be blank");Objects.requireNonNull(compileStatus);if(contentChecksum==null||!SHA.matcher(contentChecksum).matches())throw new IllegalArgumentException("invalid contentChecksum");Objects.requireNonNull(lifecycleStatus);if(lifecycleStatus==PolicyLifecycleStatus.PUBLISHED&&publishedAt==null)throw new IllegalArgumentException("published version requires publishedAt");}
    public static IntegrationPolicyVersion compiled(long policyId,String api,String document,String compiler,String diagnostics,String checksum){return new IntegrationPolicyVersion(null,policyId,0,api,document,compiler,PolicyCompileStatus.COMPILED,diagnostics,checksum,PolicyLifecycleStatus.DRAFT,null,null);}
}
