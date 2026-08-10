package com.ftk.tpip.provider.domain.repository;

import com.ftk.tpip.provider.domain.model.CredentialRef;
import com.ftk.tpip.provider.domain.model.CredentialRefQuery;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.Optional;

public interface CredentialRefRepository {

    Optional<CredentialRef> findById(long id);

    Optional<CredentialRef> findByCodeAndEnvironment(AssetCode credentialCode, String environmentCode);

    List<CredentialRef> findAll(CredentialRefQuery query);

    long count(CredentialRefQuery query);

    CredentialRef create(CredentialRef credentialRef, String actor);

    CredentialRef update(CredentialRef credentialRef, String actor);
}
