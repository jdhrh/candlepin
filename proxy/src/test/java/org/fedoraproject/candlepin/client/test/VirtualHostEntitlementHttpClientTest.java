package org.fedoraproject.candlepin.client.test;

import static org.junit.Assert.*;

import java.util.List;

import org.fedoraproject.candlepin.client.test.ConsumerHttpClientTest.TestServletConfig;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.model.test.SpacewalkCertificateCuratorTest;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import com.redhat.rhn.common.cert.CertificateFactory;
import com.sun.jersey.api.client.WebResource;

public class VirtualHostEntitlementHttpClientTest extends AbstractGuiceGrizzlyTest {

    private Product virtHost;
    private Product virtHostPlatform;
    private Product virtGuest;
    
    private Owner o;
    private Consumer parentSystem;

    @Before
    public void setUp() throws Exception {
        TestServletConfig.servletInjector = injector;
        startServer(TestServletConfig.class);
        
        o = TestUtil.createOwner();
        ownerCurator.create(o);
        
        String certString = SpacewalkCertificateCuratorTest.readCertificate(
                "/certs/spacewalk-with-channel-families.cert");
        spacewalkCertificateCurator.parseCertificate(CertificateFactory.read(certString), o);

        List<EntitlementPool> pools = entitlementPoolCurator.listByOwner(o);
        assertTrue(pools.size() > 0);

        virtHost = productCurator.lookupByLabel(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST);
        assertNotNull(virtHost);
        
        virtHostPlatform = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_VIRT_HOST_PLATFORM);
        
        virtGuest = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_VIRT_GUEST);
        
        ConsumerType system = new ConsumerType(ConsumerType.SYSTEM);
        consumerTypeCurator.create(system);
        
        parentSystem = new Consumer("system", o, system);
        parentSystem.getFacts().setFact("total_guests", "0");
        consumerCurator.create(parentSystem);
    }
    
    @Test
    public void virtualizationHostConsumption() {
        assertNull(entitlementPoolCurator.lookupByOwnerAndProduct(o,
                parentSystem, virtGuest));
        
        WebResource r = resource()
            .path("/entitlement/consumer/" + parentSystem.getUuid() + "/product/" + virtHost.getLabel());
        String s = r.accept("application/json")
             .type("application/json")
             .post(String.class);
        
        assertVirtualizationHostConsumption();
    }

    @Test
    public void virtualizationHostPlatformConsumption() {
        assertNull(entitlementPoolCurator.lookupByOwnerAndProduct(o,
                parentSystem, virtGuest));

        WebResource r = resource()
            .path("/entitlement/consumer/" + parentSystem.getUuid() + "/product/" + virtHostPlatform.getLabel());
        String s = r.accept("application/json")
             .type("application/json")
             .post(String.class);

        assertVirtualizationHostPlatformConsumption();
    }

    private void assertVirtualizationHostConsumption() {
        // Consuming a virt host entitlement should result in a pool just for us to consume
        // virt guests.
        EntitlementPool consumerPool = entitlementPoolCurator.lookupByOwnerAndProduct(o,
                parentSystem, virtGuest);
        assertNotNull(consumerPool);
        assertNotNull(consumerPool.getConsumer());
        assertEquals(parentSystem.getId(), consumerPool.getConsumer().getId());
        assertEquals(new Long(5), consumerPool.getMaxMembers());
        assertNotNull(consumerPool.getSourceEntitlement().getId());
    }
    
    private void assertVirtualizationHostPlatformConsumption() {
        // Consuming a virt host entitlement should result in a pool just for us to consume
        // virt guests.
        EntitlementPool consumerPool = entitlementPoolCurator.lookupByOwnerAndProduct(o,
                parentSystem, virtGuest);
        assertNotNull(consumerPool.getConsumer());
        assertEquals(parentSystem.getId(), consumerPool.getConsumer().getId());
        assertTrue(consumerPool.getMaxMembers() < 0);
        assertNotNull(consumerPool.getSourceEntitlement().getId());
    }
    

}
