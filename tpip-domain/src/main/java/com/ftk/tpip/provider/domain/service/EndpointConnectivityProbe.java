package com.ftk.tpip.provider.domain.service;

import com.ftk.tpip.provider.domain.model.EndpointProbeObservation;
import com.ftk.tpip.provider.domain.model.ProviderEndpoint;

public interface EndpointConnectivityProbe {
    EndpointProbeObservation probe(ProviderEndpoint endpoint);
}
