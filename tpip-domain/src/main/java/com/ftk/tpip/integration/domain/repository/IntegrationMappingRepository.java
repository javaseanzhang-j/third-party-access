package com.ftk.tpip.integration.domain.repository;

import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.Optional;

public interface IntegrationMappingRepository {
    Optional<IntegrationMapping> findById(long id);
    Optional<IntegrationMapping> findByCode(AssetCode code);
    List<IntegrationMapping> findAll(IntegrationMappingQuery query);
    long count(IntegrationMappingQuery query);
    IntegrationMapping create(IntegrationMapping mapping, String actor);
    IntegrationMapping update(IntegrationMapping mapping, String actor);
    Optional<IntegrationMappingVersion> findVersion(long mappingId, long versionId);
    Optional<IntegrationMappingVersion> findVersionById(long versionId);
    List<IntegrationMappingVersion> findVersions(long mappingId);
    IntegrationMappingVersion createVersion(IntegrationMappingVersion version, String actor);
    IntegrationMappingVersion publishVersion(long mappingId, long versionId, String actor);
}
