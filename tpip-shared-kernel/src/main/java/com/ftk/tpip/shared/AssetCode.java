package com.ftk.tpip.shared;

import java.util.Objects;
import java.util.regex.Pattern;

public record AssetCode(String value) {

    private static final Pattern FORMAT =
            Pattern.compile("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$");

    public AssetCode {
        value = Objects.requireNonNull(value, "value must not be null").trim();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid asset code: " + value);
        }
    }

    public static AssetCode of(String value) {
        return new AssetCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
