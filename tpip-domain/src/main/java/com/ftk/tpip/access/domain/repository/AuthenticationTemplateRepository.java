package com.ftk.tpip.access.domain.repository;

import com.ftk.tpip.access.domain.model.AuthenticationTemplate;
import com.ftk.tpip.access.domain.model.AuthenticationTemplateVersion;
import java.util.List;
import java.util.Optional;

public interface AuthenticationTemplateRepository {
    Optional<AuthenticationTemplate> findById(long id);
    Optional<AuthenticationTemplate> findByCode(String code);
    List<AuthenticationTemplate> findAll(Long providerId);
    AuthenticationTemplate create(AuthenticationTemplate template, String actor);
    Optional<AuthenticationTemplateVersion> findVersionById(long versionId);
    Optional<AuthenticationTemplateVersion> findVersionById(long templateId, long versionId);
    List<AuthenticationTemplateVersion> findVersions(long templateId);
    AuthenticationTemplateVersion createVersion(AuthenticationTemplateVersion version, String actor);
    AuthenticationTemplateVersion publishVersion(long templateId, long versionId, String actor);
}
