package com.mgmresorts.order.service;

public interface IEventType {
    String EVENT_CART_CHECKOUT = "Event.Cart.Checkout";
    String EVENT_CART_CHECKOUT_FAILURE = "Event.Cart.Checkout.Failure";
    String EVENT_CART_CHECKOUT_EMAIL = "Event.Cart.Checkout.Email";
    String EVENT_CART_CHECKOUT_EMAIL_FAILURE = "Event.Cart.Checkout.Email.Failure";
    String EVENT_CART_ORDER_CHECKOUT_TIMEOUT = "Event.Cart.Order.Checkout.Timeout";
    String EVENT_CART_ORDER_CHECKOUT_TIMEOUT_FAILURE = "Event.Cart.Order.Checkout.Timeout.Failure";
    String EVENT_CART_ORDER_UPDATE = "Event.Cart.Product.Modify";
    String EVENT_CART_ORDER_UPDATE_FAILURE = "Event.Cart.Product.Modify.Failure";
    String EVENT_CART_PRODUCT_CANCEL = "Event.Cart.Product.Cancel";
    String EVENT_CART_PRODUCT_CANCEL_FAILURE = "Event.Cart.Product.Cancel.Failure";
    String ORDER_DEST_TOPIC = "event.topic.order";
}
