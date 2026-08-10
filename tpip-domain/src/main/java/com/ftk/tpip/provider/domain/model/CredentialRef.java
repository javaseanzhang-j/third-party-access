package com.ftk.tpip.provider.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record CredentialRef(
        Long id,
        long providerId,
        AssetCode credentialCode,
        String environmentCode,
        CredentialType credentialType,
        String secretUri,
        String secretMetadata,
        CredentialStatus status,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) {

    private static final Pattern ENVIRONMENT_CODE = Pattern.compile("^[a-z][a-z0-9_-]*$");
    private static final Set<String> ALLOWED_SECRET_SCHEMES = Set.of(
            "vault", "secret", "env", "aws-secretsmanager", "azure-keyvault",
            "gcp-secretmanager", "local-secret");

    public CredentialRef {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (providerId <= 0) {
            throw new IllegalArgumentException("providerId must be positive");
        }
        credentialCode = Objects.requireNonNull(credentialCode, "credentialCode must not be null");
        environmentCode = normalizeEnvironment(environmentCode);
        credentialType = Objects.requireNonNull(credentialType, "credentialType must not be null");
        secretUri = normalizeSecretUri(secretUri);
        if (secretMetadata != null && secretMetadata.length() > 16_384) {
            throw new IllegalArgumentException("secretMetadata must not exceed 16384 characters");
        }
        status = Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion must not be negative");
        }
    }

    public static CredentialRef create(
            long providerId,
            AssetCode credentialCode,
            String environmentCode,
            CredentialType credentialType,
            String secretUri,
            String secretMetadata) {
        return new CredentialRef(
                null, providerId, credentialCode, environmentCode, credentialType, secretUri,
                secretMetadata, CredentialStatus.ACTIVE, 0, null, null);
    }

    public CredentialRef revise(
            CredentialType credentialType,
            String secretUri,
            String secretMetadata,
            CredentialStatus status,
            long expectedVersion) {
        if (id == null) {
            throw new IllegalStateException("unsaved credential reference cannot be revised");
        }
        return new CredentialRef(
                id, providerId, credentialCode, environmentCode, credentialType, secretUri,
                secretMetadata, status, expectedVersion, createdAt, updatedAt);
    }

    private static String normalizeEnvironment(String value) {
        String normalized = Objects.requireNonNull(value, "environmentCode must not be null").trim();
        if (normalized.length() > 32 || !ENVIRONMENT_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "environmentCode must match ^[a-z][a-z0-9_-]*$ and not exceed 32 characters");
        }
        return normalized;
    }

    private static String normalizeSecretUri(String value) {
        String normalized = Objects.requireNonNull(value, "secretUri must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("secretUri must not be blank or exceed 500 characters");
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_SECRET_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("secretUri uses an unsupported secret reference scheme");
            }
            if (uri.getRawSchemeSpecificPart() == null || uri.getRawSchemeSpecificPart().isBlank()) {
                throw new IllegalArgumentException("secretUri must identify a secret reference");
            }
            if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("secretUri must not contain user-info, query, or fragment data");
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("secretUri is not a valid URI", exception);
        }
    }
}
