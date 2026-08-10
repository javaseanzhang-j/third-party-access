package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.DriftGovernanceExecution;
import com.ftk.tpip.release.domain.repository.DriftGovernanceExecutionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DriftGovernanceReminderPreviewService {
    private final DriftGovernanceExecutionRepository executions;
    private final DriftGovernanceReminderBatchApplicationService batches;

    public DriftGovernanceReminderPreviewService(DriftGovernanceExecutionRepository executions,
            DriftGovernanceReminderBatchApplicationService batches) {
        this.executions = executions; this.batches = batches;
    }

    public PreviewResult createDueDrafts(Instant now, int maximumExecutions, int maximumBatches,
            String environmentCode, String actor) {
        if (now == null || maximumExecutions < 1 || maximumExecutions > 1000
                || maximumBatches < 1 || maximumBatches > 100)
            throw new IllegalArgumentException("preview cycle arguments are invalid");
        var groups = new LinkedHashMap<GroupKey, List<Long>>();
        List<DriftGovernanceExecution> due = executions.findDueWithoutActiveBatch(now, maximumExecutions);
        due.forEach(value -> groups.computeIfAbsent(new GroupKey(value.workspaceId(), value.aggregationKey(),
                value.ownerCode()), ignored -> new ArrayList<>()).add(value.id()));
        int attempted = 0; int created = 0; int conflicts = 0;
        outer: for (var entry : groups.entrySet()) {
            List<Long> ids = entry.getValue();
            for (int from = 0; from < ids.size(); from += 100) {
                if (attempted >= maximumBatches) break outer;
                attempted++;
                try {
                    batches.createAutomated(entry.getKey().workspaceId(), environmentCode,
                            ids.subList(from, Math.min(from + 100, ids.size())), actor);
                    created++;
                } catch (IllegalArgumentException concurrentOrChanged) {
                    conflicts++;
                }
            }
        }
        return new PreviewResult(due.size(), created, conflicts);
    }

    private record GroupKey(long workspaceId, String aggregationKey, String ownerCode) {}
    public record PreviewResult(int eligibleExecutions, int createdBatches, int conflicts) {}
}
