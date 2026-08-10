package com.ftk.tpip.control.application.release;
import com.ftk.tpip.control.configuration.GlobalImpactSchedulingProperties;import org.slf4j.*;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component public class GlobalImpactJobStallMonitor{private static final Logger LOG=LoggerFactory.getLogger(GlobalImpactJobStallMonitor.class);private final GlobalImpactJobSchedulingService service;private final GlobalImpactSchedulingProperties p;
 public GlobalImpactJobStallMonitor(GlobalImpactJobSchedulingService service,GlobalImpactSchedulingProperties p){this.service=service;this.p=p;}
 @Scheduled(fixedDelayString="${tpip.global-impact-scheduling.stall-monitor-interval:1m}") public void inspect(){if(!p.isStallMonitorEnabled())return;for(var state:service.stalled(p.getStalledQueryLimit())){String severity=state.stalledSeconds()>=p.getCriticalStallThreshold().toSeconds()?"CRITICAL":"WARNING";
  LOG.warn("TPIP_GLOBAL_IMPACT_JOB_STALLED jobId={} severity={} stalledSeconds={} priority={} recommendation={}",state.job().jobId(),severity,state.stalledSeconds(),state.priority(),state.recoveryRecommendation());}}
}
