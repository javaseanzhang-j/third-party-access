package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.FixtureSuite;
import com.ftk.tpip.release.domain.model.FixtureSuiteVersion;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.Optional;

public interface FixtureSuiteRepository {
    Optional<FixtureSuite> findById(long id);
    Optional<FixtureSuite> findByCode(AssetCode code);
    List<FixtureSuite> findByBinding(long bindingId);
    FixtureSuite create(FixtureSuite suite, String actor);
    Optional<FixtureSuiteVersion> findVersion(long suiteId, long versionId);
    Optional<FixtureSuiteVersion> findVersionById(long versionId);
    List<FixtureSuiteVersion> findVersions(long suiteId);
    FixtureSuiteVersion createVersion(FixtureSuiteVersion version, String actor);
    FixtureSuiteVersion publishVersion(long suiteId, long versionId, String actor);
}
