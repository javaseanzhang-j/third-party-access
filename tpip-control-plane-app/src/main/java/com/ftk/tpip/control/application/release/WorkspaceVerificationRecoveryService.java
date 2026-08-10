package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.VerificationCheck;
import com.ftk.tpip.release.domain.model.VerificationCheckStatus;
import com.ftk.tpip.release.domain.model.VerificationStatus;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceVerificationRecoveryService {
    static final String ACTOR = "system-workspace-verification-recovery";
    private final ReleaseRepository releases;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper json;

    public WorkspaceVerificationRecoveryService(ReleaseRepository releases, CanonicalJsonService canonicalJson,
            ObjectMapper json) { this.releases = releases; this.canonicalJson = canonicalJson; this.json = json; }

    @Transactional
    public int recoverStale(Instant cutoff, int limit, Instant recoveredAt) {
        int recovered = 0;
        for (var run : releases.findRunningVerificationsStartedBefore(cutoff, limit)) {
            if (releases.findVerification(run.id()).filter(value -> value.status() == VerificationStatus.RUNNING).isEmpty()) continue;
            String details = "{\"reason\":\"VERIFICATION_TIMEOUT\",\"timeoutCutoff\":\"" + cutoff + "\"}";
            String evidence = "{\"recoveredAt\":\"" + recoveredAt + "\",\"serverExecuted\":true}";
            releases.recordVerificationCheck(new VerificationCheck(null, run.id(), "ENGINE_TIMEOUT",
                    "Verification execution timeout", VerificationCheckStatus.FAILED,
                    details, evidence, run.startedAt(), recoveredAt), ACTOR);
            ObjectNode summary = json.createObjectNode();
            summary.put("failed", 1); summary.put("passed", 0); summary.put("reason", "VERIFICATION_TIMEOUT");
            summary.put("serverExecuted", true);
            try {
                long fixtureSuiteVersionId = json.readTree(run.resultSummary()).path("fixtureSuiteVersionId").asLong();
                if (fixtureSuiteVersionId > 0) summary.put("fixtureSuiteVersionId", fixtureSuiteVersionId);
            } catch (Exception failure) {
                throw new IllegalStateException("Stored verification summary is invalid", failure);
            }
            releases.completeVerification(run.id(), VerificationStatus.FAILED, 1, 0, 1,
                    "db://tpip-verification/" + run.id(),
                    canonicalJson.write(summary),
                    ACTOR);
            recovered++;
        }
        return recovered;
    }
}
