package com.ftk.tpip.access.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ftk.tpip.access.domain.model.AccessParameter;
import com.ftk.tpip.access.domain.model.AccessParameterDataType;
import com.ftk.tpip.access.domain.model.AccessParameterLocation;
import com.ftk.tpip.access.domain.model.AccessParameterOverrideMode;
import com.ftk.tpip.access.domain.model.AccessParameterScope;
import com.ftk.tpip.access.domain.model.AccessParameterSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessParameterResolverTest {
    private final AccessParameterResolver resolver = new AccessParameterResolver();

    @Test
    void interfaceValueOverridesChannelValue() {
        AccessParameter common = parameter(AccessParameterScope.CHANNEL, null, "version", "\"1.0\"",
                AccessParameterOverrideMode.REPLACE);
        AccessParameter override = parameter(AccessParameterScope.INTERFACE, 9L, "version", "\"2.0\"",
                AccessParameterOverrideMode.REPLACE);
        var effective = resolver.resolve(List.of(common, override), 9);
        assertEquals(1, effective.size());
        assertEquals("\"2.0\"", effective.getFirst().parameter().valueDocument());
        assertEquals(AccessParameterScope.INTERFACE, effective.getFirst().resolvedFrom());
    }

    @Test
    void interfaceCanDisableInheritedParameter() {
        AccessParameter common = parameter(AccessParameterScope.CHANNEL, null, "legacy", "true",
                AccessParameterOverrideMode.REPLACE);
        AccessParameter disabled = parameter(AccessParameterScope.INTERFACE, 9L, "legacy", null,
                AccessParameterOverrideMode.DISABLE);
        assertEquals(List.of(), resolver.resolve(List.of(common, disabled), 9));
    }

    private AccessParameter parameter(AccessParameterScope scope, Long contractId, String code, String value,
            AccessParameterOverrideMode mode) {
        return new AccessParameter(null, 1, scope, contractId, code, code,
                AccessParameterLocation.QUERY, AccessParameterSource.FIXED, AccessParameterDataType.STRING,
                value, null, null, mode, false, false, true, null, 0, null, null);
    }
}
