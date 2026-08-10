package com.ftk.tpip.catalog.domain.model;
import static org.junit.jupiter.api.Assertions.*;import com.ftk.tpip.shared.AssetCode;import org.junit.jupiter.api.Test;
class CatalogHierarchyTest{
@Test void createsActiveDomainAndCapability(){var d=BusinessDomain.create(AssetCode.of("payment")," Payments ",null,"payments");var c=CanonicalCapability.create(1,AssetCode.of("payment.refund")," Refund ",null,"payments");assertEquals("Payments",d.domainName());assertEquals(CatalogAssetStatus.ACTIVE,d.status());assertEquals("Refund",c.capabilityName());assertEquals(1,c.domainId());}
@Test void keepsHierarchyIdentityOnRevision(){var c=new CanonicalCapability(2L,1,AssetCode.of("payment.refund"),"Refund",null,"payments",CatalogAssetStatus.ACTIVE,0,null,null);var revised=c.revise("Refund Management",null,"payments",CatalogAssetStatus.INACTIVE,0);assertEquals(1,revised.domainId());assertEquals("payment.refund",revised.capabilityCode().value());}
}
