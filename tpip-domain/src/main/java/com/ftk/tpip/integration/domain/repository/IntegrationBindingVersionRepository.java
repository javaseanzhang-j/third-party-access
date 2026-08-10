package com.ftk.tpip.integration.domain.repository;

import com.ftk.tpip.integration.domain.model.IntegrationBindingVersion;
import java.util.List;
import java.util.Optional;

public interface IntegrationBindingVersionRepository {
    Optional<IntegrationBindingVersion> findVersion(long bindingId, long versionId);
    List<IntegrationBindingVersion> findVersions(long bindingId);
    IntegrationBindingVersion createVersion(IntegrationBindingVersion version, String actor);
    IntegrationBindingVersion publishVersion(long bindingId, long versionId, String actor);
}
