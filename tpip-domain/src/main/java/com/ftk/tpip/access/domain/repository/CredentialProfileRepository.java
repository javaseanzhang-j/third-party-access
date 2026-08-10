package com.ftk.tpip.access.domain.repository;

import com.ftk.tpip.access.domain.model.CredentialProfile;
import com.ftk.tpip.access.domain.model.CredentialProfileItem;
import java.util.List;
import java.util.Optional;

public interface CredentialProfileRepository {
    Optional<CredentialProfile> findById(long id);
    Optional<CredentialProfile> findByCode(long providerId, String code);
    List<CredentialProfile> findAll(Long providerId);
    CredentialProfile create(CredentialProfile profile, String actor);
    List<CredentialProfileItem> findItems(long profileId);
    CredentialProfileItem addItem(CredentialProfileItem item, String actor);
}
