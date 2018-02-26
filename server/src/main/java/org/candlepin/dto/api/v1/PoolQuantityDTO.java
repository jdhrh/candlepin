/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.CandlepinDTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * A DTO representation of the Branding entity used internally by
 * other DTO entities, like PoolDTO and SubscriptionDTO.
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PoolQuantityDTO extends CandlepinDTO<PoolQuantityDTO> {

    public static final long serialVersionUID = 1L;

    private Integer quantity;
    private PoolDTO pool;

    /**
     * Initializes a new BrandingDTO instance with null values.
     */
    public PoolQuantityDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new PoolQuantityDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public PoolQuantityDTO(PoolQuantityDTO source) {
        super(source);
    }

    /**
     * Initializes a new PoolQuantityDTO instance based on the given values.
     *
     * @param pool this pool quantity's pool.
     *
     * @param quantity this pool quantity's quantity.
     */
    @JsonCreator
    public PoolQuantityDTO(
        @JsonProperty("pool") PoolDTO pool,
        @JsonProperty("quantity") Integer quantity) {

        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("quantity is not valid");
        }

        this.pool = pool;
        this.quantity = quantity;
    }

    /**
     * Returns this pool quantity's product id.
     *
     * @return this pool quantity's product id.
     */
    public PoolDTO getPool() {
        return pool;
    }

    /**
     * Sets this pool quantity's product id.
     *
     * @param pool the pool to set on this pool quantity DTO.
     *
     * @return a reference to this pool quantity DTO object.
     */
    public PoolQuantityDTO setPool(PoolDTO pool) {
        this.pool = pool;
        return this;
    }

    /**
     * Returns this pool quantity's quantity.
     *
     * @return this pool quantity's quantity.
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * Sets this pool quantity's name.
     *
     * @param quantity the quantity to set on this pool quantity DTO.
     *
     * @return a reference to this pool quantity DTO object.
     */
    public PoolQuantityDTO setQuantity(Integer quantity) {
        this.quantity = quantity;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        PoolDTO pool = this.getPool();
        return String.format("PoolQuantityDTO [poolId: %s, quantity: %s]",
            pool != null ? pool.getId() : null, this.getQuantity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof PoolQuantityDTO) {
            PoolQuantityDTO that = (PoolQuantityDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getQuantity() != null ? this.getQuantity().intValue() : null,
                that.getQuantity() != null ? that.getQuantity().intValue() : null)
                .append(this.getPool() != null ? this.getPool().getId() : null,
                that.getPool() != null ? that.getPool().getId() : null);

            return builder.isEquals();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getQuantity())
            .append(this.getPool() != null ? this.getPool().getId() : null);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolQuantityDTO clone() {
        // Nothing to copy here. All fields are immutable types.

        return super.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolQuantityDTO populate(PoolQuantityDTO source) {
        super.populate(source);

        this.setPool(source.getPool());
        this.setQuantity(source.getQuantity());

        return this;
    }
}
