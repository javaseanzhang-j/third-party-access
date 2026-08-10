package com.ftk.tpip.access.domain.repository;

import com.ftk.tpip.access.domain.model.AccessChannel;
import com.ftk.tpip.access.domain.model.AccessParameter;
import java.util.List;
import java.util.Optional;

public interface AccessChannelRepository {
    Optional<AccessChannel> findById(long id);
    Optional<AccessChannel> findByCode(String code);
    List<AccessChannel> findAll(Long providerId);
    AccessChannel create(AccessChannel channel, String actor);
    AccessChannel update(AccessChannel channel, String actor);
    void attachInterface(long channelId, long providerContractId, String actor);
    boolean hasInterface(long channelId, long providerContractId);
    List<Long> findInterfaceIds(long channelId);
    List<AccessParameter> findParameters(long channelId);
    AccessParameter upsertParameter(AccessParameter parameter, String actor);
}
