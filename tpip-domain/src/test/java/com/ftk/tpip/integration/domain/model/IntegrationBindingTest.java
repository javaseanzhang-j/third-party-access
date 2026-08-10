package com.ftk.tpip.integration.domain.model;
import static org.junit.jupiter.api.Assertions.*;import com.ftk.tpip.shared.AssetCode;import org.junit.jupiter.api.Test;
class IntegrationBindingTest{
@Test void createsActiveBinding(){var b=IntegrationBinding.create(AssetCode.of("payment.refund.channel-a"),"Channel A Refund",3,7,"payments");assertEquals(BindingStatus.ACTIVE,b.status());assertEquals(3,b.operationId());assertEquals(7,b.providerContractId());}
@Test void preservesReferencesWhenRevised(){var b=new IntegrationBinding(1L,AssetCode.of("payment.refund.channel-a"),"Old",3,7,"payments",BindingStatus.ACTIVE,2,null,null);var revised=b.revise("New","integration",BindingStatus.INACTIVE,2);assertEquals(3,revised.operationId());assertEquals(7,revised.providerContractId());assertEquals("payment.refund.channel-a",revised.bindingCode().value());}
}
