package com.ftk.tpip.control.application.release;

import com.ftk.tpip.control.configuration.GlobalImpactJobMaintenanceProperties;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GlobalImpactJobMaintenanceScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(GlobalImpactJobMaintenanceScheduler.class);
    private static final String ACTOR="system-global-impact-maintenance";
    private final GlobalImpactJobMaintenanceOrchestrator orchestrator;
    private final GlobalImpactJobMaintenanceService service;
    private final GlobalImpactJobMaintenanceMetrics metrics;
    private final GlobalImpactJobMaintenanceProperties properties;
    public GlobalImpactJobMaintenanceScheduler(GlobalImpactJobMaintenanceOrchestrator orchestrator,
            GlobalImpactJobMaintenanceService service,GlobalImpactJobMaintenanceMetrics metrics,
            GlobalImpactJobMaintenanceProperties properties){this.orchestrator=orchestrator;this.service=service;
        this.metrics=metrics;this.properties=properties;properties.validate();}
    @Scheduled(fixedDelayString="${tpip.global-impact-job-maintenance.poll-interval:1h}")
    public void runCycle(){if(!properties.isExpiryEnabled()&&!properties.isPurgeEnabled())return;
        int expired=properties.isExpiryEnabled()?service.expireDue(properties.getBatchSize(),ACTOR).size():0;
        metrics.expired(expired);
        var result=properties.isPurgeEnabled()?orchestrator.run(properties.getRetention(),properties.getBatchSize(),
                properties.isDeleteOrphanSnapshots(),false,ACTOR,"retention policy maintenance",false)
                :new GlobalImpactJobMaintenanceOrchestrator.Result(false,0,java.util.List.of(),java.util.List.of(),0);
        LOG.info("TPIP_GLOBAL_IMPACT_MAINTENANCE expired={} candidates={} purged={} failures={} orphanDeletion={}",
                expired,result.candidates().size(),result.receipts().size(),result.failureCount(),
                properties.isDeleteOrphanSnapshots());}
}
