package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactJobRepository;
import java.time.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalImpactJobMaintenanceService {
    private final GlobalDriftPolicyImpactJobRepository jobs;
    public GlobalImpactJobMaintenanceService(GlobalDriftPolicyImpactJobRepository jobs){this.jobs=jobs;}
    @Transactional public List<GlobalDriftPolicyImpactJob> expireDue(int limit,String actor){
        bounds(limit); return jobs.expireDue(Instant.now(),limit,required(actor,"actor",100));
    }
    @Transactional(readOnly=true) public List<GlobalDriftPolicyImpactJob> candidates(Duration retention,int limit){
        validate(retention,limit); return jobs.findPurgeCandidates(Instant.now().minus(retention),limit);
    }
    @Transactional public GlobalDriftPolicyImpactJobPurgeReceipt purge(String id,long rowVersion,Duration retention,
            boolean deleteOrphans,String actor,String reason){
        validate(retention,1); Instant now=Instant.now(); return jobs.purge(id,rowVersion,now.minus(retention),deleteOrphans,
                required(actor,"actor",100),required(reason,"reason",500),now);
    }
    @Transactional(readOnly=true) public GlobalDriftPolicyImpactJobPurgeReceipt receipt(String jobId){
        return jobs.findPurgeReceipt(required(jobId,"jobId",36)).orElseThrow(()->
                new IllegalArgumentException("Purge receipt does not exist: "+jobId));}
    private static void validate(Duration retention,int limit){if(retention==null||retention.compareTo(Duration.ofDays(1))<0
            ||retention.compareTo(Duration.ofDays(3650))>0)throw new IllegalArgumentException("retention must be between 1 and 3650 days");bounds(limit);}
    private static void bounds(int limit){if(limit<1||limit>100)throw new IllegalArgumentException("limit must be between 1 and 100");}
    private static String required(String v,String f,int max){if(v==null||v.isBlank()||v.trim().length()>max)throw new IllegalArgumentException(f+" is invalid");return v.trim();}
}
