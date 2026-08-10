package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class GlobalImpactSnapshotQueryControllerContractTest {
    @Test
    void exposesStableSnapshotViewResources() throws Exception {
        var root = GlobalImpactSnapshotQueryController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[] {
                "/control/v1/verification-drift-workbench/global-governance-policy-impact-snapshot-views"
        }, root.value());
        assertArrayEquals(new String[] {"/{snapshotId}"}, mapping("snapshot", String.class).value());
        assertArrayEquals(new String[] {"/{snapshotId}/workspace-snapshots"},
                mapping("workspaceSnapshots", String.class, int.class, int.class).value());
    }

    private static GetMapping mapping(String name, Class<?>... types) throws Exception {
        return GlobalImpactSnapshotQueryController.class.getMethod(name, types).getAnnotation(GetMapping.class);
    }
}
