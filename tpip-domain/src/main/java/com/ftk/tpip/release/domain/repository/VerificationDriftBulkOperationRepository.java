package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.VerificationDriftBulkOperation;
import java.util.Optional;

public interface VerificationDriftBulkOperationRepository {
    Optional<VerificationDriftBulkOperation> findByCommandKey(String commandKey);
    VerificationDriftBulkOperation save(VerificationDriftBulkOperation operation);
}

