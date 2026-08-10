package com.ftk.tpip.provider.domain.repository;

import com.ftk.tpip.provider.domain.model.EndpointProbeResult;
import java.util.List;

public interface EndpointProbeRepository {
    EndpointProbeResult save(EndpointProbeResult value);
    List<EndpointProbeResult> findByEndpoint(long endpointId, int limit);
}
