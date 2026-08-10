package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.NotificationOutboxMessage;
import com.ftk.tpip.release.domain.model.RegressionScheduleCandidate;
import com.ftk.tpip.release.domain.model.VerificationDriftReport;
import com.ftk.tpip.release.domain.model.VerificationDriftStatus;
import com.ftk.tpip.release.domain.model.VerificationRun;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import com.ftk.tpip.release.domain.repository.RegressionPolicyRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegressionAutomationRecorder {
    private static final String AGGREGATE_TYPE = "VERIFICATION_BASELINE";
    private final RegressionPolicyRepository policies;
    private final NotificationOutboxRepository outbox;
    private final CanonicalJsonService canonical;
    private final ObjectMapper json;

    public RegressionAutomationRecorder(RegressionPolicyRepository policies, NotificationOutboxRepository outbox,
            CanonicalJsonService canonical, ObjectMapper json) {
        this.policies = policies; this.outbox = outbox; this.canonical = canonical; this.json = json;
    }

    @Transactional
    public void success(RegressionScheduleCandidate candidate, String leaseOwner, String environmentCode,
            VerificationRun run, VerificationDriftReport report, Instant completedAt, Instant nextRunAt) {
        policies.completeSuccess(candidate.policy().id(), candidate.version().id(), leaseOwner, completedAt,
                nextRunAt, run.id(), report.id(), report.driftStatus().name());
        if (report.driftStatus() == VerificationDriftStatus.DRIFTED) {
            ObjectNode payload = base(candidate, run, report);
            enqueue("TPIP_VERIFICATION_DRIFT_DETECTED", candidate.version().baselineId(), environmentCode,
                    payload, completedAt);
        }
    }

    @Transactional
    public boolean failure(RegressionScheduleCandidate candidate, String leaseOwner, String environmentCode,
            Instant completedAt, Instant nextRunAt, int consecutiveFailures, String error, boolean pause) {
        boolean suspended = policies.completeFailure(candidate.policy().id(), candidate.version().id(), leaseOwner,
                completedAt, nextRunAt, consecutiveFailures, error, pause);
        if (suspended) {
            ObjectNode payload = json.createObjectNode();
            payload.put("policyId", candidate.policy().id()).put("policyCode", candidate.policy().policyCode().value())
                    .put("policyVersionId", candidate.version().id())
                    .put("baselineId", candidate.version().baselineId())
                    .put("consecutiveFailures", consecutiveFailures).put("reasonCode", error);
            enqueue("TPIP_REGRESSION_POLICY_SUSPENDED", candidate.version().baselineId(), environmentCode,
                    payload, completedAt);
        }
        return suspended;
    }

    private ObjectNode base(RegressionScheduleCandidate candidate, VerificationRun run,
            VerificationDriftReport report) {
        return json.createObjectNode().put("policyId", candidate.policy().id())
                .put("policyCode", candidate.policy().policyCode().value())
                .put("policyVersionId", candidate.version().id())
                .put("baselineId", candidate.version().baselineId())
                .put("verificationRunId", run.id()).put("driftReportId", report.id())
                .put("driftStatus", report.driftStatus().name())
                .put("comparedCheckCount", report.comparedCheckCount()).put("driftCount", report.driftCount());
    }

    private void enqueue(String eventType, long baselineId, String environmentCode,
            ObjectNode payload, Instant availableAt) {
        outbox.enqueue(new NotificationOutboxMessage(null, eventType, AGGREGATE_TYPE,
                Long.toString(baselineId), environmentCode, canonical.canonicalString(payload), availableAt, null));
    }
}
