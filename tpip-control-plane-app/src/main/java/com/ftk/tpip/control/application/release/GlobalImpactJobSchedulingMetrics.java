package com.ftk.tpip.control.application.release;
import io.micrometer.core.instrument.*;import org.springframework.stereotype.Component;
@Component final class GlobalImpactJobSchedulingMetrics{private final MeterRegistry r;GlobalImpactJobSchedulingMetrics(MeterRegistry r){this.r=r;}
 void dispatch(int count,String outcome){Counter.builder("tpip.global.impact.scheduling.dispatches").tag("outcome",outcome).register(r).increment();if(count>0)Counter.builder("tpip.global.impact.scheduling.jobs").register(r).increment(count);}
 void backpressure(){Counter.builder("tpip.global.impact.scheduling.backpressure").register(r).increment();}
 void stall(String severity,int count){if(count>0)Counter.builder("tpip.global.impact.scheduling.stalled").tag("severity",severity).register(r).increment(count);}}
