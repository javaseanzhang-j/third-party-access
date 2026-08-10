package com.ftk.tpip.policy.api;

import com.ftk.tpip.policy.ir.CompiledPolicyPlan;

public interface PolicyCompiler {

    CompiledPolicyPlan compile(PolicyDocument document, PolicyTypeRegistrySnapshot registry);
}
