package com.mgmresorts.order.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.mgmresorts.common.data.AuditEntity;

@JsonPropertyOrder({"id, confirmationNumber, ttl"})
public class OrderConfirmationMapping extends AuditEntity {
    @JsonProperty("id")
    private String id;

    @JsonProperty("confirmationNumber")
    private String confirmationNumber;
    
    @JsonProperty("cartId")
    private String cartId;
    
    @JsonProperty("mgmId")
    private String mgmId;
    
    @JsonProperty("type")
    private Type type;
    
    @JsonProperty("version")
    private Version version;

    @JsonProperty("ttl")
    private long ttl;

    public String getConfirmationNumber() {
        return this.confirmationNumber;
    }

    public void setConfirmationNumber(String confirmationNumber) {
        this.confirmationNumber = confirmationNumber;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getMgmId() {
        return mgmId;
    }

    public void setMgmId(String mgmId) {
        this.mgmId = mgmId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }
}
