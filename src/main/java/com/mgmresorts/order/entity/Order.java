package com.mgmresorts.order.entity;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.mgmresorts.common.data.AuditEntity;
import com.mgmresorts.order.dto.PackageConfigDetails;
import com.mgmresorts.order.dto.PriceDetails;
import com.mgmresorts.order.dto.RoomTotals;
import com.mgmresorts.order.dto.ShowTotals;


@JsonPropertyOrder({ "id", "mgmId", "cartId", "newCartId", "status", "customerId", "enableJwb", "ttl", "lineItems",
        "rawCart", "priceDetails", "roomTotals", "showTotals", "packageConfigDetails", "type", "version", "canRetryCheckout", "f1Package", "complete", "checkoutRequest",
"orderInitiatedAt", "orderUpdatedAt", "paymentSessionId"})
public class Order extends AuditEntity {
    @JsonProperty("id")
    private String id;

    @JsonProperty("ttl")
    private long ttl;

    @JsonProperty("mgmId")
    private String mgmId;

    @JsonProperty("cartId")
    private String cartId;

    @JsonProperty("newCartId")
    private String newCartId;

    @JsonProperty("status")
    private OrderStatus status;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("mlifeId")
    private String mlifeId;

    @JsonProperty("lineItems")
    private List<LineItem> lineItems;

    @JsonProperty("enableJwb")
    private boolean enableJwb;
    
    @JsonProperty("jwbFlow")
    private boolean jwbFlow;

    @JsonProperty("rawCart")
    private String rawCart;
    
    @JsonProperty("priceDetails")
    private PriceDetails priceDetails;
    
    @JsonProperty("roomTotals")
    private RoomTotals roomTotals;
    
    @JsonProperty("showTotals")
    private ShowTotals showTotals;

    @JsonProperty("packageConfigDetails")
    private PackageConfigDetails packageConfigDetails;
    
    @JsonProperty("type")
    private Type type;
    
    @JsonProperty("version")
    private Version version;
    
    @JsonProperty("f1Package")
    private boolean f1Package;
    
    @JsonProperty("canRetryCheckout")
    private boolean canRetryCheckout;

    @JsonProperty("complete")
    private boolean complete;

    @JsonProperty("checkoutRequest")
    private String checkoutRequest;

    @JsonProperty("orderInitiatedAt")
    private ZonedDateTime orderInitiatedAt;

    @JsonProperty("orderUpdatedAt")
    private ZonedDateTime orderUpdatedAt;
    
    @JsonProperty("paymentSessionId")
    private String paymentSessionId;

    @JsonProperty("encryptedEmailAddress")
    private String encryptedEmailAddress;

    public boolean isEnableJwb() {
        return enableJwb;
    }

    public void setEnableJwb(boolean enableJwb) {
        this.enableJwb = enableJwb;
    }

    public boolean isJwbFlow() {
        return jwbFlow;
    }

    public void setJwbFlow(boolean jwbFlow) {
        this.jwbFlow = jwbFlow;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public String getMgmId() {
        return mgmId;
    }

    public void setMgmId(String mgmId) {
        this.mgmId = mgmId;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getNewCartId() {
        return newCartId;
    }

    public void setNewCartId(String newCartId) {
        this.newCartId = newCartId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getMlifeId() {
        return mlifeId;
    }

    public void setMlifeId(String mlifeId) {
        this.mlifeId = mlifeId;
    }

    public List<LineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<LineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public String getRawCart() {
        return rawCart;
    }

    public void setRawCart(String rawCart) {
        this.rawCart = rawCart;
    }

    public PriceDetails getPriceDetails() {
        return priceDetails;
    }

    public void setPriceDetails(PriceDetails priceDetails) {
        this.priceDetails = priceDetails;
    }

    public RoomTotals getRoomTotals() {
        return roomTotals;
    }

    public void setRoomTotals(RoomTotals roomTotals) {
        this.roomTotals = roomTotals;
    }

    public ShowTotals getShowTotals() {
        return showTotals;
    }

    public void setShowTotals(ShowTotals showTotals) {
        this.showTotals = showTotals;
    }

    public PackageConfigDetails getPackageConfigDetails() {
        return packageConfigDetails;
    }

    public void setPackageConfigDetails(PackageConfigDetails packageConfigDetails) {
        this.packageConfigDetails = packageConfigDetails;
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

    public boolean isF1Package() {
        return f1Package;
    }

    public void setF1Package(boolean f1Package) {
        this.f1Package = f1Package;
    }

    public boolean isCanRetryCheckout() {
        return canRetryCheckout;
    }

    public void setCanRetryCheckout(boolean canRetryCheckout) {
        this.canRetryCheckout = canRetryCheckout;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public String getCheckoutRequest() {
        return checkoutRequest;
    }

    public void setCheckoutRequest(String checkoutRequest) {
        this.checkoutRequest = checkoutRequest;
    }

    public ZonedDateTime getOrderInitiatedAt() {
        return orderInitiatedAt;
    }

    public void setOrderInitiatedAt(ZonedDateTime orderInitiatedAt) {
        this.orderInitiatedAt = orderInitiatedAt;
    }

    public String getPaymentSessionId() {
        return paymentSessionId;
    }

    public void setPaymentSessionId(String paymentSessionId) {
        this.paymentSessionId = paymentSessionId;
    }

    public String getEncryptedEmailAddress() {
        return encryptedEmailAddress;
    }

    public void setEncryptedEmailAddress(String encryptedEmailAddress) {
        this.encryptedEmailAddress = encryptedEmailAddress;
    }

    public ZonedDateTime getOrderUpdatedAt() {
        return orderUpdatedAt;
    }

    public void setOrderUpdatedAt(ZonedDateTime orderUpdatedAt) {
        this.orderUpdatedAt = orderUpdatedAt;
    }
}
