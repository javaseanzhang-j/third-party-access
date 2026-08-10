package com.ftk.tpip.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {
    @Test
    void incrementsPatchWithoutUserInput() {
        assertEquals("1.4.10", new SemanticVersion(1, 4, 9).nextPatch().toString());
    }

    @Test
    void rejectsPatchOverflow() {
        assertThrows(IllegalStateException.class,
                () -> new SemanticVersion(1, 0, Integer.MAX_VALUE).nextPatch());
    }
}
