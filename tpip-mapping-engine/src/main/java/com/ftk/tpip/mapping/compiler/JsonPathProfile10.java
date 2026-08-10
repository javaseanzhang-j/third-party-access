package com.ftk.tpip.mapping.compiler;

import com.ftk.tpip.mapping.selector.DeterministicJsonPath;

/** Deterministic JSONPath subset used by TPIP Mapping IR v0.1. */
final class JsonPathProfile10 {
    private JsonPathProfile10() {}
    static boolean validSource(String value) { return valid(value); }
    static boolean validTarget(String value) { return valid(value); }
    private static boolean valid(String value) {
        try { DeterministicJsonPath.parse(value); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }
}
