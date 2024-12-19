package com.mgmresorts.order.function;

public interface IFunctions {
    String NAME_CHECKOUT = "checkout-cart";
    String NAME_ORDER_READ = "read-order";
    String NAME_RESERVATION_RETRIEVE = "retrieve-reservation";
    String NAME_RESERVATION_PREVIEW = "preview-reservation";
    String NAME_RESERVATION_UPDATE = "update-reservation";
    String NAME_RESERVATION_CANCEL = "cancel-reservation";

    String URL_CHECKOUT = "checkout";
    String URL_ORDER_READ = "order";
    String URL_RESERVATION_RETRIEVE = "reservation";
    String URL_RESERVATION_PREVIEW = "reservation/preview";
    String URL_RESERVATION_UPDATE = "reservation/update";
    String URL_RESERVATION_CANCEL = "reservation/cancel";

    String ORDER_EVENT_CONSUMER = "order-event-consumer";
}
