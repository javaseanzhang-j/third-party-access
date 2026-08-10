package com.ftk.tpip.integration.domain.model;
import static org.junit.jupiter.api.Assertions.*;import com.ftk.tpip.shared.AssetCode;import org.junit.jupiter.api.Test;
class IntegrationPolicyTest{@Test void revisePreservesStableIdentity(){var p=new IntegrationPolicy(1L,2,AssetCode.of("provider.refund.policy"),"Refund",PolicyStatus.ACTIVE,3,null,null);var r=p.revise("Refund v2",PolicyStatus.INACTIVE,3);assertEquals(p.policyCode(),r.policyCode());assertEquals(p.bindingId(),r.bindingId());}}
