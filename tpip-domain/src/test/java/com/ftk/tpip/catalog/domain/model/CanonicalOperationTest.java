package com.ftk.tpip.catalog.domain.model;
import static org.junit.jupiter.api.Assertions.*;import com.ftk.tpip.shared.AssetCode;import org.junit.jupiter.api.Test;
class CanonicalOperationTest{
@Test void createsActiveStableOperation(){var o=CanonicalOperation.create(1,AssetCode.of("payment.refund.apply")," Refund Apply ",null,InvocationMode.SYNC,IdempotencyClass.IDEMPOTENT_WITH_KEY,DataClassification.CONFIDENTIAL,"payments");assertEquals("Refund Apply",o.operationName());assertEquals(OperationStatus.ACTIVE,o.status());assertEquals(0,o.rowVersion());}
@Test void preservesIdentityWhenRevised(){var saved=new CanonicalOperation(9L,2,AssetCode.of("payment.refund.apply"),"Refund",null,InvocationMode.SYNC,IdempotencyClass.UNKNOWN,DataClassification.INTERNAL,"payments",OperationStatus.ACTIVE,3,null,null);var revised=saved.revise("Refund Apply",null,InvocationMode.ASYNC,IdempotencyClass.IDEMPOTENT_WITH_KEY,DataClassification.CONFIDENTIAL,"payments",OperationStatus.ACTIVE,3);assertEquals(9,revised.id());assertEquals(2,revised.capabilityId());assertEquals("payment.refund.apply",revised.operationCode().value());}
}
