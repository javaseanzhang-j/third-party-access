package com.ftk.tpip.provider.domain.repository;

import com.ftk.tpip.provider.domain.model.InterfaceTransportVersion;
import java.util.List;
import java.util.Optional;

public interface InterfaceTransportRepository {
    Optional<InterfaceTransportVersion> findVersionById(long contractId, long versionId);
    List<InterfaceTransportVersion> findVersions(long contractId);
    InterfaceTransportVersion createVersion(InterfaceTransportVersion version, String actor);
    InterfaceTransportVersion publishVersion(long contractId, long versionId, String actor);
}
