package com.ftk.tpip.policy.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.policy.api.PolicyFailureAction;
import com.ftk.tpip.policy.api.PolicyStage;
import com.ftk.tpip.policy.ir.CompiledPolicyPlan;
import com.ftk.tpip.policy.ir.CompiledPolicyStep;
import com.ftk.tpip.policy.ir.PolicyPlanLayer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyPlanComposerTest {
    private final PolicyPlanComposer composer = new PolicyPlanComposer();

    @Test
    void composesChannelInterfaceAndImplementationInOrderWithOverrideAndDisable() {
        var channel = plan("channel", step("request-id", "builtin.transport.inject"), step("auth", "builtin.auth.api-key"));
        var contract = plan("interface", step("auth", "builtin.auth.hmac-sha256"), step("interface-header", "builtin.transport.inject"));
        var implementation = plan("implementation", step("implementation-header", "builtin.transport.inject"));

        CompiledPolicyPlan result = composer.compose("sms.send.effective-policy", 1, List.of(
                new PolicyPlanLayer("channel", channel, Set.of()),
                new PolicyPlanLayer("interface", contract, Set.of("request-id")),
                new PolicyPlanLayer("implementation", implementation, Set.of())));

        assertEquals(List.of("auth", "interface-header", "implementation-header"), result.stages()
                .get(PolicyStage.BEFORE_TRANSPORT).stream().map(CompiledPolicyStep::stepId).toList());
        assertEquals("builtin.auth.hmac-sha256", result.stages().get(PolicyStage.BEFORE_TRANSPORT).getFirst().policyType());
        assertEquals(64, result.checksum().length());
    }

    @Test
    void producesStableChecksumAndRejectsDuplicateLayerIdentity() {
        var layer = new PolicyPlanLayer("channel", plan("channel", step("auth", "builtin.auth.api-key")), Set.of());
        assertEquals(composer.compose("sms.send.policy", 1, List.of(layer)).checksum(),
                composer.compose("sms.send.policy", 1, List.of(layer)).checksum());
        assertThrows(IllegalArgumentException.class,
                () -> composer.compose("sms.send.policy", 1, List.of(layer, layer)));
    }

    private static CompiledPolicyPlan plan(String code, CompiledPolicyStep... steps) {
        return new CompiledPolicyPlan(code, 1, Map.of(PolicyStage.BEFORE_TRANSPORT, List.of(steps)), "a".repeat(64));
    }

    private static CompiledPolicyStep step(String id, String type) {
        return new CompiledPolicyStep(id, type, "1.0.0", null, Map.of(), PolicyFailureAction.FAIL, 100);
    }
}
