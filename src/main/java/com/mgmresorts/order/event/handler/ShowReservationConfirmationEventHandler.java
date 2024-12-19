package com.mgmresorts.order.event.handler;

import java.util.function.Consumer;

import javax.inject.Inject;

import com.mgmresorts.common.dto.services.WebHookResponse;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.event.dto.Event;
import com.mgmresorts.event.dto.OrderServiceEvent;
import com.mgmresorts.event.dto.show.ShowReservationFromBody;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.event.EventHandler;
import com.mgmresorts.order.event.IEventHandler;
import com.mgmresorts.order.event.InEventType;
import com.mgmresorts.order.service.consumer.IMergeConsumer;

@EventHandler(InEventType.SHOW_RESERVATION_CREATE)
public class ShowReservationConfirmationEventHandler implements IEventHandler {
    private final Logger logger = Logger.get(ShowReservationConfirmationEventHandler.class);

    @Inject
    private IOrderAccess orderAccess;

    @Inject
    private IMergeConsumer mergeConsumer;

    @Override
    public void process(WebHookResponse response, Event data) throws AppException {
        response.setResponse("Success");
    }

    @Override
    public void process(OrderServiceEvent data) throws AppException {
        final ShowReservationFromBody payload = data.getData().getBody();
        
        if (payload != null && payload.getShowReservation() != null
                && payload.getShowReservation().getReservationResponse() != null
                && !Utils.isEmpty(payload.getShowReservation().getReservationResponse().getOrderId())
                && !Utils.isEmpty(payload.getShowReservation().getReservationResponse().getOrderLineItemId())) {
            final String orderId = payload.getShowReservation().getReservationResponse().getOrderId();
            final Consumer<com.mgmresorts.order.entity.Order> merger = mergeConsumer.updateShowSuccess(payload);
            orderAccess.mergeAndUpdate(orderId, com.mgmresorts.order.entity.Order.class, merger);
        } else {
            logger.info("Missing contents from SBS event payload or event is not for asynchronous checkout");
        }
    }
}
