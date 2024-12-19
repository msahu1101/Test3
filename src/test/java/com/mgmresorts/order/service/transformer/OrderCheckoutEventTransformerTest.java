package com.mgmresorts.order.service.transformer;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.event.dto.Order;
import com.mgmresorts.order.event.dto.PackageCategoryInclusion;
import com.mgmresorts.order.event.dto.RoomSelectionDetails;
import com.mgmresorts.order.event.dto.ShowSelectionDetails;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.DeliveryMethod;
import com.mgmresorts.shopping.cart.dto.ItemType;
import org.junit.jupiter.api.Test;
import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OrderCheckoutEventTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final OrderCheckoutEventTransformer transformer = new OrderCheckoutEventTransformer();

    @Test
    final void testToRightOrderLineItem() throws AppException {
        final com.mgmresorts.order.entity.OrderEvent orderEventEntity = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.entity.OrderEvent.class);
        setUpDataForContent(orderEventEntity);
        final Order orderEvent = transformer.toRight(orderEventEntity);
        assertEquals(orderEvent.getType(), orderEventEntity.getOrder().getType());
        assertEquals(orderEvent.getVersion(), orderEventEntity.getOrder().getVersion());
        assertEquals(orderEvent.getJwbFlow(), orderEventEntity.getOrder().getJwbFlow());
        assertEquals(orderEvent.getTotalStrikethroughPrice(), orderEventEntity.getOrder().getPriceDetails().getTotalStrikethroughPrice());
        assertEquals(orderEvent.getOrderStatus().value().toUpperCase(), orderEventEntity.getOrder().getStatus().value().toUpperCase());
        assertEquals(orderEvent.getOrderLineItems().get(0).getPackageId(), orderEventEntity.getOrder().getOrderLineItems().get(0).getPackageId());
        assertEquals(orderEvent.getOrderLineItems().get(0).getNumberOfNights(), orderEventEntity.getOrder().getOrderLineItems().get(0).getNumberOfNights());
        assertEquals(orderEvent.getOrderLineItems().get(0).getUpgraded(), orderEventEntity.getOrder().getOrderLineItems().get(0).getUpgraded());
        assertEquals(orderEvent.getOrderLineItems().get(0).getLineItemStrikethroughPrice(), orderEventEntity.getOrder().getOrderLineItems().get(0).getLineItemStrikethroughPrice());
    }

    @Test
    final void testToRightCustomerProfile() throws AppException {
        final com.mgmresorts.order.entity.OrderEvent orderEventEntity = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.entity.OrderEvent.class);
        setUpDataForContent(orderEventEntity);
        final Order orderEvent = transformer.toRight(orderEventEntity);
        assertEquals(orderEvent.getId(), orderEventEntity.getOrder().getId());

        assertEquals(orderEvent.getCustomerProfile().getAddress().getCity(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getCity());
        assertEquals(orderEvent.getCustomerProfile().getAddress().getCountry(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getCountry());
        assertEquals(orderEvent.getCustomerProfile().getAddress().getPostalCode(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getPostalCode());
        assertEquals(orderEvent.getCustomerProfile().getAddress().getState(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getState());
        assertEquals(orderEvent.getCustomerProfile().getAddress().getStreet1(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getStreet1());
        assertEquals(orderEvent.getCustomerProfile().getAddress().getStreet2(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getStreet2());

        assertEquals(orderEvent.getCustomerProfile().getPhoneNumber().getNumber(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getPhoneNumbers().get(0).getNumber());
        assertEquals(orderEvent.getCustomerProfile().getEmail(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getEmailAddress1());
        assertEquals(orderEvent.getCustomerProfile().getFirstName(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getFirstName());
        assertEquals(orderEvent.getCustomerProfile().getLastName(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getLastName());
        assertEquals(orderEvent.getCustomerProfile().getCustomerId(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getId());
        assertEquals(orderEvent.getCustomerProfile().getmLifeId(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getMlifeNo());
        assertEquals(orderEvent.getCustomerProfile().getPerpetualOfferEligible(),
                orderEventEntity.getCheckoutRequest().getGuestProfile().getPerpetualOfferEligible());

        if(orderEventEntity.getCart().getPaymentRequired() != null && orderEventEntity.getCart().getPaymentRequired()) {
            assertEquals(orderEvent.getPaymentMethods().getFirstName(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getPayment().getFirstName());
            assertEquals(orderEvent.getPaymentMethods().getLastName(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getPayment().getLastName());
            assertEquals(orderEvent.getPaymentMethods().getCardHolder(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getPayment().getCardHolder());
            assertEquals(orderEvent.getPaymentMethods().getExpiry(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getPayment().getExpiry());
            assertEquals(orderEvent.getPaymentMethods().getCcToken(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getPayment().getCcToken());
            assertEquals(orderEvent.getPaymentMethods().getMaskNumber(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getPayment().getMaskedNumber());

            assertEquals(orderEvent.getPaymentMethods().getBillingAddress().getCity(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getAddress().getCity());
            assertEquals(orderEvent.getPaymentMethods().getBillingAddress().getCountry(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getAddress().getCountry());
            assertEquals(orderEvent.getPaymentMethods().getBillingAddress().getPostalCode(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getAddress().getPostalCode());
            assertEquals(orderEvent.getPaymentMethods().getBillingAddress().getState(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getAddress().getState());
            assertEquals(orderEvent.getPaymentMethods().getBillingAddress().getStreet1(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getAddress().getStreet1());
            assertEquals(orderEvent.getPaymentMethods().getBillingAddress().getStreet2(),
                    orderEventEntity.getCheckoutRequest().getBillings().get(0).getAddress().getStreet2());
        }

    }

    @Test
    final void testToRightRoomSelectionDetails() throws AppException {
        final com.mgmresorts.order.entity.OrderEvent orderEventEntity = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.entity.OrderEvent.class);
        setUpDataForContent(orderEventEntity);
        final Order orderEvent = transformer.toRight(orderEventEntity);
        assertEquals(orderEvent.getId(), orderEventEntity.getOrder().getId());
        orderEvent.getOrderLineItems().forEach(orderLineItem -> {
            if (orderLineItem.getProductType().toString().equals("ROOM")) {
                
                final CartLineItem cartLineItem =
                        orderEventEntity.getCart().getCartLineItems().stream().filter(item -> orderLineItem.getCartLineItemId().equals(item.getCartLineItemId())).findAny().orElse(null);
                assert cartLineItem != null;
                final com.mgmresorts.shopping.cart.dto.RoomSelectionDetails cartRoomSelectionDetails = cartLineItem.getItemSelectionDetails().getRoomSelectionDetails();
                final RoomSelectionDetails orderRoomSelectionDetails = orderLineItem.getItemSelectionDetails().getRoomSelectionDetails();
                
                assertEquals(orderLineItem.getPropertyName(), cartLineItem.getPropertyName());

                assertEquals(orderRoomSelectionDetails.getProgramId(), cartRoomSelectionDetails.getProgramId());
                assertEquals(orderRoomSelectionDetails.getProgramName(), cartRoomSelectionDetails.getProgramName());
                assertEquals(orderRoomSelectionDetails.getProgramType(), cartRoomSelectionDetails.getProgramType());
                assertEquals(orderRoomSelectionDetails.getF1Package(), cartRoomSelectionDetails.getF1Package());
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getAdjustedRoomSubtotal(), cartRoomSelectionDetails.getRatesSummary().getAdjustedRoomSubtotal(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getAveragePricePerNight(), cartRoomSelectionDetails.getRatesSummary().getAveragePricePerNight(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getBalanceUponCheckIn(), cartRoomSelectionDetails.getRatesSummary().getBalanceUponCheckIn(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getCasinoSurcharge(), cartRoomSelectionDetails.getRatesSummary().getCasinoSurcharge(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getCasinoSurchargeAndTax(), cartRoomSelectionDetails.getRatesSummary().getCasinoSurchargeAndTax(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getChangeInDeposit(), cartRoomSelectionDetails.getRatesSummary().getChangeInDeposit(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getDepositDue(), cartRoomSelectionDetails.getRatesSummary().getDepositDue(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getDiscountedAveragePrice(), cartRoomSelectionDetails.getRatesSummary().getDiscountedAveragePrice(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getDiscountedSubtotal(), cartRoomSelectionDetails.getRatesSummary().getDiscountedSubtotal(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getOccupancyFee(), cartRoomSelectionDetails.getRatesSummary().getOccupancyFee(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getProgramDiscount(), cartRoomSelectionDetails.getRatesSummary().getProgramDiscount(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getReservationTotal(), cartRoomSelectionDetails.getRatesSummary().getReservationTotal(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getResortFee(), cartRoomSelectionDetails.getRatesSummary().getResortFee(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getResortFeeAndTax(), cartRoomSelectionDetails.getRatesSummary().getResortFeeAndTax(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getRoomChargeTax(), cartRoomSelectionDetails.getRatesSummary().getRoomChargeTax(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getRoomSubtotal(), cartRoomSelectionDetails.getRatesSummary().getRoomSubtotal(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getTripSubtotal(), cartRoomSelectionDetails.getRatesSummary().getTripSubtotal(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getTourismFee(), cartRoomSelectionDetails.getRatesSummary().getTourismFee(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatesSummary().getTourismFeeAndTax(), cartRoomSelectionDetails.getRatesSummary().getTourismFeeAndTax(), 0.001);
                assertEquals(orderRoomSelectionDetails.getRatePlanTags(), cartRoomSelectionDetails.getRatePlanTags());

                assertEquals(orderLineItem.getAddOnsPrice(), cartLineItem.getAddOnsPrice());
                assertEquals(orderLineItem.getAddOnsTax(), cartLineItem.getAddOnsTax());
                assertEquals(orderLineItem.getUpsellAvailable(), cartLineItem.getUpsellAvailable());
                
               if (cartLineItem.getUpgraded()) {
                   assertEquals(cartLineItem.getUpsellRoomRateDifference(), orderLineItem.getUpsellPriceDifference());
                   assertEquals(cartLineItem.getUpsellGrossRevenueDifference(), orderLineItem.getUpsellGrossRevenueDifference());
               }
                
                if (cartLineItem.getUpsellAvailable() != null && cartLineItem.getUpsellAvailable() && cartLineItem.getUpsellLineItem() != null) {
                    assert orderLineItem.getUpsellLineItem() != null;
                    assertEquals(orderLineItem.getUpsellLineItem().getStatus(), "SUCCESS");
                    assertEquals(orderLineItem.getUpsellLineItem().getProductType().value(), cartLineItem.getUpsellLineItem().getType().value());
                    assertEquals(orderLineItem.getUpsellLineItem().getPropertyName(), cartLineItem.getUpsellLineItem().getPropertyName());
                    assertEquals(orderLineItem.getUpsellLineItem().getConfirmationNumber(), orderLineItem.getUpsellLineItem().getConfirmationNumber());
                    
                    final com.mgmresorts.shopping.cart.dto.RoomSelectionDetails upsellCartRoomSelectionDetails = cartLineItem.getUpsellLineItem().getItemSelectionDetails().getRoomSelectionDetails();
                    final RoomSelectionDetails upsellOrderRoomSelectionDetails = orderLineItem.getUpsellLineItem().getItemSelectionDetails().getRoomSelectionDetails();
                    
                    assertEquals(upsellOrderRoomSelectionDetails.getF1Package(), upsellCartRoomSelectionDetails.getF1Package());         
                }
                
                for (int i = 0; i < orderRoomSelectionDetails.getBookings().size(); i++) {
                    assertEquals(orderRoomSelectionDetails.getBookings().get(i).getPrice(), cartRoomSelectionDetails.getBookings().get(i).getPrice());
                    assertEquals(orderRoomSelectionDetails.getBookings().get(i).getBasePrice(), cartRoomSelectionDetails.getBookings().get(i).getBasePrice());
                    assertEquals(orderRoomSelectionDetails.getBookings().get(i).getDate(), cartRoomSelectionDetails.getBookings().get(i).getDate());
                    assertEquals(orderRoomSelectionDetails.getBookings().get(i).getDiscounted(), cartRoomSelectionDetails.getBookings().get(i).getIsDiscounted());
                    assertEquals(orderRoomSelectionDetails.getBookings().get(i).getComp(), cartRoomSelectionDetails.getBookings().get(i).getIsComp());
                    assertEquals(orderRoomSelectionDetails.getBookings().get(i).getProgramId(), cartRoomSelectionDetails.getBookings().get(i).getProgramId());
                    assertEquals(orderRoomSelectionDetails.getBookings().get(i).getResortFee(), cartRoomSelectionDetails.getBookings().get(i).getResortFee());
                }
                for (int i = 0; i < orderRoomSelectionDetails.getAddOnComponents().size(); i++) {
                    assertEquals(orderRoomSelectionDetails.getAddOnComponents().get(i).getPrice(), cartRoomSelectionDetails.getAddOnComponents().get(i).getPrice());
                    assertEquals(orderRoomSelectionDetails.getAddOnComponents().get(i).getActive(), cartRoomSelectionDetails.getAddOnComponents().get(i).getActive());
                    assertEquals(orderRoomSelectionDetails.getAddOnComponents().get(i).getNonEditable(), cartRoomSelectionDetails.getAddOnComponents().get(i).getNonEditable());
                    assertEquals(orderRoomSelectionDetails.getAddOnComponents().get(i).getId(), cartRoomSelectionDetails.getAddOnComponents().get(i).getId());
                    assertEquals(orderRoomSelectionDetails.getAddOnComponents().get(i).getCode(), cartRoomSelectionDetails.getAddOnComponents().get(i).getCode());
                    assertEquals(orderRoomSelectionDetails.getAddOnComponents().get(i).getTripPrice(), cartRoomSelectionDetails.getAddOnComponents().get(i).getTripPrice());
                    assertEquals(orderRoomSelectionDetails.getAddOnComponents().get(i).getTripTax(), cartRoomSelectionDetails.getAddOnComponents().get(i).getTripTax());
                    assertEquals(orderRoomSelectionDetails.getAddOnComponents().get(i).getDepositAmount(), cartRoomSelectionDetails.getAddOnComponents().get(i).getDepositAmount());
                }
            }
        });
    }

    @Test
    final void testToRightShowSelectionDetails() throws AppException {
        final com.mgmresorts.order.entity.OrderEvent orderEventEntity = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.entity.OrderEvent.class);
        setUpDataForContent(orderEventEntity);
        final Order orderEvent = transformer.toRight(orderEventEntity);
        assertEquals(orderEvent.getId(), orderEventEntity.getOrder().getId());
        orderEvent.getOrderLineItems().forEach(orderLineItem -> {
            if (orderLineItem.getProductType().toString().equals("SHOW")) {
                
                final CartLineItem cartLineItem =
                        orderEventEntity.getCart().getCartLineItems().stream().filter(item -> orderLineItem.getCartLineItemId().equals(item.getCartLineItemId())).findAny().orElse(null);
                assert cartLineItem != null;
                final com.mgmresorts.shopping.cart.dto.ShowSelectionDetails cartShowSelectionDetails = cartLineItem.getItemSelectionDetails().getShowSelectionDetails();
                final ShowSelectionDetails eventShowSelectionDetails = orderLineItem.getItemSelectionDetails().getShowSelectionDetails();
                
                assertEquals(eventShowSelectionDetails.getEventDate(), cartShowSelectionDetails.getEventDate());
                assertEquals(eventShowSelectionDetails.getOfferType().toString(), cartShowSelectionDetails.getOfferType().toString());
                assertEquals(eventShowSelectionDetails.getEventTime(), cartShowSelectionDetails.getEventTime());
                assertEquals(eventShowSelectionDetails.getSeasonId(), cartShowSelectionDetails.getSeasonId());
                assertEquals(eventShowSelectionDetails.getProgramId(), cartShowSelectionDetails.getProgramId());
                assertEquals(eventShowSelectionDetails.getProgramName(), cartShowSelectionDetails.getProgramName());
                assertEquals(eventShowSelectionDetails.getMyVegasCode(), cartShowSelectionDetails.getMyVegasCode());
                assertEquals(eventShowSelectionDetails.getMyVegasComp(), cartShowSelectionDetails.getMyVegasComp());
                assertEquals(eventShowSelectionDetails.getHdePackage(), cartShowSelectionDetails.getHdePackage());
                assertEquals(eventShowSelectionDetails.getNumberOfTickets(), cartShowSelectionDetails.getNumberOfTickets());
                assertEquals(eventShowSelectionDetails.getRatesSummary().getDeliveryFee(), cartShowSelectionDetails.getRatesSummary().getDeliveryFee(), 0.001);
                assertEquals(eventShowSelectionDetails.getRatesSummary().getGratuity(), cartShowSelectionDetails.getRatesSummary().getGratuity(), 0.001);
                assertEquals(eventShowSelectionDetails.getRatesSummary().getLet(), cartShowSelectionDetails.getRatesSummary().getLet(), 0.001);
                assertEquals(eventShowSelectionDetails.getRatesSummary().getServiceChargeFee(), cartShowSelectionDetails.getRatesSummary().getServiceChargeFee(), 0.001);
                assertEquals(eventShowSelectionDetails.getRatesSummary().getServiceChargeTax(), cartShowSelectionDetails.getRatesSummary().getServiceChargeTax(), 0.001);
                assertEquals(eventShowSelectionDetails.getRatesSummary().getTransactionFee(), cartShowSelectionDetails.getRatesSummary().getTransactionFee(), 0.001);
                assertEquals(eventShowSelectionDetails.getRatesSummary().getTransactionTax(), cartShowSelectionDetails.getRatesSummary().getTransactionTax(), 0.001);
                assertEquals(eventShowSelectionDetails.getRatesSummary().getShowSubtotal(), cartShowSelectionDetails.getRatesSummary().getShowSubtotal(), 0.001);
                assertEquals(eventShowSelectionDetails.getRatesSummary().getDiscountedSubtotal(), cartShowSelectionDetails.getRatesSummary().getDiscountedSubtotal(), 0.001);
                assertEquals(eventShowSelectionDetails.getRatesSummary().getReservationTotal(), cartShowSelectionDetails.getRatesSummary().getReservationTotal(), 0.001);
                
                final com.mgmresorts.order.event.dto.DeliveryMethod eventDeliveryMethod = eventShowSelectionDetails.getSelectedDeliveryMethod();
                DeliveryMethod cartDeliveryMethod = cartShowSelectionDetails.getPermissibleDeliveryMethods().stream().filter(delMethod -> eventDeliveryMethod.getId().equals(delMethod.getId())).findAny().orElse(null);
                assert cartDeliveryMethod != null;
                assertEquals(eventDeliveryMethod.getSelected(), cartDeliveryMethod.getSelected());
                assertEquals(eventDeliveryMethod.getCode(), cartDeliveryMethod.getCode());
                assertEquals(eventDeliveryMethod.getName(), cartDeliveryMethod.getName());
                assertEquals(eventDeliveryMethod.getDescription(), cartDeliveryMethod.getDescription());
                assertEquals(eventDeliveryMethod.getActive(), cartDeliveryMethod.getActive());
                assertEquals(eventDeliveryMethod.getDefaultDeliveryMethod(), cartDeliveryMethod.getDefaultDeliveryMethod());
                assertEquals(eventDeliveryMethod.getAmount(), cartDeliveryMethod.getAmount(), 0.001);
                
                for (int i = 0; i < eventShowSelectionDetails.getShowTickets().size(); i++) {
                    assertEquals(eventShowSelectionDetails.getShowTickets().get(i).getPriceCode(), cartShowSelectionDetails.getShowTickets().get(i).getPriceCode());
                    assertEquals(eventShowSelectionDetails.getShowTickets().get(i).getPriceCodeDescription(), cartShowSelectionDetails.getShowTickets().get(i).getPriceCodeDescription());
                    assertEquals(eventShowSelectionDetails.getShowTickets().get(i).getTicketTypeCode(), cartShowSelectionDetails.getShowTickets().get(i).getTicketTypeCode());
                    assertEquals(eventShowSelectionDetails.getShowTickets().get(i).getHoldClass(), cartShowSelectionDetails.getShowTickets().get(i).getHoldClass());
                    assertEquals(eventShowSelectionDetails.getShowTickets().get(i).getBasePrice(), cartShowSelectionDetails.getShowTickets().get(i).getBasePrice(), 0.001);
                    assertEquals(eventShowSelectionDetails.getShowTickets().get(i).getDiscountedPrice(), cartShowSelectionDetails.getShowTickets().get(i).getDiscountedPrice(), 0.001);
                    assertEquals(eventShowSelectionDetails.getShowTickets().get(i).getSeat().getSectionName(), cartShowSelectionDetails.getShowTickets().get(i).getSeat().getSectionName());
                    assertEquals(eventShowSelectionDetails.getShowTickets().get(i).getSeat().getRowName(), cartShowSelectionDetails.getShowTickets().get(i).getSeat().getRowName());
                    assertEquals(eventShowSelectionDetails.getShowTickets().get(i).getSeat().getSeatNumber(), cartShowSelectionDetails.getShowTickets().get(i).getSeat().getSeatNumber());
                }
            }
        });
    }

    @Test
    final void testToRightDiningSelctionDetails() throws AppException {
        final com.mgmresorts.order.entity.OrderEvent orderEventEntity = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.entity.OrderEvent.class);
        orderEventEntity.getCart().getCartLineItems().get(0).getItemSelectionDetails().setRoomSelectionDetails(null);
        orderEventEntity.getCart().getCartLineItems().get(0).getItemSelectionDetails().setShowSelectionDetails(null);
        setUpDataForContent(orderEventEntity);
        final Order orderEvent = transformer.toRight(orderEventEntity);
        assertEquals(orderEvent.getId(), orderEventEntity.getOrder().getId());
        com.mgmresorts.order.event.dto.OrderLineItem rightLineItem = orderEvent.getOrderLineItems().get(0);
        CartLineItem lineItem = orderEventEntity.getCart().getCartLineItems().get(0);

        assertEquals(lineItem.getItemSelectionDetails().getDiningSelectionDetails().getReservationDate(),
                rightLineItem.getItemSelectionDetails().getDiningSelectionDetails().getReservationDate() );
        assertEquals(lineItem.getItemSelectionDetails().getDiningSelectionDetails().getReservationTime(),
                rightLineItem.getItemSelectionDetails().getDiningSelectionDetails().getReservationTime());
        assertEquals(lineItem.getItemSelectionDetails().getDiningSelectionDetails().getPartySize(),
                rightLineItem.getItemSelectionDetails().getDiningSelectionDetails().getPartySize());
    }

    @Test
final void testToRightPackageConfigDetails() throws AppException {
        final com.mgmresorts.order.entity.OrderEvent orderEventEntity = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.entity.OrderEvent.class);
        setUpDataForContent(orderEventEntity);
        final Order orderEvent = transformer.toRight(orderEventEntity);
        assertEquals(orderEvent.getId(), orderEventEntity.getOrder().getId());

        assertEquals(orderEvent.getPackageConfigDetails().getPackageCategoryId(), orderEventEntity.getOrder().getPackageConfigDetails().getPackageCategoryId());
        assertEquals(orderEvent.getPackageConfigDetails().getPackagePriceBreakdown(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePriceBreakdown());
        assertEquals(orderEvent.getPackageConfigDetails().getPackagePricingDetails().getPackageBaseTotal(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getPackageBaseTotal());
        assertEquals(orderEvent.getPackageConfigDetails().getPackagePricingDetails().getPackageStartingPrice(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getPackageStartingPrice());
        assertEquals(orderEvent.getPackageConfigDetails().getPackagePricingDetails().getPackageTotal(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getPackageTotal());
        assertEquals(orderEvent.getPackageConfigDetails().getPackagePricingDetails().getRoomModification(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getRoomModification());
        assertEquals(orderEvent.getPackageConfigDetails().getPackagePricingDetails().getShowModification(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getShowModification());
        assertEquals(orderEvent.getPackageConfigDetails().getPackagePricingDetails().getRoomTotal(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getRoomTotal());
        assertEquals(orderEvent.getPackageConfigDetails().getPackagePricingDetails().getShowTotal(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getShowTotal());
        assertEquals(orderEvent.getPackageConfigDetails().getPackagePricingDetails().getIsMultiDayEvent(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getIsMultiDayEvent());
        if (orderEvent.getPackageConfigDetails().getPackagePricingDetails().getIsMultiDayEvent()) {
            assertEquals(orderEvent.getPackageConfigDetails().getPackagePricingDetails().getEventStartDate(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getEventStartDate());
            assertEquals(orderEvent.getPackageConfigDetails().getPackagePricingDetails().getEventEndDate(), orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getEventEndDate());
        }
        final List<PackageCategoryInclusion> packageInclusions = orderEvent.getPackageConfigDetails().getPackagePricingDetails().getPackageInclusions();
        final List<com.mgmresorts.order.dto.PackageCategoryInclusion> packageInclusionsEntity= orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getPackageInclusions();
        assertEquals(packageInclusionsEntity.size(), packageInclusions.size());
        assertEquals(packageInclusionsEntity.get(0).getDescription(), packageInclusions.get(0).getDescription());
        assertEquals(packageInclusionsEntity.get(0).getDetailText(), packageInclusions.get(0).getDetailText());
        assertEquals(packageInclusionsEntity.get(0).getName(), packageInclusions.get(0).getName());
        assertEquals(packageInclusionsEntity.get(0).getEnabled(), packageInclusions.get(0).getEnabled());
        assertEquals(packageInclusionsEntity.get(0).getBookingDestinationUrl(), packageInclusions.get(0).getBookingDestinationUrl());
        assertEquals(packageInclusionsEntity.get(0).getCarouselGridDisplayText(), packageInclusions.get(0).getCarouselGridDisplayText());
        assertEquals(packageInclusionsEntity.get(0).getDisplayInCarouselGrid(), packageInclusions.get(0).getDisplayInCarouselGrid());
        assertEquals(packageInclusionsEntity.get(0).getInclusionMultiplierType().value(), packageInclusions.get(0).getInclusionMultiplierType().value());
    }

    void setUpDataForContent(com.mgmresorts.order.entity.OrderEvent orderEventEntity) {        
        for (int i = 0; i < orderEventEntity.getCart().getCartLineItems().size(); i++) {
            final CartLineItem cartLineItem = orderEventEntity.getCart().getCartLineItems().get(i);
            final OrderLineItem orderLineItem = orderEventEntity.getOrder().getOrderLineItems().get(i);
            final String lineItemId = UUID.randomUUID().toString();

            cartLineItem.setCartLineItemId(lineItemId);
            orderLineItem.setCartLineItemId(lineItemId);
            if (i == 0) {
                cartLineItem.setType(ItemType.SHOW);
                cartLineItem.setContent("{\"eventDate\":\"2022-04-17\",\"eventTime\":\"8:00 PM\",\"eventTz\":\"2022-04-18T03:00Z\","
                        + "\"seasonId\":\"6e12bad4-330f-433e-92e4-eb1fa1976674\",\"showEventId\":\"0a67bde5-e7a6-46d0-bee3-0b5385f7724a\","
                        + "\"programId\":\"4b9c1682-c452-4b76-8559-fe693f72450b\",\"propertyId\":\"66964e2b-2550-4476-84c3-1a4c0c5c067f\",\"comp\":false,\"hdePackage\":false,"
                        + "\"discounted\":true,\"permissibleDeliveryMethods\":[{\"default\":true,\"code\":\"TF\",\"amount\":0.0,\"description\":\"Tickets by Email and Mobile\","
                        + "\"name\":\"Ticket Fast\",\"active\":true,\"id\":\"5ccce20e-4cab-4f9c-aa0d-4ef30627e40e\"},{\"default\":false,\"code\":\"WC\",\"amount\":0.0,"
                        + "\"description\":\"Will Call\",\"name\":\"Will Call\",\"active\":true,\"id\":\"5a0a68b8-13ca-43bc-9644-80becfbfc699\"}],"
                        + "\"tickets\":[{\"priceCode\":\"AZ\",\"priceCodeDescription\":\"ZONE 1 - Passkey Adult\",\"holdClass\":\"OPEN\",\"basePrice\":71.37,"
                        + "\"discountedPrice\":59.97,\"holdId\":\"100484194\",\"holdDuration\":1648519126225,\"state\":\"HELD\","
                        + "\"showEventId\":\"0a67bde5-e7a6-46d0-bee3-0b5385f7724a\",\"ticketTypeCode\":\"_QO\",\"seat\":{\"seatNumber\":38,\"sectionName\":\"GA1\","
                        + "\"rowName\":\"GA0\"}}],\"charges\":{\"discountedSubtotal\":59.9700,\"showSubtotal\":71.3700,\"let\":5.4000,\"deliveryFee\":0.0000,\"gratuity\":5.0000,"
                        + "\"reservationTotal\":78.5500,\"serviceCharge\":{\"amount\":8.1800,\"itemized\":{\"charge\":7.5000,\"tax\":0.6800}},\"transactionFee\":{\"amount\":0"
                        + ".0000,\"itemized\":{\"charge\":0.0000,\"tax\":0.0000}}}}");
                
                orderLineItem.setProductType(OrderLineItem.ProductType.SHOW);
                orderLineItem.setContent("{  \"id\": \"100484194\",  \"bookDate\": \"2022-03-28\",  \"confirmationNumber\": \"100484194\",  \"programId\": "
                        + "\"4b9c1682-c452-4b76-8559-fe693f72450b\",  \"propertyId\": \"66964e2b-2550-4476-84c3-1a4c0c5c067f\",  \"eventDate\": \"2022-04-17\",  \"eventTime\": "
                        + "\"8:00 PM\",  \"reservationStatus\": \"Reserved\",  \"cancellable\": false,  \"seasonId\": \"6e12bad4-330f-433e-92e4-eb1fa1976674\",  \"showEventId\": "
                        + "\"0a67bde5-e7a6-46d0-bee3-0b5385f7724a\",  \"showEventCode\": \"BGC0417E\",  \"deliveryMethodCode\": \"TF\",  \"comp\": false,  \"hdePackage\": false, "
                        + " \"resendEmailAllowed\": true,  \"tickets\": [    {      \"priceCode\": \"AZ\",      \"holdClass\": \"OPEN\",      \"basePrice\": 71.37,      "
                        + "\"discountedPrice\": 59.97,      \"description\": \"Passkey Adult\",      \"showEventId\": \"0a67bde5-e7a6-46d0-bee3-0b5385f7724a\",      "
                        + "\"showEventCode\": \"BGC0417E\",      \"ticketTypeCode\": \"_QO\",      \"seat\": {        \"sectionName\": \"GA1\",        \"rowName\": \"GA0\",      "
                        + "  \"seatNumber\": 38      }    }  ],  \"charges\": {    \"discountedSubtotal\": 59.97,    \"showSubtotal\": 71.37,    \"let\": 5.4,    \"deliveryFee\":"
                        + " 0.0,    \"gratuity\": 5.0,    \"reservationTotal\": 78.55,    \"serviceCharge\": {      \"amount\": 8.18,      \"itemized\": {        \"charge\": 7.5,"
                        + "        \"tax\": 0.68      }    },    \"transactionFee\": {      \"amount\": 0.0,      \"itemized\": {        \"charge\": 0.0,        \"tax\": 0.0     "
                        + " }    }  },  \"profile\": {    \"archticsId\": \"249587226\",    \"firstName\": \"test\",    \"lastName\": \"user\",    \"emailAddress1\": "
                        + "\"fake1@email.com\",    \"mlifeNo\": \"79664267\",    \"addresses\": [      {        \"type\": \"Home\",        \"preferred\": true,        "
                        + "\"country\": \"USA\"      }    ]  }}");
            } else {
                cartLineItem.setType(ItemType.ROOM);
                cartLineItem.setContent("{\"propertyId\":\"bee81f88-286d-43dd-91b5-3917d9d62a68\",\"roomTypeId\":\"ROOMCD-v-DPRK-d-PROP-v-MV290\",\"programId\":" +
                        "\"RPCD-v-PREVL-d-PROP-v-MV290\",\"customerId\":\"0\",\"perpetualPricing\":false,\"tripDetails\":{\"checkInDate\":\"2021-09-02\"," +
                        "\"checkOutDate\":\"2021-09-03\",\"numAdults\":1,\"numChildren\":0,\"numRooms\":1},\"bookings\":[{\"date\":\"2021-09-02\",\"basePrice" +
                        "\":40.62,\"customerPrice\":0.00,\"price\":40.62,\"programIdIsRateTable\":false,\"overridePrice\":0.00,\"overrideProgramIdIsRateTable" +
                        "\":false,\"isComp\":false,\"resortFeeIsSpecified\":false,\"resortFee\":0.00,\"programId\":\"RPCD-v-PREVL-d-PROP-v-MV290\"}],\"chargesAndTaxes" +
                        "\":{\"charges\":[{\"date\":\"2021-09-02\",\"amount\":77.62,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":40.62},{\"itemType\":\"ResortFee" +
                        "\",\"amount\":37.00}]}],\"taxesAndFees\":[{\"date\":\"2021-09-02\",\"amount\":10.38,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\"" +
                        ":5.43},{\"itemType\":\"ResortFeeTax\",\"amount\":4.95}]}]},\"availableComponents\":[{\"id\":\"COMPONENTCD-v-1SPARK-d-TYP-v-Component-d-PROP-v-MV290-d-NRPCD-v-PREVL\",\"code" +
                        "\":\"1SPARK\",\"shortDescription\":\"Self Parking\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-09-02\",\"amount\":10.00,\"tax" +
                        "\":1.34}]},{\"id\":\"COMPONENTCD-v-2SPARK-d-TYP-v-Component-d-PROP-v-MV290-d-NRPCD-v-PREVL\",\"code\":\"2SPARK\",\"shortDescription\":\"2 Self Parking\",\"active" +
                        "\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-09-02\",\"amount\":20.00,\"tax\":2.68}]},{\"id\":\"COMPONENTCD-v-3SPARK-d-TYP-v-Component-d-PROP-v-MV290-d-NRPCD-v-" +
                        "\",\"code\":\"3SPARK\",\"shortDescription\":\"3 Self Parking\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-09-02\",\"amount\":30.00,\"tax\":4.01}]},{\"id\":" +
                        "\"COMPONENTCD-v-4SPARK-d-TYP-v-Component-d-PROP-v-MV290-d-NRPCD-v-PREVL\",\"code\":\"4SPARK\",\"shortDescription\":\"4 Self Parking\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":" +
                        "\"NIGHTLY\",\"prices\":[{\"date\":\"2021-09-02\",\"amount\":40.00,\"tax\":5.35}]},{\"id\":\"COMPONENTCD-v-BUFDAY-d-TYP-v-Component-d-PROP-v-MV290-d-NRPCD-v-PREVL\",\"code" +
                        "\":\"BUFDAY\",\"shortDescription\":\"Buffet Breakfast & Lunch \",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-09-02\",\"amount\":50.00,\"tax" +
                        "\":4.19}]},{\"id\":\"COMPONENTCD-v-BUFDDAY-d-TYP-v-Component-d-PROP-v-MV290-d-NRPCD-v-PREVL\",\"code\":\"BUFDDAY\",\"shortDescription\":\"Buffet Breakfast, Lunch & Dinner " +
                        "\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-09-02\",\"amount\":50.00,\"tax\":4.19}]},{\"id\":\"COMPONENTCD-v-SPA-d-TYP-v-Component-d-PROP-v-MV290-d-NRPCD-v-PREVL\"" +
                        ",\"code\":\"SPA\",\"shortDescription\":\"Spa Service \",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-09-02\",\"amount\":75.00,\"tax\":6.28}]}],\"ratesSummary\":{\"roomSubtotal" +
                        "\":40.6200,\"programDiscount\":0.0000,\"roomRequestsTotal\":0.0000,\"adjustedRoomSubtotal\":40.6200,\"resortFee\":37.0000,\"resortFeeAndTax\":41.9500,\"roomChargeTax\":5.4300,\"occupancyFee\":0.0000,\"tourismFee" +
                        "\":0.0000,\"tourismFeeAndTax\":0.0000,\"reservationTotal\":88.0000,\"depositDue\":88.0000,\"balanceUponCheckIn\":0.0000},\"depositDetails\":{\"dueDate\":\"2021-08-01\",\"amount\":88.00,\"forfeitDate\":\"2021-08-29\",\"forfeitAmount\":88.00,\"overrideAmount" +
                        "\":-1.00},\"depositPolicy\":{\"depositRequired\":true,\"creditCardRequired\":false}}");

                orderLineItem.setProductType(OrderLineItem.ProductType.ROOM);
                orderLineItem.setContent("{\"roomReservation\":{\"id\":\"af4b05ed-bf72-4a5a-ba35-62e1911e521f\",\"customerId\":848368566530,\"propertyId\":" +
                        "\"e0f70eb3-7e27-4c33-8bcd-f30bf3b1103a\",\"itineraryId\":\"9330238978\",\"roomTypeId\":\"ROOMCD-v-DRST-d-PROP-v-MV275\",\"programId\":" +
                        "\"RPCD-v-PREVL-d-PROP-v-MV275\",\"state\":\"Booked\",\"nrgStatus\":false,\"specialRequests\":[],\"thirdParty\":false,\"confirmationNumber\":\"" +
                        "1571452775\",\"bookDate\":\"2021-08-03\",\"profile\":{\"id\":848368566530,\"mlifeNo\":0,\"firstName\":\"Riddhi\",\"lastName\":\"Deshmukh\",\"" +
                        "addresses\":[{\"type\":\"Home\",\"preferred\":true,\"street1\":\"123 Main St\",\"street2\":null,\"city\":\"Las Vegas\",\"state\":\"NV\",\"country\"" +
                        ":\"US\",\"postalCode\":\"89118\"}]},\"billing\":[{\"payment\":{\"cardHolder\":\"first last\",\"firstName\":\"first\",\"lastName\":\"last\",\"ccToken\"" +
                        ":\"4111110A001DQXFHY79D9XCZ1111\",\"encryptedccToken\":\"4111110A001DQXFHY79D9XCZ1111\",\"maskedNumber\":\"XXXXXXXXXXXXXXXXXXXXXXXX1111\",\"amount\"" +
                        ":188.06,\"expiry\":\"10/2022\",\"type\":\"Visa\"},\"address\":{\"street1\":\"234 W Main St\",\"street2\":null,\"city\":\"Las Vegas\",\"state\":\"NV\",\"" +
                        "postalCode\":\"89129\",\"country\":\"US\"}}],\"tripDetails\":{\"checkInDate\":\"2021-09-02\",\"checkOutDate\":\"2021-09-03\",\"numAdults\":1,\"numChildren\"" +
                        ":0,\"numRooms\":1},\"markets\":[{\"date\":\"2021-09-02\",\"marketCode\":\"TFIT\",\"sourceCode\":\"WB\"}],\"bookings\":[{\"date\":\"2021-09-02\",\"basePrice\"" +
                        ":0.00,\"customerPrice\":0.00,\"price\":49.00,\"programIdIsRateTable\":false,\"overridePrice\":-1.00,\"overrideProgramIdIsRateTable\":false,\"" +
                        "resortFeeIsSpecified\":false,\"resortFee\":39.00,\"programId\":\"RPCD-v-PREVL-d-PROP-v-MV275\",\"isComp\":false}],\"chargesAndTaxes\":{\"charges\"" +
                        ":[{\"date\":\"2021-09-02\",\"amount\":88.00,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":49.00},{\"itemType\":\"ResortFee\",\"amount\"" +
                        ":39.00}]}],\"taxesAndFees\":[{\"date\":\"2021-09-02\",\"amount\":11.78,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":6.56},{\"itemType\"" +
                        ":\"ResortFeeTax\",\"amount\":5.22}]}]},\"ratesSummary\":{\"roomSubtotal\":0.00,\"programDiscount\":-49.00,\"discountedSubtotal\":49.00,\"" +
                        "roomRequestsTotal\":0.00,\"adjustedRoomSubtotal\":49.00,\"resortFee\":39.00,\"resortFeePerNight\":39.00,\"resortFeeAndTax\":44.22,\"roomChargeTax\"" +
                        ":6.56,\"occupancyFee\":0.00,\"tourismFee\":0.00,\"tourismFeeAndTax\":0.00,\"reservationTotal\":99.78,\"depositDue\":99.78,\"balanceUponCheckIn\"" +
                        ":0.00},\"depositDetails\":{\"dueDate\":\"2021-08-01\",\"amount\":99.78,\"forfeitDate\":\"2021-09-01\",\"forfeitAmount\":99.78,\"overrideAmount\"" +
                        ":188.06},\"payments\":[{\"reservationInstance\":0,\"chargeAmount\":99.78,\"chargeCardExpiry\":\"2022-10-01\",\"status\":\"Settled\",\"chargeCardHolder\"" +
                        ":\"first last\",\"chargeCardType\":\"Visa\",\"chargeCardMaskedNumber\":\"XXXXXXXXXXXXXXXXXXXXXXXX1111\",\"chargeCardNumber\":\"4111110A001DQXFHY79D9XCZ1111\"" +
                        ",\"fxChecked\":false,\"fxEligible\":false,\"fxTransDate\":\"2021-08-03\",\"fxAmount\":0.00,\"fxRate\":0.00,\"fxSettleAmount\":0.00,\"isExternal\"" +
                        ":false,\"isDeposit\":true},{\"reservationInstance\":0,\"chargeAmount\":88.28,\"chargeCardExpiry\":\"2022-10-01\",\"status\":\"Settled\"" +
                        ",\"chargeCardHolder\":\"first last\",\"chargeCardType\":\"Visa\",\"chargeCardMaskedNumber\":\"XXXXXXXXXXXXXXXXXXXXXXXX1111\",\"" +
                        "chargeCardNumber\":\"4111110A001DQXFHY79D9XCZ1111\",\"fxChecked\":false,\"fxEligible\":false,\"fxTransDate\":\"2021-08-03\",\"" +
                        "fxAmount\":0.00,\"fxRate\":0.00,\"fxSettleAmount\":0.00,\"isExternal\":false,\"isDeposit\":true}],\"amountDue\":188.06,\"" +
                        "customerRank\":0,\"customerSegment\":0,\"routingInstructions\":[],\"perpetualPricing\":false,\"isGroupCode\":false}}");
            }
        }
    }
}
