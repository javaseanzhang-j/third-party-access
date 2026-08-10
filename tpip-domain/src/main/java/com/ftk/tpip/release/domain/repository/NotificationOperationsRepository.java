package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.NotificationOperationsAlert;
import com.ftk.tpip.release.domain.model.NotificationOperationsEvaluation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationOperationsRepository {
    Optional<NotificationOperationsEvaluation> findEvaluation(String environmentCode,
            Instant windowStart, Instant windowEnd);
    boolean createEvaluationIfAbsent(NotificationOperationsEvaluation evaluation);
    List<NotificationOperationsEvaluation> findEvaluations(String environmentCode, int limit);
    Optional<NotificationOperationsAlert> findAlert(long id);
    Optional<NotificationOperationsAlert> findActiveAlert(String environmentCode);
    NotificationOperationsAlert createAlert(NotificationOperationsAlert alert);
    NotificationOperationsAlert acknowledgeAlert(long id, String actor, Instant at);
    Optional<NotificationOperationsAlert> markEscalated(long id, Instant at);
    Optional<NotificationOperationsAlert> markRepeatNotified(long id, Instant eligibleBefore, Instant at);
    List<NotificationOperationsAlert> resolveActiveAlerts(String environmentCode, Instant at);
    List<NotificationOperationsAlert> findAlerts(String environmentCode, int limit);
}
