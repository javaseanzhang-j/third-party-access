package com.ftk.tpip.policy.api;

import com.ftk.tpip.policy.ir.CompiledPolicyPlan;

public interface PolicyExecutor<C> {

    PolicyExecutionResult execute(PolicyStage stage, CompiledPolicyPlan plan, C context);
}
