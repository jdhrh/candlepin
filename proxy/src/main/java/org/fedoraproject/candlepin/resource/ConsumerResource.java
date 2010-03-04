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
package org.fedoraproject.candlepin.resource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.ClientCertificate;
import org.fedoraproject.candlepin.model.ClientCertificateStatus;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerFacts;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificate;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificateCurator;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Product;

import com.google.inject.Inject;

/**
 * API Gateway for Consumers
 */
@Path("/consumer")
public class ConsumerResource {
    
    @Context 
    private UriInfo uriInfo;
    
    private static Logger log = Logger.getLogger(ConsumerResource.class);
    private OwnerCurator ownerCurator;
    private Owner owner;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private ConsumerIdentityCertificateCurator consumerIdCertCurator;

    private String username;

    /**
     * @param ownerCurator interact with owner
     * @param consumerCurator interact with consumer
     * @param consumerTypeCurator interact wtih consumerType
     * @param consumerIdCertCurator interact wtih consumerIdCert
     * @param request servlet request
     */
    @Inject
    public ConsumerResource(OwnerCurator ownerCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        ConsumerIdentityCertificateCurator consumerIdCertCurator,
        @Context HttpServletRequest request) {

        this.ownerCurator = ownerCurator;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.consumerIdCertCurator = consumerIdCertCurator;
        this.username = (String) request.getAttribute("username");
        if (username != null) {
            this.owner = ownerCurator.lookupByName(username);
            if (owner == null) {
                owner = ownerCurator.create(new Owner(username));
            }
        }
    }
   
    /**
     * List available Consumers
     * @return list of available consumers.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<Consumer> list() {
        return consumerCurator.findAll();
    }
   
    /**
     * Return the consumer identified by the given uuid.
     * @param uuid uuid of the consumer sought.
     * @return the consumer identified by the given uuid.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{consumer_uuid}")
    public Consumer getConsumer(@PathParam("consumer_uuid") String uuid) {
        Consumer toReturn = consumerCurator.lookupByUuid(uuid);
        
        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException(
            "Consumer with UUID '" + uuid + "' could not be found"); 
    }
    
    /**
     * Create a Consumer
     * @param in Consumer metadata encapsulated in a ConsumerInfo.
     * @return newly created Consumer
     * 
     *  We are calling this "registerConsumer" in the api discussions
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Consumer create(Consumer in) {
        // API:registerConsumer
        Owner owner = ownerCurator.findAll().get(0); // TODO: actually get current owner
        Consumer consumer = new Consumer();
        
        log.debug("Got consumerTypeLabel of: " + in.getType().getLabel());
        ConsumerType type = consumerTypeCurator.lookupByLabel(in.getType().getLabel());
        log.debug("got metadata: ");
        log.debug(in.getFacts().getMetadata());
        for (String key : in.getFacts().getMetadata().keySet()) {
            log.debug("   " + key + " = " + in.getFact(key));
        }
        
        if (type == null) {
            throw new BadRequestException(
                "No such consumer type: " + in.getType().getLabel());
        }

        try {
            consumer = consumerCurator.create(Consumer.createFromConsumer(in, owner, type));
            
            ConsumerIdentityCertificate idCert = consumerIdCertCurator.getCert();
            consumer.setIdCert(idCert);
            return consumer;
            
        }
        catch (RuntimeException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
   
    /**
     * delete the consumer.
     * @param uuid uuid of the consumer to delete.
     */
    @DELETE
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{consumer_uuid}")
    public void deleteConsumer(@PathParam("consumer_uuid") String uuid) {
        log.debug("deleteing  consumer_uuid" + uuid);
        try {
            consumerCurator.delete(consumerCurator.lookupByUuid(uuid));
        }
        catch (RuntimeException e) {
            throw new NotFoundException(e.getMessage());
        }
    }
    
