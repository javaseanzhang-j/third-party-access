package com.ftk.tpip.control.application.release;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

@Component
final class GlobalImpactJobMaintenanceMetrics {
    private final MeterRegistry registry;
    GlobalImpactJobMaintenanceMetrics(MeterRegistry registry){this.registry=registry;}
    void expired(int n){if(n>0)Counter.builder("tpip.global.impact.jobs.expired").register(registry).increment(n);}
    void purged(String outcome){Counter.builder("tpip.global.impact.jobs.purge").tag("outcome",outcome).register(registry).increment();}
    void orphanSnapshots(int found,int deleted){if(found>0)Counter.builder("tpip.global.impact.orphan.snapshots").tag("action","found").register(registry).increment(found);if(deleted>0)Counter.builder("tpip.global.impact.orphan.snapshots").tag("action","deleted").register(registry).increment(deleted);}
}
