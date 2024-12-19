package com.mgmresorts.order.service.transformer;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.dto.services.Version;
import com.mgmresorts.rtc.Metadata;
import com.mgmresorts.rtc.ReservationEventBody;
import com.mgmresorts.rtc.RoomDetailsElement;
import com.mgmresorts.rtc.ShowReservation;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.ItemType;
import mockit.Expectations;
import org.junit.jupiter.api.Test;
import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OrderCheckoutEmailEventTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final OrderCheckoutEmailEventTransformer transformer = new OrderCheckoutEmailEventTransformer();

    @Test
    final void testToRightOrderEvent() throws AppException {
        final com.mgmresorts.order.entity.OrderEvent orderEventEntity = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.entity.OrderEvent.class);
        setUpDataForContent(orderEventEntity);
        orderEventEntity.getOrder().setType(Type.PACKAGE);
        orderEventEntity.getOrder().setVersion(Version.V2);
        orderEventEntity.getOrder().getPackageConfigDetails().setSeatMapOptions("SEATMAP");
        final ReservationEventBody reservationEventBody = transformer.toReservationEventBody(orderEventEntity);

        new Expectations() {
            {
                URI.create(anyString);
                result = URI.create("http://localhost:8080");
            }
        };

        assertEquals(orderEventEntity.getOrder().getId(), reservationEventBody.getId());
        if (orderEventEntity.getOrder().getStatus() != null && reservationEventBody.getStatus() != null) {
            assertEquals(orderEventEntity.getOrder().getStatus().toString(), reservationEventBody.getStatus().toString());
        }
        assertEquals(Metadata.ChannelEnum.EMAIL, reservationEventBody.getMetadata().getDefaultChannel());
        assertEquals(Metadata.ChannelEnum.EMAIL, reservationEventBody.getMetadata().getFallbackChannel());
        assertNotNull(reservationEventBody.getReservations());
        assertEquals(orderEventEntity.getOrder().getOrderLineItems().size(), reservationEventBody.getReservations().size());
        if (orderEventEntity.getOrder().getType().equals(Type.PACKAGE) && orderEventEntity.getOrder().getVersion().equals(Version.V2)) {
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageCategoryId(), reservationEventBody.getPackageConfigDetails().getPackageCategoryId());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackagePriceBreakdown(), reservationEventBody.getPackageConfigDetails().getPackagePriceBreakdownType());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageName(), reservationEventBody.getPackageConfigDetails().getPackageName());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getSeatMapOptions(), reservationEventBody.getPackageConfigDetails().getSeatMapOptions().toString());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getPackageInclusions().size(),
                    reservationEventBody.getPackageConfigDetails().getPackageInclusions().size());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getPackageTotal(),
                    reservationEventBody.getPackageConfigDetails().getPackagePricingDetails().getPackageTotal());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getPackageBaseTotal(),
                    reservationEventBody.getPackageConfigDetails().getPackagePricingDetails().getPackageSubTotal());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().size(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().size());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getCode(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getCode());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getId(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getId());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getStart(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getStart());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getEnd(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getEnd());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getTaxRate(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getTaxRate());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanName(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanName());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanCode(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanCode());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getPricingApplied(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getPricingApplied());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getNightlyCharge(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getNightlyCharge());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getAmtAftTax(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getAmtAfterTax());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getLongDescription(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getLongDescription());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getDescription(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getDescription());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackageComponentDetails().get(0).getShortDescription(),
                    reservationEventBody.getPackageConfigDetails().getPackageComponentDetails().get(0).getShortDescription());
        }
        

        //First item - show
        assertNotNull(reservationEventBody.getReservations().get(0));
        assertNotNull(reservationEventBody.getReservations().get(0).getShowReservation());
        final OrderLineItem showOrderLineItem = orderEventEntity.getOrder().getOrderLineItems().get(0);
        final CartLineItem showCartLineItem = orderEventEntity.getCart().getCartLineItems().get(0);
        final ShowReservation showReservation = reservationEventBody.getReservations().get(0).getShowReservation();
        assertEquals(showOrderLineItem.getOrderLineItemId(), showReservation.getId());
        if (showOrderLineItem.getStatus() != null && showReservation.getStatus() != null) {
            assertEquals(showOrderLineItem.getStatus().toString(), showReservation.getStatus().toString());
        }
        assertEquals(showOrderLineItem.getConfirmationNumber(), showReservation.getConfirmationNumber());
        assertNotNull(showReservation.getPropertyDetails());
        assertEquals(showCartLineItem.getItemSelectionDetails().getShowSelectionDetails().getEventDate().toString(),showReservation.getShowDate());
        assertEquals(showCartLineItem.getItemSelectionDetails().getShowSelectionDetails().getEventTime().toString(),showReservation.getShowTime());
        assertEquals(showCartLineItem.getItemSelectionDetails().getShowSelectionDetails().getShowVenueName(),showReservation.getShowVenue());
        assertEquals(showCartLineItem.getProductName(),showReservation.getShowName());
        assertEquals(showOrderLineItem.getNumberOfTickets(),showReservation.getTicketCount());
        assertNotNull(showReservation.getShowSeats());
        assertEquals(showCartLineItem.getItemSelectionDetails().getShowSelectionDetails().getShowTickets().get(0).getPriceCodeDescription(),
                showReservation.getShowSeats().get(0).getPriceCodeDescription());
        assertEquals(showCartLineItem.getItemSelectionDetails().getShowSelectionDetails().getShowTickets().get(0).getTicketTypeCodeDescription(),
                showReservation.getShowSeats().get(0).getTicketTypeCodeDescription());
        assertNotNull(showReservation.getChargeDetails());
        assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getIsMultiDayEvent(),showReservation.getAllDayEventFlag());
        if (orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getIsMultiDayEvent()) {
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getEventStartDate(),showReservation.getShowStartDate());
            assertEquals(orderEventEntity.getOrder().getPackageConfigDetails().getPackagePricingDetails().getEventEndDate(),showReservation.getShowEndDate());
        }

        //Second item - room
        assertNotNull(reservationEventBody.getReservations().get(1));
        assertNotNull(reservationEventBody.getReservations().get(1).getRoomReservation());
        final OrderLineItem roomOrderLineItem = orderEventEntity.getOrder().getOrderLineItems().get(1);
        final CartLineItem roomCartLineItem = orderEventEntity.getCart().getCartLineItems().get(1);
        final com.mgmresorts.rtc.RoomReservation roomReservation = reservationEventBody.getReservations().get(1).getRoomReservation();
        assertEquals(roomOrderLineItem.getOrderLineItemId(), roomReservation.getId());
        if (roomOrderLineItem.getStatus() != null && roomReservation.getStatus() != null) {
            assertEquals(roomOrderLineItem.getStatus().toString(), roomReservation.getStatus().toString());
        }
        assertEquals(roomOrderLineItem.getConfirmationNumber(), roomReservation.getConfirmationNumber());
        assertNotNull(roomReservation.getRoomDetails());
        assertNotNull(roomReservation.getArrivalDate());
        assertNotNull(roomReservation.getDepartureDate());
        assertNotNull(roomReservation.getPropertyDetails());
        assertNotNull(roomReservation.getGuestDetails());
        assertNotNull(roomReservation.getRoomDetails());
        assertNotNull(roomReservation.getReservationConciergeEmail());
        final RoomDetailsElement roomDetailsElement = roomReservation.getRoomDetails();
        assertEquals(roomCartLineItem.getProductName(),roomDetailsElement.getRoomName());
        assertEquals(roomOrderLineItem.getAddOnComponents().size(),roomDetailsElement.getRoomRequestsCount());
        assertEquals(roomOrderLineItem.getAddOnComponents().size(),roomDetailsElement.getRoomRequests().size());
        assertNotNull(roomReservation.getChargeDetails());

    }

    void setUpDataForContent(com.mgmresorts.order.entity.OrderEvent orderEventEntity) {        
        for (int i = 0; i < orderEventEntity.getCart().getCartLineItems().size(); i++) {
            final CartLineItem cartLineItem = orderEventEntity.getCart().getCartLineItems().get(i);
            final OrderLineItem orderLineItem = orderEventEntity.getOrder().getOrderLineItems().get(i);
            final String lineItemId = UUID.randomUUID().toString();

            cartLineItem.setCartLineItemId(lineItemId);
            orderLineItem.setCartLineItemId(lineItemId);
            orderLineItem.setStatus(OrderLineItem.Status.SUCCESS);
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
