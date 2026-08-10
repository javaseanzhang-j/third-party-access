package com.ftk.tpip.integration.domain.repository;

import com.ftk.tpip.integration.domain.model.IntegrationBinding;
import com.ftk.tpip.integration.domain.model.IntegrationBindingQuery;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.Optional;

public interface IntegrationBindingRepository {
    Optional<IntegrationBinding> findById(long id);
    Optional<IntegrationBinding> findByCode(AssetCode bindingCode);
    List<IntegrationBinding> findAll(IntegrationBindingQuery query);
    long count(IntegrationBindingQuery query);
    IntegrationBinding create(IntegrationBinding binding, String actor);
    IntegrationBinding update(IntegrationBinding binding, String actor);
}
