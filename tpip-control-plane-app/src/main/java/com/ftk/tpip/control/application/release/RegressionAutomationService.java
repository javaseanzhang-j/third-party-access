package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.repository.RegressionPolicyRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RegressionAutomationService {
    private static final String ACTOR = "system-regression-scheduler";
    private final RegressionPolicyRepository policies;
    private final VerificationBaselineApplicationService baselines;
    private final RegressionAutomationRecorder recorder;
    private final Clock clock;

    @Autowired
    public RegressionAutomationService(RegressionPolicyRepository policies,
            VerificationBaselineApplicationService baselines, RegressionAutomationRecorder recorder) {
        this(policies, baselines, recorder, Clock.systemUTC());
    }

    RegressionAutomationService(RegressionPolicyRepository policies,
            VerificationBaselineApplicationService baselines, RegressionAutomationRecorder recorder, Clock clock) {
        this.policies = policies; this.baselines = baselines; this.recorder = recorder;
        this.clock = clock;
    }

    public CycleResult executeDue(Instant now, int limit, String leaseOwner, java.time.Duration lease) {
        int claimed = 0, completed = 0, drifted = 0, failed = 0, suspended = 0;
        for (var candidate : policies.findDue(now, limit)) {
            if (!policies.claim(candidate.policy().id(), candidate.version().id(), candidate.nextRunAt(),
                    leaseOwner, now, now.plus(lease))) continue;
            claimed++;
            try {
                var result = baselines.run(candidate.version().baselineId(), ACTOR);
                Instant completedAt = clock.instant();
                recorder.success(candidate, leaseOwner, candidate.environmentCode(), result.verificationRun(), result.driftReport(),
                        completedAt, completedAt.plus(candidate.version().interval()));
                completed++;
                if (result.driftReport().driftCount() > 0) drifted++;
            } catch (RuntimeException failure) {
                failed++;
                Instant completedAt = clock.instant();
                int failures = candidate.consecutiveFailures() + 1;
                boolean shouldPause = failures >= candidate.version().maximumConsecutiveFailures();
                if (recorder.failure(candidate, leaseOwner, candidate.environmentCode(), completedAt,
                        completedAt.plus(candidate.version().failureBackoff()), failures,
                        failure.getClass().getSimpleName(), shouldPause)) suspended++;
            }
        }
        return new CycleResult(claimed, completed, drifted, failed, suspended);
    }

    public record CycleResult(int claimed, int completed, int drifted, int failed, int suspended) {}
}
