package com.ftk.tpip.provider.domain.model;

public enum EndpointScheme {
    HTTP("http"),
    HTTPS("https");

    private final String value;

    EndpointScheme(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static EndpointScheme fromValue(String value) {
        for (EndpointScheme scheme : values()) {
            if (scheme.value.equalsIgnoreCase(value)) {
                return scheme;
            }
        }
        throw new IllegalArgumentException("Unsupported endpoint scheme: " + value);
    }
}
