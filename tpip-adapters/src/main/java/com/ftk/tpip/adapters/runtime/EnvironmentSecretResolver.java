package com.ftk.tpip.adapters.runtime;

import com.ftk.tpip.runtime.SecretResolver;
import com.ftk.tpip.runtime.SecretValue;
import java.net.URI;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class EnvironmentSecretResolver implements SecretResolver {
    private static final Pattern NAME = Pattern.compile("^TPIP_SECRET_[A-Z0-9_]{1,100}$");
    private final Function<String, String> environment;

    public EnvironmentSecretResolver() {
        this(System::getenv);
    }

    public EnvironmentSecretResolver(Function<String, String> environment) {
        this.environment = Objects.requireNonNull(environment);
    }

    @Override
    public boolean supports(String secretReference) {
        try {
            return NAME.matcher(variable(secretReference)).matches();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public SecretValue resolve(String secretReference) {
        String name = variable(secretReference);
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Environment Secret Reference is outside the TPIP allowlist");
        }
        String value = environment.apply(name);
        if (value == null || value.isEmpty()) throw new IllegalStateException("Environment secret is unavailable: " + name);
        return SecretValue.of(value.toCharArray());
    }

    private static String variable(String reference) {
        URI uri = URI.create(Objects.requireNonNull(reference, "secretReference must not be null"));
        if (!"env".equalsIgnoreCase(uri.getScheme()) || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Secret Reference must use env://");
        }
        String name = uri.getRawAuthority();
        if (name == null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw new IllegalArgumentException("Invalid environment Secret Reference");
        }
        return name;
    }
}
