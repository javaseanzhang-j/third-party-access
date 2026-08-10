package com.ftk.tpip.control.api.access;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ProviderProductControllerContractTest {
    @Test
    void exposesProviderScopedProductsAndProductInterfaces() throws Exception {
        assertArrayEquals(new String[] {"/control/v1/product-model/provider-products"},
                ProviderProductController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[0], ProviderProductController.class.getDeclaredMethod("list", Long.class)
                .getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[0], ProviderProductController.class
                .getDeclaredMethod("create", ProviderProductController.Create.class, String.class)
                .getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[] {"/{productId}/interfaces"}, ProviderProductController.class
                .getDeclaredMethod("interfaces", long.class).getAnnotation(GetMapping.class).value());
    }
}
