package com.ftk.tpip.control.api.mcp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class McpToolAssetControllerContractTest {

    @Test
    void exposesVersionSpecificContractImpactWithoutMutationCommand() throws Exception {
        assertArrayEquals(new String[] {"/control/v1/mcp-tools"},
                McpToolAssetController.class.getAnnotation(RequestMapping.class).value());
        GetMapping mapping = McpToolAssetController.class
                .getDeclaredMethod("contractImpact", long.class, long.class)
                .getAnnotation(GetMapping.class);
        assertArrayEquals(new String[] {"/{toolId}/versions/{versionId}/contract-impact"}, mapping.value());
    }
}
