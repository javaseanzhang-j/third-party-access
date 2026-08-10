package com.ftk.tpip.control.application.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.provider.domain.exception.CredentialRefAlreadyExistsException;
import com.ftk.tpip.provider.domain.model.CredentialRef;
import com.ftk.tpip.provider.domain.model.CredentialRefQuery;
import com.ftk.tpip.provider.domain.model.CredentialStatus;
import com.ftk.tpip.provider.domain.model.CredentialType;
import com.ftk.tpip.provider.domain.model.ProviderStatus;
import com.ftk.tpip.provider.domain.repository.CredentialRefRepository;
import com.ftk.tpip.provider.domain.repository.ProviderRepository;
import com.ftk.tpip.shared.AssetCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredentialRefApplicationService {

    private final CredentialRefRepository credentialRepository;
    private final ProviderRepository providerRepository;
    private final CredentialMetadataCanonicalizer metadataCanonicalizer;

    public CredentialRefApplicationService(
            CredentialRefRepository credentialRepository,
            ProviderRepository providerRepository,
            CredentialMetadataCanonicalizer metadataCanonicalizer) {
        this.credentialRepository = credentialRepository;
        this.providerRepository = providerRepository;
        this.metadataCanonicalizer = metadataCanonicalizer;
    }

    @Transactional
    public CredentialRef create(
            long providerId,
            String credentialCode,
            String environmentCode,
            CredentialType credentialType,
            String secretUri,
            JsonNode secretMetadata,
            String actor) {
        requireActiveProvider(providerId);
        AssetCode code = AssetCode.of(credentialCode);
        String normalizedActor = normalizeActor(actor);
        if (credentialRepository.findByCodeAndEnvironment(code, environmentCode).isPresent()) {
            throw new CredentialRefAlreadyExistsException(code.value(), environmentCode);
        }
        CredentialRef credential = CredentialRef.create(
                providerId,
                code,
                environmentCode,
                credentialType,
                secretUri,
                metadataCanonicalizer.canonicalize(secretMetadata));
        return credentialRepository.create(credential, normalizedActor);
    }

    @Transactional(readOnly = true)
    public CredentialRef get(long id) {
        return credentialRepository.findById(id).orElseThrow(() -> new CredentialRefNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public CredentialRefPage findAll(
            Long providerId,
            String environmentCode,
            String keyword,
            CredentialStatus status,
            int page,
            int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page offset is too large");
        }
        CredentialRefQuery query = new CredentialRefQuery(
                providerId, environmentCode, keyword, status, (int) offset, size);
        return new CredentialRefPage(
                credentialRepository.findAll(query), page, size, credentialRepository.count(query));
    }

    @Transactional
    public CredentialRef update(
            long id,
            CredentialType credentialType,
            String secretUri,
            JsonNode secretMetadata,
            CredentialStatus status,
            long rowVersion,
            String actor) {
        CredentialRef current = get(id);
        requireActiveProvider(current.providerId());
        CredentialRef revised = current.revise(
                credentialType,
                secretUri,
                metadataCanonicalizer.canonicalize(secretMetadata),
                status,
                rowVersion);
        return credentialRepository.update(revised, normalizeActor(actor));
    }

    private void requireActiveProvider(long providerId) {
        var provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        if (provider.status() != ProviderStatus.ACTIVE) {
            throw new IllegalArgumentException("providerId must reference an ACTIVE provider");
        }
    }

    private static String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("X-Operator must not be blank");
        }
        String normalized = actor.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("X-Operator must not exceed 100 characters");
        }
        return normalized;
    }
}
