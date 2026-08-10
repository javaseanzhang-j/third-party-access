package com.ftk.tpip.catalog.domain.repository;

import com.ftk.tpip.catalog.domain.model.CanonicalOperation;
import com.ftk.tpip.catalog.domain.model.CanonicalOperationQuery;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.Optional;

public interface CanonicalOperationRepository {
    Optional<CanonicalOperation> findById(long id);
    Optional<CanonicalOperation> findByCode(AssetCode operationCode);
    List<CanonicalOperation> findAll(CanonicalOperationQuery query);
    long count(CanonicalOperationQuery query);
    boolean capabilityIsActive(long capabilityId);
    CanonicalOperation create(CanonicalOperation operation, String actor);
    CanonicalOperation update(CanonicalOperation operation, String actor);
}
