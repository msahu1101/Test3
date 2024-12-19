package com.mgmresorts.order.service.task.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.concurrent.Executors;
import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.IRoomBookingAccess;
import com.mgmresorts.order.backend.handler.IPaymentProcessingHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionCommonHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionRoomHandler;
import com.mgmresorts.order.backend.handler.impl.CartHandler;
import com.mgmresorts.order.backend.handler.impl.ItineraryHandler;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.database.access.IOrderConfirmationAccess;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.BillingAddress;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.dto.Payment;
import com.mgmresorts.order.dto.services.CancelReservationRequest;
import com.mgmresorts.order.dto.services.CancelReservationResponse;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.PreviewReservationRequest;
import com.mgmresorts.order.dto.services.PreviewReservationResponse;
import com.mgmresorts.order.dto.services.ReservationType;
import com.mgmresorts.order.dto.services.RetrieveReservationResponse;
import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.dto.services.UpdateReservationRequest;
import com.mgmresorts.order.dto.services.UpdateReservationResponse;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.order.entity.LineItem;
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.entity.OrderConfirmationMapping;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.consumer.IMergeConsumer;
import com.mgmresorts.order.utils.Orders;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.psm.model.SessionError;
import com.mgmresorts.rbs.model.CancelRoomReservationResponse;
import com.mgmresorts.rbs.model.CancelRoomReservationV3Request;
import com.mgmresorts.rbs.model.CreateRoomReservationRequest;
import com.mgmresorts.rbs.model.Deposit;
import com.mgmresorts.rbs.model.ErrorResponse;
import com.mgmresorts.rbs.model.ErrorResponseError;
import com.mgmresorts.rbs.model.GetRoomReservationResponse;
import com.mgmresorts.rbs.model.ModifyCommitErrorResponse;
import com.mgmresorts.rbs.model.ModifyCommitPutRequest;
import com.mgmresorts.rbs.model.PremodifyPutRequest;
import com.mgmresorts.rbs.model.PremodifyPutRequestTripDetails;
import com.mgmresorts.rbs.model.RefundCommitPutRequest;
import com.mgmresorts.rbs.model.ReservationProfile;
import com.mgmresorts.rbs.model.RoomBillingDetails;
import com.mgmresorts.rbs.model.RoomBillingDetailsResponse;
import com.mgmresorts.rbs.model.RoomReservationResponse;
import com.mgmresorts.rbs.model.UpdateRoomReservationResponse;
import com.mgmresorts.shopping.cart.dto.AddOnComponent;
import com.mgmresorts.shopping.cart.dto.AgentInfo;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.ItemSelectionDetails;
import com.mgmresorts.shopping.cart.dto.ItemType;
import com.mgmresorts.shopping.cart.dto.RoomRatesSummary;
import com.mgmresorts.shopping.cart.dto.RoomSelectionDetails;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@SuppressWarnings("unchecked")
public class RoomHandlerTest {
    @Tested
    RoomHandler roomHandler;

    @Injectable
    private IHttpService service;
    @Injectable
    private OAuthTokenRegistry registry;
    @Injectable
    private Executors executors;
    @Injectable
    private IOrderAccess orderAccess;
    @Injectable
    private IMergeConsumer mergeConsumer;
    @Injectable
    private IPaymentSessionCommonHandler paymentSessionCommonHandler;
    @Injectable
    private IRoomBookingAccess roomBookingAccess;
    @Injectable
    private IPaymentProcessingHandler paymentProcessingHandler;
    @Injectable
    private IPaymentSessionRoomHandler paymentSessionRoomHandler;
    @Injectable
    private IOrderConfirmationAccess orderConfirmationAccess;
    @Injectable
    private Orders orders;
    @Injectable
    private ITransformer<com.mgmresorts.order.dto.services.Order, Order> orderTransformer;

    private JSonMapper jsonMapper = new JSonMapper();
    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @BeforeEach
    public void before() {
        assertNotNull(service);
        ErrorManager.clean();
        new Errors();
    }

