package com.ftk.tpip.control.configuration;
import static org.junit.jupiter.api.Assertions.*;import java.time.Duration;import org.junit.jupiter.api.Test;
class GlobalImpactSchedulingPropertiesTest{
 @Test void governedDefaultsAreValid(){assertDoesNotThrow(new GlobalImpactSchedulingProperties()::validate);}
 @Test void criticalThresholdCannotBeShorterThanWarning(){var p=new GlobalImpactSchedulingProperties();p.setCriticalStallThreshold(Duration.ofMinutes(1));assertThrows(IllegalArgumentException.class,p::validate);}
 @Test void dynamicBatchBoundsMustBeOrdered(){var p=new GlobalImpactSchedulingProperties();p.setMinimumBatchSize(20);p.setMaximumBatchSize(10);assertThrows(IllegalArgumentException.class,p::validate);}}
