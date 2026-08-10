package com.ftk.tpip.control.application.release;

import java.util.List;

/** Execution port that allows the synchronous engine to move to a Worker without changing orchestration. */
public interface WorkspaceVerificationExecutor {
    List<WorkspaceVerificationEngine.CheckOutcome> execute(long bindingId, long bindingVersionId,
            long fixtureSuiteVersionId, long runId, String actor);
}
