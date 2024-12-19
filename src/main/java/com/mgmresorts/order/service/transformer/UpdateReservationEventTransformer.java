package com.mgmresorts.order.service.transformer;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.mgmresorts.common.event.enterprise.publish.IEnterpriseEventTransformer;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.dto.services.UpdateReservationResponse;
import com.mgmresorts.order.event.dto.ItemSelectionDetails;
import com.mgmresorts.order.event.dto.Order;
import com.mgmresorts.order.event.dto.OrderEvent;
import com.mgmresorts.order.event.dto.OrderLineItem;
import com.mgmresorts.order.event.dto.RoomSelectionDetails;
import com.mgmresorts.rbs.model.PurchasedComponents;
import com.mgmresorts.rbs.model.PurchasedComponentsPrices;
import com.mgmresorts.rbs.model.RoomReservationResponse;

public class UpdateReservationEventTransformer implements IEnterpriseEventTransformer<UpdateReservationResponse, OrderEvent> {
    @Override
    public float version() {
        return 1.0f;
    }

    @Override
    public String referenceNumber(Object data) {
        return data instanceof OrderEvent ? ((OrderEvent) data).getOrderEventId() : null;
    }

    @Override
    public OrderEvent transform(UpdateReservationResponse in) throws AppException {
        final OrderEvent entity = new OrderEvent();
        if (StringUtils.isNotBlank(in.getOrderId())) {
            entity.setEventOccurrenceTime(ZonedDateTime.now());
            entity.setEventTriggerTime(ZonedDateTime.now());
            entity.setOrderEventId(in.getOrderId());
            entity.setEventName("Updated Item");
            entity.setTriggeringOrderLineItemIds(List.of(in.getRoomReservationResponse().getConfirmationNumber()));
            entity.setOrder(toRight(in));
            entity.setOrderUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
        }
        return entity;
    }

    public Order toRight(UpdateReservationResponse in) throws AppException {
        final Order order = new Order();
        order.setId(in.getOrderId());
        if (in.getRoomReservationResponse() != null) {
            order.setOrderLineItems(toRoomItem(in.getRoomReservationResponse()));
        }
        return order;
    }

    private List<com.mgmresorts.order.event.dto.OrderLineItem> toRoomItem(RoomReservationResponse in) {

        final List<com.mgmresorts.order.event.dto.OrderLineItem> eventOrderLineItems = new ArrayList<>();
        final com.mgmresorts.order.event.dto.OrderLineItem eventOli = new com.mgmresorts.order.event.dto.OrderLineItem();

        eventOli.setProductType(OrderLineItem.ProductType.ROOM);
        eventOli.setLineItemPrice(in.getRatesSummary().getReservationTotal());
        eventOli.setLineItemDeposit(in.getRatesSummary().getDepositDue());
        eventOli.setLineItemDiscount(in.getRatesSummary().getProgramDiscount());
        eventOli.setLineItemBalance(in.getRatesSummary().getBalanceUponCheckIn());
        eventOli.setStartsAt(LocalDate.parse(in.getTripDetails().getCheckInDate()));
        eventOli.setEndsAt(LocalDate.parse(in.getTripDetails().getCheckOutDate()));
        eventOli.setItemSelectionDetails(new ItemSelectionDetails());
        eventOli.getItemSelectionDetails().setRoomSelectionDetails(new RoomSelectionDetails());
        eventOli.getItemSelectionDetails().getRoomSelectionDetails()
                .setAddOnComponents(toAddOnComponents(in.getPurchasedComponents()));


        eventOrderLineItems.add(eventOli);
        return eventOrderLineItems;
    }

    private List<com.mgmresorts.order.event.dto.AddOnComponent> toAddOnComponents(List<PurchasedComponents> purchasedComponentsList) {
        final List<com.mgmresorts.order.event.dto.AddOnComponent> calculateAddonComponents =
                !Utils.isEmpty(purchasedComponentsList) ? purchasedComponentsList.stream().map(purchasedComponent -> {
            final com.mgmresorts.order.event.dto.AddOnComponent calculateAddOn = new com.mgmresorts.order.event.dto.AddOnComponent();

                calculateAddOn.setActive(purchasedComponent.isActive());
                calculateAddOn.setCode(purchasedComponent.getCode());
                calculateAddOn.setId(purchasedComponent.getId());
                calculateAddOn.setPricingApplied(com.mgmresorts.order.event.dto.AddOnComponent.PricingApplied.fromValue(
                        purchasedComponent.getPricingApplied().getValue()
                ));
                calculateAddOn.setNonEditable(purchasedComponent.isNonEditable());
                calculateAddOn.setShortDescription(purchasedComponent.getShortDescription());
                calculateAddOn.setTripPrice(purchasedComponent.getTripPrice().doubleValue());
                calculateAddOn.setTripTax(purchasedComponent.getTripTax().doubleValue());
                calculateAddOn.setPrice(purchasedComponent.getPrice().doubleValue());
                calculateAddOn.setPrices(toAddOnComponentPrices(purchasedComponent.getPrices()));

            return calculateAddOn;
        }).collect(Collectors.toList()) : Collections.emptyList();

        return calculateAddonComponents;
    }

    private List<com.mgmresorts.order.event.dto.AddOnComponentPrice> toAddOnComponentPrices(List<PurchasedComponentsPrices> list) {
        final List<com.mgmresorts.order.event.dto.AddOnComponentPrice> addOnPrices = !Utils.isEmpty(list) ? list.stream().map(componentPrice -> {
            final com.mgmresorts.order.event.dto.AddOnComponentPrice addOnPrice = new com.mgmresorts.order.event.dto.AddOnComponentPrice();
            if (componentPrice != null) {
                if (componentPrice.getAmount() != null) {
                    addOnPrice.setAmount(componentPrice.getAmount().doubleValue());
                }
                if (componentPrice.getTax() != null) {
                    addOnPrice.setTax(componentPrice.getTax().doubleValue());
                }
                if (componentPrice.getDate() != null) {
                    addOnPrice.setDate(componentPrice.getDate());
                }
            }

            return addOnPrice;
        }).collect(Collectors.toList()) : Collections.emptyList();

        return addOnPrices;
    }
}
