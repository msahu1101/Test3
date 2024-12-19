package com.mgmresorts.order.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.mgmresorts.common.data.AuditEntity;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.Order;
import com.mgmresorts.shopping.cart.dto.Cart;
import java.time.ZonedDateTime;


@JsonPropertyOrder({ "order", "cart", "checkoutRequest", "orderInitiatedAt"})
public class OrderEvent extends AuditEntity {
    @JsonProperty("order")
    private Order order;

    @JsonProperty("cart")
    private Cart cart;

    @JsonProperty("checkoutRequest")
    private CheckoutRequest checkoutRequest;

    @JsonProperty("orderInitiatedAt")
    private ZonedDateTime orderInitiatedAt;

    @JsonProperty("orderUpdatedAt")
    private ZonedDateTime orderUpdatedAt;

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;
    }

    public ZonedDateTime getOrderInitiatedAt() {
        return orderInitiatedAt;
    }

    public void setOrderInitiatedAt(ZonedDateTime orderInitiatedAt) {
        this.orderInitiatedAt = orderInitiatedAt;
    }

    public CheckoutRequest getCheckoutRequest() {
        return checkoutRequest;
    }

    public void setCheckoutRequest(CheckoutRequest checkoutRequest) {
        this.checkoutRequest = checkoutRequest;
    }

    public ZonedDateTime getOrderUpdatedAt() {
        return orderUpdatedAt;
    }

    public void setOrderUpdatedAt(ZonedDateTime orderUpdatedAt) {
        this.orderUpdatedAt = orderUpdatedAt;
    }
}
