package com.ftk.tpip.release.domain.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DriftGovernancePolicyTest {
    @Test
    void scopeMustMatchWorkspaceIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new DriftGovernancePolicy(null,
                AssetCode.of("drift.global"), "Global", DriftGovernancePolicyScope.GLOBAL, 23L,
                DriftGovernancePolicyStatus.DRAFT, null, 0, null, null));
    }

    @Test
    void immutableVersionRejectsDuplicateSuppressions() {
        assertThrows(IllegalArgumentException.class, () -> new DriftGovernancePolicyVersion(null, 1, 0,
                Duration.ofHours(72), Duration.ofHours(24), Duration.ofHours(24), 3, "owner",
                List.of(), List.of("BUNDLE_PREVIEW", "BUNDLE_PREVIEW"), "a".repeat(64),
                DriftGovernancePolicyVersionStatus.DRAFT, null, null));
    }
}
