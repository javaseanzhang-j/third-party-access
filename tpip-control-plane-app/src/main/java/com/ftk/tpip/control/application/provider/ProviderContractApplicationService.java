package com.ftk.tpip.control.application.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.provider.domain.exception.ProviderContractCodeAlreadyExistsException;
import com.ftk.tpip.provider.domain.model.ContractStatus;
import com.ftk.tpip.provider.domain.model.ProtocolType;
import com.ftk.tpip.provider.domain.model.ProviderContract;
import com.ftk.tpip.provider.domain.model.ProviderContractQuery;
import com.ftk.tpip.provider.domain.model.ProviderContractVersion;
import com.ftk.tpip.provider.domain.repository.ProviderContractRepository;
import com.ftk.tpip.provider.domain.repository.ProviderRepository;
import com.ftk.tpip.shared.AssetCode;
import com.ftk.tpip.shared.SemanticVersion;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderContractApplicationService {

    private final ProviderContractRepository contractRepository;
    private final ProviderRepository providerRepository;
    private final ContractContentCanonicalizer contentCanonicalizer;

    public ProviderContractApplicationService(
            ProviderContractRepository contractRepository,
            ProviderRepository providerRepository,
            ContractContentCanonicalizer contentCanonicalizer) {
        this.contractRepository = contractRepository;
        this.providerRepository = providerRepository;
        this.contentCanonicalizer = contentCanonicalizer;
    }

    @Transactional
    public ProviderContract create(
            long providerId,
            String contractCode,
            String contractName,
            ProtocolType protocolType,
            String description,
            String actor) {
        providerRepository.findById(providerId).orElseThrow(() -> new ProviderNotFoundException(providerId));
        AssetCode code = AssetCode.of(contractCode);
        if (contractRepository.findByCode(code).isPresent()) {
            throw new ProviderContractCodeAlreadyExistsException(code.value());
        }
        ProviderContract contract = ProviderContract.create(
                providerId, code, contractName, protocolType, description);
        return contractRepository.create(contract, normalizeActor(actor));
    }

    @Transactional(readOnly = true)
    public ProviderContract get(long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ProviderContractNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public ProviderContractPage findAll(
            Long providerId, String keyword, ContractStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 200");
        }
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page offset is too large");
        }
        ProviderContractQuery query = new ProviderContractQuery(
                providerId, keyword, status, (int) offset, size);
        return new ProviderContractPage(
                contractRepository.findAll(query),
                page,
                size,
                contractRepository.count(query));
    }

    @Transactional
    public ProviderContract update(
            long id,
            String contractName,
            ProtocolType protocolType,
            String description,
            ContractStatus status,
            long rowVersion,
            String actor) {
        ProviderContract revised = get(id).revise(
                contractName, protocolType, description, status, rowVersion);
        return contractRepository.update(revised, normalizeActor(actor));
    }

    @Transactional
    public ProviderContractVersion createVersion(
            long contractId,
            JsonNode requestSchema,
            JsonNode responseSchema,
            JsonNode errorSchema,
            JsonNode callbackSchema,
            JsonNode examples,
            String actor) {
        get(contractId);
        SemanticVersion semanticVersion = nextSemanticVersion(contractId);
        CanonicalContractContent content = contentCanonicalizer.canonicalize(
                requestSchema, responseSchema, errorSchema, callbackSchema, examples);
        ProviderContractVersion draft = ProviderContractVersion.draft(
                contractId,
                semanticVersion,
                content.requestSchema(),
                content.responseSchema(),
                content.errorSchema(),
                content.callbackSchema(),
                content.examples(),
                content.checksum());
        return contractRepository.createVersion(draft, normalizeActor(actor));
    }

    private SemanticVersion nextSemanticVersion(long contractId) {
        return contractRepository.findVersions(contractId).stream()
                .map(ProviderContractVersion::semanticVersion)
                .max(java.util.Comparator.comparingInt(SemanticVersion::major)
                        .thenComparingInt(SemanticVersion::minor)
                        .thenComparingInt(SemanticVersion::patch))
                .map(SemanticVersion::nextPatch)
                .orElse(new SemanticVersion(1, 0, 0));
    }

    @Transactional(readOnly = true)
    public List<ProviderContractVersion> findVersions(long contractId) {
        get(contractId);
        return contractRepository.findVersions(contractId);
    }

    @Transactional
    public ProviderContractVersion publishVersion(long contractId, long versionId, String actor) {
        getVersion(contractId, versionId);
        return contractRepository.publishVersion(contractId, versionId, normalizeActor(actor));
    }

    @Transactional(readOnly = true)
    public ProviderContractVersion getVersion(long contractId, long versionId) {
        get(contractId);
        return contractRepository.findVersionById(contractId, versionId)
                .orElseThrow(() -> new ProviderContractVersionNotFoundException(contractId, versionId));
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
