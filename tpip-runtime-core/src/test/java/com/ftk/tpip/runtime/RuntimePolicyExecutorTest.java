package com.ftk.tpip.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ftk.tpip.policy.api.PolicyFailureAction;
import com.ftk.tpip.policy.api.PolicyStage;
import com.ftk.tpip.policy.ir.CompiledPolicyPlan;
import com.ftk.tpip.policy.ir.CompiledPolicyStep;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimePolicyExecutorTest {
    @Test
    void resolvesApiKeyWithoutExposingSecretToDiagnosticsAndClearsSensitiveHeader() {
        SecretResolver resolver = resolver("env://TPIP_SECRET_PROVIDER_KEY", "secret-123");
        RuntimePolicyExecutor executor = new RuntimePolicyExecutor(resolver);
        InvocationContext context = new InvocationContext("req-1", "trace-1", "customer.lookup",
                JsonNodeFactory.instance.objectNode());
        var step = new CompiledPolicyStep("api-key", "builtin.auth.api-key", "1.0.0", null,
                Map.of("secretRef", "env://TPIP_SECRET_PROVIDER_KEY", "headerName", "Authorization",
                        "prefix", "ApiKey "), PolicyFailureAction.FAIL, 100);
        var plan = new CompiledPolicyPlan("auth.policy", 1,
                Map.of(PolicyStage.BEFORE_TRANSPORT, List.of(step)), "a".repeat(64));

        var result = executor.execute(PolicyStage.BEFORE_TRANSPORT, plan, context);
        assertTrue(result.success());
        assertEquals(List.of("ApiKey secret-123"), context.transportHeaders().get("Authorization"));
        assertFalse(result.diagnostics().toString().contains("secret-123"));

        context.clearSensitiveTransportHeaders();
        assertFalse(context.transportHeaders().containsKey("Authorization"));
    }

    @Test
    void failsClosedWhenResolverDoesNotSupportReference() {
        var executor = new RuntimePolicyExecutor(SecretResolver.unavailable());
        var context = new InvocationContext("req-1", "trace-1", "customer.lookup",
                JsonNodeFactory.instance.objectNode());
        var step = new CompiledPolicyStep("api-key", "builtin.auth.api-key", "1.0.0", null,
                Map.of("secretRef", "secret://provider/key"), PolicyFailureAction.FAIL, 100);
        var plan = new CompiledPolicyPlan("auth.policy", 1,
                Map.of(PolicyStage.BEFORE_TRANSPORT, List.of(step)), "a".repeat(64));
        var result = executor.execute(PolicyStage.BEFORE_TRANSPORT, plan, context);
        assertFalse(result.success());
        assertFalse(result.diagnostics().toString().contains("secret://provider/key"));
    }

    @Test
    void signsInterpolatedSourceWithHmacSha256WithoutExposingTheKey() {
        RuntimePolicyExecutor executor = new RuntimePolicyExecutor(resolver("env://TPIP_HMAC_KEY", "key"));
        InvocationContext context = new InvocationContext("req-1", "trace-1", "sms.send",
                JsonNodeFactory.instance.objectNode());
        context.putAttribute("timestamp", "1700000000");
        var step = new CompiledPolicyStep("hmac", "builtin.auth.hmac-sha256", "1.0.0", null,
                Map.of("secretRef", "env://TPIP_HMAC_KEY", "sourceTemplate", "${context.operationCode}:${context.attributes.timestamp}",
                        "headerName", "X-Signature", "encoding", "HEX_LOWER", "prefix", "sha256="),
                PolicyFailureAction.FAIL, 100);
        var plan = new CompiledPolicyPlan("auth.hmac", 1,
                Map.of(PolicyStage.BEFORE_TRANSPORT, List.of(step)), "a".repeat(64));

        var result = executor.execute(PolicyStage.BEFORE_TRANSPORT, plan, context);

        assertTrue(result.success());
        assertEquals(List.of("sha256=d054fb8c8e851c7c40d751fa10446bdd0ec5d6f25a3a9db74452f2f1450302df"),
                context.transportHeaders().get("X-Signature"));
        assertFalse(result.diagnostics().toString().contains("key"));
        context.clearSensitiveTransportHeaders();
        assertFalse(context.transportHeaders().containsKey("X-Signature"));
    }

    private static SecretResolver resolver(String expectedReference, String value) {
        return new SecretResolver() {
            @Override public boolean supports(String reference) { return expectedReference.equals(reference); }
            @Override public SecretValue resolve(String reference) { return SecretValue.of(value.toCharArray()); }
        };
    }
}
