/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.*;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerInfo;
import org.fedoraproject.candlepin.model.ConsumerRepository;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeRepository;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;

/**
 * ConsumerResourceTest
 * @version $Rev$
 */
public class ConsumerResourceTest extends DatabaseTestFixture {
    
    private ConsumerRepository consumerRepository;
    private ConsumerTypeRepository consumerTypeRepository;
    private ConsumerType standardSystemType;

    @Before
    public void setUp() {
        super.setUp();
        
        Injector injector = Guice.createInjector(
                new CandlePingTestingModule(), 
                PersistenceService.usingJpa()
                    .across(UnitOfWork.TRANSACTION)
                    .buildModule()
        );
        
        
        consumerRepository = injector.getInstance(ConsumerRepository.class);
        consumerTypeRepository = injector.getInstance(ConsumerTypeRepository.class);
        
        standardSystemType = new ConsumerType("standard-system");
        consumerTypeRepository.create(standardSystemType);
    }
    
    @Test
    public void testCreateConsumer() throws Exception {
        String newname = "test-consumer-" + System.currentTimeMillis();
        
        ConsumerResource capi = new ConsumerResource();
        ConsumerInfo ci = new ConsumerInfo();
        ci.setMetadataField("name", newname);
        
        Consumer c = new Consumer();
        c.setName(ci.getMetadataField("name"));
        c.setType(standardSystemType);
        c.setInfo(ci);
        
        Consumer saved = consumerRepository.create(c);
        
        assertEquals(saved.getId(), consumerRepository.find(saved.getId()).getId());
        //capi.create(ci, type);
    }
    
//    @Test
//    public void testDelete() {
//        Consumer c = TestUtil.createConsumer();
//        String uuid = c.getUuid();
//        ConsumerResource capi = new ConsumerResource();
//        assertNotNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
//        capi.delete(uuid);
//        assertNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
//    }

//    @Test
//    public void testJSON() { 
//        ClientConfig cc = new DefaultClientConfig();
//        Client c = Client.create(cc);
//
//        ConsumerInfo ci = new ConsumerInfo();
//        ci.setMetadataField("name", "jsontestname");
//        ci.setType(new ConsumerType("standard-system"));
//        
//        WebResource res =
//            c.resource("http://localhost:8080/candlepin/consumer/");
//        Consumer rc = res.type("application/json").post(Consumer.class, ci);
//        assertNotNull(rc);
//        assertNotNull(rc.getUuid());
//        System.out.println(rc.getUuid());
        
//        WebResource delres =
//          c.resource("http://localhost:8080/candlepin/consumer/");
//        delres.accept("application/json").delete(rc.getUuid());
//        
//        assertNull(ObjectFactory.get().lookupByUUID(c.getClass(), rc.getUuid()));
//    }
}
