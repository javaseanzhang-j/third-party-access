package com.ftk.tpip.provider.domain.repository;

import com.ftk.tpip.provider.domain.model.Provider;
import com.ftk.tpip.provider.domain.model.ProviderQuery;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.Optional;

public interface ProviderRepository {

    Optional<Provider> findById(long id);

    Optional<Provider> findByCode(AssetCode providerCode);

    List<Provider> findAll(ProviderQuery query);

    long count(ProviderQuery query);

    Provider create(Provider provider, String actor);

    Provider update(Provider provider, String actor);
}
