package com.ftk.tpip.runtime;

public interface SecretResolver {
    boolean supports(String secretReference);
    SecretValue resolve(String secretReference);

    static SecretResolver unavailable() {
        return new SecretResolver() {
            @Override public boolean supports(String secretReference) { return false; }
            @Override public SecretValue resolve(String secretReference) {
                throw new IllegalStateException("No Secret Resolver is configured for the reference");
            }
        };
    }
}
