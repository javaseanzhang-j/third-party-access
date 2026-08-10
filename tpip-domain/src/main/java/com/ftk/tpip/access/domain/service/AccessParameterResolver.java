package com.ftk.tpip.access.domain.service;

import com.ftk.tpip.access.domain.model.AccessParameter;
import com.ftk.tpip.access.domain.model.AccessParameterOverrideMode;
import com.ftk.tpip.access.domain.model.AccessParameterScope;
import com.ftk.tpip.access.domain.model.EffectiveAccessParameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AccessParameterResolver {
    public List<EffectiveAccessParameter> resolve(List<AccessParameter> parameters, long providerContractId) {
        Map<String, EffectiveAccessParameter> effective = new LinkedHashMap<>();
        parameters.stream().filter(item -> item.scope() == AccessParameterScope.CHANNEL)
                .forEach(item -> effective.put(item.resolutionKey(), new EffectiveAccessParameter(item, item.scope())));
        parameters.stream().filter(item -> item.scope() == AccessParameterScope.INTERFACE
                        && item.providerContractId() == providerContractId)
                .forEach(item -> {
                    if (item.overrideMode() == AccessParameterOverrideMode.DISABLE) effective.remove(item.resolutionKey());
                    else effective.put(item.resolutionKey(), new EffectiveAccessParameter(item, item.scope()));
                });
        return List.copyOf(effective.values());
    }
}
