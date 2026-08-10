package com.ftk.tpip.control.configuration;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GlobalImpactJobMaintenancePropertiesTest {
    @Test void disabledMaintenanceNeedsNoOperationalConfiguration(){assertDoesNotThrow(new GlobalImpactJobMaintenanceProperties()::validate);}
    @Test void enabledMaintenanceRejectsUnsafeRetention(){var p=new GlobalImpactJobMaintenanceProperties();p.setPurgeEnabled(true);p.setRetention(Duration.ofHours(23));assertThrows(IllegalArgumentException.class,p::validate);}
    @Test void enabledMaintenanceAcceptsGovernedDefaults(){var p=new GlobalImpactJobMaintenanceProperties();p.setExpiryEnabled(true);assertDoesNotThrow(p::validate);}
}
