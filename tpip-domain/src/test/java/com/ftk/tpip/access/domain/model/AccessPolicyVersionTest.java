package com.ftk.tpip.access.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccessPolicyVersionTest {
    @Test
    void validatesScopeAndKeepsDisabledStepsDeterministicallySorted() {
        var version = AccessPolicyVersion.draft(1, AccessParameterScope.INTERFACE, 2L,
                AssetCode.of("aliyun.sms.interface.2.policy"), "短信接口覆盖规则", null,
                Set.of("request-trace", "authentication"), "compiler/1", "a".repeat(64));
        assertEquals(List.of("authentication", "request-trace"), version.disabledStepIds().stream().toList());
        assertEquals("INTERFACE:2", version.scopeKey());
    }

    @Test
    void rejectsEmptyOrMismatchedPolicyVersion() {
        assertThrows(IllegalArgumentException.class, () -> AccessPolicyVersion.draft(1,
                AccessParameterScope.CHANNEL, 2L, AssetCode.of("aliyun.sms.policy"), "规则", "{}", Set.of(),
                "compiler/1", "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> AccessPolicyVersion.draft(1,
                AccessParameterScope.CHANNEL, null, AssetCode.of("aliyun.sms.policy"), "规则", null, Set.of(),
                "compiler/1", "a".repeat(64)));
    }
}
