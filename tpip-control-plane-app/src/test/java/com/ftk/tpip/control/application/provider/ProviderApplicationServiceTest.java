package com.ftk.tpip.control.application.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.provider.domain.exception.ProviderCodeAlreadyExistsException;
import com.ftk.tpip.provider.domain.exception.ProviderConcurrentModificationException;
import com.ftk.tpip.provider.domain.model.Provider;
import com.ftk.tpip.provider.domain.model.ProviderQuery;
import com.ftk.tpip.provider.domain.model.ProviderStatus;
import com.ftk.tpip.provider.domain.model.ProviderType;
import com.ftk.tpip.provider.domain.repository.ProviderRepository;
import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderApplicationServiceTest {

    private InMemoryProviderRepository repository;
    private ProviderApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProviderRepository();
        service = new ProviderApplicationService(repository);
    }

    @Test
    void createsAndQueriesProvider() {
        Provider created = service.create(
                "payment.channel",
                "Payment Channel",
                ProviderType.CHANNEL,
                null,
                "integration-team",
                "tester");

        ProviderPage page = service.findAll("payment", ProviderStatus.ACTIVE, 0, 20);

        assertEquals(1L, created.id());
        assertEquals(1, page.items().size());
        assertEquals(1, page.totalElements());
        assertEquals(1, page.totalPages());
    }

    @Test
    void rejectsDuplicateProviderCode() {
        service.create(
                "payment.channel",
                "Payment Channel",
                ProviderType.CHANNEL,
                null,
                "integration-team",
                "tester");

        assertThrows(ProviderCodeAlreadyExistsException.class, () -> service.create(
                "payment.channel",
                "Another Channel",
                ProviderType.CHANNEL,
                null,
                "integration-team",
                "tester"));
    }

    @Test
    void updatesWithOptimisticLock() {
        Provider created = service.create(
                "payment.channel",
                "Payment Channel",
                ProviderType.CHANNEL,
                null,
                "integration-team",
                "tester");

        Provider updated = service.update(
                created.id(),
                "Payment Channel V2",
                ProviderType.PLATFORM,
                "updated",
                "platform-team",
                ProviderStatus.INACTIVE,
                0,
                "tester");

        assertEquals(1, updated.rowVersion());
        assertEquals(ProviderStatus.INACTIVE, updated.status());
        assertThrows(ProviderConcurrentModificationException.class, () -> service.update(
                created.id(),
                "stale update",
                ProviderType.CHANNEL,
                null,
                "integration-team",
                ProviderStatus.ACTIVE,
                0,
                "tester"));
    }

    private static final class InMemoryProviderRepository implements ProviderRepository {

        private final Map<Long, Provider> providers = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<Provider> findById(long id) {
            return Optional.ofNullable(providers.get(id));
        }

        @Override
        public Optional<Provider> findByCode(AssetCode providerCode) {
            return providers.values().stream()
                    .filter(provider -> provider.providerCode().equals(providerCode))
                    .findFirst();
        }

        @Override
        public List<Provider> findAll(ProviderQuery query) {
            List<Provider> matches = matching(query);
            int fromIndex = Math.min(query.offset(), matches.size());
            int toIndex = Math.min(fromIndex + query.limit(), matches.size());
            return matches.subList(fromIndex, toIndex);
        }

        @Override
        public long count(ProviderQuery query) {
            return matching(query).size();
        }

        @Override
        public Provider create(Provider provider, String actor) {
            if (findByCode(provider.providerCode()).isPresent()) {
                throw new ProviderCodeAlreadyExistsException(provider.providerCode().value());
            }
            Instant now = Instant.parse("2026-08-08T00:00:00Z");
            Provider saved = new Provider(
                    ++sequence,
                    provider.providerCode(),
                    provider.providerName(),
                    provider.providerType(),
                    provider.description(),
                    provider.ownerCode(),
                    provider.status(),
                    0,
                    now,
                    now);
            providers.put(saved.id(), saved);
            return saved;
        }

        @Override
        public Provider update(Provider provider, String actor) {
            Provider current = providers.get(provider.id());
            if (current == null || current.rowVersion() != provider.rowVersion()) {
                throw new ProviderConcurrentModificationException(provider.id(), provider.rowVersion());
            }
            Provider saved = new Provider(
                    provider.id(),
                    provider.providerCode(),
                    provider.providerName(),
                    provider.providerType(),
                    provider.description(),
                    provider.ownerCode(),
                    provider.status(),
                    provider.rowVersion() + 1,
                    provider.createdAt(),
                    Instant.parse("2026-08-08T00:01:00Z"));
            providers.put(saved.id(), saved);
            return saved;
        }

        private List<Provider> matching(ProviderQuery query) {
            String keyword = query.keyword() == null ? null : query.keyword().toLowerCase();
            return providers.values().stream()
                    .filter(provider -> query.status() == null || provider.status() == query.status())
                    .filter(provider -> keyword == null
                            || provider.providerCode().value().contains(keyword)
                            || provider.providerName().toLowerCase().contains(keyword))
                    .sorted((left, right) -> Long.compare(right.id(), left.id()))
                    .toList();
        }
    }
}
