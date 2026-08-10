package com.ftk.tpip.runtime.access;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ConsumerAccessSnapshot(String apiVersion, Instant generatedAt, String checksum, List<Entry> entries) {
    public ConsumerAccessSnapshot { entries=entries==null?List.of():List.copyOf(entries); }
    public record Entry(long applicationId,String appCode,String appKey,String secretReference,
            Instant credentialValidFrom,Instant credentialValidUntil,long grantId,long grantVersionId,
            String serviceCode,Instant grantValidFrom,Instant grantValidUntil,List<String> allowedCidrs,
            Set<String> allowedScenarios) {
        public Entry { allowedCidrs=allowedCidrs==null?List.of():List.copyOf(allowedCidrs);allowedScenarios=allowedScenarios==null?Set.of():Set.copyOf(allowedScenarios); }
    }
}
