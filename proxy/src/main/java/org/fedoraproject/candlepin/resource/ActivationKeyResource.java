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

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.ActivationKey;
import org.fedoraproject.candlepin.model.ActivationKeyCurator;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * SubscriptionTokenResource
 */
public class ActivationKeyResource {
    private static Logger log = Logger
        .getLogger(ActivationKeyResource.class);
    private ActivationKeyCurator activationKeyCurator;
    private PoolCurator poolCurator;
    private I18n i18n;
    private ConsumerResource consumerResource;

    @Inject
    public ActivationKeyResource(ActivationKeyCurator activationKeyCurator,
        I18n i18n,
        PoolCurator poolCurator,
        ConsumerResource consumerResource) {
        this.activationKeyCurator = activationKeyCurator;
        this.i18n = i18n;
        this.poolCurator = poolCurator;
        this.consumerResource = consumerResource;
    }

    @GET
    @Path("/activation_keys/{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKey getActivationKey(
        @PathParam("activation_key_id") String activationKeyId) {
        ActivationKey key = findKey(activationKeyId);

        return key;
    }

    @GET
    @Path("/activation_keys/{activation_key_id}/pools")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Pool> getActivationKeyPools(
        @PathParam("activation_key_id") String activationKeyId) {
        ActivationKey key = findKey(activationKeyId);
        return key.getPools();
    }

    @POST
    @Path("/activation_keys/{activation_key_id}/pools/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pool addPoolToKey(
        @PathParam("activation_key_id") String activationKeyId,
        @PathParam("pool_id") String poolId) {
        ActivationKey key = findKey(activationKeyId);
        Pool pool = findPool(poolId);
        key.getPools().add(pool);
        activationKeyCurator.update(key);
        return pool;
    }

    @POST
    @Path("/activate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @AllowRoles(roles = { Role.NO_AUTH})
    public Consumer activate(Consumer consumer, @Context Principal principal,
        @QueryParam("username") String userName,
        @QueryParam("activation_keys") List<String> keyStrings)
        throws BadRequestException {

        // first, look for keys. If it is not found, throw an exception
        List<ActivationKey> keys = new ArrayList<ActivationKey>();
        Owner owner = null;
        if (keyStrings == null || keyStrings.size() == 0) {
            throw new BadRequestException(
                i18n.tr("No activation keys were provided"));
        }
        for (String keyString : keyStrings) {
            ActivationKey key = findKey(keyString);
            if (owner == null) {
                owner = key.getOwner();
            }
            else {
                if (owner.getId() != key.getOwner().getId()) {
                    throw new BadRequestException(
                        i18n.tr("The keys provided are for different owners"));
                }
            }
            keys.add(findKey(keyString));
        }

        // set the owner on the principal off of the first key
        principal.setOwner(owner);

        // Create the consumer via the normal path
        Consumer newConsumer = consumerResource.create(consumer, principal, userName);

        return newConsumer;
    }

    @DELETE
    @Path("/activation_keys/{activation_key_id}/pools/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pool removePoolFromKey(
        @PathParam("activation_key_id") String activationKeyId,
        @PathParam("pool_id") String poolId) {
        ActivationKey key = findKey(activationKeyId);
        Pool pool = findPool(poolId);
        key.getPools().remove(pool);
        activationKeyCurator.update(key);
        return pool;
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/activation_keys")
    public List<ActivationKey> findActivationKey() {
        List<ActivationKey> keyList = activationKeyCurator.listAll();
        return keyList;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/activation_keys")
    public ActivationKey createActivationKey(
        ActivationKey activationKey) {
        this.verifyName(activationKey);
        ActivationKey newKey = activationKeyCurator
            .create(activationKey);

        return newKey;
    }

    @DELETE
    @Path("/activation_keys/{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteActivationKey(
        @PathParam("activation_key_id") String activationKeyId) {
        ActivationKey key = findKey(activationKeyId);

        log.debug("Deleting info " + activationKeyId);

        activationKeyCurator.delete(key);
    }

    protected void verifyName(ActivationKey key) {
        if (key.getName() == null) {
            throw new BadRequestException(
                i18n.tr("Names are required for Activation keys"));
        }
    }

    protected ActivationKey findKey(String activationKeyId) {
        ActivationKey key = activationKeyCurator
        .find(activationKeyId);

        if (key == null) {
            throw new BadRequestException(i18n.tr(
                "ActivationKey with id {0} could not be found",
                activationKeyId));
        }
        return key;
    }

    protected Pool findPool(String poolId) {
        Pool pool = poolCurator
        .find(poolId);

        if (pool == null) {
            throw new BadRequestException(i18n.tr(
                "Pool with id {0} could not be found",
                poolId));
        }
        return pool;
    }
}
