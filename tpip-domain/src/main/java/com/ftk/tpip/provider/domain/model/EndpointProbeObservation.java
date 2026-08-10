package com.ftk.tpip.provider.domain.model;

public record EndpointProbeObservation(boolean success, String reasonCode, long latencyMs) {}
