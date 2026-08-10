package com.ftk.tpip.control.application.provider;

import com.ftk.tpip.provider.domain.exception.ProviderCodeAlreadyExistsException;
import com.ftk.tpip.provider.domain.model.Provider;
import com.ftk.tpip.provider.domain.model.ProviderQuery;
import com.ftk.tpip.provider.domain.model.ProviderStatus;
import com.ftk.tpip.provider.domain.model.ProviderType;
import com.ftk.tpip.provider.domain.repository.ProviderRepository;
import com.ftk.tpip.shared.AssetCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderApplicationService {

    private final ProviderRepository providerRepository;

    public ProviderApplicationService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @Transactional
    public Provider create(
            String providerCode,
            String providerName,
            ProviderType providerType,
            String description,
            String ownerCode,
            String actor) {
        AssetCode code = AssetCode.of(providerCode);
        String normalizedActor = normalizeActor(actor);
        if (providerRepository.findByCode(code).isPresent()) {
            throw new ProviderCodeAlreadyExistsException(code.value());
        }
        Provider provider = Provider.create(code, providerName, providerType, description, ownerCode);
        return providerRepository.create(provider, normalizedActor);
    }

    @Transactional(readOnly = true)
    public Provider get(long id) {
        return providerRepository.findById(id).orElseThrow(() -> new ProviderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public ProviderPage findAll(String keyword, ProviderStatus status, int page, int size) {
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
        ProviderQuery query = new ProviderQuery(keyword, status, (int) offset, size);
        return new ProviderPage(
                providerRepository.findAll(query),
                page,
                size,
                providerRepository.count(query));
    }

    @Transactional
    public Provider update(
            long id,
            String providerName,
            ProviderType providerType,
            String description,
            String ownerCode,
            ProviderStatus status,
            long rowVersion,
            String actor) {
        Provider current = get(id);
        Provider revised = current.revise(
                providerName,
                providerType,
                description,
                ownerCode,
                status,
                rowVersion);
        return providerRepository.update(revised, normalizeActor(actor));
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
