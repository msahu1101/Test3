package com.mgmresorts.order.service.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.services.UpdateReservationResponse;
import com.mgmresorts.order.event.dto.Order;
import com.mgmresorts.order.event.dto.OrderLineItem;
import com.mgmresorts.rbs.model.RatesSummary;

import uk.co.jemos.podam.api.PodamFactoryImpl;

public class UpdateReservationEventTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final UpdateReservationEventTransformer transformer = new UpdateReservationEventTransformer();

    @Test
    final void testToRight() throws AppException {
        UpdateReservationResponse updateReservationResponse = podamFactoryImpl.manufacturePojo(UpdateReservationResponse.class);
        updateReservationResponse.getRoomReservationResponse().getTripDetails().setCheckInDate("2024-02-02");
        updateReservationResponse.getRoomReservationResponse().getTripDetails().setCheckOutDate("2024-02-04");
        final Order orderEvent = transformer.toRight(updateReservationResponse);

        assertEquals(updateReservationResponse.getOrderId(),orderEvent.getId());
        assertNotNull(orderEvent.getOrderLineItems());
        assertNotNull(orderEvent.getOrderLineItems().get(0));
        OrderLineItem  orderLineItem = orderEvent.getOrderLineItems().get(0);
        RatesSummary ratesSummary = updateReservationResponse.getRoomReservationResponse().getRatesSummary();
        assertEquals(OrderLineItem.ProductType.ROOM,orderLineItem.getProductType());
        assertEquals(ratesSummary.getReservationTotal(), orderLineItem.getLineItemPrice());
        assertEquals(ratesSummary.getDepositDue(), orderLineItem.getLineItemDeposit());
        assertEquals(ratesSummary.getProgramDiscount(), orderLineItem.getLineItemDiscount());
        assertEquals(ratesSummary.getBalanceUponCheckIn(), orderLineItem.getLineItemBalance());
        assertEquals(updateReservationResponse.getRoomReservationResponse().getTripDetails().getCheckInDate(),
                orderLineItem.getStartsAt().toString());
        assertEquals(updateReservationResponse.getRoomReservationResponse().getTripDetails().getCheckOutDate(),
                orderLineItem.getEndsAt().toString());
        assertNotNull(orderLineItem.getItemSelectionDetails());
        assertNotNull(orderLineItem.getItemSelectionDetails().getRoomSelectionDetails());
        assertNotNull(orderLineItem.getItemSelectionDetails().getRoomSelectionDetails().getAddOnComponents());
        assertEquals(updateReservationResponse.getRoomReservationResponse().getPurchasedComponents().size(),
                orderLineItem.getItemSelectionDetails().getRoomSelectionDetails().getAddOnComponents().size());
        assertEquals(updateReservationResponse.getRoomReservationResponse().getPurchasedComponents().get(0).getId(),
                orderLineItem.getItemSelectionDetails().getRoomSelectionDetails().getAddOnComponents().get(0).getId());}
}
