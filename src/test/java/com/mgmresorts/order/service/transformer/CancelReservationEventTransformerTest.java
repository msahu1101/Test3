package com.mgmresorts.order.service.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.services.CancelReservationResponse;
import com.mgmresorts.order.event.dto.OrderEvent;

import uk.co.jemos.podam.api.PodamFactoryImpl;

public class CancelReservationEventTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final CancelReservationEventTransformer transformer = new CancelReservationEventTransformer();

    @Test
    final void testTransform() throws AppException {
        final CancelReservationResponse cancelReservationResponse = podamFactoryImpl.manufacturePojo(CancelReservationResponse.class);
        final OrderEvent orderEvent = transformer.transform(cancelReservationResponse);
        assertEquals(orderEvent.getOrderEventId(), cancelReservationResponse.getOrderId());
        assertEquals(orderEvent.getTriggeringOrderLineItemIds().get(0), cancelReservationResponse.getRoomReservationResponse().getConfirmationNumber());
        assertNotNull(orderEvent.getOrderUpdatedAt());
    }
}
