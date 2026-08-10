package com.ftk.tpip.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.policy.api.PolicyExecutionResult;
import com.ftk.tpip.policy.api.PolicyExecutor;
import com.ftk.tpip.policy.api.PolicyFailureAction;
import com.ftk.tpip.policy.api.PolicyStage;
import com.ftk.tpip.policy.ir.CompiledPolicyPlan;
import com.ftk.tpip.policy.ir.CompiledPolicyStep;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RuntimePolicyExecutor implements PolicyExecutor<InvocationContext> {
    private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{([a-z]+(?:\\.[A-Za-z0-9_-]+)+)}");
    private static final Pattern EQUALITY = Pattern.compile("^([a-z]+(?:\\.[A-Za-z0-9_-]+)+)\\s*(==|!=)\\s*(.+)$");
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}$");
    private final SecretResolver secrets;

    public RuntimePolicyExecutor() {
        this(SecretResolver.unavailable());
    }

    public RuntimePolicyExecutor(SecretResolver secrets) {
        this.secrets = Objects.requireNonNull(secrets);
    }

    @Override
    public PolicyExecutionResult execute(PolicyStage stage, CompiledPolicyPlan plan, InvocationContext context) {
        if (plan == null) return PolicyExecutionResult.succeeded();
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(context, "context must not be null");
        List<String> diagnostics = new ArrayList<>();
        boolean success = true;
        for (CompiledPolicyStep step : plan.stages().getOrDefault(stage, List.of())) {
            if (!matches(step.conditionExpression(), context)) continue;
            try {
                execute(step, context);
            } catch (RuntimeException failure) {
                diagnostics.add(step.stepId() + ": " + safeMessage(failure));
                if (step.onFailure() != PolicyFailureAction.CONTINUE) success = false;
            }
        }
        return new PolicyExecutionResult(success, diagnostics);
    }

    private void execute(CompiledPolicyStep step, InvocationContext context) {
        switch (step.policyType()) {
            case "builtin.transport.inject" -> injectHeaders(step.parameters(), context);
            case "builtin.auth.api-key" -> apiKey(step.parameters(), context);
            case "builtin.auth.hmac-sha256" -> hmacSha256(step.parameters(), context);
            default -> throw new IllegalArgumentException("Unsupported runtime policy type "
                    + step.policyType() + "@" + step.policyVersion());
        }
    }

    private void hmacSha256(Map<String, Object> parameters, InvocationContext context) {
        String reference = required(parameters, "secretRef");
        String sourceTemplate = required(parameters, "sourceTemplate");
        String header = parameters.get("headerName") == null ? "X-Signature" : String.valueOf(parameters.get("headerName"));
        String encoding = parameters.get("encoding") == null ? "HEX_LOWER" : String.valueOf(parameters.get("encoding"));
        String prefix = parameters.get("prefix") == null ? "" : String.valueOf(parameters.get("prefix"));
        if (!HEADER_NAME.matcher(header).matches()) throw new IllegalArgumentException("headerName is invalid");
        if (sourceTemplate.length() > 16_384) throw new IllegalArgumentException("sourceTemplate is too long");
        if (!Set.of("HEX_LOWER", "BASE64").contains(encoding)) throw new IllegalArgumentException("encoding is invalid");
        if (prefix.length() > 100 || prefix.indexOf('\r') >= 0 || prefix.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("prefix is invalid");
        }
        if (!secrets.supports(reference)) throw new IllegalStateException("No Secret Resolver supports the reference");
        String source = interpolate(sourceTemplate, context);
        try (SecretValue secret = secrets.resolve(reference)) {
            char[] characters = secret.copy();
            byte[] key = utf8(characters);
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key, "HmacSHA256"));
                byte[] digest = mac.doFinal(source.getBytes(StandardCharsets.UTF_8));
                try {
                    String signature = "BASE64".equals(encoding) ? Base64.getEncoder().encodeToString(digest) : hex(digest);
                    context.putSensitiveTransportHeader(header, prefix + signature);
                } finally {
                    Arrays.fill(digest, (byte) 0);
                }
            } catch (GeneralSecurityException failure) {
                throw new IllegalStateException("HMAC-SHA256 is unavailable", failure);
            } finally {
                Arrays.fill(characters, '\0');
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    private static byte[] utf8(char[] value) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        byte[] result = new byte[encoded.remaining()];
        encoded.get(result);
        if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        return result;
    }

    private static String hex(byte[] value) {
        char[] result = new char[value.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < value.length; index++) {
            int current = value[index] & 0xff;
            result[index * 2] = digits[current >>> 4];
            result[index * 2 + 1] = digits[current & 0x0f];
        }
        return new String(result);
    }

    private void apiKey(Map<String, Object> parameters, InvocationContext context) {
        String reference = required(parameters, "secretRef");
        String header = parameters.get("headerName") == null ? "X-API-Key" : String.valueOf(parameters.get("headerName"));
        String prefix = parameters.get("prefix") == null ? "" : String.valueOf(parameters.get("prefix"));
        if (!HEADER_NAME.matcher(header).matches()) throw new IllegalArgumentException("headerName is invalid");
        if (prefix.length() > 100 || prefix.indexOf('\r') >= 0 || prefix.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("prefix is invalid");
        }
        if (!secrets.supports(reference)) throw new IllegalStateException("No Secret Resolver supports the reference");
        try (SecretValue secret = secrets.resolve(reference)) {
            char[] value = secret.copy();
            try {
                context.putSensitiveTransportHeader(header, prefix + new String(value));
            } finally {
                java.util.Arrays.fill(value, '\0');
            }
        }
    }

    private void injectHeaders(Map<String, Object> parameters, InvocationContext context) {
        Object configured = parameters.get("headers");
        if (!(configured instanceof Map<?, ?> headers)) throw new IllegalArgumentException("headers must be an object");
        headers.forEach((name, value) -> context.putTransportHeader(String.valueOf(name), interpolate(String.valueOf(value), context)));
    }

    private boolean matches(String expression, InvocationContext context) {
        if (expression == null || expression.isBlank()) return true;
        if ("true".equals(expression.trim())) return true;
        if ("false".equals(expression.trim())) return false;
        Matcher matcher = EQUALITY.matcher(expression.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("Unsupported runtime condition: " + expression);
        String actual = scalar(resolve(matcher.group(1), context));
        String expected = unquote(matcher.group(3).trim());
        boolean equal = Objects.equals(actual, expected);
        return "==".equals(matcher.group(2)) ? equal : !equal;
    }

    private String interpolate(String template, InvocationContext context) {
        Matcher matcher = INTERPOLATION.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = scalar(resolve(matcher.group(1), context));
            if (replacement == null) throw new IllegalArgumentException("Interpolation value is missing: " + matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Object resolve(String path, InvocationContext context) {
        return switch (path) {
            case "context.requestId" -> context.requestId();
            case "context.traceId" -> context.traceId();
            case "context.operationCode" -> context.operationCode();
            case "transport.statusCode" -> context.providerStatusCode();
            default -> {
                if (path.startsWith("context.attributes.")) yield context.attributes().get(path.substring(19));
                if (path.startsWith("canonical.")) yield jsonPath(context.canonicalResponse() != null
                        ? context.canonicalResponse() : context.canonicalRequest(), path.substring(10));
                if (path.startsWith("provider.")) yield jsonPath(context.providerResponse() != null
                        ? context.providerResponse() : context.providerRequest(), path.substring(9));
                yield null;
            }
        };
    }

    private static JsonNode jsonPath(JsonNode node, String path) {
        if (node == null) return null;
        JsonNode current = node;
        for (String segment : path.split("\\.")) current = current == null ? null : current.get(segment);
        return current;
    }

    private static String scalar(Object value) {
        if (value == null) return null;
        if (value instanceof JsonNode node) return node.isValueNode() && !node.isNull() ? node.asText() : null;
        return String.valueOf(value);
    }

    private static String unquote(String value) {
        if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static String required(Map<String, Object> parameters, String name) {
        Object value = parameters.get(name);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " is required");
        return text;
    }
}