    @Test
    void testCheckout(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
        };
        
        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 1;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 1;
            }
        };

    }

    @Test
    void testCheckoutWithNoSpecialRequest(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
        };
        
        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        RoomSelectionDetails roomSelectionDetails = podamFactoryImpl.manufacturePojo(RoomSelectionDetails.class);
        roomSelectionDetails.setSpecialRequests(null);
        ItemSelectionDetails itemSelectionDetails = new ItemSelectionDetails();
        itemSelectionDetails.setRoomSelectionDetails(roomSelectionDetails);
        cartLineItem.setItemSelectionDetails(itemSelectionDetails);

        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        new Verifications() {
            {
                CreateRoomReservationRequest request;
                roomBookingAccess.createRoomReservation(request = withCapture());
                assertEquals(null, request.getRoomReservation().getComments());
                assertEquals(null, request.getRoomReservation().getAdditionalComments());
            }
        };

        assertEquals("SUCCESS", orderLineItem.getStatus().toString());
        assertEquals(null, orderLineItem.getSpecialRequests());
    }

    @Test
    void testCheckoutWithOneSpecialRequest(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        RoomSelectionDetails roomSelectionDetails = podamFactoryImpl.manufacturePojo(RoomSelectionDetails.class);

        List<String> specialRequests = new ArrayList<>();
        specialRequests.add("One request");
        roomSelectionDetails.setSpecialRequests(specialRequests);
        roomSelectionDetails.setNumberOfNights(5);
        ItemSelectionDetails itemSelectionDetails = new ItemSelectionDetails();
        itemSelectionDetails.setRoomSelectionDetails(roomSelectionDetails);
        cartLineItem.setItemSelectionDetails(itemSelectionDetails);

        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        new Verifications() {
            {
                CreateRoomReservationRequest request;
                roomBookingAccess.createRoomReservation(request = withCapture());
                assertEquals(specialRequests.get(0), request.getRoomReservation().getComments());
                assertEquals(0, request.getRoomReservation().getAdditionalComments().size());
            }
        };

        assertEquals("SUCCESS", orderLineItem.getStatus().toString());
        assertEquals(1, orderLineItem.getSpecialRequests().size());
        assertTrue(orderLineItem.getSpecialRequests().get(0).equals(specialRequests.get(0)));
    }

    @Test
    void testCheckoutWithTwoSpecialRequest(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        RoomSelectionDetails roomSelectionDetails = podamFactoryImpl.manufacturePojo(RoomSelectionDetails.class);

        List<String> specialRequests = new ArrayList<>();
        specialRequests.add("First request");
        specialRequests.add("Second request");
        roomSelectionDetails.setSpecialRequests(specialRequests);
        roomSelectionDetails.setNumberOfNights(8);
        ItemSelectionDetails itemSelectionDetails = new ItemSelectionDetails();
        itemSelectionDetails.setRoomSelectionDetails(roomSelectionDetails);
        cartLineItem.setItemSelectionDetails(itemSelectionDetails);

        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        new Verifications() {
            {
                CreateRoomReservationRequest request;
                roomBookingAccess.createRoomReservation(request = withCapture());
                assertEquals(specialRequests.get(0), request.getRoomReservation().getComments());
                assertEquals(1, request.getRoomReservation().getAdditionalComments().size());
                assertEquals(specialRequests.get(1), request.getRoomReservation().getAdditionalComments().get(0));
            }
        };

        assertEquals("SUCCESS", orderLineItem.getStatus().toString());
        assertEquals(2, orderLineItem.getSpecialRequests().size());
        assertTrue(orderLineItem.getSpecialRequests().get(0).equals(specialRequests.get(0)));
        assertTrue(orderLineItem.getSpecialRequests().get(1).equals(specialRequests.get(1)));
        assertEquals(8, orderLineItem.getNumberOfNights());
    }

    @Test
    void testCheckoutInvalidCCToken(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(invalidCCTokenResponse()), "Error while calling http endpoint", new String[] { "header" });
            }
        };

        CartLineItem cartLineItem = new CartLineItem();
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        ItemSelectionDetails selectionDetails = new ItemSelectionDetails();
        RoomSelectionDetails roomSelectionDetails = new RoomSelectionDetails();
        roomSelectionDetails.setNumberOfNights(3);
        selectionDetails.setRoomSelectionDetails(roomSelectionDetails);
        cartLineItem.setItemSelectionDetails(selectionDetails);
        RoomRatesSummary roomRatesSummary = new RoomRatesSummary();
        roomSelectionDetails.setRatesSummary(roomRatesSummary);
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
    }
    
    @Test
    void testCheckoutInvalidBillingPostalCode(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(invalidBillingPostalCodeResponse()), "Error while calling http endpoint", new String[] { "header" });
            }
        };

        CartLineItem cartLineItem = new CartLineItem();
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        ItemSelectionDetails selectionDetails = new ItemSelectionDetails();
        RoomSelectionDetails roomSelectionDetails = new RoomSelectionDetails();
        roomSelectionDetails.setNumberOfNights(3);
        selectionDetails.setRoomSelectionDetails(roomSelectionDetails);
        cartLineItem.setItemSelectionDetails(selectionDetails);
        RoomRatesSummary roomRatesSummary = new RoomRatesSummary();
        roomSelectionDetails.setRatesSummary(roomRatesSummary);
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
    }

    @Test
    void testCheckoutInvalidCVSToken(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(invalidCVSTokenResponse()), "Error while calling http endpoint", new String[] { "header" });
            }
        };

        CartLineItem cartLineItem = new CartLineItem();
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        ItemSelectionDetails selectionDetails = new ItemSelectionDetails();
        RoomSelectionDetails roomSelectionDetails = new RoomSelectionDetails();
        roomSelectionDetails.setNumberOfNights(3);
        selectionDetails.setRoomSelectionDetails(roomSelectionDetails);
        cartLineItem.setItemSelectionDetails(selectionDetails);
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
    }

    @Test
    void testCheckoutFailureDuringPayment(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {
        new Expectations() {
            {
                itinerary.create((GuestProfile) any, (Cart) any);
                result = "test";
                minTimes = 0;
            }

            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(paymentFailureTokenResponse()), "Error while calling http endpoint",
                        new String[] { "header" });
                minTimes = 0;
            }
        };

        CartLineItem cartLineItem = new CartLineItem();
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        ItemSelectionDetails selectionDetails = new ItemSelectionDetails();
        RoomSelectionDetails roomSelectionDetails = new RoomSelectionDetails();
        RoomRatesSummary roomRatesSummary = new RoomRatesSummary();
        roomSelectionDetails.setNumberOfNights(3);
        roomSelectionDetails.setRatesSummary(roomRatesSummary);
        selectionDetails.setRoomSelectionDetails(roomSelectionDetails);
        cartLineItem.setItemSelectionDetails(selectionDetails);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
    }

    @Test
    void testCheckoutWithProductType_ProductId_PropertyId_programId(@Injectable ItineraryHandler itinerary,
            @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setProductId("roomTypeId1");
        cartLineItem.setType(ItemType.ROOM);
        cartLineItem.setPropertyId("propertyId1");
        cartLineItem.setPackageId("packageId1");
        cartLineItem.setPackageLineItemId("packageLineItemId1");
        RoomSelectionDetails roomDetails = new RoomSelectionDetails();
        roomDetails.setProgramId("14caa425-8ed7-4530-bb48-d7068d3e367e");
        cartLineItem.setItemSelectionDetails(new ItemSelectionDetails());
        cartLineItem.getItemSelectionDetails().setRoomSelectionDetails(roomDetails);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");

        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(true), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());
        assertEquals("14caa425-8ed7-4530-bb48-d7068d3e367e", orderLineItem.getProgramId());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 0;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 0;
            }
        };
    }

    @Test
    void testCheckoutForBorgata(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getBorgataReservationResponse();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getBorgataHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        assertEquals(2.00, orderLineItem.getLineItemCasinoSurcharge());
        assertEquals(2.27, orderLineItem.getLineItemCasinoSurchargeAndTax());
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());

    }

    @Test
    void testCheckoutMergeAndUpdateFailure(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                result = new AppException(SystemError.UNABLE_TO_CALL_BACKEND);
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        assertThrows(AppException.class,
                () -> roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId",
                        agentInfo, false, false, null, null));
    }
    
    @Test
    void testCheckoutResponseForOrderLineItemCharges(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
            @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
            @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
        };
        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("cartLineItemId1");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("3511");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
        assertEquals(149.99, orderLineItem.getLineItemCharge());
        assertEquals(316.32, orderLineItem.getLineItemPrice());
        assertEquals(170.06, orderLineItem.getLineItemDeposit());
        assertEquals(0.00, orderLineItem.getLineItemDiscount());
        assertEquals(146.26, orderLineItem.getLineItemBalance());
        assertEquals(0.00, orderLineItem.getLineItemTourismFeeAndTax());
        assertEquals(39.00, orderLineItem.getLineItemResortFeePerNight());
        assertEquals(0.00, orderLineItem.getLineItemOccupancyFee());
        assertEquals(44.22, orderLineItem.getLineItemResortFeeAndTax());
        assertEquals(239.99, orderLineItem.getAveragePricePerNight());
        assertEquals(239.99, orderLineItem.getLineItemAdjustedItemSubtotal());
        assertEquals(188.99, orderLineItem.getLineItemTripSubtotal());
        assertEquals(0.00, orderLineItem.getLineItemCasinoSurcharge());
        assertEquals(0.00, orderLineItem.getLineItemCasinoSurchargeAndTax());
        assertEquals(false, orderLineItem.getF1Package());
        assertEquals(32.11, orderLineItem.getLineItemTax());
        assertEquals(cartLineItem.getAddOnsPrice(), orderLineItem.getAddOnsPrice());
        assertEquals(cartLineItem.getAddOnsTax(), orderLineItem.getAddOnsTax());
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());
    }

    @Test
    void testUpdateCaptureSuccess(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                  @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                  @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                  @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer
    ) throws AppException, IOException {

        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFields());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_success_response.json"), ModifyCommitErrorResponse.class);
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
            {
                orderAccess.read(anyString);
                result = createOrder();
            }
            {
                orderAccess.update((Order) any);
                result = createOrder();
            }
            {
                paymentProcessingHandler.captureTransaction(anyString, anyString, (PaymentAuthFields) any);
                result = null;
            }
            {
                orderTransformer.toLeft((Order) any);
                result = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.dto.services.Order.class);
            }
            {
                orderTransformer.toRight((com.mgmresorts.order.dto.services.Order) any);
                result = createOrder();
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);

        new Verifications() {
            {
                roomBookingAccess.refundCommitRoomReservation((RefundCommitPutRequest) any);
                times = 0;
            }
            {
                Order order = null;
                orderAccess.update(order = withCapture());
                assertNotNull(order.getOrderUpdatedAt());
                times = 1;
            }
        };

        assertNotNull(response);
        assertNull(response.getErrorPaymentSessionResponse());
        assertNull(response.getErrorRoomReservationResponse());
        assertNotNull(response.getRoomReservationResponse());
        assertEquals(response.getRoomReservationResponse().getConfirmationNumber(), "M083E2635");
        assertEquals(response.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(response.getRoomReservationResponse().getProfile().getLastName(), "Doe");
    }

    @Test
    void testUpdateRefundSuccess(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                  @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                  @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                  @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer
    ) throws AppException, IOException {

        final ModifyCommitErrorResponse errorResponse = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_success_response.json"),
                ModifyCommitErrorResponse.class);
        errorResponse.getRoomReservation().getRatesSummary().setChangeInDeposit(-100.00);
        
        final UpdateRoomReservationResponse commitRedundResponse = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_success_response.json"),
                UpdateRoomReservationResponse.class);

        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFields());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setSuccess(true);
        paymentAuthFields.setAuthorizationCode("auth");

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = errorResponse;
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
            {
                orderAccess.read(anyString);
                result = createOrder();
            }
            {
                orderAccess.update((Order) any);
                result = createOrder();
            }
            {
                paymentProcessingHandler.refundTransaction(anyString, anyString, anyString, anyDouble, (List<Billing>) any, anyString, anyString);
                result = null;
            }
            {
                roomBookingAccess.refundCommitRoomReservation((RefundCommitPutRequest) any);
                result = commitRedundResponse;
            }
            {
                orderTransformer.toLeft((Order) any);
                result = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.dto.services.Order.class);
            }
            {
                orderTransformer.toRight((com.mgmresorts.order.dto.services.Order) any);
                result = createOrder();
            }
            {
                paymentProcessingHandler.refundTransaction(anyString,anyString,anyString,anyDouble,(List<Billing>) any, anyString, anyString);
                result = paymentAuthFields;
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);

        new Verifications() {
            {
                roomBookingAccess.refundCommitRoomReservation((RefundCommitPutRequest) any);
                times = 1;
            }
            {
                orderAccess.update((Order) any);
                times = 1;
            }
            {
                roomBookingAccess.releaseRoomReservation(anyString,anyString,anyString,anyBoolean);
                times = 0;
            }
        };

        assertNotNull(response);
        assertNull(response.getErrorPaymentSessionResponse());
        assertNull(response.getErrorRoomReservationResponse());
        assertNotNull(response.getRoomReservationResponse());
        assertEquals(response.getRoomReservationResponse().getConfirmationNumber(), "M083E2635");
        assertEquals(response.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(response.getRoomReservationResponse().getProfile().getLastName(), "Doe");
    }

    @Test
    void testPaymentProcessingGlobalUpdateTCOLVSuccess(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                         @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                         @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                         @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException, IOException {

        final ModifyCommitErrorResponse errorResponse = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_success_response.json"),
                ModifyCommitErrorResponse.class);
        errorResponse.getRoomReservation().getRatesSummary().setChangeInDeposit(-100.00);

        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFieldsTCOLV());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setSuccess(true);
        paymentAuthFields.setAuthorizationCode("auth");

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = errorResponse;
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
            {
                orderAccess.read(anyString);
                result = createOrder();
            }
            {
                orderAccess.update((Order) any);
                result = createOrder();
            }
            {
                orderTransformer.toLeft((Order) any);
                result = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.dto.services.Order.class);
            }
            {
                orderTransformer.toRight((com.mgmresorts.order.dto.services.Order) any);
                result = createOrder();
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);

        new Verifications() {
            {
                roomBookingAccess.refundCommitRoomReservation((RefundCommitPutRequest) any);
                times = 0;
            }
            {
                paymentProcessingHandler.refundTransaction(anyString, anyString, anyString, anyDouble, (List<Billing>) any, anyString, anyString);
                times = 0;
            }
            {
                orderAccess.update((Order) any);
                times = 1;
            }
            {
                roomBookingAccess.releaseRoomReservation(anyString,anyString,anyString,anyBoolean);
                times = 0;
            }
        };

        assertNotNull(response);
        assertNull(response.getErrorPaymentSessionResponse());
        assertNull(response.getErrorRoomReservationResponse());
        assertNotNull(response.getRoomReservationResponse());
        assertEquals(response.getRoomReservationResponse().getConfirmationNumber(), "M083E2635");
        assertEquals(response.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(response.getRoomReservationResponse().getProfile().getLastName(), "Doe");
    }

    @Test
    void testPaymentProcessingGlobalUpdateTCOLVFailure(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                         @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                         @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                         @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException, IOException {


        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFieldsTCOLV());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setSuccess(false);

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = null;
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
        };

        @SuppressWarnings("unused")
        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);

        new Verifications() {
            {
                roomBookingAccess.refundCommitRoomReservation((RefundCommitPutRequest) any);
                times = 0;
            }
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                times = 1;
            }
            {
                paymentProcessingHandler.refundTransaction(anyString, anyString, anyString, anyDouble, (List<Billing>) any, anyString, anyString);
                times = 0;
            }
            {
                paymentProcessingHandler.voidTransaction(anyString, anyString, (PaymentAuthFields) any);
                times = 0;
            }
        };
    }

    @Test
    void testUpdateRefundFailure(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                 @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                 @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                 @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer
    ) throws AppException, IOException {

        final ModifyCommitErrorResponse errorResponse = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_success_response.json"),
                ModifyCommitErrorResponse.class);
        errorResponse.getRoomReservation().getRatesSummary().setChangeInDeposit(-100.00);

        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFields());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setSuccess(false);

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = errorResponse;
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
            {
                orderAccess.read(anyString);
                result = createOrder();
            }
            {
                paymentProcessingHandler.refundTransaction(anyString, anyString, anyString, anyDouble, (List<Billing>) any, anyString, anyString);
                result = null;
            }
            {
                paymentProcessingHandler.refundTransaction(anyString,anyString,anyString,anyDouble,(List<Billing>) any, anyString, anyString);
                result = paymentAuthFields;
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);

        new Verifications() {
            {
                roomBookingAccess.refundCommitRoomReservation((RefundCommitPutRequest) any);
                times = 0;
            }
            {
                orderAccess.update((Order) any);
                times = 0;
            }
            {
                roomBookingAccess.releaseRoomReservation(anyString,anyString,anyString,anyBoolean);
                times = 1;
            }
        };

        assertNotNull(response);
        assertNull(response.getErrorPaymentSessionResponse());
        assertNull(response.getErrorRoomReservationResponse());
        assertNotNull(response.getRoomReservationResponse());
        assertEquals(response.getRoomReservationResponse().getConfirmationNumber(), "M083E2635");
        assertEquals(response.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(response.getRoomReservationResponse().getProfile().getLastName(), "Doe");
    }


    @Test
    void testUpdateReservationAddOnCompSuccess(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                 @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                 @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                 @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer
    ) throws AppException, IOException {

        final ModifyCommitErrorResponse commitResponse = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_success_response.json"),
                ModifyCommitErrorResponse.class);
        commitResponse.getRoomReservation().getRatesSummary().setChangeInDeposit(-100.00);
        commitResponse.getRoomReservation().getPurchasedComponents().get(0).setId("comp2");
        
        final UpdateRoomReservationResponse commitRedundResponse = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_success_response.json"),
                UpdateRoomReservationResponse.class);

        Order order = createOrder();
        order.setLineItems(new ArrayList<>());
        LineItem lineItem = new LineItem();
        lineItem.setConfirmationNumber(commitResponse.getRoomReservation().getConfirmationNumber());
        lineItem.setAddOnComponents(new ArrayList<>());
        com.mgmresorts.order.dto.AddOnComponent addOnComponent1 = new com.mgmresorts.order.dto.AddOnComponent();
        addOnComponent1.setId("comp1");
        addOnComponent1.setSelected(true);
        lineItem.getAddOnComponents().add(addOnComponent1);
        order.getLineItems().add(lineItem);

        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFields());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setSuccess(true);
        paymentAuthFields.setAuthorizationCode("auth");

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = commitResponse;
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
            {
                orderAccess.read(anyString);
                result = order;
            }
            {
                orderAccess.update((Order) any);
                result = order;
            }
            {
                paymentProcessingHandler.refundTransaction(anyString, anyString, anyString, anyDouble, (List<Billing>) any, anyString, anyString);
                result = null;
            }
            {
                roomBookingAccess.refundCommitRoomReservation((RefundCommitPutRequest) any);
                result = commitRedundResponse;
            }
            {
                orderTransformer.toLeft((Order) any);
                result = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.dto.services.Order.class);
            }
            {
                orderTransformer.toRight((com.mgmresorts.order.dto.services.Order) any);
                result = createOrder();
            }
            {
                paymentProcessingHandler.refundTransaction(anyString,anyString,anyString,anyDouble,(List<Billing>) any, anyString, anyString);
                result = paymentAuthFields;
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);
        assertNotNull(response);
        assertNull(response.getErrorPaymentSessionResponse());
        assertNull(response.getErrorRoomReservationResponse());
        assertNotNull(response.getRoomReservationResponse());
        assertEquals(response.getRoomReservationResponse().getConfirmationNumber(), "M083E2635");
        assertEquals(response.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(response.getRoomReservationResponse().getProfile().getLastName(), "Doe");

        new Verifications() {
            {
                Order c;
                orderTransformer.toLeft(c = withCapture());
                times = 1;
                assertEquals(commitResponse.getRoomReservation().getPurchasedComponents().get(0).getId()
                        ,c.getLineItems().get(0).getAddOnComponents().get(0).getId());
                assertEquals(commitResponse.getRoomReservation().getPurchasedComponents().get(0).getShortDescription()
                        ,c.getLineItems().get(0).getAddOnComponents().get(0).getShortDescription());
                assertEquals(commitResponse.getRoomReservation().getPurchasedComponents().get(0).isActive()
                        ,c.getLineItems().get(0).getAddOnComponents().get(0).getActive());
                assertEquals(commitResponse.getRoomReservation().getPurchasedComponents().get(0).isNonEditable()
                        ,c.getLineItems().get(0).getAddOnComponents().get(0).getNonEditable());
                assertEquals(commitResponse.getRoomReservation().getPurchasedComponents().get(0).getPricingApplied().toString()
                        ,c.getLineItems().get(0).getAddOnComponents().get(0).getPricingApplied().value());
                assertEquals(commitResponse.getRoomReservation().getPurchasedComponents().get(0).getLongDescription()
                        ,c.getLineItems().get(0).getAddOnComponents().get(0).getLongDescription());
                assertEquals(commitResponse.getRoomReservation().getPurchasedComponents().get(0).getCode()
                        ,c.getLineItems().get(0).getAddOnComponents().get(0).getCode());
                assertEquals(commitResponse.getRoomReservation().getPurchasedComponents().get(0).getTripPrice().doubleValue()
                        ,c.getLineItems().get(0).getAddOnComponents().get(0).getTripPrice());
                assertEquals(commitResponse.getRoomReservation().getPurchasedComponents().get(0).getTripTax().doubleValue()
                        ,c.getLineItems().get(0).getAddOnComponents().get(0).getTripTax());
                assertEquals(commitResponse.getRoomReservation().getPurchasedComponents().get(0).getPrice().doubleValue()
                        ,c.getLineItems().get(0).getAddOnComponents().get(0).getPrice());
            }
        };


    }

    @Test
    void testUpdateRBSCommitPriceChangeException(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                 @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                 @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                 @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer
    ) throws AppException, IOException {

        final ModifyCommitErrorResponse errorResponseError = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_400_failure_response_price_change.json"),
                ModifyCommitErrorResponse.class);

        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFields());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = errorResponseError;
            }
            {
                paymentProcessingHandler.voidTransaction(anyString, anyString, (PaymentAuthFields) any);
                result = null;
            }
            {
                paymentSessionRoomHandler.managePaymentSessionForRoomReservation((RoomReservationResponse) any, anyString, CallType.UPDATE);
                result = null;
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);
        assertNotNull(response);
        assertNull(response.getErrorPaymentSessionResponse());
        assertNotNull(response.getRoomReservationResponse());
        assertEquals(response.getRoomReservationResponse().getConfirmationNumber(), "M083E2635");
        assertEquals(response.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(response.getRoomReservationResponse().getProfile().getLastName(), "Doe");
        assertNotNull(response.getErrorRoomReservationResponse());
        assertEquals(response.getErrorRoomReservationResponse().getCode(), "632-2-259");
    }

    @Test
    void testUpdateRBSCommitPriceChangeAndUpdateSessionException(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                 @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                 @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                 @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer
    ) throws AppException, IOException {
        final ModifyCommitErrorResponse errorResponseError = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_400_failure_response_price_change.json"),
                ModifyCommitErrorResponse.class);
        final SessionError sessionError = new SessionError();
        sessionError.setErrorCode("123-456");
        sessionError.setErrorMessage("Unable to update session");

        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFields());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = errorResponseError;
            }
            {
                paymentProcessingHandler.voidTransaction(anyString, anyString, (PaymentAuthFields) any);
                result = null;
            }
            {
                paymentSessionRoomHandler.managePaymentSessionForRoomReservation((RoomReservationResponse) any, anyString, CallType.UPDATE);
                result = new SourceAppException(ApplicationError.UNABLE_TO_UPDATE_PAYMENT_SESSION, "123-456", "Unable to update new session", jsonMapper.asJsonString(sessionError));
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);
        assertNotNull(response);
        assertNotNull(response.getRoomReservationResponse());
        assertEquals(response.getRoomReservationResponse().getConfirmationNumber(), "M083E2635");
        assertEquals(response.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(response.getRoomReservationResponse().getProfile().getLastName(), "Doe");
        assertNotNull(response.getErrorRoomReservationResponse());
        assertEquals(response.getErrorRoomReservationResponse().getCode(), "632-2-259");
        assertNotNull(response.getErrorPaymentSessionResponse());
        assertEquals(response.getErrorPaymentSessionResponse().getErrorCode(), "123-456");
    }

    @Test
    void testUpdateRBSCommit4xxException(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                 @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                 @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                 @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer
    ) throws AppException, IOException {

        final ModifyCommitErrorResponse errorResponseError = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_400_failure_response.json"),
                ModifyCommitErrorResponse.class);

        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFields());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = new SourceAppException(Errors.UNABLE_TO_UPDATE_ROOM_RESERVATION, "632-1-101", "Transaction id is missing in the request header",
                        jsonMapper.asJsonString(errorResponseError));
            }
            {
                paymentProcessingHandler.voidTransaction(anyString, anyString, (PaymentAuthFields) any);
                result = null;
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);
        assertNotNull(response);
        assertNull(response.getRoomReservationResponse());
        assertNull(response.getErrorPaymentSessionResponse());
        assertNotNull(response.getErrorRoomReservationResponse());
        assertEquals(response.getErrorRoomReservationResponse().getCode(), "632-1-101");
    }

    @Test
    void testUpdateRBSCommit5xxException(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                         @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                         @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                         @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer
    ) throws AppException, IOException {

        final ModifyCommitErrorResponse errorResponseError = jsonMapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_500_failure_response.json"),
                ModifyCommitErrorResponse.class);

        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFields());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = new SourceAppException(Errors.UNABLE_TO_UPDATE_ROOM_RESERVATION, "632-1-101", "Transaction id is missing in the request header",
                        jsonMapper.asJsonString(errorResponseError));
            }
            {
                paymentProcessingHandler.voidTransaction(anyString, anyString, (PaymentAuthFields) any);
                result = null;
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);
        assertNotNull(response);
        assertNull(response.getRoomReservationResponse());
        assertNull(response.getErrorPaymentSessionResponse());
        assertNotNull(response.getErrorRoomReservationResponse());
        assertEquals(response.getErrorRoomReservationResponse().getCode(), "632-1-100");
    }

    @Test
    void testUpdateRBSCommitUnknownException(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                         @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                         @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                         @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer
    ) throws AppException {


        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFields());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = new AppException(Errors.UNABLE_TO_UPDATE_ROOM_RESERVATION, "[Error from RBS] Commit room reservation call failed with app exception: {}");
            }
            {
                paymentProcessingHandler.voidTransaction(anyString, anyString, (PaymentAuthFields) any);
                result = null;
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields);
        assertNotNull(response);
        assertNull(response.getRoomReservationResponse());
        assertNull(response.getErrorPaymentSessionResponse());
        assertNull(response.getErrorRoomReservationResponse());
    }

    @Test
    void testUpdateRBSCommitNullException(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                             @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                             @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                             @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer
    ) throws AppException {

        UpdateReservationRequest updateReservationRequest = new UpdateReservationRequest();
        updateReservationRequest.setModifyRoomReservationRequest(createRoomReservationCommitRequest());
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setPaymentAuthFieldsMap(new HashMap<>());
        paymentSessionBaseFields.getPaymentAuthFieldsMap().put(getOrderConfirmationMapping().getConfirmationNumber(), getPaymentAuthFields());
        paymentSessionBaseFields.setBillings(getBillingsList());
        paymentSessionBaseFields.setOrderReferenceNumber("56789087");

        new Expectations() {
            {
                roomBookingAccess.commitRoomReservation((ModifyCommitPutRequest) any);
                result = new AppException(Errors.UNABLE_TO_UPDATE_ROOM_RESERVATION, "Could not commit room reservation. No response from backend.");
            }
            {
                paymentProcessingHandler.voidTransaction(anyString, anyString, (PaymentAuthFields) any);
                result = null;
            }
        };

        final UpdateReservationResponse response = roomHandler.updateReservation(updateReservationRequest, paymentSessionBaseFields );
        assertNotNull(response);
        assertNull(response.getRoomReservationResponse());
        assertNull(response.getErrorPaymentSessionResponse());
        assertNull(response.getErrorRoomReservationResponse());
    }

    @Test
    void testGetReservationSuccess(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                   @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                   @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                   @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer) throws AppException {
        GetRoomReservationResponse getRoomReservationResponse = new GetRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("1234");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        getRoomReservationResponse.setRoomReservation(roomReservationResponse);
        EnableSessionResponse enableSessionResponse = new EnableSessionResponse();
        enableSessionResponse.setSessionId("1234");

        new Expectations() {
            {
                roomBookingAccess.getRoomReservation(anyString, anyString, anyString);
                result = getRoomReservationResponse;
            }
            {
                paymentSessionRoomHandler.managePaymentSessionForRoomReservation((RoomReservationResponse) any, anyString, (CallType) any);
                result = enableSessionResponse;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = roomHandler.getReservation("1234", "John", "Doe", true, null);
        assertEquals(retrieveBookingResponse.getRoomReservationResponse().getConfirmationNumber(), "1234");
        assertEquals(retrieveBookingResponse.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(retrieveBookingResponse.getRoomReservationResponse().getProfile().getLastName(), "Doe");
        assertEquals(retrieveBookingResponse.getPaymentSessionId(), "1234");
    }

    @Test
    void testPreviewRoomReservationTest_PSMKnownException(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                   @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                   @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                   @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer) throws AppException {
        UpdateRoomReservationResponse updateRoomReservationResponse = new UpdateRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("1234");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        updateRoomReservationResponse.setRoomReservation(roomReservationResponse);
        final SessionError sessionError = new SessionError();
        sessionError.setErrorCode("123-456");
        sessionError.setErrorMessage("Unable to update session");

        new Expectations() {
            {
                roomBookingAccess.previewRoomReservation((PremodifyPutRequest) any);
                result = updateRoomReservationResponse;
            }
            {
                paymentSessionRoomHandler.managePaymentSessionForRoomReservation((RoomReservationResponse) any, anyString, (CallType) any);
                result = new SourceAppException(ApplicationError.UNABLE_TO_UPDATE_PAYMENT_SESSION, "123-456", "", jsonMapper.asJsonString(sessionError));
            }
        };

        PreviewReservationResponse previewReservationResponse = roomHandler.previewReservation(new PreviewReservationRequest());
        assertNull(previewReservationResponse.getRoomReservationResponse());
        assertNotNull( previewReservationResponse.getErrorPaymentSessionResponse());
        assertEquals("123-456", previewReservationResponse.getErrorPaymentSessionResponse().getErrorCode());
        assertEquals("Unable to update session", previewReservationResponse.getErrorPaymentSessionResponse().getErrorMessage());
    }

    @Test
    void testPreviewRoomReservationTest_UnknownException(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                          @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                          @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                          @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer) throws AppException {
        UpdateRoomReservationResponse updateRoomReservationResponse = new UpdateRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("1234");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        updateRoomReservationResponse.setRoomReservation(roomReservationResponse);
        final SessionError sessionError = new SessionError();
        sessionError.setErrorCode("123-456");
        sessionError.setErrorMessage("Unable to update session");

        new Expectations() {
            {
                roomBookingAccess.previewRoomReservation((PremodifyPutRequest) any);
                result = updateRoomReservationResponse;
            }
            {
                paymentSessionRoomHandler.managePaymentSessionForRoomReservation((RoomReservationResponse) any, anyString, (CallType) any);
                result = new AppException(1000 , "Could not call Update enablePaymentSession. Unexpected error occurred.");
            }
        };

        assertThrows(AppException.class, () -> roomHandler.previewReservation(new PreviewReservationRequest()));
    }


    @Test
    void testPreviewRoomReservationTest_RBSException(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                         @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                         @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                         @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer) throws AppException {
        UpdateRoomReservationResponse updateRoomReservationResponse = new UpdateRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("1234");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        updateRoomReservationResponse.setRoomReservation(roomReservationResponse);
        final ErrorResponse rbsErrorResponse = new ErrorResponse();
        ErrorResponseError errorResponseError = new ErrorResponseError();
        errorResponseError.setCode("632-2-242");
        errorResponseError.setMessage("No reservation available for the input confirmation number.");
        rbsErrorResponse.setError(errorResponseError);

        new Expectations() {
            {
                roomBookingAccess.previewRoomReservation((PremodifyPutRequest) any);
                result = new SourceAppException(3028, "632-2-242", "No reservation available for the input confirmation number.",  jsonMapper.asJsonString(rbsErrorResponse));
            }
        };

        PreviewReservationResponse previewReservationResponse = roomHandler.previewReservation(new PreviewReservationRequest());
        assertNull(previewReservationResponse.getRoomReservationResponse());
        assertNotNull( previewReservationResponse.getErrorRoomReservationResponse());
        assertNotNull( previewReservationResponse.getErrorRoomReservationResponse().getError());
        assertEquals("632-2-242", previewReservationResponse.getErrorRoomReservationResponse().getError().getCode());
        assertEquals("No reservation available for the input confirmation number.", previewReservationResponse.getErrorRoomReservationResponse().getError().getMessage());
    }

    @Test
    void testPreviewReservationSuccess(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                       @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                       @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                       @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer) throws AppException {
        UpdateRoomReservationResponse updateRoomReservationResponse = new UpdateRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("1234");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        updateRoomReservationResponse.setRoomReservation(roomReservationResponse);
        EnableSessionResponse enableSessionResponse = new EnableSessionResponse();
        enableSessionResponse.setSessionId("1234");

        new Expectations() {
            {
                roomBookingAccess.previewRoomReservation((PremodifyPutRequest) any);
                result = updateRoomReservationResponse;
            }
            {
                paymentSessionRoomHandler.managePaymentSessionForRoomReservation((RoomReservationResponse) any, anyString, (CallType) any);
                result = enableSessionResponse;
            }
        };

        PreviewReservationResponse previewReservationResponse = roomHandler.previewReservation(new PreviewReservationRequest());
        assertEquals("1234", previewReservationResponse.getRoomReservationResponse().getConfirmationNumber());
        assertEquals("John", previewReservationResponse.getRoomReservationResponse().getProfile().getFirstName());
        assertEquals("Doe", previewReservationResponse.getRoomReservationResponse().getProfile().getLastName());
        assertEquals("1234", previewReservationResponse.getPaymentSessionId());
    }

    private OrderConfirmationMapping getOrderConfirmationMapping() {
        final OrderConfirmationMapping orderConfirmationMapping = new OrderConfirmationMapping();
        orderConfirmationMapping.setConfirmationNumber("M083E2635");
        orderConfirmationMapping.setId("123456");
        return orderConfirmationMapping;
    }

    private PaymentAuthFields getPaymentAuthFields() {
        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setPaymentId("43243124432");
        paymentAuthFields.setAuthorizationCode("OA14k");
        paymentAuthFields.setAmount(1000.00);
        paymentAuthFields.setConfirmationNumber("M083E2635");
        paymentAuthFields.setItemId("itemId-dfghj-ertyu");
        return paymentAuthFields;
    }

    private PaymentAuthFields getPaymentAuthFieldsTCOLV() {
        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setPaymentId("43243124432");
        paymentAuthFields.setAuthorizationCode("OA14k");
        paymentAuthFields.setAmount(1000.00);
        paymentAuthFields.setConfirmationNumber("M083E2635");
        paymentAuthFields.setPropertyId("e5d3f1c9-833a-83f1-e053-d303fe0ad83c");
        paymentAuthFields.setItemId("ugiysgiygsdf-gutfsee-sefuuusdf");
        return paymentAuthFields;
    }

    private List<Billing> getBillingsList() {
        final List<Billing> billings = new ArrayList<>();
        final Billing billing = new Billing();
        final Payment payment = new Payment();
        payment.setExpiry("07/2030");
        payment.setCcToken("4329814172512343290148012341234321");
        billing.setPayment(payment);
        billings.add(billing);
        return billings;
    }

    private ModifyCommitPutRequest createRoomReservationCommitRequest() {
        ModifyCommitPutRequest request = new ModifyCommitPutRequest();
        request.setConfirmationNumber("M083E2635");
        request.setFirstName("John");
        request.setLastName("Doe");
        PremodifyPutRequestTripDetails tripDetails = new PremodifyPutRequestTripDetails();
        tripDetails.setCheckInDate("2024-02-16");
        tripDetails.setCheckOutDate("2024-02-18");
        request.setTripDetails(tripDetails);
        List<String> roomRequests = new ArrayList<>();
        roomRequests.add("a197e21b-ef75-45aa-b9d3-3480f557c77a");
        request.setRoomRequests(roomRequests);
        request.setPreviewReservationDeposit(136.4);
        request.setPreviewReservationTotal(236.4);
        request.setPreviewReservationChangeInDeposit(20.0);
        request.setCvv("123");
        return request;
    }

    private Order createOrder() {
        final Order order = new Order();
        order.setId("123456");
        order.setLineItems(getOrderLineItems());
        return order;
    }

    private List<LineItem> getOrderLineItems() {
        final List<LineItem> lineItems = new ArrayList<>();
        final LineItem lineItem = new LineItem();
        lineItem.setConfirmationNumber("M083E2635");
        lineItems.add(lineItem);
        return lineItems;
    }

    @Test
    void testPaymentProcessingGlobalCheckoutTCOLVSuccess(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                  @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                  @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                  @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        RoomBillingDetails roomBillingDetails = podamFactoryImpl.manufacturePojo(RoomBillingDetails.class);
        List<RoomBillingDetails> roomBillingDetailsList = new ArrayList<>();
        roomBillingDetailsList.add(roomBillingDetails);
        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationRespTCOLV();
            }
            {
                billingTransformer.toRight((Collection<Billing>) any);
                result = roomBillingDetailsList;
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(5.0);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setPropertyId("e5d3f1c9-833a-83f1-e053-d303fe0ad83c");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setAuthorizationCode("authCode");
        paymentAuthFields.setPaymentId("payId");
        paymentAuthFields.setSuccess(true);
        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 1;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 1;
            }
            {
                CreateRoomReservationRequest c;
                roomBookingAccess.createRoomReservation(c = withCapture());
                times = 1;
                assertEquals(true, c.getRoomReservation().isSkipPaymentProcess());
                assertEquals( true, c.getRoomReservation().isSkipFraudCheck());
            }
            {
                paymentProcessingHandler.captureTransaction(anyString, anyString, (PaymentAuthFields) any);
                times = 0;
            }
        };
    }

    @Test
    void testPaymentProcessingGlobalCheckoutTCOLVFailure(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                         @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                         @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                         @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        RoomBillingDetails roomBillingDetails = podamFactoryImpl.manufacturePojo(RoomBillingDetails.class);
        List<RoomBillingDetails> roomBillingDetailsList = new ArrayList<>();
        roomBillingDetailsList.add(roomBillingDetails);
        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(paymentFailureTokenResponse()), "Error while calling http endpoint",
                        new String[] { "header" });
            }
            {
                billingTransformer.toRight((Collection<Billing>) any);
                result = roomBillingDetailsList;
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(5.0);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setPropertyId("e5d3f1c9-833a-83f1-e053-d303fe0ad83c");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setAuthorizationCode("authCode");
        paymentAuthFields.setPaymentId("payId");
        paymentAuthFields.setSuccess(true);
        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 1;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 1;
            }
            {
                CreateRoomReservationRequest c;
                roomBookingAccess.createRoomReservation(c = withCapture());
                times = 1;
                assertEquals(true, c.getRoomReservation().isSkipPaymentProcess());
                assertEquals( true, c.getRoomReservation().isSkipFraudCheck());
            }
            {
                paymentProcessingHandler.voidTransaction(anyString, anyString, (PaymentAuthFields) any);
                times = 0;
            }
        };
    }

    @Test
    void testPaymentProcessingGlobalCheckout(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                      @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                      @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                      @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        RoomBillingDetails roomBillingDetails = podamFactoryImpl.manufacturePojo(RoomBillingDetails.class);
        List<RoomBillingDetails> roomBillingDetailsList = new ArrayList<>();
        roomBillingDetailsList.add(roomBillingDetails);
        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
            {
                billingTransformer.toRight((Collection<Billing>) any);
                result = roomBillingDetailsList;
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(5.0);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setAuthorizationCode("authCode");
        paymentAuthFields.setPaymentId("payId");
        paymentAuthFields.setAmount(100.0);;
        paymentAuthFields.setSuccess(true);
        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 1;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 1;
            }
            {
                PaymentAuthFields c;
                String s;
                paymentProcessingHandler.captureTransaction(anyString, s = withCapture(), c = withCapture());
                times = 1;
                assertEquals(paymentAuthFields.getPaymentId(),c.getPaymentId());
                assertEquals(paymentAuthFields.getAuthorizationCode(),c.getAuthorizationCode());
                assertTrue(c.isSuccess());
                assertEquals("orderRef", s);
            }
        };

    }

    @Test
    void testPaymentProcessingGlobalCheckout_Comp(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                             @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                             @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                             @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        RoomBillingDetails roomBillingDetails = podamFactoryImpl.manufacturePojo(RoomBillingDetails.class);
        List<RoomBillingDetails> roomBillingDetailsList = new ArrayList<>();
        roomBillingDetailsList.add(roomBillingDetails);
        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
            {
                billingTransformer.toRight((Collection<Billing>) any);
                result = roomBillingDetailsList;
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(0.0);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setAuthorizationCode("authCode");
        paymentAuthFields.setPaymentId("payId");
        paymentAuthFields.setSuccess(true);
        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 1;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 1;
            }
            {
                paymentProcessingHandler.captureTransaction(anyString, anyString, (PaymentAuthFields) any);
                times = 0;
            }
        };

    }

    @Test
    void testPaymentFailureGlobalCheckoutFailedAuth(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                       @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                       @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                       @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();

        paymentAuthFields.setAuthorizationCode("authCode");
        paymentAuthFields.setPaymentId("payId");
        paymentAuthFields.setSuccess(false);

        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();

        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 1;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 1;
            }
            {
                paymentProcessingHandler.captureTransaction(anyString, anyString, (PaymentAuthFields) any);
                times = 0;
            }
        };

    }

    @Test
    void testPaymentFailureGlobalCheckoutMissingFields(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                              @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                              @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                              @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();

        paymentAuthFields.setAuthorizationCode(null);
        paymentAuthFields.setPaymentId(null);
        paymentAuthFields.setSuccess(true);

        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();

        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 1;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 1;
            }
            {
                paymentProcessingHandler.captureTransaction(anyString, anyString, (PaymentAuthFields) any);
                times = 0;
            }
        };

    }
    @Test
    void testPaymentProcessingPackageCheckout(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                              @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                              @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                              @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        RoomBillingDetails roomBillingDetails = podamFactoryImpl.manufacturePojo(RoomBillingDetails.class);
        List<RoomBillingDetails> roomBillingDetailsList = new ArrayList<>();
        roomBillingDetailsList.add(roomBillingDetails);
        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
            {
                billingTransformer.toRight((Collection<Billing>) any);
                result = roomBillingDetailsList;
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(5.0);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setAuthorizationCode("authCode");
        paymentAuthFields.setPaymentId("payId");
        paymentAuthFields.setAmount(100.0);
        paymentAuthFields.setSuccess(true);
        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(true), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 0;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 0;
            }
            {
                PaymentAuthFields c;
                String s;
                paymentProcessingHandler.captureTransaction(anyString, s = withCapture(), c = withCapture());
                times = 1;
                assertEquals(paymentAuthFields.getPaymentId(),c.getPaymentId());
                assertEquals(paymentAuthFields.getAuthorizationCode(),c.getAuthorizationCode());
                assertTrue(c.isSuccess());
                assertEquals("orderRef", s);
            }
        };

    }

    @Test
    void testPaymentFailurePackageCheckoutFailedAuth(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                     @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                     @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                     @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();

        paymentAuthFields.setAuthorizationCode("authCode");
        paymentAuthFields.setPaymentId("payId");
        paymentAuthFields.setSuccess(false);

        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();

        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(true), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 0;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 0;
            }
            {
                paymentProcessingHandler.captureTransaction(anyString, anyString, (PaymentAuthFields) any);
                times = 0;
            }
        };

    }

    @Test
    void testPaymentFailurePackageCheckoutMissingFields(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                        @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                        @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                        @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();

        paymentAuthFields.setAuthorizationCode(null);
        paymentAuthFields.setPaymentId(null);
        paymentAuthFields.setSuccess(true);

        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();

        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(true), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 0;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 0;
            }
            {
                paymentProcessingHandler.captureTransaction(anyString, anyString, (PaymentAuthFields) any);
                times = 0;
            }
        };

    }

    @Test
    void testPaymentProcessingPackageCheckout_Comp(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                                   @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                                   @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                                   @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        RoomBillingDetails roomBillingDetails = podamFactoryImpl.manufacturePojo(RoomBillingDetails.class);
        List<RoomBillingDetails> roomBillingDetailsList = new ArrayList<>();
        roomBillingDetailsList.add(roomBillingDetails);

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
            {
                billingTransformer.toRight((Collection<Billing>) any);
                result = roomBillingDetailsList;
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(0.0);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();

        paymentAuthFields.setAuthorizationCode("authCode");
        paymentAuthFields.setPaymentId("payId");
        paymentAuthFields.setSuccess(true);

        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();

        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = roomHandler.checkout(getCheckoutRequest(true), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                mergeConsumer.create((OrderLineItem) any);
                times = 0;
            }
            {
                orderAccess.mergeAndUpdate(anyString, Order.class, (Consumer<Order>) any);
                times = 0;
            }
            {
                paymentProcessingHandler.captureTransaction(anyString, anyString, (PaymentAuthFields) any);
                times = 0;
            }
        };

    }

    @Test
    void testCancelReservationSuccess(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                      @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer,
                                      @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                      @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer) throws AppException {

        CancelReservationRequest cancelReservationRequest = getTestInputCancelReservationRequest();
        PaymentSessionBaseFields paymentSessionBaseFields = getTestInputPaymentSessionBaseFields();


        CancelRoomReservationResponse cancelRoomReservationResponse = getMockCancelRoomReservationResponse();
        OrderConfirmationMapping orderConfirmationMapping = getMockConfirmationMapping();
        Order order = getMockOrder();

        PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setAuthorizationCode("authCode");
        paymentAuthFields.setPaymentId("payId");
        paymentAuthFields.setSuccess(true);

        new Expectations() {
            {
                roomBookingAccess.cancelRoomReservation(cancelReservationRequest.getCancelRoomReservationRequest());
                result = cancelRoomReservationResponse;
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber("M083E2635");
                result = orderConfirmationMapping;
            }
            {
                paymentProcessingHandler.refundTransaction(anyString,anyString,anyString,anyDouble,(List<Billing>) any,anyString, anyString);
                result = paymentAuthFields;
            }
            {
                orderAccess.read("1234");
                result = order;
            }
            {
                orderAccess.update(order);
                result = order;
            }
        };

        final CancelReservationResponse response = roomHandler.cancelReservation(cancelReservationRequest, paymentSessionBaseFields);

        new Verifications() {
            {
                roomBookingAccess.cancelRoomReservation((CancelRoomReservationV3Request) any);
                times = 2;
            }
            {
                roomBookingAccess.releaseRoomReservation(anyString, anyString, anyString, anyBoolean);
                times = 0;
            }
            {
                paymentProcessingHandler.refundTransaction(anyString,anyString,anyString,anyDouble,(List<Billing>) any,anyString, anyString);
                times = 1;
            }
        };

        assertNotNull(response);
        assertNull(response.getErrorPaymentSessionResponse());
        assertNull(response.getErrorRoomReservationResponse());
        assertNull(response.getErrorPaymentRefundResponse());
        assertNotNull(response.getRoomReservationResponse());
        assertEquals("M083E2635", response.getRoomReservationResponse().getConfirmationNumber());
        assertEquals("John", response.getRoomReservationResponse().getProfile().getFirstName());
        assertEquals("Doe", response.getRoomReservationResponse().getProfile().getLastName());
    }

    @Test
    void testCancelReservationForTCOLVSuccess(@Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                      @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer,
                                      @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                      @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer) throws AppException {

        CancelReservationRequest cancelReservationRequest = getTestInputCancelReservationRequest();
        PaymentSessionBaseFields paymentSessionBaseFields = getTestInputPaymentSessionBaseFieldsForTCOLV();

        CancelRoomReservationResponse cancelRoomReservationResponse = getMockCancelRoomReservationResponseForTCOLV();
        OrderConfirmationMapping orderConfirmationMapping = getMockConfirmationMapping();
        Order order = getMockOrder();

        new Expectations() {
            {
                roomBookingAccess.cancelRoomReservation(cancelReservationRequest.getCancelRoomReservationRequest());
                result = cancelRoomReservationResponse;
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber("M083E2635");
                result = orderConfirmationMapping;
            }
            {
                orderAccess.read("1234");
                result = order;
            }
            {
                orderAccess.update(order);
                result = order;
            }
        };

        final CancelReservationResponse response = roomHandler.cancelReservation(cancelReservationRequest, paymentSessionBaseFields);

        new Verifications() {
            {
                roomBookingAccess.cancelRoomReservation((CancelRoomReservationV3Request) any);
                times = 1;
            }
            {
                roomBookingAccess.releaseRoomReservation(anyString, anyString, anyString, anyBoolean);
                times = 0;
            }
            {
                paymentProcessingHandler.refundTransaction(anyString,anyString,anyString,anyDouble,(List<Billing>) any,anyString, anyString);
                times = 0;
            }
        };

        assertNotNull(response);
        assertNull(response.getErrorPaymentSessionResponse());
        assertNull(response.getErrorRoomReservationResponse());
        assertNull(response.getErrorPaymentRefundResponse());
        assertNotNull(response.getRoomReservationResponse());
        assertEquals("M083E2635", response.getRoomReservationResponse().getConfirmationNumber());
        assertEquals("John", response.getRoomReservationResponse().getProfile().getFirstName());
        assertEquals("Doe", response.getRoomReservationResponse().getProfile().getLastName());
        assertEquals("e5d3f1c9-833a-83f1-e053-d303fe0ad83c", response.getRoomReservationResponse().getPropertyId());
    }

    @Test
    void testCancelReservationRBSError(@Injectable IOrderAccess orderAccess,
                                      @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                      @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer,
                                      @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                      @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer) throws AppException {

        CancelReservationRequest cancelReservationRequest = getTestInputCancelReservationRequest();
        PaymentSessionBaseFields paymentSessionBaseFields = getTestInputPaymentSessionBaseFields();

        new Expectations() {
            {
                roomBookingAccess.cancelRoomReservation(cancelReservationRequest.getCancelRoomReservationRequest());
                result = new SourceAppException(3028, "632-2-242", "No reservation available for the input confirmation number.", reservationCancelBackendErrorResponse());
            }
        };

        final CancelReservationResponse cancelReservationResponse = roomHandler.cancelReservation(cancelReservationRequest, paymentSessionBaseFields);
        assertNotNull(cancelReservationResponse);
        assertNull(cancelReservationResponse.getRoomReservationResponse());
        assertNotNull(cancelReservationResponse.getErrorRoomReservationResponse());
        assertEquals("632-2-242", cancelReservationResponse.getErrorRoomReservationResponse().getError().getCode());
        assertEquals("No reservation available for the input confirmation number.", cancelReservationResponse.getErrorRoomReservationResponse().getError().getMessage());
    }

    @Test
    void testCancelReservationRefundError(@Injectable IOrderAccess orderAccess,
                                       @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                       @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer,
                                       @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                       @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer) throws AppException {

        CancelReservationRequest cancelReservationRequest = getTestInputCancelReservationRequest();
        PaymentSessionBaseFields paymentSessionBaseFields = getTestInputPaymentSessionBaseFields();

        CancelRoomReservationResponse cancelRoomReservationResponse = getMockCancelRoomReservationResponse();

        new Expectations() {
            {
                roomBookingAccess.cancelRoomReservation(cancelReservationRequest.getCancelRoomReservationRequest());
                result = cancelRoomReservationResponse;
            }
            {
                paymentProcessingHandler.refundTransaction(anyString,anyString,anyString, anyDouble, paymentSessionBaseFields.getBillings(), anyString, anyString);
                result = new SourceAppException(3035, "632-2-243", "Unable to refund payment.", reservationCancelRefundErrorResponse());
            }
        };

        final CancelReservationResponse cancelReservationResponse = roomHandler.cancelReservation(cancelReservationRequest, paymentSessionBaseFields);

        new Verifications() {
            {
                roomBookingAccess.releaseRoomReservation(anyString, anyString, anyString, anyBoolean);
                times = 1;
            }
            {
                roomBookingAccess.cancelRoomReservation((CancelRoomReservationV3Request) any);
                times = 1;
            }
        };

        assertNotNull(cancelReservationResponse);
        assertNull(cancelReservationResponse.getRoomReservationResponse());
        assertNull(cancelReservationResponse.getErrorRoomReservationResponse());
        assertNotNull(cancelReservationResponse.getErrorPaymentRefundResponse());
        assertEquals("632-2-243", cancelReservationResponse.getErrorPaymentRefundResponse().getErrorCode());
        assertEquals("Unable to refund payment.", cancelReservationResponse.getErrorPaymentRefundResponse().getErrorMessage());
    }

    @Test
    void testCheckoutWithTicketComponent(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> profileTransformer,
                                          @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> billingTransformer,
                                          @Injectable ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer,
                                          @Injectable CartHandler cart, @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer)
            throws AppException, HttpFailureException {

        new Expectations() {
            {
                roomBookingAccess.createRoomReservation((CreateRoomReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        RoomSelectionDetails roomSelectionDetails = podamFactoryImpl.manufacturePojo(RoomSelectionDetails.class);
        roomSelectionDetails.setSpecialRequests(null);
        ItemSelectionDetails itemSelectionDetails = new ItemSelectionDetails();
        itemSelectionDetails.setRoomSelectionDetails(roomSelectionDetails);
        cartLineItem.setItemSelectionDetails(itemSelectionDetails);

        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContentRbsPackageV2());
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        roomHandler.checkout(getCheckoutRequest(true), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
        new Verifications() {
            {
                CreateRoomReservationRequest request;
                roomBookingAccess.createRoomReservation(request = withCapture());
                assertNotNull(request);
                assertNotNull(request.getRoomReservation());
                assertNotNull(request.getRoomReservation().getSpecialRequests());
                assertNotNull(request.getRoomReservation().getSpecialRequests().get(0));
                assertNotNull(request.getRoomReservation().getPkgComponents());
                assertNotNull(request.getRoomReservation().getPkgComponents().get(0));
            }
        };
    }

    private String reservationCancelBackendErrorResponse() {
        return "{\r\n"
                + "  \"error\": {\r\n"
                + "     \"code\":\"632-2-242\",\r\n"
                + "     \"message\": \"No reservation available for the input confirmation number.\"\r\n"
                + "    }\r\n"
                + "}";
    }

    private String reservationCancelRefundErrorResponse() {
        return "{\r\n"
                + "     \"errorCode\":\"632-2-243\",\r\n"
                + "     \"paymentId\":\"123456789\",\r\n"
                + "     \"errorMessage\": \"Unable to refund payment.\"\r\n"
                + "}";
    }

    private static CancelReservationRequest getTestInputCancelReservationRequest() {
        CancelReservationRequest cancelReservationRequest = new CancelReservationRequest();
        cancelReservationRequest.setPaymentSessionId("123");
        cancelReservationRequest.setReservationType(ReservationType.ROOM);

        CancelRoomReservationV3Request cancelRoomReservationV3Request = new CancelRoomReservationV3Request();
        cancelRoomReservationV3Request.setOverrideDepositForfeit(false);
        cancelRoomReservationV3Request.setCancellationReason("Testing");
        cancelRoomReservationV3Request.setConfirmationNumber("M083E2635");
        cancelRoomReservationV3Request.setPropertyId("123");
        cancelRoomReservationV3Request.setFirstName("John");
        cancelRoomReservationV3Request.setLastName("Doe");
        cancelRoomReservationV3Request.setSkipPaymentProcess(true);
        cancelReservationRequest.setCancelRoomReservationRequest(cancelRoomReservationV3Request);
        return cancelReservationRequest;
    }

    private static Order getMockOrder() {
        Order order = new Order();
        List<LineItem> lineItems = new ArrayList<>();
        LineItem lineItem = new LineItem();
        lineItem.setStatus("SUCCESS");
        lineItem.setConfirmationNumber("M083E2635");
        lineItems.add(lineItem);
        order.setLineItems(lineItems);
        return order;
    }

    private static OrderConfirmationMapping getMockConfirmationMapping() {
        OrderConfirmationMapping orderConfirmationMapping = new OrderConfirmationMapping();
        orderConfirmationMapping.setId("1234");
        orderConfirmationMapping.setConfirmationNumber("M083E2635");
        return orderConfirmationMapping;
    }

    private static PaymentSessionBaseFields getTestInputPaymentSessionBaseFields() {
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        final Map<String, PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setPaymentId(null);
        paymentAuthFields.setAuthorizationCode(null);
        paymentAuthFields.setConfirmationNumber("M083E2635");
        paymentAuthFields.setAmount(100.0);
        paymentAuthFieldsMap.put("M083E2635", paymentAuthFields);
        paymentSessionBaseFields.setPaymentAuthFieldsMap(paymentAuthFieldsMap);

        List<Billing> billings = new ArrayList<>();
        Payment payment = new Payment();
        Billing billing = new Billing();
        billing.setPayment(payment);
        billings.add(billing);
        paymentSessionBaseFields.setBillings(billings);
        return paymentSessionBaseFields;
    }

    private static PaymentSessionBaseFields getTestInputPaymentSessionBaseFieldsForTCOLV() {
        PaymentSessionBaseFields paymentSessionBaseFields = getTestInputPaymentSessionBaseFields();
        paymentSessionBaseFields.getPaymentAuthFieldsMap().get("M083E2635").setPropertyId("e5d3f1c9-833a-83f1-e053-d303fe0ad83c");
        return paymentSessionBaseFields;
    }

    private CancelRoomReservationResponse getMockCancelRoomReservationResponse() {
        CancelRoomReservationResponse cancelRoomReservationResponse = new CancelRoomReservationResponse();
        ReservationProfile reservationProfile = new ReservationProfile();
        reservationProfile.setFirstName("John");
        reservationProfile.setLastName("Doe");
        cancelRoomReservationResponse.setRoomReservation(new RoomReservationResponse());
        cancelRoomReservationResponse.getRoomReservation().setConfirmationNumber("M083E2635");
        cancelRoomReservationResponse.getRoomReservation().setProfile(reservationProfile);
        cancelRoomReservationResponse.getRoomReservation().setDepositForfeit(false);
        Deposit deposit = new Deposit();
        deposit.setRefundAmount(BigDecimal.valueOf(100.0));
        cancelRoomReservationResponse.getRoomReservation().setDepositDetails(deposit);
        List<RoomBillingDetailsResponse> list = new ArrayList<>();
        list.add(podamFactoryImpl.manufacturePojo(RoomBillingDetailsResponse.class));
        cancelRoomReservationResponse.getRoomReservation().setBilling(list);
        return cancelRoomReservationResponse;
    }

    private CancelRoomReservationResponse getMockCancelRoomReservationResponseForTCOLV() {
        CancelRoomReservationResponse cancelRoomReservationResponse = getMockCancelRoomReservationResponse();
        cancelRoomReservationResponse.getRoomReservation().setPropertyId("e5d3f1c9-833a-83f1-e053-d303fe0ad83c");
        return cancelRoomReservationResponse;
    }

    private Billing getBillings() {
        Payment payment = new Payment();
        payment.setAmount(1000.00);
        payment.setCardHolder("first last");
        payment.setCcToken("21G1110A001DAXFHY79D9XCE11L1");
        payment.setCvv("123");
        payment.setMaskedNumber("XXXXXXXXXXXX1111");

        BillingAddress addressReq = new BillingAddress();
        addressReq.setCity("city");
        addressReq.setPostalCode("postalcode");

        Billing billingReq = new Billing();
        billingReq.setAddress(addressReq);
        billingReq.setPayment(payment);
        return billingReq;
    }

    private CheckoutRequest getCheckoutRequest(boolean isPackage) {
        Payment payment = new Payment();
        payment.setAmount(1000.00);
        payment.setCardHolder("first last");
        payment.setCcToken("21G1110A001DAXFHY79D9XCE11L1");
        payment.setCvv("123");
        payment.setMaskedNumber("XXXXXXXXXXXX1111");

        BillingAddress addressReq = new BillingAddress();
        addressReq.setCity("city");
        addressReq.setPostalCode("postalcode");

        Billing billingReq = new Billing();
        billingReq.setAddress(addressReq);
        billingReq.setPayment(payment);
        final List<Billing> creditcards = new ArrayList<>();
        creditcards.add(billingReq);

        GuestProfile guestProfile = new GuestProfile();
        guestProfile.setId("guestprofileid");
        guestProfile.setFirstName("first");
        guestProfile.setLastName("last");

        CheckoutRequest request = new CheckoutRequest();
        request.setCartId("id");
        request.setBillings(creditcards);
        request.setGuestProfile(guestProfile);
        request.setEnableJwb(true);
        request.setItineraryId("itineraryid");
        request.setCartType(isPackage ? Type.PACKAGE : Type.GLOBAL);

        // Request Object Set End
        return request;
    }

    private ErrorResponse invalidCCTokenResponse() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("632-2-160");
        error.setMessage("<_antifraud_error>[ Anti-fraud service returned unable to process the transaction ]");
        responseError.setError(error);
        return responseError;
    }

    private ErrorResponse invalidCVSTokenResponse() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("632-2-242");
        error.setMessage("<InvalidCreditCard>[Charge credit card is invalid]");
        responseError.setError(error);
        return responseError;
    }
    
    private ErrorResponse invalidBillingPostalCodeResponse() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("632-1-279");
        error.setMessage("Billing postal Code is invalid or missing");
        responseError.setError(error);
        return responseError;
    }

    private ErrorResponse paymentFailureTokenResponse() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("632-2-243");
        error.setMessage("<_payment_authorization_failed>[ Payment authorization failed ]");
        responseError.setError(error);
        return responseError;
    }

    final String getHoldContentRbsPackageV2() {
        return "{\n" +
                "    \"propertyId\": \"44e610ab-c209-4232-8bb4-51f7b9b13a75\",\n" +
                "    \"roomTypeId\": \"ROOMCD-v-SPEA-d-PROP-v-MV190\",\n" +
                "    \"programId\": \"RPCD-v-TAAAB-d-PROP-v-MV190\",\n" +
                "    \"customerId\": 0,\n" +
                "    \"isGroupCode\": null,\n" +
                "    \"perpetualPricing\": false,\n" +
                "    \"specialRequests\": [\"PARKTIX1\"],\n" +
                "    \"pkgComponents\": [{\"id\": \"PARKTIX1\"}],\n" +
                "    \"f1Package\": false,\n" +
                "    \"promo\": null,\n" +
                "    \"pricingMode\": \"PROGRAM\",\n" +
                "    \"guaranteeCode\": null,\n" +
                "    \"shoppedItineraryId\": null,\n" +
                "    \"id\": null,\n" +
                "    \"confirmationNumber\": null,\n" +
                "    \"profile\": null,\n" +
                "    \"tripDetails\": {\n" +
                "        \"checkInDate\": \"2025-06-13\",\n" +
                "        \"checkOutDate\": \"2025-06-14\",\n" +
                "        \"numAdults\": 1,\n" +
                "        \"numChildren\": 0,\n" +
                "        \"numRooms\": 1\n" +
                "    },\n" +
                "    \"bookings\": [{\n" +
                "        \"date\": \"2025-06-13\",\n" +
                "        \"basePrice\": 135.00,\n" +
                "        \"customerPrice\": 0.00,\n" +
                "        \"price\": 135.00,\n" +
                "        \"isDiscounted\": false,\n" +
                "        \"programIdIsRateTable\": false,\n" +
                "        \"overridePrice\": -1.00,\n" +
                "        \"overrideProgramIdIsRateTable\": false,\n" +
                "        \"isComp\": false,\n" +
                "        \"resortFeeIsSpecified\": false,\n" +
                "        \"resortFee\": 0.00,\n" +
                "        \"programId\": \"RPCD-v-TAAAB-d-PROP-v-MV190\",\n" +
                "        \"pricingRuleId\": null,\n" +
                "        \"overrideProgramId\": null,\n" +
                "        \"overridePricingRuleId\": null\n" +
                "    }],\n" +
                "    \"chargesAndTaxes\": {\n" +
                "        \"charges\": [{\n" +
                "            \"date\": \"2025-06-13\",\n" +
                "            \"amount\": 180.00,\n" +
                "            \"itemized\": [{\n" +
                "                \"itemType\": \"RoomCharge\",\n" +
                "                \"amount\": 135.00,\n" +
                "                \"item\": null\n" +
                "            }, {\n" +
                "                \"itemType\": \"ResortFee\",\n" +
                "                \"amount\": 45.00,\n" +
                "                \"item\": \"RSFEE\"\n" +
                "            }]\n" +
                "        }],\n" +
                "        \"taxesAndFees\": [{\n" +
                "            \"date\": \"2025-06-13\",\n" +
                "            \"amount\": 24.08,\n" +
                "            \"itemized\": [{\n" +
                "                \"itemType\": \"RoomChargeTax\",\n" +
                "                \"amount\": 18.06,\n" +
                "                \"item\": \"OCTAX\"\n" +
                "            }, {\n" +
                "                \"itemType\": \"ResortFeeTax\",\n" +
                "                \"amount\": 6.02,\n" +
                "                \"item\": \"RSFTX\"\n" +
                "            }]\n" +
                "        }]\n" +
                "    },\n" +
                "    \"availableComponents\": [{\n" +
                "        \"id\": \"COMPONENTCD-v-DOGFEE-d-TYP-v-COMPONENT-d-PROP-v-MV190-d-NRPCD-v-DOGFRIENDLY\",\n" +
                "        \"code\": \"DOGFEE\",\n" +
                "        \"shortDescription\": null,\n" +
                "        \"longDescription\": null,\n" +
                "        \"active\": true,\n" +
                "        \"nonEditable\": false,\n" +
                "        \"depositAmount\": 113.38,\n" +
                "        \"pricingApplied\": \"NIGHTLY\",\n" +
                "        \"tripPrice\": 100.00,\n" +
                "        \"tripTax\": 13.38,\n" +
                "        \"price\": 100.00,\n" +
                "        \"prices\": [{\n" +
                "            \"date\": \"2025-06-13\",\n" +
                "            \"amount\": 100.00,\n" +
                "            \"tax\": 13.38\n" +
                "        }]\n" +
                "    }, {\n" +
                "        \"id\": \"COMPONENTCD-v-DOGFEE-d-TYP-v-COMPONENT-d-PROP-v-MV190-d-NRPCD-v-DOGFRIENDLYSTE\",\n" +
                "        \"code\": \"DOGFEE\",\n" +
                "        \"shortDescription\": null,\n" +
                "        \"longDescription\": null,\n" +
                "        \"active\": true,\n" +
                "        \"nonEditable\": false,\n" +
                "        \"depositAmount\": 226.76,\n" +
                "        \"pricingApplied\": \"NIGHTLY\",\n" +
                "        \"tripPrice\": 200.00,\n" +
                "        \"tripTax\": 26.76,\n" +
                "        \"price\": 200.00,\n" +
                "        \"prices\": [{\n" +
                "            \"date\": \"2025-06-13\",\n" +
                "            \"amount\": 200.00,\n" +
                "            \"tax\": 26.76\n" +
                "        }]\n" +
                "    }, {\n" +
                "        \"id\": \"COMPONENTCD-v-EARLYCI-d-TYP-v-COMPONENT-d-PROP-v-MV190-d-NRPCD-v-EARLYCKIN\",\n" +
                "        \"code\": \"EARLYCI\",\n" +
                "        \"shortDescription\": null,\n" +
                "        \"longDescription\": null,\n" +
                "        \"active\": true,\n" +
                "        \"nonEditable\": false,\n" +
                "        \"depositAmount\": 56.69,\n" +
                "        \"pricingApplied\": \"CHECKIN\",\n" +
                "        \"tripPrice\": 50.00,\n" +
                "        \"tripTax\": 6.69,\n" +
                "        \"price\": 50.00,\n" +
                "        \"prices\": [{\n" +
                "            \"date\": \"2025-06-13\",\n" +
                "            \"amount\": 50.00,\n" +
                "            \"tax\": 6.69\n" +
                "        }]\n" +
                "    }, {\n" +
                "        \"id\": \"COMPONENTCD-v-LATECO-d-TYP-v-COMPONENT-d-PROP-v-MV190-d-NRPCD-v-LATECKOUT\",\n" +
                "        \"code\": \"LATECO\",\n" +
                "        \"shortDescription\": null,\n" +
                "        \"longDescription\": null,\n" +
                "        \"active\": true,\n" +
                "        \"nonEditable\": false,\n" +
                "        \"depositAmount\": 68.03,\n" +
                "        \"pricingApplied\": \"CHECKOUT\",\n" +
                "        \"tripPrice\": 60.00,\n" +
                "        \"tripTax\": 8.03,\n" +
                "        \"price\": 60.00,\n" +
                "        \"prices\": [{\n" +
                "            \"date\": \"2025-06-13\",\n" +
                "            \"amount\": 60.00,\n" +
                "            \"tax\": 8.03\n" +
                "        }]\n" +
                "    }, {\n" +
                "        \"id\": \"COMPONENTCD-v-MYVEGASFEE-d-TYP-v-COMPONENT-d-PROP-v-MV190-d-NRPCD-v-MYVEGASFEE\",\n" +
                "        \"code\": \"MYVEGASFEE\",\n" +
                "        \"shortDescription\": null,\n" +
                "        \"longDescription\": null,\n" +
                "        \"active\": true,\n" +
                "        \"nonEditable\": false,\n" +
                "        \"depositAmount\": 0.00,\n" +
                "        \"pricingApplied\": \"CHECKIN\",\n" +
                "        \"tripPrice\": 0.00,\n" +
                "        \"tripTax\": 0.00,\n" +
                "        \"price\": 0.00,\n" +
                "        \"prices\": [{\n" +
                "            \"date\": \"2025-06-13\",\n" +
                "            \"amount\": 0.00,\n" +
                "            \"tax\": 0.00\n" +
                "        }]\n" +
                "    }]\n" +
                "}";

    }

    final String getHoldContent() {
        return "{\"propertyId\":\"dc00e77f-d6bb-4dd7-a8ea-dc33ee9675ad\","
                + "\"roomTypeId\":\"74559601-6fbb-4444-ab12-04f5722116bd\",\"programId\":\"f115f057-0b8e-4a7c-a183-e0e37a8441c6\","
                + "\"customerId\":\"0\",\"guaranteeCode\":null,\"perpetualPricing\":false,\"promoCode\":null,"
                + "\"customerDominantPlay\":null,\"customerRank\":0,\"itineraryId\":null,\"profile\":null,"
                + "\"tripDetails\":{\"checkInDate\":\"2021-04-09\",\"checkOutDate\":\"2021-04-10\",\"numAdults\":1,"
                + "\"numChildren\":0,\"numRooms\":1},\"chargesAndTaxes\":{\"charges\":[{\"date\":\"2021-04-09\",\"amount\":182.99,"
                + "\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":145.99},{\"itemType\":\"ResortFee\",\"amount\":37}]}],"
                + "\"taxesAndFees\":[{\"date\":\"2021-04-09\",\"amount\":24.48,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":19.53},"
                + "{\"itemType\":\"ResortFeeTax\",\"amount\":4.95}]}]},\"availableComponents\":[{\"id\":\"PREVL\",\"code\":\"1SPARK\","
                + "\"shortDescription\":\"Self Parking\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-04-09\","
                + "\"amount\":10,\"tax\":2.18}]},{\"id\":\"PREVL\",\"code\":\"2SPARK\",\"shortDescription\":\"2 Self Parking\",\"active\":true,"
                + "\"pricingApplied\":\"NIGHTLY\",\"nonEditable\":false,\"prices\":[{\"date\":\"2021-04-09\",\"amount\":20,\"tax\":4.36}]},{\"id\":\"PREVL\","
                + "\"code\":\"3SPARK\",\"shortDescription\":\"3 Self Parking\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\","
                + "\"prices\":[{\"date\":\"2021-04-09\",\"amount\":30,\"tax\":6.52}]},{\"id\":\"PREVL\",\"code\":\"4SPARK\","
                + "\"shortDescription\":\"4 Self Parking\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-04-09\","
                + "\"amount\":40,\"tax\":8.7}]},{\"id\":\"PREVL\",\"code\":\"BUFDAY\",\"shortDescription\":\"Buffet Breakfast & Lunch \","
                + "\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-04-09\",\"amount\":50,\"tax\":4.19}]},"
                + "{\"id\":\"PREVL\",\"code\":\"DOGFR\",\"shortDescription\":\"Dog Friendly \",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\","
                + "\"prices\":[{\"date\":\"2021-04-09\",\"amount\":50,\"tax\":10.88}]},{\"id\":\"PREVL\",\"code\":\"DOGFRS\","
                + "\"shortDescription\":\"Dog Friendly Suite\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-04-09\","
                + "\"amount\":100,\"tax\":21.76}]},{\"id\":\"PREVL\",\"code\":\"EARLYCI\",\"shortDescription\":\"Early Check-In\",\"active\":true,"
                + "\"pricingApplied\":\"CHECKOUT\",\"nonEditable\":false,\"prices\":[{\"date\":\"2021-04-09\",\"amount\":25,\"tax\":10.88},{\"date\":\"2021-04-10\","
                + "\"amount\":25,\"tax\":0}]},{\"id\":\"PREVL\",\"code\":\"LATECO\",\"shortDescription\":\"Late Check-Out\",\"active\":true,"
                + "\"pricingApplied\":\"NIGHTLY\",\"nonEditable\":false,\"prices\":[{\"date\":\"2021-04-09\",\"amount\":25,\"tax\":5.44}]},{\"id\":\"PREVL\","
                + "\"code\":\"LCO3PM\",\"shortDescription\":\"Late Check-Out - 3pm\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\","
                + "\"prices\":[{\"date\":\"2021-04-09\",\"amount\":75,\"tax\":16.32}]},{\"id\":\"PREVL\",\"code\":\"LCO6PM\","
                + "\"shortDescription\":\"Late Check-Out - 6pm\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-04-09\","
                + "\"amount\":125,\"tax\":27.2}]},{\"id\":\"PREVL\",\"code\":\"LPARK\",\"shortDescription\":\"Oversized Parking \",\"active\":true,"
                + "\"pricingApplied\":\"NIGHTLY\",\"nonEditable\":false,\"prices\":[{\"date\":\"2021-04-09\",\"amount\":20,\"tax\":4.36}]},{\"id\":\"PREVL\","
                + "\"code\":\"RSFEE\",\"shortDescription\":\"RSFEE\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-04-09\","
                + "\"amount\":37,\"tax\":0}]},{\"id\":\"PREVL\",\"code\":\"TAXHOLDR\",\"shortDescription\":\"Dummy Code to hold Tax\","
                + "\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-04-09\",\"amount\":1,\"tax\":0.08}]},"
                + "{\"id\":\"PREVL\",\"code\":\"VIPLNGE\",\"shortDescription\":\"VIP Lounge Access\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\","
                + "\"prices\":[{\"date\":\"2021-04-09\",\"amount\":30,\"tax\":6.52}]},{\"id\":\"PREVL\",\"code\":\"XLPARK\","
                + "\"shortDescription\":\"XL Oversized Parking\",\"active\":true,\"nonEditable\":false,\"pricingApplied\":\"NIGHTLY\",\"prices\":[{\"date\":\"2021-04-09\","
                + "\"amount\":30,\"tax\":6.52}]}],\"bookings\":[{\"date\":\"2021-04-09\",\"basePrice\":0,\"customerPrice\":0,"
                + "\"price\":145.99,\"programIdIsRateTable\":false,\"overridePrice\":0,\"overrideProgramIdIsRateTable\":false,"
                + "\"resortFeeIsSpecified\":false,\"resortFee\":0,\"programId\":\"f115f057-0b8e-4a7c-a183-e0e37a8441c6\",\"isComp\":false}],"
                + "\"ratesSummary\":{\"roomSubtotal\":0,\"programDiscount\":-145.99,\"discountedSubtotal\":145.99,\"roomRequestsTotal\":0,"
                + "\"adjustedRoomSubtotal\":145.99,\"tripSubtotal\":145.99,\"casinoSurcharge\":0.00,\"casinoSurchargeAndTax\":0.00,\"resortFee\":0,\"resortFeeAndTax\":41.95,\"roomChargeTax\":19.53,\"occupancyFee\":0,"
                + "\"tourismFee\":0,\"tourismFeeAndTax\":0,\"reservationTotal\":207.47,\"depositDue\":207.47,\"balanceUponCheckIn\":0},"
                + "\"depositDetails\":{\"dueDate\":\"2021-03-19\",\"amount\":207.47,\"forfeitDate\":\"2021-04-05\",\"forfeitAmount\":207.47,"
                + "\"overrideAmount\":-1},\"amountDue\":0,\"depositPolicy\":{\"depositRequired\":true,\"creditCardRequired\":false},"
                + "\"markets\":null,\"id\":null,\"confirmationNumber\":null,\"groupCode\":false}";

    }

    final String getBorgataHoldContent() {
        return "{\"propertyId\":\"773000cc-468a-4d86-a38f-7ae78ecfa6aa\",\"roomTypeId\":\"e9dfe214-438b-41e7-a1ef-909bfaa0ae14\",\"programId\":\"49ae61c6-2a92-4b8d-8fd2-c697f4af7b77\","
                + "\"customerId\":\"0\",\"perpetualPricing\":false,\"guaranteeCode\":\"CC\",\"tripDetails\":{\"checkInDate\":\"2022-03-31\",\"checkOutDate\":\"2022-04-01\","
                + "\"numAdults\":2,\"numChildren\":0,\"numRooms\":1},\"bookings\":[{\"date\":\"2022-03-31\",\"basePrice\":150.00,\"customerPrice\":0.00,\"price\":97.50,\"isDiscounted\":true,"
                + "\"programIdIsRateTable\":false,\"overridePrice\":-1.00,\"overrideProgramIdIsRateTable\":false,\"isComp\":false,\"resortFeeIsSpecified\":false,\"resortFee\":15.00,"
                + "\"programId\":\"49ae61c6-2a92-4b8d-8fd2-c697f4af7b77\",\"pricingRuleId\":\"dad10d3a-98ce-46c9-ab64-4cc41ceafee9\"}],"
                + "\"chargesAndTaxes\":{\"charges\":[{\"date\":\"2022-03-31\",\"amount\":219.50,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":97.50,"
                + "\"item\":\"Room Charge\"},{\"itemType\":\"ExtraGuestCharge\",\"amount\":0.00,\"item\":\"Extra Guest Charge\"},{\"itemType\":\"ResortFee\","
                + "\"amount\":15.00,\"item\":\"Resort Fee\"},{\"amount\":3.00,\"item\":\"OCC\"},{\"amount\":2.00,\"item\":\"TOR\"},{\"amount\":2.00,\"item\":\"COS\"}]}],"
                + "\"taxesAndFees\":[{\"date\":\"2022-03-31\",\"amount\":25.87,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":13.28,\"item\":\"Room Tax\"},"
                + "{\"itemType\":\"ExtraGuestChargeTax\",\"amount\":0.00,\"item\":\"Extra Guest Tax\"},{\"itemType\":\"ResortFeeTax\",\"amount\":2.04,\"item\":\"Resort Fee Tax\"},"
                + "{\"amount\":0.00,\"item\":\"OCC\"},{\"amount\":0.27,\"item\":\"TOR\"},{\"amount\":0.27,\"item\":\"COS\"}]}]},\"availableComponents\":"
                + "[{\"id\":\"52ecdfd3-b4e8-46b8-864a-ae0fed659e90\",\"code\":\"CR\",\"shortDescription\":\"Crib\",\"longDescription\":\"Crib\",\"active\":true,"
                + "\"depositAmount\":0.00,\"nonEditable\":false,\"pricingApplied\":\"CHECKIN\",\"tripPrice\":0.00,\"tripTax\":0.00,\"price\":0.00,\"prices\":[{\"date\":\"2022-03-31\","
                + "\"amount\":0.00,\"tax\":0.00}]},{\"id\":\"c11e846b-b6a5-4a1d-9b6f-686d58701ea7\",\"code\":\"PET\",\"shortDescription\":\"Dog Friendly Room\","
                + "\"longDescription\":\"Dog Friendly Room updated\",\"active\":true,\"nonEditable\":false,\"depositAmount\":0.00,\"pricingApplied\":\"NIGHTLY\",\"tripPrice\":100.00,"
                + "\"tripTax\":10.00,\"price\":100.00,\"prices\":[{\"date\":\"2022-03-31\",\"amount\":100.00,\"tax\":10.00}]},{\"id\":\"37392304-6afd-45f2-af25-d55dec8116d7\","
                + "\"code\":\"RWN\",\"shortDescription\":\"Rollaway No Charge\",\"longDescription\":\"Rollaway No Charge\",\"active\":true,\"nonEditable\":false,\"depositAmount\":0.00,"
                + "\"pricingApplied\":\"CHECKIN\",\"tripPrice\":0.00,\"tripTax\":0.00,\"price\":0.00,\"prices\":[{\"date\":\"2022-03-31\",\"amount\":0.00,\"tax\":0.00}]},"
                + "{\"id\":\"eab011ff-0c0e-45c4-ba77-eba0375864f9\",\"code\":\"SA\",\"shortDescription\":\"Service Animal\",\"longDescription\":\"Service Animal\",\"active\":true,"
                + "\"depositAmount\":0.00,\"nonEditable\":false,\"pricingApplied\":\"CHECKIN\",\"tripPrice\":0.00,\"tripTax\":0.00,\"price\":0.00,\"prices\":[{\"date\":\"2022-03-31\","
                + "\"amount\":0.00,\"tax\":0.00}]}],\"ratesSummary\":{\"roomSubtotal\":150.0000,\"programDiscount\":52.5000,\"discountedSubtotal\":97.5000,"
                + "\"discountedAveragePrice\":97.5000,\"roomRequestsTotal\":0.0000,\"adjustedRoomSubtotal\":97.5000,\"resortFee\":15.0000,\"resortFeePerNight\":15.0000,"
                + "\"tripSubtotal\":112.5000,\"resortFeeAndTax\":17.0400,\"roomChargeTax\":13.2800,\"occupancyFee\":3.0000,\"tourismFee\":2.0000,\"tourismFeeAndTax\":2.2700,\"casinoSurcharge\":2.00,"
                + "\"casinoSurchargeAndTax\":2.27,\"reservationTotal\":135.3600,\"depositDue\":110.7800,\"balanceUponCheckIn\":24.5800},\"depositDetails\":{\"dueDate\":\"2022-03-01\",\"amountPolicy\":\"Nights\","
                + "\"amount\":110.78,\"forfeitDate\":\"2022-03-31\",\"forfeitAmount\":0.00,\"overrideAmount\":-1.00,\"depositRuleCode\":\"1NT\",\"cancellationRuleCode\":\"NOCXL\","
                + "\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":97.50,\"item\":\"Room Charge\"},{\"itemType\":\"RoomChargeTax\",\"amount\":13.28,\"item\":\"Room Tax\"}]},"
                + "\"depositPolicy\":{\"depositRequired\":true,\"creditCardRequired\":true},\"markets\":[{\"date\":\"2022-03-31\",\"marketCode\":\"TFIT\",\"sourceCode\":\"TFITIC\"}]}";

    }

    final String getReservationResp() {
        return "{\"roomReservation\":{\"id\":\"d2701aab-b272-4551-aee1-b5d80c9d1ed7\",\"customerId\":848203481346,\"propertyId\":\"66964e2b-2550-4476-84c3-1a4c0c5c067f\","
                + "\"itineraryId\":\"9328925186\",\"roomTypeId\":\"5873c8fe-d110-4628-a155-f2adbdf842d9\",\"programId\":\"14caa425-8ed7-4530-bb48-d7068d3e367e\","
                + "\"state\":\"Booked\",\"nrgStatus\":false,\"specialRequests\":[\"f40dd38d-f4f0-4dbf-bd83-bbcd09fa351b\",\"8e937458-c37f-42ba-974c-76faa8d91c37\"],"
                + "\"thirdParty\":false,\"confirmationNumber\":\"M04DA2F42\",\"bookDate\":\"2021-07-20\",\"profile\":{\"id\":848203481346,\"mlifeNo\":0,"
                + "\"firstName\":\"Mekhi\",\"lastName\":\"Weimann\",\"addresses\":[{\"type\":\"Home\",\"preferred\":false,\"street1\":\"123 Main St\","
                + "\"street2\":null,\"city\":\"Las Vegas\",\"state\":\"NV\",\"country\":\"US\",\"postalCode\":\"89118\"}]},\"billing\":[{\"payment\":"
                + "{\"cardHolder\":\"Mekhi Weimann\",\"firstName\":\"Mekhi\",\"lastName\":\"Weimann\","
                + "\"ccToken\":\"FDNKICfu7r5lXM4Ti9QlWuKJBz4WjIcjRgNom9tkiAW53phsKZAyxhuWXA3KB2C6vI5oWd54+tgbVB7vhHwVkQ==\","
                + "\"encryptedccToken\":\"FDNKICfu7r5lXM4Ti9QlWuKJBz4WjIcjRgNom9tkiAW53phsKZAyxhuWXA3KB2C6vI5oWd54+tgbVB7vhHwVkQ==\","
                + "\"maskedNumber\":\"XXXXXXXXXXXXXXXXXXXXXXXX1111\",\"amount\":170.06,\"expiry\":\"10/2022\",\"type\":\"Visa\"},"
                + "\"address\":{\"street1\":\"234 W Main St\",\"street2\":null,\"city\":\"Las Vegas\",\"state\":\"NV\",\"postalCode\":\"89129\",\"country\":\"US\"}}],"
                + "\"tripDetails\":{\"checkInDate\":\"2021-08-09\",\"checkOutDate\":\"2021-08-10\",\"numAdults\":1,\"numChildren\":1,\"numRooms\":1},"
                + "\"markets\":[{\"date\":\"2021-08-09\",\"marketCode\":\"TFIT\",\"sourceCode\":\"CSWEBRES\"}],\"bookings\":[{\"date\":\"2021-08-09\","
                + "\"basePrice\":149.99,\"customerPrice\":0.00,\"price\":149.99,\"programIdIsRateTable\":false,\"overridePrice\":-1.00,"
                + "\"overrideProgramIdIsRateTable\":false,\"resortFeeIsSpecified\":false,\"resortFee\":39.00,\"programId\":\"14caa425-8ed7-4530-bb48-d7068d3e367e\","
                + "\"pricingRuleId\":\"78cb9aaa-9a3c-4638-92da-10507440c4ce\",\"isComp\":false}],\"chargesAndTaxes\":{\"charges\":[{\"date\":\"2021-08-09\","
                + "\"amount\":278.99,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":149.99,\"item\":\"Room Charge\"},{\"itemType\":\"ExtraGuestCharge\","
                + "\"amount\":0.00,\"item\":\"Extra Guest Charge\"},{\"itemType\":\"ResortFee\",\"amount\":39.00,\"item\":\"Resort Fee\"},{\"itemType\":\"ComponentCharge\","
                + "\"amount\":40.00,\"item\":\"PKCI30\"},{\"itemType\":\"ComponentCharge\",\"amount\":50.00,\"item\":\"PKCKOUT\"}]}],"
                + "\"taxesAndFees\":[{\"date\":\"2021-08-09\",\"amount\":37.33,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":20.07,\"item\":\"Room Tax\"},"
                + "{\"itemType\":\"ExtraGuestChargeTax\",\"amount\":0.00,\"item\":\"Extra Guest Tax\"},{\"itemType\":\"ResortFeeTax\",\"amount\":5.22,"
                + "\"item\":\"Resort Fee Tax\"},{\"itemType\":\"ComponentChargeTax\",\"amount\":5.35,\"item\":\"PKCI30\"},{\"itemType\":\"ComponentChargeTax\","
                + "\"amount\":6.69,\"item\":\"PKCKOUT\"}]}]},\"ratesSummary\":{\"roomSubtotal\":149.99,\"programDiscount\":0.00,\"discountedSubtotal\":149.99,"
                + "\"roomRequestsTotal\":90.00,\"tripSubtotal\":188.99,\"casinoSurcharge\":0.00,\"casinoSurchargeAndTax\":0.00,\"adjustedRoomSubtotal\":239.99,\"resortFee\":39.00,\"resortFeePerNight\":39.00,\"resortFeeAndTax\":44.22,"
                + "\"roomChargeTax\":32.11,\"occupancyFee\":0.00,\"tourismFee\":0.00,\"tourismFeeAndTax\":0.00,\"reservationTotal\":316.32,\"depositDue\":170.06,"
                + "\"balanceUponCheckIn\":146.26},\"depositDetails\":{\"dueDate\":\"2021-07-20\",\"amountPolicy\":\"Nights\",\"amount\":170.06,\"forfeitDate\":\"2021-08-07\","
                + "\"forfeitAmount\":170.06,\"overrideAmount\":-1.00,\"depositRuleCode\":\"1NT\",\"cancellationRuleCode\":\"48H\",\"itemized\":[{\"itemType\":\"RoomCharge\","
                + "\"amount\":149.99,\"item\":\"Room Charge\"},{\"itemType\":\"RoomChargeTax\",\"amount\":20.07,\"item\":\"Room Tax\"}]},"
                + "\"depositPolicy\":{\"depositRequired\":true,\"creditCardRequired\":true},\"payments\":[{\"reservationInstance\":1,\"chargeAmount\":170.06,"
                + "\"chargeCardExpiry\":\"2022-10-01\",\"status\":\"Settled\",\"chargeCardHolder\":\"Mekhi Weimann\",\"chargeCurrencyCode\":\"USD\","
                + "\"chargeCardType\":\"Visa\",\"chargeCardMaskedNumber\":\"XXXXXXXXXXXXXXXXXXXXXXXX1111\","
                + "\"chargeCardNumber\":\"FDNKICfu7r5lXM4Ti9QlWuKJBz4WjIcjRgNom9tkiAW53phsKZAyxhuWXA3KB2C6vI5oWd54+tgbVB7vhHwVkQ==\",\"fxChecked\":true,"
                + "\"fxEligible\":false,\"fxTransDate\":\"2021-07-20\",\"fxAmount\":0.00,\"fxRate\":0.00,\"fxSettleAmount\":170.06,\"fxCurrencyCode\":\"USD\","
                + "\"fxAcceptMessage\":\"Approved\",\"fxAuthApprovalCode\":\"019780\",\"isExternal\":false,\"isDeposit\":true}],\"amountDue\":170.06,"
                + "\"guaranteeCode\":\"CC\",\"customerRank\":0,\"customerSegment\":0,\"perpetualPricing\":false,\"bookingSource\":\"mgmri\",\"bookingChannel\":\"web\","
                + "\"isGroupCode\":false}}";
    }

    final String getReservationRespTCOLV() {
        return "{\"roomReservation\":{\"amountDue\":146.26,\"billing\":[{\"address\":{\"city\":\"***\",\"country\":\"***\",\"postalCode\":\"***\",\"state\":\"***\",\"street1\":\"***\",\"street2\":\"***\"},\"payment\":{\"amount\":146.26,\"cardHolder\":\"***\",\"ccToken\":\"***\",\"encryptedccToken\":\"***\",\"expiry\":\"***\",\"firstName\":\"***\",\"lastName\":\"***\",\"maskedNumber\":\"XXXXXXXXXXXXXXXXXXXXXXXX4444\",\"type\":\"Mastercard\"}}],\"bookDate\":\"2024-06-28\",\"bookingChannel\":\"web\",\"bookings\":[{\"basePrice\":170.0,\"customerPrice\":0.0,\"date\":\"2024-06-30\",\"isComp\":false,\"isDiscounted\":true,\"overridePrice\":-1.0,\"overrideProgramIdIsRateTable\":false,\"price\":129.0,\"pricingRuleId\":\"07029d61-b161-11ee-927f-0620876a3799\",\"programId\":\"209713b8-b979-4e98-ab23-e57cf984fddf\",\"programIdIsRateTable\":false,\"resortFee\":50.0,\"resortFeeIsSpecified\":false},{\"basePrice\":140.0,\"customerPrice\":0.0,\"date\":\"2024-07-01\",\"isComp\":false,\"isDiscounted\":true,\"overridePrice\":-1.0,\"overrideProgramIdIsRateTable\":false,\"price\":106.0,\"pricingRuleId\":\"07029d61-b161-11ee-927f-0620876a3799\",\"programId\":\"209713b8-b979-4e98-ab23-e57cf984fddf\",\"programIdIsRateTable\":false,\"resortFee\":50.0,\"resortFeeIsSpecified\":false}],\"bookingSource\":\"mgmri\",\"chargesAndTaxes\":{\"charges\":[{\"amount\":179.0,\"date\":\"2024-06-30\",\"itemized\":[{\"amount\":129.0,\"item\":\"Room Charge\",\"itemType\":\"RoomCharge\"},{\"amount\":0.0,\"item\":\"Extra Guest Charge\",\"itemType\":\"ExtraGuestCharge\"},{\"amount\":50.0,\"item\":\"Resort Fee\",\"itemType\":\"ResortFee\"}]},{\"amount\":156.0,\"date\":\"2024-07-01\",\"itemized\":[{\"amount\":106.0,\"item\":\"Room Charge\",\"itemType\":\"RoomCharge\"},{\"amount\":0.0,\"item\":\"Extra Guest Charge\",\"itemType\":\"ExtraGuestCharge\"},{\"amount\":50.0,\"item\":\"Resort Fee\",\"itemType\":\"ResortFee\"}]}],\"taxesAndFees\":[{\"amount\":23.95,\"date\":\"2024-06-30\",\"itemized\":[{\"amount\":17.26,\"item\":\"Room Tax\",\"itemType\":\"RoomChargeTax\"},{\"amount\":0.0,\"item\":\"Extra Guest Tax\",\"itemType\":\"ExtraGuestChargeTax\"},{\"amount\":6.69,\"item\":\"Resort Fee Tax\",\"itemType\":\"ResortFeeTax\"}]},{\"amount\":20.87,\"date\":\"2024-07-01\",\"itemized\":[{\"amount\":14.18,\"item\":\"Room Tax\",\"itemType\":\"RoomChargeTax\"},{\"amount\":0.0,\"item\":\"Extra Guest Tax\",\"itemType\":\"ExtraGuestChargeTax\"},{\"amount\":6.69,\"item\":\"Resort Fee Tax\",\"itemType\":\"ResortFeeTax\"}]}]},\"confirmationNumber\":\"M0840F722\",\"customerId\":1137674223874,\"customerRank\":0,\"customerSegment\":0,\"depositDetails\":{\"amount\":146.26,\"amountPolicy\":\"Nights\",\"cancellationRuleCode\":\"72CXL\",\"depositRuleCode\":\"1NIGHT\",\"dueDate\":\"2024-06-28\",\"forfeitAmount\":146.26,\"forfeitDate\":\"2024-06-28\",\"itemized\":[{\"amount\":129.0,\"item\":\"Room Charge\",\"itemType\":\"RoomCharge\"},{\"amount\":17.26,\"item\":\"Room Tax\",\"itemType\":\"RoomChargeTax\"}],\"overrideAmount\":-1.0},\"depositForfeit\":true,\"depositPolicy\":{\"creditCardRequired\":true,\"depositRequired\":true},\"f1Package\":false,\"guaranteeCode\":\"CC\",\"hdePackage\":false,\"id\":\"ebf4c591-bd33-4436-8fc1-ec2e9f55acf4\",\"instance\":1,\"isCancellable\":true,\"isCreditCardExpired\":false,\"isGroupCode\":false,\"isStayDateModifiable\":false,\"itineraryId\":\"15752221698\",\"markets\":[{\"date\":\"2024-06-30\",\"marketCode\":\"CO\",\"sourceCode\":\"T2\"},{\"date\":\"2024-07-01\",\"marketCode\":\"CO\",\"sourceCode\":\"T2\"}],\"nrgStatus\":false,\"operaHotelCode\":\"195\",\"payments\":[{\"chargeAmount\":146.26,\"chargeCardExpiry\":\"***\",\"chargeCardHolder\":\"***\",\"chargeCardMaskedNumber\":\"XXXXXXXXXXXXXXXXXXXXXXXX4444\",\"chargeCardNumber\":\"***\",\"chargeCardType\":\"Mastercard\",\"chargeCurrencyCode\":\"USD\",\"fxAmount\":0.0,\"fxChecked\":false,\"fxEligible\":false,\"fxRate\":0.0,\"fxSettleAmount\":0.0,\"isDeposit\":true,\"isExternal\":false,\"reservationInstance\":1,\"status\":\"LMSPayment\"}],\"perpetualPricing\":false,\"postingState\":\"Queued\",\"profile\":{\"addresses\":[{\"city\":\"***\",\"country\":\"***\",\"postalCode\":\"***\",\"preferred\":false,\"state\":\"***\",\"street1\":\"***\",\"street2\":\"***\",\"type\":\"***\"}],\"emailAddress1\":\"fake1@email.com\",\"firstName\":\"***\",\"id\":1137674223874,\"lastName\":\"***\",\"mlifeNo\":0,\"phoneNumbers\":[{\"number\":\"***\",\"type\":\"***\"}]},\"programId\":\"209713b8-b979-4e98-ab23-e57cf984fddf\",\"propertyId\":\"e5d3f1c9-833a-83f1-e053-d303fe0ad83c\",\"purchasedComponents\":[],\"ratesSummary\":{\"adjustedRoomSubtotal\":235.0,\"balanceUponCheckIn\":233.56,\"casinoSurcharge\":0.0,\"casinoSurchargeAndTax\":0.0,\"depositDue\":146.26,\"discountedAveragePrice\":117.5,\"discountedSubtotal\":235.0,\"occupancyFee\":0.0,\"programDiscount\":75.0,\"reservationTotal\":379.82,\"resortFee\":100.0,\"resortFeeAndTax\":113.38,\"resortFeePerNight\":50.0,\"roomChargeTax\":31.44,\"roomRequestsTotal\":0.0,\"roomSubtotal\":310.0,\"tourismFee\":0.0,\"tourismFeeAndTax\":0.0,\"tripSubtotal\":335.0},\"roomTypeId\":\"f1c0c5f5-e62d-421a-b899-303dbc732587\",\"routingInstructions\":[],\"specialRequests\":[],\"state\":\"Booked\",\"thirdParty\":false,\"tripDetails\":{\"checkInDate\":\"2024-06-30\",\"checkOutDate\":\"2024-07-02\",\"numAdults\":1,\"numChildren\":0,\"numRooms\":1}}}";
    }

    final String getBorgataReservationResponse() {
        return "{\"roomReservation\":{\"id\":\"f5cccee4-ac7c-44a0-afc1-75d33d1bdc00\",\"customerId\":1028839768321,\"propertyId\":\"773000cc-468a-4d86-a38f-7ae78ecfa6aa\","
                + "\"itineraryId\":\"10491331585\",\"roomTypeId\":\"e9dfe214-438b-41e7-a1ef-909bfaa0ae14\",\"programId\":\"49ae61c6-2a92-4b8d-8fd2-c697f4af7b77\","
                + "\"state\":\"Booked\",\"nrgStatus\":false,\"specialRequests\":[],\"thirdParty\":false,\"confirmationNumber\":\"M056EF3C1\",\"operaHotelCode\":\"304\","
                + "\"postingState\":\"Queued\",\"bookDate\":\"2022-03-01\",\"profile\":{\"id\":1028839768321,\"mlifeNo\":0,\"firstName\":\"test\",\"lastName\":\"user\","
                + "\"emailAddress1\":\"fake1@email.com\",\"phoneNumbers\":[{\"type\":\"Home\",\"number\":\"7778889999\"}],\"addresses\":[{\"type\":\"Home\","
                + "\"preferred\":false,\"street1\":\"123 Main St\",\"street2\":null,\"city\":\"Las Vegas\",\"state\":\"NV\",\"country\":\"US\","
                + "\"postalCode\":\"89118\"}]},\"billing\":[{\"payment\":{\"cardHolder\":\"test user\",\"firstName\":\"test\",\"lastName\":\"user\","
                + "\"ccToken\":\"H9YDkRVwMbref2vfY+7Iy5+rVJNf2AANEBsF0v1WSvEwaaZ5hvzaruku1Hwc27atBMGtJk2TnR1sKuJp+wkT1A==\","
                + "\"encryptedccToken\":\"H9YDkRVwMbref2vfY+7Iy5+rVJNf2AANEBsF0v1WSvEwaaZ5hvzaruku1Hwc27atBMGtJk2TnR1sKuJp+wkT1A==\","
                + "\"maskedNumber\":\"XXXXXXXXXXXXXXXXXXXXXXXX1111\",\"amount\":110.78,\"expiry\":\"10/2022\",\"type\":\"Visa\"},\"address\":{\"street1\":\"234 W Main St\","
                + "\"street2\":null,\"city\":\"Las Vegas\",\"state\":\"NV\",\"postalCode\":\"89129\",\"country\":\"US\"}}],\"tripDetails\":{\"checkInDate\":\"2022-03-31\","
                + "\"checkOutDate\":\"2022-04-01\",\"numAdults\":2,\"numChildren\":0,\"numRooms\":1},\"markets\":[{\"date\":\"2022-03-31\",\"marketCode\":\"TFIT\","
                + "\"sourceCode\":\"TFITIC\"}],\"bookings\":[{\"date\":\"2022-03-31\",\"basePrice\":150.00,\"customerPrice\":0.00,\"price\":97.50,\"programIdIsRateTable\":false,"
                + "\"overridePrice\":-1.00,\"overrideProgramIdIsRateTable\":false,\"resortFeeIsSpecified\":false,\"resortFee\":15.00,"
                + "\"programId\":\"49ae61c6-2a92-4b8d-8fd2-c697f4af7b77\",\"pricingRuleId\":\"dad10d3a-98ce-46c9-ab64-4cc41ceafee9\",\"isComp\":false}],"
                + "\"chargesAndTaxes\":{\"charges\":[{\"date\":\"2022-03-31\",\"amount\":119.50,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":97.50,"
                + "\"item\":\"Room Charge\"},{\"itemType\":\"ExtraGuestCharge\",\"amount\":0.00,\"item\":\"Extra Guest Charge\"},{\"itemType\":\"ResortFee\","
                + "\"amount\":15.00,\"item\":\"Resort Fee\"},{\"itemType\":\"ComponentCharge\",\"amount\":3.00,\"item\":\"OCC\"},{\"itemType\":\"ComponentCharge\","
                + "\"amount\":2.00,\"item\":\"TOR\"},{\"itemType\":\"ComponentCharge\",\"amount\":2.00,\"item\":\"COS\"}]}],\"taxesAndFees\":[{\"date\":\"2022-03-31\","
                + "\"amount\":15.87,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":13.28,\"item\":\"Room Tax\"},{\"itemType\":\"ExtraGuestChargeTax\","
                + "\"amount\":0.00,\"item\":\"Extra Guest Tax\"},{\"itemType\":\"ResortFeeTax\",\"amount\":2.04,\"item\":\"Resort Fee Tax\"},{\"itemType\":\"ComponentChargeTax\","
                + "\"amount\":0.00,\"item\":\"OCC\"},{\"itemType\":\"ComponentChargeTax\",\"amount\":0.27,\"item\":\"TOR\"},{\"itemType\":\"ComponentChargeTax\","
                + "\"amount\":0.27,\"item\":\"COS\"}]}]},\"ratesSummary\":{\"roomSubtotal\":150.00,\"programDiscount\":52.50,\"discountedSubtotal\":97.50,"
                + "\"discountedAveragePrice\":97.50,\"roomRequestsTotal\":0.00,\"adjustedRoomSubtotal\":97.50,\"resortFee\":15.00,\"resortFeePerNight\":15.00,"
                + "\"tripSubtotal\":112.50,\"resortFeeAndTax\":17.04,\"roomChargeTax\":13.28,\"occupancyFee\":3.00,\"tourismFee\":2.00,\"tourismFeeAndTax\":2.27,"
                + "\"casinoSurcharge\":2.00,\"casinoSurchargeAndTax\":2.27,\"reservationTotal\":135.36,\"depositDue\":110.78,\"balanceUponCheckIn\":24.58},"
                + "\"depositDetails\":{\"dueDate\":\"2022-03-01\",\"amountPolicy\":\"Nights\",\"amount\":110.78,\"forfeitDate\":\"2022-03-31\",\"forfeitAmount\":0.00,"
                + "\"overrideAmount\":-1.00,\"depositRuleCode\":\"1NT\",\"cancellationRuleCode\":\"NOCXL\",\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":97.50,"
                + "\"item\":\"Room Charge\"},{\"itemType\":\"RoomChargeTax\",\"amount\":13.28,\"item\":\"Room Tax\"}]},\"depositPolicy\":{\"depositRequired\":true,"
                + "\"creditCardRequired\":true},\"payments\":[{\"reservationInstance\":1,\"chargeAmount\":110.78,\"chargeCardExpiry\":\"2022-10-01\",\"status\":\"Settled\","
                + "\"chargeCardHolder\":\"test user\",\"chargeCurrencyCode\":\"USD\",\"chargeCardType\":\"Visa\",\"chargeCardMaskedNumber\":\"XXXXXXXXXXXXXXXXXXXXXXXX1111\","
                + "\"chargeCardNumber\":\"H9YDkRVwMbref2vfY+7Iy5+rVJNf2AANEBsF0v1WSvEwaaZ5hvzaruku1Hwc27atBMGtJk2TnR1sKuJp+wkT1A==\",\"fxChecked\":true,"
                + "\"fxEligible\":false,\"fxTransDate\":\"2022-03-01\",\"fxAmount\":0.00,\"fxRate\":0.00,\"fxSettleAmount\":110.78,\"fxCurrencyCode\":\"USD\","
                + "\"fxAcceptMessage\":\"Approved\",\"fxAuthApprovalCode\":\"783119\",\"isExternal\":false,\"isDeposit\":true}],\"purchasedComponents\":[],"
                + "\"amountDue\":110.78,\"guaranteeCode\":\"CC\",\"customerRank\":0,\"customerSegment\":0,\"routingInstructions\":[],\"perpetualPricing\":false,"
                + "\"bookingSource\":\"ice\",\"bookingChannel\":\"ice\",\"isGroupCode\":false,\"isCreditCardExpired\":false,\"isStayDateModifiable\":true}}";
    }
}
