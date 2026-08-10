package com.ftk.tpip.control.application.provider;

import com.ftk.tpip.provider.domain.model.EndpointLifecycleStatus;
import com.ftk.tpip.provider.domain.model.EndpointProbeResult;
import com.ftk.tpip.provider.domain.repository.EndpointProbeRepository;
import com.ftk.tpip.provider.domain.repository.ProviderEndpointRepository;
import com.ftk.tpip.provider.domain.service.EndpointConnectivityProbe;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProviderEndpointProbeApplicationService {
    private final ProviderEndpointRepository endpoints;
    private final EndpointProbeRepository results;
    private final EndpointConnectivityProbe probe;
    private final TransactionTemplate transactions;

    public ProviderEndpointProbeApplicationService(ProviderEndpointRepository endpoints,
            EndpointProbeRepository results, EndpointConnectivityProbe probe,
            PlatformTransactionManager transactionManager) {
        this.endpoints = endpoints; this.results = results; this.probe = probe;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public EndpointProbeResult probe(long endpointId, String actor) {
        var endpoint = endpoints.findById(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("endpoint revision does not exist"));
        if (endpoint.lifecycleStatus() != EndpointLifecycleStatus.PUBLISHED) {
            throw new IllegalArgumentException("only PUBLISHED endpoint revision can be probed");
        }
        String operator = actor(actor);
        var observation = probe.probe(endpoint);
        return transactions.execute(status -> results.save(new EndpointProbeResult(null, endpointId,
                observation.success() ? "SUCCESS" : "FAILURE", observation.reasonCode(),
                observation.latencyMs(), operator, null)));
    }

    @Transactional(readOnly = true)
    public List<EndpointProbeResult> history(long endpointId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        if (endpoints.findById(endpointId).isEmpty()) throw new IllegalArgumentException("endpoint revision does not exist");
        return results.findByEndpoint(endpointId, limit);
    }

    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new IllegalArgumentException("X-Operator is invalid");
        }
        return value.trim();
    }
}
