package com.mgmresorts.order.service.transformer;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.mgmresorts.common.event.enterprise.publish.IEnterpriseEventTransformer;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.services.CancelReservationResponse;
import com.mgmresorts.order.event.dto.OrderEvent;

public class CancelReservationEventTransformer implements IEnterpriseEventTransformer<CancelReservationResponse, OrderEvent> {
    @Override
    public float version() {
        return 1.0f;
    }

    @Override
    public String referenceNumber(Object data) {
        return data instanceof OrderEvent ? ((OrderEvent) data).getOrderEventId() : null;
    }

    @Override
    public OrderEvent transform(CancelReservationResponse in) throws AppException {
        final OrderEvent entity = new OrderEvent();
        entity.setEventOccurrenceTime(ZonedDateTime.now());
        entity.setEventTriggerTime(ZonedDateTime.now());
        if (StringUtils.isNotBlank(in.getOrderId())) {
            entity.setOrderEventId(in.getOrderId());
        }
        entity.setEventName("Canceled Reservation");
        entity.setTriggeringOrderLineItemIds(List.of(in.getRoomReservationResponse().getConfirmationNumber()));
        entity.setOrderUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
        return entity;
    }
}
