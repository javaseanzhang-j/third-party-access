package com.ftk.tpip.control.application.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.provider.domain.exception.EndpointCredentialMismatchException;
import com.ftk.tpip.provider.domain.model.ContractStatus;
import com.ftk.tpip.provider.domain.model.EndpointHttpMethod;
import com.ftk.tpip.provider.domain.model.EndpointLifecycleStatus;
import com.ftk.tpip.provider.domain.model.EndpointScheme;
import com.ftk.tpip.provider.domain.model.ProviderContract;
import com.ftk.tpip.provider.domain.model.ProviderEndpoint;
import com.ftk.tpip.provider.domain.model.ProviderEndpointQuery;
import com.ftk.tpip.provider.domain.repository.ProviderContractRepository;
import com.ftk.tpip.provider.domain.repository.ProviderEndpointRepository;
import com.ftk.tpip.shared.AssetCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderEndpointApplicationService {

    private final ProviderEndpointRepository endpointRepository;
    private final ProviderContractRepository contractRepository;
    private final EndpointContentCanonicalizer contentCanonicalizer;

    public ProviderEndpointApplicationService(
            ProviderEndpointRepository endpointRepository,
            ProviderContractRepository contractRepository,
            EndpointContentCanonicalizer contentCanonicalizer) {
        this.endpointRepository = endpointRepository;
        this.contractRepository = contractRepository;
        this.contentCanonicalizer = contentCanonicalizer;
    }

    @Transactional
    public ProviderEndpoint createRevision(
            long providerContractId,
            String endpointCode,
            String environmentCode,
            EndpointScheme protocolScheme,
            String baseUrl,
            String resourcePath,
            EndpointHttpMethod httpMethod,
            String contentType,
            String charsetName,
            int connectTimeoutMs,
            int readTimeoutMs,
            int totalTimeoutMs,
            Long credentialRefId,
            JsonNode networkConfig,
            JsonNode tlsConfig,
            String actor) {
        requireActiveContract(providerContractId);
        validateCredential(providerContractId, credentialRefId, environmentCode);
        AssetCode code = AssetCode.of(endpointCode);
        CanonicalEndpointContent content = contentCanonicalizer.canonicalize(
                providerContractId,
                code.value(),
                environmentCode,
                protocolScheme,
                baseUrl,
                resourcePath,
                httpMethod,
                contentType,
                charsetName,
                connectTimeoutMs,
                readTimeoutMs,
                totalTimeoutMs,
                credentialRefId,
                networkConfig,
                tlsConfig);
        ProviderEndpoint draft = ProviderEndpoint.draft(
                providerContractId,
                code,
                environmentCode,
                protocolScheme,
                baseUrl,
                resourcePath,
                httpMethod,
                contentType,
                charsetName,
                connectTimeoutMs,
                readTimeoutMs,
                totalTimeoutMs,
                credentialRefId,
                content.networkConfig(),
                content.tlsConfig(),
                content.checksum());
        return endpointRepository.createRevision(draft, normalizeActor(actor));
    }

    @Transactional(readOnly = true)
    public ProviderEndpoint get(long id) {
        return endpointRepository.findById(id).orElseThrow(() -> new EndpointNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public EndpointPage findAll(
            Long providerContractId,
            String endpointCode,
            String environmentCode,
            EndpointLifecycleStatus lifecycleStatus,
            boolean latestOnly,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 200");
        }
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page offset is too large");
        }
        ProviderEndpointQuery query = new ProviderEndpointQuery(
                providerContractId,
                endpointCode,
                environmentCode,
                lifecycleStatus,
                latestOnly,
                (int) offset,
                size);
        return new EndpointPage(
                endpointRepository.findAll(query),
                page,
                size,
                endpointRepository.count(query));
    }

    @Transactional
    public ProviderEndpoint publish(long endpointId, String actor) {
        ProviderEndpoint endpoint = get(endpointId);
        requireActiveContract(endpoint.providerContractId());
        validateCredential(
                endpoint.providerContractId(), endpoint.credentialRefId(), endpoint.environmentCode());
        return endpointRepository.publish(endpointId, normalizeActor(actor));
    }

    private ProviderContract requireActiveContract(long contractId) {
        ProviderContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ProviderContractNotFoundException(contractId));
        if (contract.status() != ContractStatus.ACTIVE) {
            throw new IllegalArgumentException("Provider contract must be ACTIVE");
        }
        return contract;
    }

    private void validateCredential(long contractId, Long credentialRefId, String environmentCode) {
        if (credentialRefId != null
                && !endpointRepository.credentialMatchesEndpoint(
                        contractId, credentialRefId, environmentCode)) {
            throw new EndpointCredentialMismatchException(contractId, credentialRefId);
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
