package com.ftk.tpip.control.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NotificationAttemptArchivePropertiesTest {
    @Test
    void validatesProviderSpecificS3RequirementsWithoutRequiringStaticCredentials() {
        var properties = new NotificationAttemptArchiveProperties();
        assertDoesNotThrow(properties::validate);

        properties.setStorageProvider("S3");
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.getS3().setBucket("tpip-archive");
        assertDoesNotThrow(properties::validate);

        properties.getS3().setAccessKey("access");
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.getS3().setSecretKey("secret");
        assertDoesNotThrow(properties::validate);
    }
}
