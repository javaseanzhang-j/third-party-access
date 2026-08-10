package com.ftk.tpip.provider.domain.repository;

import com.ftk.tpip.provider.domain.model.ProviderEndpoint;
import com.ftk.tpip.provider.domain.model.ProviderEndpointQuery;
import java.util.List;
import java.util.Optional;

public interface ProviderEndpointRepository {

    Optional<ProviderEndpoint> findById(long id);

    List<ProviderEndpoint> findAll(ProviderEndpointQuery query);

    long count(ProviderEndpointQuery query);

    boolean credentialMatchesEndpoint(long contractId, long credentialRefId, String environmentCode);

    ProviderEndpoint createRevision(ProviderEndpoint endpoint, String actor);

    ProviderEndpoint publish(long endpointId, String actor);
}
