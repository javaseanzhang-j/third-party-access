package com.ftk.tpip.control.application.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.provider.domain.repository.EndpointProbeRepository;
import com.ftk.tpip.provider.domain.repository.ProviderEndpointRepository;
import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ProviderEndpointProbeApplicationServiceTest {
    @Test
    void recordsSafeObservationForPublishedEndpoint() {
        ProviderEndpoint endpoint = endpoint(EndpointLifecycleStatus.PUBLISHED);
        FakeResults results = new FakeResults();
        var service = new ProviderEndpointProbeApplicationService(new FakeEndpoints(endpoint), results,
                value -> new EndpointProbeObservation(false, "CONNECT_TIMEOUT", 123), transactionManager());

        EndpointProbeResult result = service.probe(9, "operator-a");

        assertEquals("FAILURE", result.outcome());
        assertEquals("CONNECT_TIMEOUT", result.reasonCode());
        assertEquals("operator-a", result.actorCode());
        assertEquals(1, service.history(9, 10).size());
    }

    @Test
    void rejectsDraftEndpointWithoutPerformingProbe() {
        var service = new ProviderEndpointProbeApplicationService(new FakeEndpoints(endpoint(EndpointLifecycleStatus.DRAFT)),
                new FakeResults(), value -> { throw new AssertionError("probe must not run"); }, transactionManager());
        assertThrows(IllegalArgumentException.class, () -> service.probe(9, "operator-a"));
    }

    private static ProviderEndpoint endpoint(EndpointLifecycleStatus status) {
        Instant now = Instant.now();
        return new ProviderEndpoint(9L, 7, AssetCode.of("notify.endpoint"), "prod", 1,
                EndpointScheme.HTTPS, "https://notify.example.test", "/events", EndpointHttpMethod.POST,
                "application/json", "UTF-8", 1000, 2000, 3000, null, null, null, status,
                "a".repeat(64), status == EndpointLifecycleStatus.PUBLISHED ? now : null, now);
    }

    private record FakeEndpoints(ProviderEndpoint endpoint) implements ProviderEndpointRepository {
        public Optional<ProviderEndpoint> findById(long id){return endpoint.id()==id?Optional.of(endpoint):Optional.empty();}
        public List<ProviderEndpoint> findAll(ProviderEndpointQuery query){return List.of(endpoint);}
        public long count(ProviderEndpointQuery query){return 1;}
        public boolean credentialMatchesEndpoint(long a,long b,String c){return false;}
        public ProviderEndpoint createRevision(ProviderEndpoint e,String a){throw new UnsupportedOperationException();}
        public ProviderEndpoint publish(long e,String a){throw new UnsupportedOperationException();}
    }

    private static final class FakeResults implements EndpointProbeRepository {
        private final List<EndpointProbeResult> values = new ArrayList<>();
        public EndpointProbeResult save(EndpointProbeResult value) {
            var saved = new EndpointProbeResult((long) values.size()+1, value.endpointId(), value.outcome(),
                    value.reasonCode(), value.latencyMs(), value.actorCode(), Instant.now());
            values.add(saved); return saved;
        }
        public List<EndpointProbeResult> findByEndpoint(long endpointId,int limit) {
            return values.stream().filter(v -> v.endpointId()==endpointId).limit(limit).toList();
        }
    }

    private static PlatformTransactionManager transactionManager() {
        return new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition definition){return new SimpleTransactionStatus();}
            public void commit(TransactionStatus status) {}
            public void rollback(TransactionStatus status) {}
        };
    }
}
