package com.ftk.tpip.control.api.access;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

class AuthenticationTemplateControllerContractTest {
    @Test void exposesFormDrivenAuthenticationTemplates() throws Exception {
        assertArrayEquals(new String[] {"/control/v1/business-integration/authentication-templates"},
                AuthenticationTemplateController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[] {"/{templateId}/versions/{versionId}"}, AuthenticationTemplateController.class
                .getDeclaredMethod("version", long.class, long.class).getAnnotation(GetMapping.class).value());
    }
}
