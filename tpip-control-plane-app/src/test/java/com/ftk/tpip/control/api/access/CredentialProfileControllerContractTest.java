package com.ftk.tpip.control.api.access;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

class CredentialProfileControllerContractTest {
    @Test void exposesBusinessCredentialProfiles() throws Exception {
        assertArrayEquals(new String[] {"/control/v1/business-integration/credential-profiles"},
                CredentialProfileController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[0], CredentialProfileController.class.getDeclaredMethod("list", Long.class)
                .getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[0], CredentialProfileController.class
                .getDeclaredMethod("create", CredentialProfileController.Create.class, String.class)
                .getAnnotation(PostMapping.class).value());
    }
}
