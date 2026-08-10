package com.ftk.tpip.runtime;

import java.util.Arrays;
import java.util.Objects;

public final class SecretValue implements AutoCloseable {
    private char[] value;

    private SecretValue(char[] value) {
        if (value.length == 0) throw new IllegalArgumentException("secret value must not be empty");
        this.value = Arrays.copyOf(value, value.length);
    }

    public static SecretValue of(char[] value) {
        return new SecretValue(Objects.requireNonNull(value, "value must not be null"));
    }

    public char[] copy() {
        if (value == null) throw new IllegalStateException("secret value has been closed");
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public void close() {
        if (value != null) {
            Arrays.fill(value, '\0');
            value = null;
        }
    }

    @Override
    public String toString() {
        return "SecretValue[REDACTED]";
    }
}
