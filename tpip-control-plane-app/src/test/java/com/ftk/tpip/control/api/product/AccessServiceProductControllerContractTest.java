package com.ftk.tpip.control.api.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

class AccessServiceProductControllerContractTest {
    @Test void exposesProductServiceAggregateEndpoints() throws Exception {
        assertArrayEquals(new String[]{"/control/v1/product-model/services"},
                AccessServiceProductController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/targets"}, AccessServiceProductController.class
                .getDeclaredMethod("addTarget", long.class, AccessServiceProductController.AddTargetRequest.class, String.class)
                .getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/targets:provision"}, AccessServiceProductController.class
                .getDeclaredMethod("provisionTarget", long.class,
                        AccessServiceProductController.ProvisionTargetRequest.class, String.class)
                .getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/targets:provision-business"}, AccessServiceProductController.class
                .getDeclaredMethod("provisionBusinessTarget", long.class,
                        AccessServiceProductController.BusinessProvisionTargetRequest.class, String.class)
                .getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/readiness"}, AccessServiceProductController.class
                .getDeclaredMethod("readiness", long.class).getAnnotation(GetMapping.class).value());
        org.junit.jupiter.api.Assertions.assertEquals(List.class,
                AccessServiceProductController.class.getDeclaredMethod("list").getReturnType());
    }
}
