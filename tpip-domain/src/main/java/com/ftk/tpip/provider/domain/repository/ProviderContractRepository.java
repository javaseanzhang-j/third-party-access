package com.ftk.tpip.provider.domain.repository;

import com.ftk.tpip.provider.domain.model.ProviderContract;
import com.ftk.tpip.provider.domain.model.ProviderContractQuery;
import com.ftk.tpip.provider.domain.model.ProviderContractVersion;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.Optional;

public interface ProviderContractRepository {

    Optional<ProviderContract> findById(long id);

    Optional<ProviderContract> findByCode(AssetCode contractCode);

    List<ProviderContract> findAll(ProviderContractQuery query);

    long count(ProviderContractQuery query);

    ProviderContract create(ProviderContract contract, String actor);

    ProviderContract update(ProviderContract contract, String actor);

    Optional<ProviderContractVersion> findVersionById(long contractId, long versionId);

    List<ProviderContractVersion> findVersions(long contractId);

    ProviderContractVersion createVersion(ProviderContractVersion version, String actor);

    ProviderContractVersion publishVersion(long contractId, long versionId, String actor);
}