    /**
     * Returns the ConsumerInfo for the given Consumer.
     * @return the ConsumerInfo for the given Consumer.
     */
    @GET @Path("/info")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    // TODO: What consumer?
    public ConsumerFacts getInfo() {
        ConsumerFacts ci = new ConsumerFacts();
//        ci.setType(new ConsumerType("system"));
        ci.setConsumer(null);
//        Map<String,String> m = new HashMap<String,String>();
//        m.put("cpu", "i386");
//        m.put("hey", "biteme");
//        ci.setMetadata(m);
        ci.setFact("cpu", "i386");
        ci.setFact("hey", "foobar");
        return ci;
    }
    
    /**
     * removes the product whose id matches pid, from the consumer, cid.
     * @param cid Consumer ID to affect
     * @param pid Product ID to remove from Consumer.
     */
//    @DELETE @Path("{cid}/products/{pid}")
//    public void delete(@PathParam("cid") String cid,
//                       @PathParam("pid") String pid) {
//        System.out.println("cid " + cid + " pid = " + pid);
//        Consumer c = (Consumer) ObjectFactory.get().lookupByUUID(Consumer.class, cid);
//        if (!c.getConsumedProducts().remove(pid)) {
//            log.error("no product " + pid + " found.");
//        }
//    }

    /**
     * Returns the product whose id matches pid, from the consumer, cid.
     * @param cid Consumer ID to affect
     * @param pid Product ID to remove from Consumer.
     * @return the product whose id matches pid, from the consumer, cid.
     */
    @GET @Path("{cid}/products/{pid}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Product getProduct(@PathParam("cid") String cid,
                       @PathParam("pid") String pid) {
        return null;
    }

    /**
     * Return the content of the file identified by the given filename.
     * @param path filename path.
     * @return the content of the file identified by the given filename.
     * @throws Exception if there's a problem loading the file.
     */
    public byte[] getBytesFromFile(String path) throws Exception {
        InputStream is = this.getClass().getResource(path).openStream();
        byte [] bytes = null;
        try {
            bytes = IOUtils.toByteArray(is);
        }
        finally {
            IOUtils.closeQuietly(is);
        }
        return bytes;
    }

    /**
     * Return the client certificate for the given consumer.
     * 
     * @param consumerUuid uuid of the consumer whose client certificate is
     * sought.
     * @return list of the client certificates for the given consumer.
     */
//    @GET
//    @Path("{consumer_uuid}/certificates")
//    @Produces({ MediaType.APPLICATION_JSON })
    private List<ClientCertificate> getClientCertificates(
        @PathParam("consumer_uuid") String consumerUuid) {

        log.debug("Getting client certificates for consumer: " + consumerUuid);

        List<ClientCertificate> allCerts = new LinkedList<ClientCertificate>();
        
        //FIXME: make this look the cert from the cert service or whatever
        // Using a static (and unusable) cert for now for demo purposes:
        try {
            byte[] bytes = getBytesFromFile("/testcert-cert.p12");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream stream = new ObjectOutputStream(baos);
            stream.write(bytes);
            stream.flush();
            stream.close();

            // FIXME : these won't be a pkcs12 bundle
            ClientCertificate cert = new ClientCertificate();
            cert.setEntitlementCert(baos.toByteArray());

            allCerts.add(cert);
            // Add it again just so we can see multiple return values:
            allCerts.add(cert);
            
            return allCerts;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
   
    /**
     * Retrieve the client certificate and it's status for the given Consumer.
     * @param consumerUuid uuid for the consumer whose certificates are sought.
     * @return list of client certificate status.
     */
    @POST
    @Path("{consumer_uuid}/certificates")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<ClientCertificateStatus> getClientCertificateStatus(
        @PathParam("consumer_uuid") String consumerUuid) {
        
        List<ClientCertificateStatus> updatedCertificateStatus =
            new LinkedList<ClientCertificateStatus>();
       
        List<ClientCertificate> clientCerts = getClientCertificates(consumerUuid);

        for (ClientCertificate clientCert : clientCerts) {
            log.debug("found client cert:" + clientCert);
            ClientCertificateStatus clientCertficiateStatus =
                new ClientCertificateStatus("somenumber-111", "", clientCert);
            updatedCertificateStatus.add(clientCertficiateStatus);
        }

        log.debug("clientCerts: " + clientCerts);
        //return clientCerts;
        //       return foo;
        return updatedCertificateStatus;
    }
}
