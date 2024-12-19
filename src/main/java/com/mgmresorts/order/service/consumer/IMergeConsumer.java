package com.mgmresorts.order.service.consumer;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.event.dto.show.ShowReservationFromBody;
import java.util.function.Consumer;

public interface IMergeConsumer {
    Consumer<Order> create(OrderLineItem orderLineItem) throws AppException;

    Consumer<Order> updateShowSuccess(ShowReservationFromBody payload) throws AppException;

    Consumer<Order> updateShowFailure(ShowReservationFromBody payload) throws AppException;
}
