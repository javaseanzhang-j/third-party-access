package com.ftk.tpip.runtime;

@FunctionalInterface
public interface ProviderTransport {
    ProviderTransportResponse exchange(ProviderTransportRequest request);
}
