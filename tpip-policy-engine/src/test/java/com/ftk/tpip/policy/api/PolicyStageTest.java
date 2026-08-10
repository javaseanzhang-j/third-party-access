package com.ftk.tpip.policy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PolicyStageTest {

    @Test
    void stagesAreExplicitAndUnique() {
        PolicyStage[] stages = PolicyStage.values();
        assertTrue(stages.length > 0);
        assertEquals(stages.length, Arrays.stream(stages).distinct().count());
    }
}
