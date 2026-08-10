package com.ftk.tpip.control.application.release;

@FunctionalInterface
public interface DriftGovernancePolicyResolver {
    DriftGovernancePolicyApplicationService.ResolvedGovernancePolicy resolve(long workspaceId);
}
