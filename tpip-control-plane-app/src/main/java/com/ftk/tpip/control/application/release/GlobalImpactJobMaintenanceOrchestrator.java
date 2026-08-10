package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import org.slf4j.*;

@Service
public class GlobalImpactJobMaintenanceOrchestrator {
    private static final Logger LOG=LoggerFactory.getLogger(GlobalImpactJobMaintenanceOrchestrator.class);
    private final GlobalImpactJobMaintenanceService service;
    private final GlobalImpactJobMaintenanceMetrics metrics;
    public GlobalImpactJobMaintenanceOrchestrator(GlobalImpactJobMaintenanceService service,
            GlobalImpactJobMaintenanceMetrics metrics){this.service=service;this.metrics=metrics;}
    public Result run(Duration retention,int limit,boolean deleteOrphans,boolean dryRun,String actor,String reason,
            boolean expireDue){
        List<GlobalDriftPolicyImpactJob> expired=expireDue&&!dryRun?service.expireDue(limit,actor):List.of();
        metrics.expired(expired.size());
        List<GlobalDriftPolicyImpactJob> candidates=service.candidates(retention,limit);
        if(dryRun)return new Result(true,expired.size(),candidates,List.of(),0);
        List<GlobalDriftPolicyImpactJobPurgeReceipt> receipts=new ArrayList<>();int failures=0;
        for(var candidate:candidates){try{var receipt=service.purge(candidate.jobId(),candidate.rowVersion(),retention,
                    deleteOrphans,actor,reason);receipts.add(receipt);metrics.purged("success");
                metrics.orphanSnapshots(receipt.orphanSnapshotCount(),receipt.deletedSnapshotCount());
            }catch(RuntimeException exception){failures++;metrics.purged("failure");
                LOG.warn("TPIP_GLOBAL_IMPACT_PURGE_FAILED jobId={} reason={}",candidate.jobId(),exception.getClass().getSimpleName());}}
        return new Result(false,expired.size(),candidates,List.copyOf(receipts),failures);
    }
    public record Result(boolean dryRun,int expiredCount,List<GlobalDriftPolicyImpactJob> candidates,
            List<GlobalDriftPolicyImpactJobPurgeReceipt> receipts,int failureCount){}
}
