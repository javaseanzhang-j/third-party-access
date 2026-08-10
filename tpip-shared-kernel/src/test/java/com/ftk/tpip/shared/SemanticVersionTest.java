package com.ftk.tpip.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {

    @Test
    void parsesSemanticVersion() {
        assertEquals(new SemanticVersion(1, 2, 3), SemanticVersion.parse("1.2.3"));
    }

    @Test
    void rejectsIncompleteVersion() {
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.2"));
    }
}
