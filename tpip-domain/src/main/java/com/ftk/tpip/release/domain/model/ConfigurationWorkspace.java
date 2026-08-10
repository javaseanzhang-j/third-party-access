package com.ftk.tpip.release.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record ConfigurationWorkspace(Long id,AssetCode workspaceCode,String workspaceName,Long baseBundleId,
        String environmentCode,WorkspaceLifecycleStatus lifecycleStatus,WorkspaceRiskLevel riskLevel,
        String ownerCode,long rowVersion,Instant createdAt,Instant updatedAt){
    private static final Pattern ENV=Pattern.compile("^[a-z][a-z0-9_-]*$");
    public ConfigurationWorkspace{if(id!=null&&id<=0)throw new IllegalArgumentException("id must be positive");Objects.requireNonNull(workspaceCode);workspaceName=required(workspaceName,"workspaceName",200);if(baseBundleId!=null&&baseBundleId<=0)throw new IllegalArgumentException("baseBundleId must be positive");environmentCode=required(environmentCode,"environmentCode",32);if(!ENV.matcher(environmentCode).matches())throw new IllegalArgumentException("Invalid environmentCode");Objects.requireNonNull(lifecycleStatus);Objects.requireNonNull(riskLevel);ownerCode=required(ownerCode,"ownerCode",100);if(rowVersion<0)throw new IllegalArgumentException("rowVersion must not be negative");}
    public static ConfigurationWorkspace draft(AssetCode code,String name,Long base,String environment,WorkspaceRiskLevel risk,String owner){return new ConfigurationWorkspace(null,code,name,base,environment,WorkspaceLifecycleStatus.DRAFT,risk,owner,0,null,null);}
    private static String required(String value,String field,int max){String v=Objects.requireNonNull(value,field+" must not be null").trim();if(v.isEmpty()||v.length()>max)throw new IllegalArgumentException(field+" is blank or too long");return v;}
}
