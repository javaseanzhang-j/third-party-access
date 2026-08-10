package com.ftk.tpip.access.domain.repository;

import com.ftk.tpip.access.domain.model.ProviderProduct;
import java.util.List;
import java.util.Optional;

public interface ProviderProductRepository {
    Optional<ProviderProduct> findById(long id);
    Optional<ProviderProduct> findByCode(long providerId, String code);
    List<ProviderProduct> findAll(Long providerId);
    ProviderProduct create(ProviderProduct product, String actor);
    Optional<Long> findProductIdByChannel(long channelId);
    Optional<Long> findProductIdByInterface(long providerContractId);
    List<Long> findInterfaceIds(long productId);
    void attachChannel(long productId, long channelId, String actor);
    void attachInterface(long productId, long providerContractId, String actor);
}
