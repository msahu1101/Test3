package com.mgmresorts.order.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.concurrent.Executor;
import com.mgmresorts.common.concurrent.Executors;
import com.mgmresorts.common.concurrent.Result;
import com.mgmresorts.common.concurrent.Task;
import com.mgmresorts.common.dto.OutHeader;
import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.security.Jwts;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.content.model.PackageConfig;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.IContentAccess;
import com.mgmresorts.order.backend.access.IDiningBookingAccess;
import com.mgmresorts.order.backend.access.IPaymentSessionAccess;
import com.mgmresorts.order.backend.access.IRTCAccess;
import com.mgmresorts.order.backend.access.IRoomBookingAccess;
import com.mgmresorts.order.backend.access.IShowBookingAccess;
import com.mgmresorts.order.backend.handler.IPaymentHandler;
import com.mgmresorts.order.backend.handler.IPaymentProcessingHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionCommonHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionRoomHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionShowHandler;
import com.mgmresorts.order.backend.handler.impl.CartHandler;
import com.mgmresorts.order.backend.handler.impl.ItineraryHandler;
import com.mgmresorts.order.backend.handler.impl.ProfileHandler;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.database.access.IOrderConfirmationAccess;
import com.mgmresorts.order.database.access.IOrderProgressAccess;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.BillingAddress;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.dto.Payment;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.CheckoutResponse;
import com.mgmresorts.order.dto.services.Message;
import com.mgmresorts.order.dto.services.Order;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.SourceSystemError;
import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.dto.PackageConfigDetails;
import com.mgmresorts.order.dto.PackagePricingDetails;
import com.mgmresorts.order.dto.services.Version;
import com.mgmresorts.order.entity.OrderStatus;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.task.IProductHandler;
import com.mgmresorts.order.service.task.OrderTaskFactory;
import com.mgmresorts.order.service.task.handler.DiningHandler;
import com.mgmresorts.order.service.task.handler.RoomHandler;
import com.mgmresorts.order.service.task.handler.ShowHandler;
import com.mgmresorts.order.service.transformer.OrderCheckoutEmailEventTransformer;
import com.mgmresorts.order.service.transformer.OrderFinancialImpactTransformer;
import com.mgmresorts.order.service.transformer.OrderTransformer;
import com.mgmresorts.order.utils.Orders;
import com.mgmresorts.shopping.cart.dto.AgentInfo;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.CartType;
import com.mgmresorts.shopping.cart.dto.CartVersion;
import com.mgmresorts.shopping.cart.dto.ItemType;
import com.mgmresorts.shopping.cart.dto.PriceDetails;
import com.mgmresorts.shopping.cart.dto.RoomTotals;
import com.mgmresorts.shopping.cart.dto.services.CartResponse;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;

@SuppressWarnings({ "unchecked" })
public class OrderServiceTest {
    private final JSonMapper mapper = new JSonMapper();

    @Tested
    OrderService orderService;

    @Injectable
    private OAuthTokenRegistry registry;
    @Injectable
    private IHttpService service;
    @Injectable
    private RoomHandler roomHandler;
    @Injectable
    private IProductHandler showHandler;
    @Injectable
    private IOrderProgressAccess orderProgressAccess;

    @Injectable
    private OrderTaskFactory task;

    @Injectable
    private Executors executors;

    @Injectable
    private IContentAccess contentAccess;
    
    @Injectable
    private IRTCAccess rtcAccess;

    @Injectable
    private IPaymentHandler paymentHandler;

    @Injectable
    private Orders orders;

    @Injectable
    private IPaymentSessionCommonHandler paymentSessionCommonHandler;

    @Mocked
    private Executor executor;

    // @Mocked
    // private Result<CheckoutResponse> checkoutResponse;

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
        Jwts.Jwt jwt = new Jwts.Jwt(token, "alg", new Date(), new HashMap<>(), null, null, null, null);
        ThreadContext.getContext().get().setJwt(jwt);
    }

    @BeforeEach
    public void before() {
        assertNotNull(registry);
        assertNotNull(service);
        assertNotNull(roomHandler);
        ErrorManager.clean();
        new Errors();
    }

    static final String token = "eyJraWQiOiJtZ0NzNEE5R3VpSmsxcVRQQmNkcW1BYlNfcGwyMmVvVU1PSnFzM3hQOVh3IiwiYWxnIjoiUlMyNTYifQ."
            + "eyJ2ZXIiOjEsImp0aSI6IkFULmMwNy16akU4X2JfWVVmQWxKT2dzWl9BaDJWdjdpdi1hVk9sYXhQaUh1X00iLCJpc3MiOiJodHRwczov"
            + "L2lhbS5tcmdyZXMuY29tL29hdXRoMi9hdXNwaDdlenAzR2trazhXTjBoNyIsImF1ZCI6Imh0dHBzOi8vYXBpLm1nbXJlc29ydHMuY29t"
            + "IiwiaWF0IjoxNjI1OTIyNTEyLCJleHAiOjE2MjU5MjYxMTIsImNpZCI6ImRldm9wc19jYXJ0cyIsInNjcCI6WyJyb29tcy5yZXNlcnZhd"
            + "Glvbi5jaGFyZ2VzOnJlYWQiLCJyb29tcy5yZXNlcnZhdGlvbjp1cGRhdGUiLCJjYXJ0OmNoZWNrb3V0Iiwicm9vbXMuYXZhaWxhYmlsa"
            + "XR5OnJlYWQiLCJyb29tcy5wcm9ncmFtOnJlYWQiLCJjYXJ0OnVwZGF0ZSIsImNhcnQ6ZGVsZXRlIiwiY2FydDpyZWFkIiwiY2FydDpjcm"
            + "VhdGUiXSwic3ViIjoiZGV2b3BzX2NhcnRzIiwiZ3JvdXBzIjpbIm1nbV9zZXJ2aWNlIl19.R9kpKGhzRnGa354KZsJQHXo6jo2nowPdMGN"
            + "bix0slFcld6U2ydusIa5zgtPKabpZEJEpZT0wJ4--USft2oQQv9_8Lk-Xw8p6pd11daNwEgOh3uvibSJLsFkMYEI7j1AS5D2oHZvZwqjAt"
            + "gL7_6geUDm9O_bNlDHG_8vJ9JCaKj_Ae9eDRsu2BR31f-p8IvZFGrRwAEZZqO4hVqcc8tbYeo3JoGoJbH_gyZb5Qed8jDP73rD52iZr61n"
            + "HpuAD-SCdNt33IZEhfR35wxf_wdVj34_wdEKQ3wf075poEPqQ6vzoP7axaBhdqZsm8ydkHH_YoFTr8AM_3V-cNIWrAfTNpw";

    @Test
    void checkoutTest_Room(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
                           @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                           @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                           @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                           @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                           @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart();

        String resvResp = "resvResp";

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setId("123");
        OrderLineItem orderLineItem = new OrderLineItem();
        orderLineItem.setLineItemPrice(cart.getCartLineItems().get(0).getLineItemPrice());
        orderLineItem.setLineItemDeposit(cart.getCartLineItems().get(0).getLineItemDeposit());
        orderLineItem.setLineItemTax(cart.getCartLineItems().get(0).getLineItemTax());
        orderLineItem.setLineItemCharge(cart.getCartLineItems().get(0).getLineItemTotalCharges());
        orderLineItem.setLineItemDiscount(cart.getCartLineItems().get(0).getLineItemDiscount());
        orderLineItem.setLineItemBalance(cart.getCartLineItems().get(0).getLineItemBalance());
        orderLineItem.setLineItemAdjustedItemSubtotal(cart.getCartLineItems().get(0).getAdjustedItemSubtotal());
        com.mgmresorts.order.dto.RoomTotals roomTotals = new com.mgmresorts.order.dto.RoomTotals();
        roomTotals.setTotalPrice(cart.getRoomTotals().getTotalPrice());
        roomTotals.setTotalTripSubtotal(cart.getRoomTotals().getTotalTripSubtotal());
        roomTotals.setTotalCasinoSurchargeAndTax(cart.getRoomTotals().getTotalCasinoSurchargeAndTax());
        roomTotals.setTotalResortFeeAndTax(cart.getRoomTotals().getTotalResortFeeAndTax());
        roomTotals.setTotalOccupancyFee(cart.getRoomTotals().getTotalOccupancyFee());
        roomTotals.setTotalResortFeePerNight(cart.getRoomTotals().getTotalResortFeePerNight());
        roomTotals.setTotalTourismFeeAndTax(cart.getRoomTotals().getTotalTourismFeeAndTax());
        order.setRoomTotals(roomTotals);
        order.getOrderLineItems().add(orderLineItem);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
        };
        new Expectations() {
            {
                roomHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false,null, null);
                result = resvResp;
                minTimes = 0;
            }
        };
        new Expectations() {
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };
        final CheckoutResponse response = assertDoesNotThrow(() -> orderService.checkout(request));
        assertEquals(response.getOrder().getId(), "123");
        // validate line item prices
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemPrice(), 1448.94);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemDeposit(), 1360.49);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemTax(), 171.00);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemCharge(), 399.98);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemDiscount(), -799.96);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemBalance(), 88.45);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemAdjustedItemSubtotal(), 1199.94);
        // validate room total prices
        assertEquals(response.getOrder().getRoomTotals().getTotalTourismFeeAndTax(), 0.00);
        assertEquals(response.getOrder().getRoomTotals().getTotalCasinoSurchargeAndTax(), 0.00);
        assertEquals(response.getOrder().getRoomTotals().getTotalResortFeeAndTax(), 39.00);
        assertEquals(response.getOrder().getRoomTotals().getTotalOccupancyFee(), 0.00);
        assertEquals(response.getOrder().getRoomTotals().getTotalTripSubtotal(), 1277.94);
        assertEquals(response.getOrder().getRoomTotals().getTotalPrice(), 1448.94);
    }

    @Test
    void checkoutTestWithoutItinerary_Room(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                           @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                           @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                           @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                           @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                           @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        final CheckoutRequest request = getCheckoutRequest();
        request.setItineraryId(null);
        request.setEnableJwb(false);
        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        request.getGuestProfile().setId("guestprofileid");

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());
        
        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
        };
        new Expectations() {
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 1;
            }
        };
        new Expectations() {
            {
                itineraryHandler.create((GuestProfile) any, (Cart) any);
                result = "createdItineraryId";
            }
        };
        new Expectations() {
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };
        assertDoesNotThrow(() -> orderService.checkout(request));
    }

    public CheckoutRequest getCheckoutRequest() {
        // Request Object Set Start
        GuestProfile guestProfile = new GuestProfile();
        guestProfile.setId("guestprofileid");
        guestProfile.setFirstName("first");
        guestProfile.setLastName("last");
        guestProfile.setPerpetualOfferEligible(false);

        CheckoutRequest request = new CheckoutRequest();
        request.setCartId("id");
        request.setBillings(getBillings());
        request.setGuestProfile(guestProfile);
        request.setItineraryId("itineraryid");
        request.setEnableJwb(false);
        // Request Object Set End
        return request;
    }

    public CheckoutRequest getCheckoutRequestPaymentWidgetFlow() {
        CheckoutRequest request = new CheckoutRequest();
        request.setCartId("id");
        request.setPaymentCaptured(true);
        request.setCartType(Type.PACKAGE);
        request.setCartVersion(Version.V2);
        request.setInAuthTransactionId("1234");
        return request;
    }

    private List<Billing> getBillings() {
        Payment payment = new Payment();
        payment.setAmount(5000.00);
        payment.setCardHolder("ssen");

        BillingAddress addressReq = new BillingAddress();
        addressReq.setCity("city");
        addressReq.setPostalCode("postalcode");

        Billing billingReq = new Billing();
        billingReq.setAddress(addressReq);
        billingReq.setPayment(payment);
        final List<Billing> creditcards = new ArrayList<>();
        creditcards.add(billingReq);
        return creditcards;
    }

    public CheckoutRequest getCheckoutRequestInvalidCCToken() {
        // Request Object Set Start
        Payment payment = new Payment();
        payment.setAmount(1000.00);
        payment.setCardHolder("first last");
        payment.setCcToken("2111110A001DAXFHY79D9XCE1111");
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
        request.setItineraryId("itineraryid");
        // Request Object Set End
        return request;
    }

    public Cart getCart() {
        // Cart Response Mock Object Start
        List<CartLineItem> lineItems = new ArrayList<>();
        CartLineItem product = new CartLineItem();
        product.setProductId("c83ecd59-93bf-471a-b31b-082c29604c2f");
        product.setType(ItemType.ROOM);
        product.setStatus(CartLineItem.Status.SAVED);
        product.setLineItemPrice(1448.9400);
        product.setLineItemDeposit(1360.4900);
        product.setLineItemTax(171.0000);
        product.setLineItemTotalCharges(399.9800);
        product.setLineItemDiscount(-799.9600);
        product.setLineItemBalance(88.4500);
        product.setAdjustedItemSubtotal(1199.9400);
        product.setPriceExpiresAt(ZonedDateTime.now().plusDays(3));
        product.setPaymentRequired(true);
        product.setUpgraded(false);
        product.setContent("{\"propertyId\":\"66964e2b-2550-4476-84c3-1a4c0c5c067f\",\"roomTypeId\":\"c83ecd59-93bf-471a-b31b-082c29604c2f\",\"programId\":\"161264f1-0bb6-41f9-8a17-ce5497349e27\",\"customerId\":\"0\",\"perpetualPricing\":false,\"guaranteeCode\":\"CC\",\"tripDetails\":{\"checkInDate\":\"2022-06-05\",\"checkOutDate\":\"2022-06-07\",\"numAdults\":1,\"numChildren\":0,\"numRooms\":1},\"bookings\":[{\"date\":\"2022-06-05\",\"basePrice\":199.99,\"customerPrice\":0.00,\"price\":599.97,\"isDiscounted\":false,\"programIdIsRateTable\":false,\"overridePrice\":-1.00,\"overrideProgramIdIsRateTable\":false,\"isComp\":false,\"resortFeeIsSpecified\":false,\"resortFee\":39.00,\"programId\":\"161264f1-0bb6-41f9-8a17-ce5497349e27\",\"pricingRuleId\":\"94ae63d1-13d1-497d-a58b-b5f535e26a51\"},{\"date\":\"2022-06-06\",\"basePrice\":199.99,\"customerPrice\":0.00,\"price\":599.97,\"isDiscounted\":false,\"programIdIsRateTable\":false,\"overridePrice\":-1.00,\"overrideProgramIdIsRateTable\":false,\"isComp\":false,\"resortFeeIsSpecified\":false,\"resortFee\":39.00,\"programId\":\"161264f1-0bb6-41f9-8a17-ce5497349e27\",\"pricingRuleId\":\"94ae63d1-13d1-497d-a58b-b5f535e26a51\"}],\"chargesAndTaxes\":{\"charges\":[{\"date\":\"2022-06-05\",\"amount\":788.97,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":599.97,\"item\":\"Room Charge\"},{\"itemType\":\"ExtraGuestCharge\",\"amount\":0.00,\"item\":\"Extra Guest Charge\"},{\"itemType\":\"ResortFee\",\"amount\":39.00,\"item\":\"Resort Fee\"}]},{\"date\":\"2022-06-06\",\"amount\":788.97,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":599.97,\"item\":\"Room Charge\"},{\"itemType\":\"ExtraGuestCharge\",\"amount\":0.00,\"item\":\"Extra Guest Charge\"},{\"itemType\":\"ResortFee\",\"amount\":39.00,\"item\":\"Resort Fee\"}]}],\"taxesAndFees\":[{\"date\":\"2022-06-05\",\"amount\":105.56,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":80.28,\"item\":\"Room Tax\"},{\"itemType\":\"ExtraGuestChargeTax\",\"amount\":0.00,\"item\":\"Extra Guest Tax\"},{\"itemType\":\"ResortFeeTax\",\"amount\":5.22,\"item\":\"Resort Fee Tax\"}]},{\"date\":\"2022-06-06\",\"amount\":105.56,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":80.28,\"item\":\"Room Tax\"},{\"itemType\":\"ExtraGuestChargeTax\",\"amount\":0.00,\"item\":\"Extra Guest Tax\"},{\"itemType\":\"ResortFeeTax\",\"amount\":5.22,\"item\":\"Resort Fee Tax\"}]}]},\"availableComponents\":[{\"id\":\"df2737d9-1a7e-4c2a-8713-d226bdc28617\",\"code\":\"DOGFRIENDLYSTE\",\"shortDescription\":\"Dog Friendly Fee\",\"longDescription\":\"MGM Grand Las Vegas is excited and prepared to accommodate your dog. Up to two dogs are allowed, with a maximum combined weight of less than 100 lbs.&nbsp;(Price per night based on one dog, fees for additional dog will be applied at check-in, if applicable.)\",\"active\":true,\"depositAmount\":340.14,\"pricingApplied\":\"NIGHTLY\",\"tripPrice\":300.00,\"tripTax\":40.14,\"price\":150.00,\"prices\":[{\"date\":\"2022-06-05\",\"amount\":150.00,\"tax\":20.07},{\"date\":\"2022-06-06\",\"amount\":150.00,\"tax\":20.07}]}],\"ratesSummary\":{\"roomSubtotal\":399.9800,\"programDiscount\":-799.9600,\"discountedSubtotal\":1199.9400,\"discountedAveragePrice\":599.9700,\"roomRequestsTotal\":0.0000,\"adjustedRoomSubtotal\":1199.9400,\"resortFee\":78.0000,\"resortFeePerNight\":39.0000,\"tripSubtotal\":1277.9400,\"resortFeeAndTax\":88.4400,\"roomChargeTax\":160.5600,\"occupancyFee\":0.0000,\"tourismFee\":0.0000,\"tourismFeeAndTax\":0.0000,\"casinoSurcharge\":0.0000,\"casinoSurchargeAndTax\":0.0000,\"reservationTotal\":1448.9400,\"depositDue\":1360.4900,\"balanceUponCheckIn\":88.4500},\"depositDetails\":{\"dueDate\":\"2022-05-09\",\"amountPolicy\":\"PercentAmount\",\"amount\":1360.49,\"forfeitDate\":\"2022-06-03\",\"forfeitAmount\":680.25,\"overrideAmount\":-1.00,\"depositRuleCode\":\"FULL\",\"cancellationRuleCode\":\"48H\",\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":1199.94,\"item\":\"Room Charge\"},{\"itemType\":\"RoomChargeTax\",\"amount\":160.55,\"item\":\"Room Tax\"},{\"amount\":0.00,\"item\":\"Extra Guest Charge\"},{\"amount\":0.00,\"item\":\"Extra Guest Tax\"}]},\"depositPolicy\":{\"depositRequired\":true,\"creditCardRequired\":true},\"markets\":[{\"date\":\"2022-06-05\",\"marketCode\":\"TFIT\",\"sourceCode\":\"TFITIC\"},{\"date\":\"2022-06-06\",\"marketCode\":\"TFIT\",\"sourceCode\":\"TFITIC\"}]}");
        lineItems.add(product);

        Cart cart = new Cart();
        cart.setCartId("61295a58-4f11-42be-938f-8d34cc84219e");
        cart.setCartLineItems(lineItems);
        cart.setPriceDetails(new PriceDetails());
        cart.getPriceDetails().setTotalDeposit(1360.4900);
        cart.getPriceDetails().setTotalPrice(1448.9400);
        cart.setIsCheckoutEligible(true);
        cart.setMinHoldDuration(360);
        cart.setShowMaxCount(9);
        cart.setRoomMaxCount(9);
        cart.setPaymentRequired(true);

        com.mgmresorts.shopping.cart.dto.PriceDetails priceDetails = new PriceDetails();
        priceDetails.setTotalCharge(399.9800);
        priceDetails.setTotalTax(171.0000);
        priceDetails.setTotalPrice(1448.9400);
        priceDetails.setTotalDeposit(1360.4900);
        priceDetails.setTotalDiscount(-799.9600);
        priceDetails.setTotalBalance(88.4500);
        cart.setPriceDetails(priceDetails);

        RoomTotals roomTotals = new RoomTotals();
        roomTotals.setTotalTourismFeeAndTax(0.0000);
        roomTotals.setTotalCasinoSurchargeAndTax(0.0000);
        roomTotals.setTotalResortFeeAndTax(39.0000);
        roomTotals.setTotalOccupancyFee(0.0000);
        roomTotals.setTotalTripSubtotal(1277.9400);
        roomTotals.setTotalPrice(1448.9400);
        cart.setRoomTotals(roomTotals);
        cart.setType(CartType.GLOBAL);
        cart.setVersion(CartVersion.V1);
        OutHeader header = new OutHeader();
        header.setApiVersion("apiversion");
        header.setExecutionId("executionId");
        CartResponse cartResp = new CartResponse();
        cartResp.setCart(cart);
        cartResp.setHeader(header);
        // Cart Response Mock Object End
        return cart;
    }

    @Test
    void checkoutTestLoggedIn_Room(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
                                   @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                   @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                   @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                   @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        String resvResp = "resvResp";
        String gseId = "gse";
        String mlife = "mlife";

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setCustomerId(gseId);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 1;
            }
            {
                roomHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false,null, null);
                result = resvResp;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };
        final CheckoutResponse response = orderService.checkout(request);
        assertEquals(response.getOrder().getCustomerId(), gseId);

    }

    @Test
    void checkoutTestMissingRequestPayload_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(null);
        });
        assertEquals(exception.getCode(), Errors.INVALID_PAYLOAD);

    }

    @Test
    void checkoutTestMissingBillings_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                          @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                          @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                          @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                          @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setBillings(null);

        Cart cart = getCart();
        cart.setPriceDetails(new PriceDetails());
        cart.getPriceDetails().setTotalDeposit(1000.0);
        cart.setIsCheckoutEligible(true);
        cart.setPaymentRequired(true);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 0;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("At least one billing method is required."));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST);

    }

    @Test
    void checkoutTestMissingCartIdAndMgmId_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setMgmId(null);
        request.setCartId(null);

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("Either cart id or mgm id is required."));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST);

    }

    @Test
    void checkoutTestInsufficientFunds_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                            @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                            @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                            @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                            @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.getBillings().get(0).getPayment().setAmount(10.0);

        Cart cart = getCart();
        cart.setPriceDetails(new PriceDetails());
        cart.getPriceDetails().setTotalDeposit(1000.0);
        cart.setIsCheckoutEligible(true);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 0;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertEquals(exception.getCode(), Errors.INSUFFICIENT_CHARGE_AMOUNT);

    }

    @Test
    void checkoutTestWhenNoCustIdInRequest_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                @Injectable RoomHandler roomHandler, @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess,
                                                @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);

        String resvResp = "resvResp";
        String custId = "guestprofileid";
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", null);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", null);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setCustomerId(custId);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {

            {
                profileHandler.createGuestProfile((CheckoutRequest) any);
                result = custId;
            }
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 0;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);
            }
            {
                roomHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false,null, null);
                result = resvResp;
                minTimes = 0;
            }

            {
                cartToOrderTransformer.toRight((Order) any);
                result = new com.mgmresorts.order.entity.Order();
                minTimes = 0;
            }
            {
                orderAccess.create((com.mgmresorts.order.entity.Order) any);
                result = null;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals(custId, checkoutResponse.getOrder().getCustomerId());
    }

    @Test
    void checkoutTestWhenCartIsNotEligible_Room(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess,
                                                @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart();
        cart.setIsCheckoutEligible(false);
        cart.setPriceExpiresAt(ZonedDateTime.now().minusDays(1));

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 0;
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("Cart is not eligible for checkout"));
        assertEquals(exception.getCode(), ApplicationError.NO_CHECKOUT_ELIGIBLE);
    }

    @Test
    void checkoutTestWhenPackageIsNotActive_Show(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                                 @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                 @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                 @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                 @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getShowPackageCart();

        cart.setIsCheckoutEligible(true);
        cart.setPriceExpiresAt(ZonedDateTime.now().minusDays(1));

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 0;
            }
            {
                contentAccess.getPackageConfigDetails(anyString);
                result = getPackageConfig();
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertEquals(exception.getCode(), ApplicationError.PACKAGE_NOT_ACTIVE);
    }

    @Test
    void checkoutTestPackageCart(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                 @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                 @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler, @Mocked IProductHandler showHandler,
                                 @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Mocked Result<CheckoutResponse> checkoutResponse, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                 @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        final CheckoutRequest request = getCheckoutRequest();
        final Cart cart = getShowPackageCart();
        final Order order = createOrderWithPendingStatus(cart.getCartLineItems(), cart, request);
        order.getOrderLineItems().get(0).setStatus(OrderLineItem.Status.SUCCESS);
        order.getOrderLineItems().get(1).setStatus(OrderLineItem.Status.SUCCESS);
        Optional<PackageConfig[]> packageConfig = getPackageConfig();
        packageConfig.get()[0].setActive(true);

        cart.setIsCheckoutEligible(true);
        cart.setPriceExpiresAt(ZonedDateTime.now().minusDays(1));

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        List<Result<CheckoutResponse>> tasks = new ArrayList<>();
        tasks.add(checkoutResponse);

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 1;
            }
            {
                contentAccess.getPackageConfigDetails(anyString);
                result = packageConfig;
            }
            {
                checkoutResponse.getOutput();
                result = getCheckoutResposne();
            }
            {
                orders.createOrderWithPendingStatus((List<CartLineItem>) any, (Cart) any, (CheckoutRequest) any);
                result = order;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };
        CheckoutResponse checkOutResp = orderService.checkout(request);
        assertEquals(checkOutResp.getOrder().getStatus(), com.mgmresorts.order.dto.services.Order.Status.SUCCESS);
    }

    @Test
    void checkoutTestAsyncPackageCart(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                      @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                      @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler, @Mocked IProductHandler showHandler,
                                      @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Mocked Result<CheckoutResponse> checkoutResponse, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                      @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        final CheckoutRequest request = getCheckoutRequest();
        request.setProgressiveCheckout(true);
        final Cart cart = getShowPackageCart();
        final Order order = createOrderWithPendingStatus(cart.getCartLineItems(), cart, request);
        order.getOrderLineItems().get(0).setStatus(OrderLineItem.Status.SUCCESS);
        order.getOrderLineItems().get(1).setStatus(OrderLineItem.Status.PENDING);
        Optional<PackageConfig[]> packageConfig = getPackageConfig();
        packageConfig.get()[0].setActive(true);

        cart.setIsCheckoutEligible(true);
        cart.setPriceExpiresAt(ZonedDateTime.now().minusDays(1));

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        List<Result<CheckoutResponse>> tasks = new ArrayList<>();
        tasks.add(checkoutResponse);

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 1;
            }
            {
                contentAccess.getPackageConfigDetails(anyString);
                result = packageConfig;
            }
            {
                checkoutResponse.getOutput();
                CheckoutResponse cartResp = new CheckoutResponse();
                Order order = new Order();
                order.setStatus(Order.Status.PENDING);
                order.setCanRetryCheckout(false);
                cartResp.setOrder(order);
                result = cartResp;
            }
            {
                orders.createOrderWithPendingStatus((List<CartLineItem>) any, (Cart) any, (CheckoutRequest) any);
                result = order;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };
        CheckoutResponse checkOutResp = orderService.checkout(request);
        assertEquals(checkOutResp.getOrder().getStatus(), Order.Status.PENDING);
        assertEquals(checkOutResp.getOrder().getCanRetryCheckout(), false);
    }

    @Test
    void checkoutTestPackageCart_NoShowInCart(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                              @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                              @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler, @Mocked IProductHandler showHandler,
                                              @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Mocked Result<CheckoutResponse> checkoutResponse, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                              @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart();
        cart.setType(CartType.PACKAGE);
        Optional<PackageConfig[]> packageConfig = getPackageConfig();
        packageConfig.get()[0].setActive(true);

        cart.setIsCheckoutEligible(true);
        cart.setPriceExpiresAt(ZonedDateTime.now().minusDays(1));

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        List<Result<CheckoutResponse>> tasks = new ArrayList<>();
        tasks.add(checkoutResponse);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                contentAccess.getPackageConfigDetails(anyString);
                result = packageConfig;
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertEquals(exception.getCode(), ApplicationError.PACKAGE_CART_NOT_HAVE_REQUIRED_ITEM);
    }

    @Test
    void checkoutTestWhenCartIsNotEligibleButInGracePeriod(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                                           @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                           @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                           @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                           @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart();
        cart.setIsCheckoutEligible(false);
        cart.setPriceExpiresAt(ZonedDateTime.now().plusSeconds(5));
        List<CartLineItem> cartLineItems = new ArrayList<>();
        // set up orderLineItem
        CartLineItem item = new CartLineItem();
        item.setCartLineItemId("id");
        item.setStatus(CartLineItem.Status.SAVED);
        CartLineItem item1 = new CartLineItem();
        item1.setCartLineItemId("id1");
        item1.setStatus(CartLineItem.Status.PRICE_EXPIRED);
        cartLineItems.add(item1);
        cartLineItems.add(item);
        cart.setCartLineItems(cartLineItems);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };

        assertDoesNotThrow(() -> orderService.checkout(request));
    }

    @Test
    void checkoutTestWhenCartIsNotEligibleButInGracePeriodOrderItemsExpired(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                                            @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                                            @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                                            @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                                            @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart();
        cart.setIsCheckoutEligible(false);
        cart.setPriceExpiresAt(ZonedDateTime.now().plusSeconds(5));
        List<CartLineItem> cartLineItems = new ArrayList<>();
        // set up orderLineItem
        CartLineItem item = new CartLineItem();
        item.setCartLineItemId("id");
        item.setStatus(CartLineItem.Status.SAVED);
        CartLineItem item1 = new CartLineItem();
        item1.setCartLineItemId("id1");
        item1.setStatus(CartLineItem.Status.ITEM_EXPIRED);
        cartLineItems.add(item1);
        cartLineItems.add(item);
        cart.setCartLineItems(cartLineItems);
        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("Cart is not eligible for checkout"));
        assertEquals(exception.getCode(), ApplicationError.NO_CHECKOUT_ELIGIBLE_INELIGIBLE_ITEMS);
    }

    @Test
    void checkoutTestCompleteAntiFraudFailure_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                   @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                   @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderTaskFactory task,
                                                   @Injectable Executors executors, @Mocked Result<OrderLineItem> firstResult, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                   @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    )
            throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set up orderLineItem
        OrderLineItem item = new OrderLineItem();
        item.setCartLineItemId("id");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        item.setOrderLineItemId("cart");

        // set error message returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_antifraud_error>[ Anti-fraud service returned unable to process the transaction ]");
        error.setSourceSystemCode("632-2-160");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);
        orderLineItems.add(item);

        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
        // List<Result<OrderLineItem>> tasks = new ArrayList<>();
        // tasks.add(firstResult);
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.PAYMENT_FAILURE);
        order.getOrderLineItems().add(item);
        checkoutResponseMock.setOrder(order);

        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);
        // tasks.add(firstResult);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 1;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }
        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals(Order.Status.PAYMENT_FAILURE, checkoutResponse.getOrder().getStatus());
        assertEquals("cart", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("632-2-160", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
    }

    @Test
    void checkoutTestCompletePaymentAuthorizationFailure_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler,
                                                              @Injectable ItineraryHandler itineraryHandler, @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                              @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderTaskFactory task, @Injectable Executors executors, @Mocked Result<OrderLineItem> firstResult,
                                                              @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                              @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set up orderLineItem
        OrderLineItem item = new OrderLineItem();
        item.setCartLineItemId("id");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        item.setOrderLineItemId("cart");
        // set error message returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_payment_authorization_failed<[ Payment authorization failed ]");
        error.setSourceSystemCode("632-2-243");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);
        orderLineItems.add(item);

        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
        // List<Result<OrderLineItem>> tasks = new ArrayList<>();
        // tasks.add(firstResult);

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.PAYMENT_FAILURE);
        order.getOrderLineItems().add(item);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 1;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }

        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals(Order.Status.PAYMENT_FAILURE, checkoutResponse.getOrder().getStatus());
        assertEquals("cart", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("632-2-243", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
    }

    @Test
    void checkoutTestCompleteCVVFailure_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                             @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                             @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderTaskFactory task,
                                             @Injectable Executors executors, @Mocked Result<OrderLineItem> firstResult, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                             @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    )
            throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set up orderLineItem
        OrderLineItem item = new OrderLineItem();
        item.setCartLineItemId("id");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        item.setOrderLineItemId("cart");
        // set error message returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<InvalidCreditCard>[Charge credit card is invalid]");
        error.setSourceSystemCode("632-2-242");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);
        orderLineItems.add(item);

        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
//        List<Result<OrderLineItem>> tasks = new ArrayList<>();
//        tasks.add(firstResult);
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.getOrderLineItems().add(item);
        order.setStatus(Order.Status.PAYMENT_FAILURE);
        order.setCanRetryCheckout(true);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 1;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }

        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals("cart", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("632-2-242", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals(Order.Status.PAYMENT_FAILURE, checkoutResponse.getOrder().getStatus());
        assertTrue(checkoutResponse.getOrder().getCanRetryCheckout());
    }

    @Test
    void checkoutTestPartialAntiFraudFailure(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                             @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                             @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                             @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderTaskFactory task,
                                             @Injectable Executors executors, @Mocked Result<OrderLineItem> firstResult, @Mocked Result<OrderLineItem> secondResult,
                                             @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                             @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will fail
        OrderLineItem item = new OrderLineItem();
        item.setOrderLineItemId("cartFailure");
        item.setCartLineItemId("idFailure");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        // -----------------------------
        // set product that will succeed
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("cartSuccess");
        itemTwo.setCartLineItemId("idSuccess");
        itemTwo.setStatus(OrderLineItem.Status.SUCCESS);
        // ----------------------------------------------
        // set error message that is returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_antifraud_error>[ Anti-fraud service returned unable to process the transaction ]");
        error.setSourceSystemCode("632-2-160");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);

        orderLineItems.add(item);
        orderLineItems.add(itemTwo);

        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
        // List<Result<OrderLineItem>> tasks = new ArrayList<>();
        // tasks.add(firstResult);
        // tasks.add(secondResult);

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.getOrderLineItems().add(item);
        order.getOrderLineItems().add(itemTwo);
        order.setNewCartId("newId");
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 1;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }
        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals("cartFailure", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("632-2-160", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals("cartSuccess", checkoutResponse.getOrder().getOrderLineItems().get(1).getOrderLineItemId());
        assertEquals("SUCCESS", checkoutResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals("newId", checkoutResponse.getOrder().getNewCartId());
    }

    @Test
    void checkoutTestPartialPaymentAuthorizationFailure(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                        @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                        @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                        @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderTaskFactory task,
                                                        @Injectable Executors executors, @Mocked Result<OrderLineItem> firstResult, @Mocked Result<OrderLineItem> secondResult,
                                                        @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                        @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will fail
        OrderLineItem item = new OrderLineItem();
        item.setOrderLineItemId("cartFailure");
        item.setCartLineItemId("idFailure");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        // -----------------------------
        // set product that will succeed
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("cartSuccess");
        itemTwo.setCartLineItemId("idSuccess");
        itemTwo.setStatus(OrderLineItem.Status.SUCCESS);
        // ----------------------------------------------
        // set error message that is returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_payment_authorization_failed<[ Payment authorization failed ]");
        error.setSourceSystemCode("632-2-243");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);

        orderLineItems.add(item);
        orderLineItems.add(itemTwo);

        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
        // List<Result<OrderLineItem>> tasks = new ArrayList<>();
        // tasks.add(firstResult);
        // tasks.add(secondResult);

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.getOrderLineItems().add(item);
        order.getOrderLineItems().add(itemTwo);
        order.setNewCartId("newId");
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 1;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }
        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals("cartFailure", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("632-2-243", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals("cartSuccess", checkoutResponse.getOrder().getOrderLineItems().get(1).getOrderLineItemId());
        assertEquals("SUCCESS", checkoutResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals("newId", checkoutResponse.getOrder().getNewCartId());
    }

    @Test
    void checkoutTestPartialCVVFailure(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                       @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                       @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                       @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderTaskFactory task,
                                       @Injectable Executors executors, @Mocked Result<OrderLineItem> firstResult, @Mocked Result<OrderLineItem> secondResult,
                                       @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                       @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will fail
        OrderLineItem item = new OrderLineItem();
        item.setOrderLineItemId("cartFailure");
        item.setCartLineItemId("idFailure");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        // -----------------------------
        // set product that will succeed
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("cartSuccess");
        itemTwo.setCartLineItemId("idSuccess");
        itemTwo.setStatus(OrderLineItem.Status.SUCCESS);
        // ----------------------------------------------
        // set error message that is returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<InvalidCreditCard>[Charge credit card is invalid]");
        error.setSourceSystemCode("632-2-242");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);

        orderLineItems.add(item);
        orderLineItems.add(itemTwo);

        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
//        List<Result<OrderLineItem>> tasks = new ArrayList<>();
//        tasks.add(firstResult);
//        tasks.add(secondResult);
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.getOrderLineItems().add(item);
        order.getOrderLineItems().add(itemTwo);
        order.setNewCartId("newId");
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }
//            {
//                cartHandler.handleCheckout(anyString, (List<String>) any, anyString, null);
//                result = "newId";
//            }

        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals("cartFailure", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("632-2-242", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals("cartSuccess", checkoutResponse.getOrder().getOrderLineItems().get(1).getOrderLineItemId());
        assertEquals("SUCCESS", checkoutResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals("newId", checkoutResponse.getOrder().getNewCartId());
    }

    @Test
    void checkoutCartInCaseOfAllProductCheckoutIsSuccessful_Room(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                                 @Injectable RoomHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                                 @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess, @Injectable OrderTransformer cartToOrderTransformer,
                                                                 @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                                 @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        String resvResp = "resvResp";
        String gseId = "gse";
        String mlife = "mlife";

        Map<String, String> claims = new HashMap<>();
        claims.put("com.mgm.gse.id", gseId);
        claims.put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().setJwt(new Jwts.Jwt("guestToken", "algo", new Date(), claims, null, null, null, null));
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.SUCCESS);
        order.setCustomerId(gseId);
        ;
        order.setCanRetryCheckout(false);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                roomHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false,null, null);
                result = resvResp;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }

        };
        final CheckoutResponse response = orderService.checkout(request);
        assertEquals(response.getOrder().getCustomerId(), gseId);
        assertEquals(Order.Status.SUCCESS, response.getOrder().getStatus());
        assertFalse(response.getOrder().getCanRetryCheckout());
    }

    @Test
    void checkoutCartInCaseOfPartialProductFailure_Room(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                                        @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                        @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                        @Mocked Result<OrderLineItem> firstResult, @Mocked Result<OrderLineItem> secondResult, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                        @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    )
            throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will fail
        OrderLineItem item = new OrderLineItem();
        item.setOrderLineItemId("cartFailure");
        item.setCartLineItemId("idFailure");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        // -----------------------------
        // set product that will succeed
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("cartSuccess");
        itemTwo.setCartLineItemId("idSuccess");
        itemTwo.setStatus(OrderLineItem.Status.SUCCESS);
        // ----------------------------------------------
        // set error message that is returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_antifraud_error>[ Anti-fraud service returned unable to process the transaction ]");
        error.setSourceSystemCode("632-2-160");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);

        orderLineItems.add(item);
        orderLineItems.add(itemTwo);

        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
//        List<Result<OrderLineItem>> tasks = new ArrayList<>();
//        tasks.add(firstResult);
//        tasks.add(secondResult);
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.getOrderLineItems().add(item);
        order.getOrderLineItems().add(itemTwo);
        order.setStatus(Order.Status.PARTIAL);
        order.setNewCartId("newId");
        order.setCanRetryCheckout(true);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }
//            {
//                cartHandler.handleCheckout(anyString, (List<String>) any, anyString, null);
//                result = "newId";
//            }
        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals("cartFailure", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("632-2-160", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals("cartSuccess", checkoutResponse.getOrder().getOrderLineItems().get(1).getOrderLineItemId());
        assertEquals("SUCCESS", checkoutResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals(Order.Status.PARTIAL, checkoutResponse.getOrder().getStatus());
        assertTrue(checkoutResponse.getOrder().getCanRetryCheckout());
        assertEquals("newId", checkoutResponse.getOrder().getNewCartId());
    }

    @Test
    void checkoutCartInCaseOfPartialProductFailureNoRetry_Room(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                                               @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                               @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                               @Mocked Result<OrderLineItem> firstResult, @Mocked Result<OrderLineItem> secondResult, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                               @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    )
            throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will fail
        OrderLineItem item = new OrderLineItem();
        item.setOrderLineItemId("cartFailure");
        item.setCartLineItemId("idFailure");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        // -----------------------------
        // set product that will succeed
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("cartSuccess");
        itemTwo.setCartLineItemId("idSuccess");
        itemTwo.setStatus(OrderLineItem.Status.SUCCESS);
        // ----------------------------------------------
        // set error message that is returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_antifraud_error>[ Anti-fraud service returned unable to process the transaction ]");
        error.setSourceSystemCode("632-2-160");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);

        orderLineItems.add(item);
        orderLineItems.add(itemTwo);

        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
        // List<Result<OrderLineItem>> tasks = new ArrayList<>();
        // tasks.add(firstResult);
        // tasks.add(secondResult);

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.getOrderLineItems().add(item);
        order.getOrderLineItems().add(itemTwo);
        order.setStatus(Order.Status.PARTIAL);
        order.setCanRetryCheckout(false);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }
        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals("cartFailure", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("632-2-160", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals("cartSuccess", checkoutResponse.getOrder().getOrderLineItems().get(1).getOrderLineItemId());
        assertEquals("SUCCESS", checkoutResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals(Order.Status.PARTIAL, checkoutResponse.getOrder().getStatus());
        assertFalse(checkoutResponse.getOrder().getCanRetryCheckout());
        assertEquals(null, checkoutResponse.getOrder().getNewCartId());
    }

    @Test
    void checkoutTestLoggedInJwbValidationFailure(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                                  @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                  @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                  @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                  @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        request.setEnableJwb(true);
        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        cart.setMgmId("123456");
        cart.getCartLineItems().get(0).setEnableJwb(true);
        String gseId = "gse";
        String mlife = "mlife";

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });

        assertTrue(exception.getDescription().contains("Join while booking is not allowed for existing members."));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST);
    }

    @Test
    void checkoutTestJwbValidationFailure(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                          @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                          @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                          @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                          @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        request.setEnableJwb(false);
        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        cart.setMgmId("123456");
        cart.getCartLineItems().get(0).setEnableJwb(true);
        String gseId = "gse";
        String mlife = "mlife";

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });

        assertTrue(exception.getDescription().contains("Join while booking is required for this cart."));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST);
    }

    @Test
    void checkoutTestJwbValidationFailureNoMgmId(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                                 @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                 @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                 @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                 @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        request.setEnableJwb(true);
        Cart cart = getCart();
        cart.setIsCheckoutEligible(true);
        cart.setMgmId("123456");
        cart.getCartLineItems().get(0).setEnableJwb(true);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });

        assertTrue(exception.getDescription().contains("The guest must join to checkout this cart."));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST);
    }

    @Test
    void checkoutTest_Show(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable ShowHandler showHandler,
                           @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                           @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                           @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                           @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                           @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setId("f694371e-a089-4f20-81c1-a66e09cf7b28");
        OrderLineItem orderLineItem = new OrderLineItem();
        orderLineItem.setLineItemCharge(cart.getCartLineItems().get(0).getLineItemTotalCharges());
        orderLineItem.setLineItemTax(cart.getCartLineItems().get(0).getLineItemTax());
        orderLineItem.setLineItemPrice(cart.getCartLineItems().get(0).getLineItemPrice());
        orderLineItem.setLineItemDeposit(cart.getCartLineItems().get(0).getLineItemDeposit());
        orderLineItem.setLineItemDiscount(cart.getCartLineItems().get(0).getLineItemDiscount());
        orderLineItem.setLineItemBalance(cart.getCartLineItems().get(0).getLineItemBalance());

        com.mgmresorts.order.dto.ShowTotals showTotals = new com.mgmresorts.order.dto.ShowTotals();
        showTotals.setTotalDeliveryFee(cart.getShowTotals().getTotalDeliveryFee());
        showTotals.setTotalGratuity(cart.getShowTotals().getTotalGratuity());
        showTotals.setTotalLet(cart.getShowTotals().getTotalLet());
        showTotals.setTotalServiceChargeFee(cart.getShowTotals().getTotalServiceChargeFee());
        showTotals.setTotalServiceChargeTax(cart.getShowTotals().getTotalServiceChargeTax());
        showTotals.setTotalTransactionFee(cart.getShowTotals().getTotalTransactionFee());
        showTotals.setTotalTransactionTax(cart.getShowTotals().getTotalTransactionTax());
        showTotals.setTotalPrice(cart.getShowTotals().getTotalPrice());
        order.setShowTotals(showTotals);
        order.getOrderLineItems().add(orderLineItem);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };

        final CheckoutResponse response = assertDoesNotThrow(() -> orderService.checkout(request));
        assertEquals(response.getOrder().getId(), "f694371e-a089-4f20-81c1-a66e09cf7b28");
        // validate line item prices
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemPrice(), 130.00);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemDeposit(), 130.00);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemTax(), 10.73);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemCharge(), 105.5500);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemDiscount(), 0.00);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemBalance(), 0.00);
        // validate show total prices
        assertEquals(response.getOrder().getShowTotals().getTotalDeliveryFee(), 0.00);
        assertEquals(response.getOrder().getShowTotals().getTotalGratuity(), 0.00);
        assertEquals(response.getOrder().getShowTotals().getTotalLet(), 9.50);
        assertEquals(response.getOrder().getShowTotals().getTotalServiceChargeFee(), 13.72);
        assertEquals(response.getOrder().getShowTotals().getTotalServiceChargeTax(), 1.23);
        assertEquals(response.getOrder().getShowTotals().getTotalTransactionFee(), 0.00);
        assertEquals(response.getOrder().getShowTotals().getTotalTransactionTax(), 0.00);
        assertEquals(response.getOrder().getShowTotals().getTotalPrice(), 130.00);
    }

    // will check later

    @Test
    void checkoutTestAsync_Show(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable ShowHandler showHandler,
                                @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();
        request.setProgressiveCheckout(true);
        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setId("f694371e-a089-4f20-81c1-a66e09cf7b28");
        OrderLineItem orderLineItem = new OrderLineItem();
        orderLineItem.setLineItemCharge(cart.getCartLineItems().get(0).getLineItemTotalCharges());
        orderLineItem.setLineItemTax(cart.getCartLineItems().get(0).getLineItemTax());
        orderLineItem.setLineItemPrice(cart.getCartLineItems().get(0).getLineItemPrice());
        orderLineItem.setLineItemDeposit(cart.getCartLineItems().get(0).getLineItemDeposit());
        orderLineItem.setLineItemDiscount(cart.getCartLineItems().get(0).getLineItemDiscount());
        orderLineItem.setLineItemBalance(cart.getCartLineItems().get(0).getLineItemBalance());

        com.mgmresorts.order.dto.ShowTotals showTotals = new com.mgmresorts.order.dto.ShowTotals();
        showTotals.setTotalDeliveryFee(cart.getShowTotals().getTotalDeliveryFee());
        showTotals.setTotalGratuity(cart.getShowTotals().getTotalGratuity());
        showTotals.setTotalLet(cart.getShowTotals().getTotalLet());
        showTotals.setTotalServiceChargeFee(cart.getShowTotals().getTotalServiceChargeFee());
        showTotals.setTotalServiceChargeTax(cart.getShowTotals().getTotalServiceChargeTax());
        showTotals.setTotalTransactionFee(cart.getShowTotals().getTotalTransactionFee());
        showTotals.setTotalTransactionTax(cart.getShowTotals().getTotalTransactionTax());
        showTotals.setTotalPrice(cart.getShowTotals().getTotalPrice());
        order.setShowTotals(showTotals);
        order.getOrderLineItems().add(orderLineItem);
        order.setStatus(Order.Status.PENDING);
        order.setCanRetryCheckout(false);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };

        final CheckoutResponse response = assertDoesNotThrow(() -> orderService.checkout(request));
        assertEquals(response.getOrder().getId(), "f694371e-a089-4f20-81c1-a66e09cf7b28");
        // validate line item prices
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemPrice(), 130.00);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemDeposit(), 130.00);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemTax(), 10.73);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemCharge(), 105.5500);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemDiscount(), 0.00);
        assertEquals(response.getOrder().getOrderLineItems().get(0).getLineItemBalance(), 0.00);
        // validate show total prices
        assertEquals(response.getOrder().getShowTotals().getTotalDeliveryFee(), 0.00);
        assertEquals(response.getOrder().getShowTotals().getTotalGratuity(), 0.00);
        assertEquals(response.getOrder().getShowTotals().getTotalLet(), 9.50);
        assertEquals(response.getOrder().getShowTotals().getTotalServiceChargeFee(), 13.72);
        assertEquals(response.getOrder().getShowTotals().getTotalServiceChargeTax(), 1.23);
        assertEquals(response.getOrder().getShowTotals().getTotalTransactionFee(), 0.00);
        assertEquals(response.getOrder().getShowTotals().getTotalTransactionTax(), 0.00);
        assertEquals(response.getOrder().getShowTotals().getTotalPrice(), 130.00);
        // validate order status
        assertEquals(response.getOrder().getStatus(), Order.Status.PENDING);
        assertEquals(response.getOrder().getCanRetryCheckout(), false);
    }

    @Test
    void checkoutTestWithoutItinerary_Show(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable ShowHandler showHandler,
                                           @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                           @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                           @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                           @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                           @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setItineraryId(null);
        request.setEnableJwb(false);
        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());
        
        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
        };
        new Expectations() {
            {
                itineraryHandler.create((GuestProfile) any, cart);
                result = "createdItineraryId";
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };

        assertDoesNotThrow(() -> orderService.checkout(request));
    }

    @Test
    void checkoutTestLoggedIn_Show(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable ShowHandler showHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                   @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess, @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                   @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                   @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);
        String resvResp = "resvResp";
        String gseId = "gse";
        String mlife = "mlife";

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setCustomerId(gseId);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                showHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false, null, null);
                result = resvResp;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };
        final CheckoutResponse response = orderService.checkout(request);
        assertEquals(response.getOrder().getCustomerId(), gseId);

    }

    @Test
    void checkoutTestMissingRequestPayload_Show(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                @Injectable ShowHandler showHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                @Injectable OrderTransformer cartToOrderTransformer,
                                                @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(null);
        });
        assertEquals(exception.getCode(), Errors.INVALID_PAYLOAD);

    }

    @Test
    void checkoutTestMissingBillings_Show(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                          @Injectable ShowHandler showHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,@Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                          @Injectable OrderTransformer cartToOrderTransformer,
                                          @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                          @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setBillings(null);

        Cart cart = getCart_Show();
        cart.setPriceDetails(new PriceDetails());
        cart.getPriceDetails().setTotalDeposit(1000.0);
        cart.setIsCheckoutEligible(true);
        cart.setPaymentRequired(true);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }

            {
                cartHandler.validateCartResponse(cart, "id", null);
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("At least one billing method is required."));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST);

    }

    @Test
    void checkoutTestMissingBillings_ShowMyVegasComp(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                     @Injectable ShowHandler showHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                     @Injectable OrderTransformer cartToOrderTransformer,
                                                     @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                     @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        request.setBillings(null);
        Cart cart = getCart_Show();
        cart.setPaymentRequired(false);
        cart.getCartLineItems().get(0).setPaymentRequired(false);
        cart.setIsCheckoutEligible(true);
        String resvResp = "resvResp";
        String gseId = "gse";
        String mlife = "mlife";

        Map<String, String> claims = new HashMap<>();
        claims.put("com.mgm.gse.id", gseId);
        claims.put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().setJwt(new Jwts.Jwt("guestToken", "algo", new Date(), claims, null, null, null, null));
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.SUCCESS);
        order.setCustomerId(gseId);
        order.setCanRetryCheckout(false);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);
        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                showHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false, null, null);
                result = resvResp;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }

        };
        final CheckoutResponse response = orderService.checkout(request);
        assertEquals(response.getOrder().getCustomerId(), gseId);
        assertEquals(Order.Status.SUCCESS, response.getOrder().getStatus());
        assertFalse(response.getOrder().getCanRetryCheckout());
    }

    @Test
    void checkoutTestMissingCartIdAndMgmId_Show(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                @Injectable ShowHandler showHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                @Injectable OrderTransformer cartToOrderTransformer,
                                                @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setMgmId(null);
        request.setCartId(null);

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("Either cart id or mgm id is required."));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST);

    }

    @Test
    void checkoutTestInsufficientFunds_Show(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                            @Injectable ShowHandler showHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                            @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                            @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.getBillings().get(0).getPayment().setAmount(10.0);

        Cart cart = getCart();
        cart.setPriceDetails(new PriceDetails());
        cart.getPriceDetails().setTotalDeposit(1000.0);
        cart.setIsCheckoutEligible(true);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }

            {
                cartHandler.validateCartResponse(cart, "id", null);
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertEquals(exception.getCode(), Errors.INSUFFICIENT_CHARGE_AMOUNT);

    }

    @Test
    void checkoutTestWhenNoCustIdInRequest_Show(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                @Injectable ShowHandler showHandler, @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess,
                                                @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);

        String resvResp = "resvResp";
        String custId = "guestprofileid";
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", null);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", null);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setCustomerId(custId);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {
            // {
//                registry.getAccessToken(anyString, anyString, anyString, anyString, anyString);
//                result = token;
//            }
            {
                profileHandler.createGuestProfile((CheckoutRequest) any);
                result = custId;
            }
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);
            }
            {
                showHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false, null, null);
                result = resvResp;
                minTimes = 0;
            }

            {
                cartToOrderTransformer.toRight((Order) any);
                result = new com.mgmresorts.order.entity.Order();
                minTimes = 0;
            }
            {
                orderAccess.create((com.mgmresorts.order.entity.Order) any);
                result = null;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals(custId, checkoutResponse.getOrder().getCustomerId());
    }

    @Test
    void checkoutTestWhenCartIsNotEligible_Show(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable ShowHandler showHandler,
                                                @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(false);
        cart.setPriceExpiresAt(ZonedDateTime.now().minusDays(1));

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("Cart is not eligible for checkout"));
        assertEquals(exception.getCode(), ApplicationError.NO_CHECKOUT_ELIGIBLE);
    }

    @Test
    void checkoutTestCompleteAntiFraudFailure_Show(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                   @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                   @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderTaskFactory task,
                                                   @Injectable Executors executors, @Mocked Result<OrderLineItem> firstResult, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                   @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    )
            throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set up orderLineItem
        OrderLineItem item = new OrderLineItem();
        item.setCartLineItemId("id");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        item.setOrderLineItemId("cart");

        // set error message returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_fraud_check_failed>[ Fraud check failed ]");
        error.setSourceSystemCode("620-2-240");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);
        orderLineItems.add(item);

        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.PAYMENT_FAILURE);
        order.getOrderLineItems().add(item);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }

        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals(Order.Status.PAYMENT_FAILURE, checkoutResponse.getOrder().getStatus());
        assertEquals("cart", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("620-2-240", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
    }

    @Test
    void checkoutTestCompletePaymentAuthorizationFailure_Show(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler,
                                                              @Injectable ItineraryHandler itineraryHandler, @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                              @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                              @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderTaskFactory task, @Injectable Executors executors, @Mocked Result<OrderLineItem> firstResult,
                                                              @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                              @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set up orderLineItem
        OrderLineItem item = new OrderLineItem();
        item.setCartLineItemId("id");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        item.setOrderLineItemId("cart");
        // set error message returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_auth_failed<[ Payment authorization failed ]");
        error.setSourceSystemCode("620-2-241");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);
        orderLineItems.add(item);

        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
        // List<Result<OrderLineItem>> tasks = new ArrayList<>();
        // tasks.add(firstResult);
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.PAYMENT_FAILURE);
        order.getOrderLineItems().add(item);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }

        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals(Order.Status.PAYMENT_FAILURE, checkoutResponse.getOrder().getStatus());
        assertEquals("cart", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("620-2-241", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
    }

    @Test
    void checkoutTestCompleteCVVFailure_Show(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                             @Injectable ProfileHandler profileHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                             @Injectable OrderTransformer cartToOrderTransformer, @Injectable OrderTaskFactory task,
                                             @Injectable Executors executors, @Mocked Result<OrderLineItem> firstResult, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                             @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    )
            throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set up orderLineItem
        OrderLineItem item = new OrderLineItem();
        item.setCartLineItemId("id");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        item.setOrderLineItemId("cart");
        // set error message returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_auth_rejected>[Authorization rejected]");
        error.setSourceSystemCode("620-2-242");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);
        orderLineItems.add(item);

        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
//        List<Result<OrderLineItem>> tasks = new ArrayList<>();
//        tasks.add(firstResult);
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.getOrderLineItems().add(item);
        order.setStatus(Order.Status.PAYMENT_FAILURE);
        order.setCanRetryCheckout(true);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }

        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals("cart", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("620-2-242", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals(Order.Status.PAYMENT_FAILURE, checkoutResponse.getOrder().getStatus());
        assertTrue(checkoutResponse.getOrder().getCanRetryCheckout());
    }

    @Test
    void checkoutCartInCaseOfAllProductCheckoutIsSuccessful_Show(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                                 @Injectable ShowHandler showHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                                 @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess, @Injectable OrderTransformer cartToOrderTransformer,
                                                                 @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                                 @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);
        String resvResp = "resvResp";
        String gseId = "gse";
        String mlife = "mlife";

        Map<String, String> claims = new HashMap<>();
        claims.put("com.mgm.gse.id", gseId);
        claims.put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().setJwt(new Jwts.Jwt("guestToken", "algo", new Date(), claims, null, null, null, null));
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.SUCCESS);
        order.setCustomerId(gseId);
        order.setCanRetryCheckout(false);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);
        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                showHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false, null, null);
                result = resvResp;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }

        };
        final CheckoutResponse response = orderService.checkout(request);
        assertEquals(response.getOrder().getCustomerId(), gseId);
        assertEquals(Order.Status.SUCCESS, response.getOrder().getStatus());
        assertFalse(response.getOrder().getCanRetryCheckout());
    }

    @Test
    void checkoutCartInCaseOfPartialProductFailure_Show(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable ShowHandler showHandler,
                                                        @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                        @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                        @Mocked Result<OrderLineItem> firstResult, @Mocked Result<OrderLineItem> secondResult, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                        @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    )
            throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will fail
        OrderLineItem item = new OrderLineItem();
        item.setOrderLineItemId("cartFailure");
        item.setCartLineItemId("idFailure");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        // -----------------------------
        // set product that will succeed
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("cartSuccess");
        itemTwo.setCartLineItemId("idSuccess");
        itemTwo.setStatus(OrderLineItem.Status.SUCCESS);
        // ----------------------------------------------
        // set error message that is returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_fraud_check_failed>[ Fraud check failed ]");
        error.setSourceSystemCode("620-2-240");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);

        orderLineItems.add(item);
        orderLineItems.add(itemTwo);

        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
//        List<Result<OrderLineItem>> tasks = new ArrayList<>();
//        tasks.add(firstResult);
//        tasks.add(secondResult);
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.getOrderLineItems().add(item);
        order.getOrderLineItems().add(itemTwo);
        order.setStatus(Order.Status.PARTIAL);
        order.setNewCartId("newId");
        order.setCanRetryCheckout(true);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }
//            {
//                cartHandler.handleCheckout(anyString, (List<String>) any, anyString, null);
//                result = "newId";
//            }
        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals("cartFailure", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("620-2-240", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals("cartSuccess", checkoutResponse.getOrder().getOrderLineItems().get(1).getOrderLineItemId());
        assertEquals("SUCCESS", checkoutResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals(Order.Status.PARTIAL, checkoutResponse.getOrder().getStatus());
        assertTrue(checkoutResponse.getOrder().getCanRetryCheckout());
        assertEquals("newId", checkoutResponse.getOrder().getNewCartId());
    }

    @Test
    void checkoutCartInCaseOfPartialProductFailureNoRetry_Show(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                               @Injectable ShowHandler showHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                               @Injectable OrderTransformer cartToOrderTransformer,
                                                               @Injectable ProfileHandler profileHandler, @Mocked Result<OrderLineItem> firstResult, @Mocked Result<OrderLineItem> secondResult,
                                                               @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                               @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();

        Collection<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will fail
        OrderLineItem item = new OrderLineItem();
        item.setOrderLineItemId("cartFailure");
        item.setCartLineItemId("idFailure");
        item.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
        // -----------------------------
        // set product that will succeed
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("cartSuccess");
        itemTwo.setCartLineItemId("idSuccess");
        itemTwo.setStatus(OrderLineItem.Status.SUCCESS);
        // ----------------------------------------------
        // set error message that is returned from RBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_fraud_check_failed>[ Fraud check failed ]");
        error.setSourceSystemCode("620-2-240");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        item.setMessages(messageList);

        orderLineItems.add(item);
        orderLineItems.add(itemTwo);

        Cart cart = getCart_Show();
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";
        // List<Result<OrderLineItem>> tasks = new ArrayList<>();
        // tasks.add(firstResult);
        // tasks.add(secondResult);

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.getOrderLineItems().add(item);
        order.getOrderLineItems().add(itemTwo);
        order.setStatus(Order.Status.PARTIAL);
        order.setCanRetryCheckout(false);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                cartHandler.validateCartResponse(cart, "id", null);

            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
            {
                tasks.getOutput();
                result = checkoutResponseMock;
            }
        };
        CheckoutResponse checkoutResponse = orderService.checkout(request);
        assertEquals("cartFailure", checkoutResponse.getOrder().getOrderLineItems().get(0).getOrderLineItemId());
        assertEquals("PAYMENT_FAILURE", checkoutResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("620-2-240", checkoutResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals("cartSuccess", checkoutResponse.getOrder().getOrderLineItems().get(1).getOrderLineItemId());
        assertEquals("SUCCESS", checkoutResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals(Order.Status.PARTIAL, checkoutResponse.getOrder().getStatus());
        assertFalse(checkoutResponse.getOrder().getCanRetryCheckout());
        assertEquals(null, checkoutResponse.getOrder().getNewCartId());
    }

    public Cart getCart_Show() {
        // Cart Response Mock Object Start
        List<CartLineItem> lineItems = new ArrayList<>();
        CartLineItem product = new CartLineItem();
        product.setProductId("id");
        product.setType(ItemType.SHOW);
        product.setLineItemPrice(130.0000);
        product.setLineItemDeposit(130.0000);
        product.setLineItemTax(10.7300);
        product.setLineItemTotalCharges(105.5500);
        product.setLineItemDiscount(0.0000);
        product.setLineItemBalance(0.0000);
        product.setPaymentRequired(true);
        product.setAdjustedItemSubtotal(0.0000);
        product.setContent(
                "{\"eventDate\":\"2022-12-05\",\"eventTime\":\"8:00 PM\",\"eventTz\":\"2022-12-06T04:00Z\",\"seasonId\":\"85639757-e023-47cd-a8db-81f2e6682614\",\"showEventId\":\"0fcf0099-e1c8-4d88-aa4f-1f4bf66f11dc\",\"propertyId\":\"607c07e7-3e31-4e4c-a4e1-f55dca66fea2\",\"comp\":false,\"hdePackage\":false,\"discounted\":false,\"permissibleDeliveryMethods\":[{\"default\":true,\"eprinting\":true,\"code\":\"TF\",\"amount\":0.0,\"description\":\"Tickets by Email and Mobile\",\"ePrinting\":true,\"name\":\"Ticket Fast\",\"active\":true,\"id\":\"5ccce20e-4cab-4f9c-aa0d-4ef30627e40e\"},{\"default\":false,\"eprinting\":false,\"code\":\"IC\",\"amount\":7.0,\"description\":\"Will Call ($7.00)\",\"ePrinting\":false,\"name\":\"Will Call $7\",\"active\":true,\"id\":\"3adaedd9-04b4-4b51-b842-e6e558f26522\"},{\"default\":false,\"eprinting\":false,\"code\":\"WC\",\"amount\":0.0,\"description\":\"Will Call\",\"ePrinting\":false,\"name\":\"Will Call\",\"active\":true,\"id\":\"5a0a68b8-13ca-43bc-9644-80becfbfc699\"},{\"default\":false,\"eprinting\":false,\"code\":\"DD\",\"amount\":0.0,\"description\":\"Delayed Ticket Delivery\",\"ePrinting\":false,\"name\":\"DELAYDLV\",\"active\":true,\"id\":\"f3ccda00-7fab-4f8d-9f08-cc0cb0999a1d\"}],\"tickets\":[{\"priceCode\":\"AN\",\"priceCodeDescription\":\"Category A\",\"holdClass\":\"OPEN\",\"basePrice\":105.55,\"discountedPrice\":105.55,\"holdId\":\"100505913\",\"holdDuration\":1652233945104,\"state\":\"HELD\",\"showEventId\":\"0fcf0099-e1c8-4d88-aa4f-1f4bf66f11dc\",\"ticketTypeCode\":\"_A\",\"ticketTypeCodeDescription\":\"Standard\",\"seat\":{\"seatNumber\":12,\"sectionName\":\"102\",\"rowName\":\"G\"}}],\"charges\":{\"discountedSubtotal\":105.5500,\"showSubtotal\":105.5500,\"let\":9.5000,\"deliveryFee\":0.0000,\"gratuity\":0.0000,\"reservationTotal\":130.0000,\"serviceCharge\":{\"amount\":14.9500,\"itemized\":{\"charge\":13.7200,\"tax\":1.2300}},\"transactionFee\":{\"amount\":0.0000,\"itemized\":{\"charge\":0.0000,\"tax\":0.0000}}}}");
        lineItems.add(product);

        Cart cart = new Cart();
        cart.setCartId("123");
        cart.setType(CartType.GLOBAL);
        cart.setCartLineItems(lineItems);
        cart.setPriceDetails(new PriceDetails());
        cart.getPriceDetails().setTotalPrice(130.0000);
        cart.getPriceDetails().setTotalDeposit(130.0000);
        com.mgmresorts.shopping.cart.dto.ShowTotals showTotals = new com.mgmresorts.shopping.cart.dto.ShowTotals();
        showTotals.setTotalDeliveryFee(0.0000);
        showTotals.setTotalGratuity(0.0000);
        showTotals.setTotalLet(9.5000);
        showTotals.setTotalServiceChargeFee(13.7200);
        showTotals.setTotalServiceChargeTax(1.2300);
        showTotals.setTotalTransactionFee(0.0000);
        showTotals.setTotalTransactionTax(0.0000);
        showTotals.setTotalPrice(130.0000);
        cart.setShowTotals(showTotals);
        cart.setPaymentRequired(true);
        OutHeader header = new OutHeader();
        header.setApiVersion("apiversion");
        header.setExecutionId("executionId");

        CartResponse cartResp = new CartResponse();
        cartResp.setCart(cart);
        cartResp.setHeader(header);
        // Cart Response Mock Object End
        return cart;
    }

    public Cart getCart_Dining() {
        // Cart Response Mock Object Start
        List<CartLineItem> lineItems = new ArrayList<>();
        CartLineItem product = new CartLineItem();
        product.setProductId("id");
        product.setType(ItemType.DINING);
        product.setPaymentRequired(false);
        product.setContent(
                "{\"restaurantId\":\"40872441-efdd-4189-a81c-d9da7f3fa803\",\"reservationDateTime\":\"2022-07-15 12:00:00\",\"partySize\":\"2\",\"reservationHoldId\":\"1657653672.141912\",\"accessPersistentId\":\"ahhzfnNldmVucm9vbXMtc2VjdXJlLWRlbW9yHAsSD25pZ2h0bG9vcF9WZW51ZRiAgJDQtOO6Cgw-1533844648.73-0.776669926593\",\"shiftPersistentId\":\"ahhzfnNldmVucm9vbXMtc2VjdXJlLWRlbW9yHAsSD25pZ2h0bG9vcF9WZW51ZRiAgJDQtOO6Cgw-LUNCH-1533676506.45\",\"holdDurationSec\":1800,\"propertyId\":\"66964e2b-2550-4476-84c3-1a4c0c5c067f\"}");
        lineItems.add(product);

        Cart cart = new Cart();
        cart.setCartId("123");
        cart.setType(CartType.GLOBAL);
        cart.setCartLineItems(lineItems);
        cart.setPaymentRequired(false);

        OutHeader header = new OutHeader();
        header.setApiVersion("apiversion");
        header.setExecutionId("executionId");

        CartResponse cartResp = new CartResponse();
        cartResp.setCart(cart);
        cartResp.setHeader(header);
        // Cart Response Mock Object End
        return cart;
    }

    @Test
    void setProfileFieldsFromExistingClaims(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
                                            @Injectable IOrderAccess orderAccess,
                                            @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                            @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                            @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                            @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException {

        final CheckoutRequest request = getCheckoutRequest();
        final Cart cart = getCart();
        cart.setIsCheckoutEligible(true);

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", "gse");
        ThreadContext.getContext().get().getJwt().getClaims().put("given_name", "first");
        ThreadContext.getContext().get().getJwt().getClaims().put("family_name", "last");
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.loyalty.tier", "sapphire");
        ThreadContext.getContext().get().getJwt().getClaims().put("birthdate", "1995-12-31");
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.loyalty.enrollment_date", "2020-12-31");
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };

        assertDoesNotThrow(() -> orderService.checkout(request));
    }

    @Test
    void profileFieldsNotInClaims(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
                                  @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                  @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                  @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                  @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                  @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException {

        final CheckoutRequest request = getCheckoutRequest();
        final Cart cart = getCart();
        cart.setIsCheckoutEligible(true);

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", "gse");
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };

        assertDoesNotThrow(() -> orderService.checkout(request));
    }

    @Test
    void checkoutNoItineraryNoGseIdFailure(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
                                           @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                           @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                           @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                           @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        final CheckoutRequest request = getCheckoutRequest();
        request.setItineraryId(null);
        request.getGuestProfile().setId(null);
        request.setEnableJwb(false);
        final Cart cart = getCart();
        cart.setIsCheckoutEligible(true);

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", "7777777");
        
        new Expectations() {
            {
                cartHandler.getCart(anyString, null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
        };

        final AppException appException = assertThrows(AppException.class, () -> orderService.checkout(request));
        assertEquals(appException.getCode(), ApplicationError.NO_GSE_ID_FOUND);
        assertEquals(appException.getHttpStatus().value(), 400);
    }

    @Test
    void readOrder_NoOrderFound(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess,
                                @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        new Expectations() {
            {
               orderAccess.read(anyString);
                result = null;
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.read("OrderId", null, null, null);
        });
        assertTrue(exception.getDescription().contains("Invalid parameters or resource not found"));
        assertEquals(exception.getCode(), Errors.REQUESTED_RESOURCE_NOT_FOUND);
    }

    @Test
    void readOrder_OrderFound(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                              @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess,
                              @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                              @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable OrderTransformer orderTransformer, @Injectable ProfileHandler profileHandler,
                              @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                              @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        com.mgmresorts.order.entity.Order orderEntity = new com.mgmresorts.order.entity.Order();
        orderEntity.setId("orderId");
        orderEntity.setStatus(OrderStatus.SUCCESS);
        orderEntity.setType(com.mgmresorts.order.entity.Type.GLOBAL);
        orderEntity.setVersion(com.mgmresorts.order.entity.Version.V1);
        Order order = new Order();
        order.setCartId(orderEntity.getCartId());
        order.setId(orderEntity.getId());
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", null);

        new Expectations() {
            {
                orderAccess.read(anyString);
                result = orderEntity;
            }
            {
                orderTransformer.toLeft((com.mgmresorts.order.entity.Order) any);
                result = order;
            }
        };

        CheckoutResponse response = orderService.read("orderId", com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1, null);
        assertEquals(response.getOrder().getId(), orderEntity.getId());
    }

    @Test
    void readOrder_AnonymousUserAttemptAccessToMemberOrder(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler,
                                                           @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess,
                                                           @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                           @Injectable IOrderConfirmationAccess orderConfirmationAccess,  @Injectable OrderTransformer orderTransformer,
                                                           @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                           @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        com.mgmresorts.order.entity.Order orderEntity = new com.mgmresorts.order.entity.Order();
        orderEntity.setId("orderId");
        orderEntity.setMgmId("member");
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", null);
        orderEntity.setStatus(OrderStatus.SUCCESS);
        orderEntity.setType(com.mgmresorts.order.entity.Type.GLOBAL);
        orderEntity.setVersion(com.mgmresorts.order.entity.Version.V1);

        new Expectations() {
            {
                orderAccess.read(anyString);
                result = orderEntity;
            }
        };

        AppException exception = assertThrows(AppException.class, () -> orderService.read("orderId", com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1, null));

        assertEquals(exception.getCode(), Errors.UNAUTHORIZED_ORDER_ACCESS);
    }

    @Test
    void readOrder_MemberUserAttemptAccessToDifferentMemberOrder(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler,
                                                                 @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                                                 @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                                 @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable OrderTransformer orderTransformer,
                                                                 @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                                 @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        com.mgmresorts.order.entity.Order orderEntity = new com.mgmresorts.order.entity.Order();
        orderEntity.setId("orderId");
        orderEntity.setMgmId("member1");
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "member2");
        orderEntity.setStatus(OrderStatus.SUCCESS);
        orderEntity.setType(com.mgmresorts.order.entity.Type.GLOBAL);
        orderEntity.setVersion(com.mgmresorts.order.entity.Version.V1);

        new Expectations() {
            {
                orderAccess.read(anyString);
                result = orderEntity;
            }
        };

        AppException exception = assertThrows(AppException.class, () -> orderService.read("orderId", com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1, null));

        assertEquals(exception.getCode(), Errors.UNAUTHORIZED_ORDER_ACCESS);
    }

    @Test
    void readOrder_MemberUserAttemptAccessToAnonymousOrder(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler,
                                                           @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess,
                                                           @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                           @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable OrderTransformer orderTransformer,
                                                           @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                           @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        com.mgmresorts.order.entity.Order orderEntity = new com.mgmresorts.order.entity.Order();
        orderEntity.setId("orderId");
        orderEntity.setMgmId(null);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "member");
        orderEntity.setStatus(OrderStatus.SUCCESS);
        orderEntity.setType(com.mgmresorts.order.entity.Type.GLOBAL);
        orderEntity.setVersion(com.mgmresorts.order.entity.Version.V1);

        new Expectations() {
            {
                orderAccess.read(anyString);
                result = orderEntity;
            }
        };

        AppException exception = assertThrows(AppException.class, () -> orderService.read("orderId", com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1, null));

        assertEquals(exception.getCode(), Errors.UNAUTHORIZED_ORDER_ACCESS);
    }

    @Test
    void readOrder_NoOrderId(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                             @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                             @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                             @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                             @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.read(null, null, null, null);
        });
        assertTrue(exception.getDescription().contains("order id is mandatory"));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST_INFORMATION);
    }

    @Test
    void checkoutTest_NonPackageTimeout(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
                                        @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                        @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                        @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                        @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable OrderTransformer orderTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                        @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException {

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart();
        cart.setMgmId("123");
        cart.setIsCheckoutEligible(true);
        final Order order = createOrderWithPendingStatus(cart.getCartLineItems(), cart, request);

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        String resvResp = "resvResp";

        final Result<CheckoutResponse> checkoutResult = new Result<>(new TimeoutException("Timeout occured"));
        final OrderTransformer transformer = new OrderTransformer();

        new Expectations() {
            {
                cartHandler.getCart(anyString, anyString, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }

            {
                roomHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false,null, null);
                result = resvResp;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
            {
                orders.createOrderWithPendingStatus((List<CartLineItem>) any, (Cart) any, (CheckoutRequest) any);
                result = order;
            }
            {
               orderAccess.read(anyString);
                result = transformer.toRight(order);
            }
            {
                orderTransformer.toLeft((com.mgmresorts.order.entity.Order) any);
                result = order;
            }
        };
        final CheckoutResponse response = orderService.checkout(request);
        assertEquals(response.getHeader().getStatus().getMessages().get(0).getText(), "Checkout for some of the cart items is still in progress");

    }

    @Test
    void checkoutTest_PackageTimeout(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
                                     @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                     @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                     @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                     @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable OrderTransformer orderTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                     @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException {

        CheckoutRequest request = getCheckoutRequest();
        request.setCartType(Type.PACKAGE);
        Cart cart = getShowPackageCart();
        cart.setIsCheckoutEligible(true);
        final Order order = createOrderWithPendingStatus(cart.getCartLineItems(), cart, request);
        order.setMgmId("123");
        order.getOrderLineItems().get(0).setStatus(OrderLineItem.Status.SUCCESS);
        order.getOrderLineItems().get(1).setStatus(OrderLineItem.Status.SUCCESS);
        Optional<PackageConfig[]> packageConfig = getPackageConfig();
        packageConfig.get()[0].setActive(true);

        String resvResp = "resvResp";

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");

        final Result<CheckoutResponse> checkoutResult = new Result<>(new TimeoutException("Timeout occured"));
        final OrderTransformer transformer = new OrderTransformer();

        new Expectations() {
            {
                cartHandler.getCart(anyString, anyString, com.mgmresorts.order.dto.services.Type.PACKAGE, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                roomHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false,null, null);
                result = resvResp;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
            {
                orders.createOrderWithPendingStatus((List<CartLineItem>) any, (Cart) any, (CheckoutRequest) any);
                result = order;
            }
            {
                contentAccess.getPackageConfigDetails(anyString);
                result = packageConfig;
            }
            {
               orderAccess.read(anyString);
                result = transformer.toRight(order);
            }
            {
                orderTransformer.toLeft((com.mgmresorts.order.entity.Order) any);
                result = order;
            }
        };
        final CheckoutResponse response = orderService.checkout(request);
        assertEquals(response.getHeader().getStatus().getMessages().get(0).getText(), "Checkout for some of the package items is still in progress");

    }

    @Test
    void checkoutTest_Dining(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable DiningHandler diningHandler,
                             @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                             @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                             @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                             @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart_Dining();
        cart.setIsCheckoutEligible(true);

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setId("f694371e-a089-4f20-81c1-a66e09cf7b28");
        OrderLineItem orderLineItem = new OrderLineItem();
        order.getOrderLineItems().add(orderLineItem);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };

        final CheckoutResponse response = assertDoesNotThrow(() -> orderService.checkout(request));
        assertEquals(response.getOrder().getId(), "f694371e-a089-4f20-81c1-a66e09cf7b28");
    }

    @Test
    void checkoutTestWithoutItinerary_Dining(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable DiningHandler diningHandler,
                                             @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                             @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                             @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                             @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                             @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setItineraryId(null);
        request.setEnableJwb(false);
        Cart cart = getCart_Dining();
        cart.setIsCheckoutEligible(true);

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());
        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 0;
            }
        };
        new Expectations() {
            {
                itineraryHandler.create((GuestProfile) any, cart);
                result = "createdItineraryId";
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };

        assertDoesNotThrow(() -> orderService.checkout(request));
    }

    @Test
    void checkoutTestLoggedIn_Dining(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable DiningHandler diningHandler,
                                     @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                     @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                     @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                     @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        Cart cart = getCart_Dining();
        cart.setIsCheckoutEligible(true);
        String resvResp = "resvResp";
        String gseId = "gse";
        String mlife = "mlife";

        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", gseId);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setCustomerId(gseId);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 0;
            }
            {
                diningHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false, null, null);
                result = resvResp;
                minTimes = 0;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };
        final CheckoutResponse response = orderService.checkout(request);
        assertEquals(response.getOrder().getCustomerId(), gseId);

    }

    @Test
    void checkoutTestMissingRequestPayload_Dining(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                  @Injectable DiningHandler diningHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                  @Injectable OrderTransformer cartToOrderTransformer,
                                                  @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                  @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(null);
        });
        assertEquals(exception.getCode(), Errors.INVALID_PAYLOAD);

    }

    @Test
    void checkoutTestMissingCartIdAndMgmId_Dining(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                  @Injectable IProductHandler diningHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                  @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                  @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                  @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setMgmId(null);
        request.setCartId(null);

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("Either cart id or mgm id is required."));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST);

    }

    @Test
    void checkoutTest_HyphenatedNamesInJWT(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
                                           @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                           @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                           @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                           @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                           @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        request.setItineraryId(null);
        Cart cart = getCart();

        String resvResp = "resvResp";

        ThreadContext.getContext().get().getJwt().getClaims().put("given_name", "-");
        ThreadContext.getContext().get().getJwt().getClaims().put("family_name", "-");
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", "mlife");

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setId("123");
        OrderLineItem orderLineItem = new OrderLineItem();
        orderLineItem.setLineItemPrice(cart.getCartLineItems().get(0).getLineItemPrice());
        orderLineItem.setLineItemDeposit(cart.getCartLineItems().get(0).getLineItemDeposit());
        orderLineItem.setLineItemTax(cart.getCartLineItems().get(0).getLineItemTax());
        orderLineItem.setLineItemCharge(cart.getCartLineItems().get(0).getLineItemTotalCharges());
        orderLineItem.setLineItemDiscount(cart.getCartLineItems().get(0).getLineItemDiscount());
        orderLineItem.setLineItemBalance(cart.getCartLineItems().get(0).getLineItemBalance());
        orderLineItem.setLineItemAdjustedItemSubtotal(cart.getCartLineItems().get(0).getAdjustedItemSubtotal());
        com.mgmresorts.order.dto.RoomTotals roomTotals = new com.mgmresorts.order.dto.RoomTotals();
        roomTotals.setTotalPrice(cart.getRoomTotals().getTotalPrice());
        roomTotals.setTotalTripSubtotal(cart.getRoomTotals().getTotalTripSubtotal());
        roomTotals.setTotalCasinoSurchargeAndTax(cart.getRoomTotals().getTotalCasinoSurchargeAndTax());
        roomTotals.setTotalResortFeeAndTax(cart.getRoomTotals().getTotalResortFeeAndTax());
        roomTotals.setTotalOccupancyFee(cart.getRoomTotals().getTotalOccupancyFee());
        roomTotals.setTotalResortFeePerNight(cart.getRoomTotals().getTotalResortFeePerNight());
        roomTotals.setTotalTourismFeeAndTax(cart.getRoomTotals().getTotalTourismFeeAndTax());
        order.setRoomTotals(roomTotals);
        order.getOrderLineItems().add(orderLineItem);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);
        
        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 0;
            }
        };
        new Expectations() {
            {
                itineraryHandler.create((GuestProfile) any, (Cart) any);
                result = "createdItineraryId";
            }
        };
        new Expectations() {
            {
                roomHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false,null, null);
                result = resvResp;
                minTimes = 0;
            }
        };
        new Expectations() {
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };
        final CheckoutResponse response = assertDoesNotThrow(() -> orderService.checkout(request));
        assertEquals(response.getOrder().getId(), "123");

        new Verifications() {
            {
                GuestProfile guest;
                itineraryHandler.create(guest = (GuestProfile) withCapture(), (Cart) any);
                assertEquals(guest.getFirstName(), "first");
                assertEquals(guest.getLastName(), "last");
            }
        };

    }

    @Test
    void checkoutTest_ValidNamesInJWT(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
                                      @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                      @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                      @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                      @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                      @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        request.setItineraryId(null);
        Cart cart = getCart();

        String resvResp = "resvResp";

        ThreadContext.getContext().get().getJwt().getClaims().put("given_name", "new first");
        ThreadContext.getContext().get().getJwt().getClaims().put("family_name", "new last");
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.mlife_number", "mlife");

        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setId("123");
        OrderLineItem orderLineItem = new OrderLineItem();
        orderLineItem.setLineItemPrice(cart.getCartLineItems().get(0).getLineItemPrice());
        orderLineItem.setLineItemDeposit(cart.getCartLineItems().get(0).getLineItemDeposit());
        orderLineItem.setLineItemTax(cart.getCartLineItems().get(0).getLineItemTax());
        orderLineItem.setLineItemCharge(cart.getCartLineItems().get(0).getLineItemTotalCharges());
        orderLineItem.setLineItemDiscount(cart.getCartLineItems().get(0).getLineItemDiscount());
        orderLineItem.setLineItemBalance(cart.getCartLineItems().get(0).getLineItemBalance());
        orderLineItem.setLineItemAdjustedItemSubtotal(cart.getCartLineItems().get(0).getAdjustedItemSubtotal());
        com.mgmresorts.order.dto.RoomTotals roomTotals = new com.mgmresorts.order.dto.RoomTotals();
        roomTotals.setTotalPrice(cart.getRoomTotals().getTotalPrice());
        roomTotals.setTotalTripSubtotal(cart.getRoomTotals().getTotalTripSubtotal());
        roomTotals.setTotalCasinoSurchargeAndTax(cart.getRoomTotals().getTotalCasinoSurchargeAndTax());
        roomTotals.setTotalResortFeeAndTax(cart.getRoomTotals().getTotalResortFeeAndTax());
        roomTotals.setTotalOccupancyFee(cart.getRoomTotals().getTotalOccupancyFee());
        roomTotals.setTotalResortFeePerNight(cart.getRoomTotals().getTotalResortFeePerNight());
        roomTotals.setTotalTourismFeeAndTax(cart.getRoomTotals().getTotalTourismFeeAndTax());
        order.setRoomTotals(roomTotals);
        order.getOrderLineItems().add(orderLineItem);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);
        
        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentHandler.validatePaymentMethod((CheckoutRequest) any);
                result = true;
                minTimes = 0;
            }
        };
        new Expectations() {
            {
                itineraryHandler.create((GuestProfile) any, (Cart) any);
                result = "createdItineraryId";
            }
        };
        new Expectations() {
            {
                roomHandler.reserve((CheckoutRequest) any, (List<CartLineItem>) any, (List<OrderLineItem>) any, (AppliedBillings) any, anyString, (AgentInfo) any, false, false,null, null);
                result = resvResp;
                minTimes = 0;
            }
        };
        new Expectations() {
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };
        final CheckoutResponse response = assertDoesNotThrow(() -> orderService.checkout(request));
        assertEquals(response.getOrder().getId(), "123");

        new Verifications() {
            {
                GuestProfile guest;
                itineraryHandler.create(guest = (GuestProfile) withCapture(), (Cart) any);
                assertEquals(guest.getFirstName(), "new first");
                assertEquals(guest.getLastName(), "new last");
            }
        };

    }

    @Test
    void checkoutTestPackageCartUpsellFlagFalse(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                                @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable OrderTransformer cartToOrderTransformer,
                                                @Injectable ProfileHandler profileHandler, @Mocked IProductHandler showHandler, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Mocked Result<CheckoutResponse> checkoutResponse, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        final CheckoutRequest request = getCheckoutRequest();
        final Cart cart = getCart();
        cart.getCartLineItems().get(0).setUpgraded(false);
        final Order order = createOrderWithPendingStatus(cart.getCartLineItems(), cart, request);
        order.getOrderLineItems().get(0).setStatus(OrderLineItem.Status.SUCCESS);

        List<Result<CheckoutResponse>> tasks = new ArrayList<>();
        tasks.add(checkoutResponse);

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                checkoutResponse.getOutput();
                result = getCheckoutResposne();
            }
            {
                orders.createOrderWithPendingStatus((List<CartLineItem>) any, (Cart) any, (CheckoutRequest) any);
                result = order;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };
        orderService.checkout(request);
        assertEquals(false, order.getOrderLineItems().get(0).getUpgraded());
    }

    @Test
    void checkoutTestPackageCartUpsellFlagTrue(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                               @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                               @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler, @Mocked IProductHandler showHandler,
                                               @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Mocked Result<CheckoutResponse> checkoutResponse, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                               @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        final CheckoutRequest request = getCheckoutRequest();
        final Cart cart = getCart();
        cart.getCartLineItems().get(0).setUpgraded(true);
        final Order order = createOrderWithPendingStatus(cart.getCartLineItems(), cart, request);
        order.getOrderLineItems().get(0).setStatus(OrderLineItem.Status.SUCCESS);

        List<Result<CheckoutResponse>> tasks = new ArrayList<>();
        tasks.add(checkoutResponse);

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                checkoutResponse.getOutput();
                result = getCheckoutResposne();
            }
            {
                orders.createOrderWithPendingStatus((List<CartLineItem>) any, (Cart) any, (CheckoutRequest) any);
                result = order;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };
        orderService.checkout(request);
        assertEquals(true, order.getOrderLineItems().get(0).getUpgraded());
    }

    @Test
    void checkoutTestPackageCartV2(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable IProductHandler roomHandler,
                                 @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                 @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler, @Mocked IProductHandler showHandler,
                                 @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Mocked Result<CheckoutResponse> checkoutResponse, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                 @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, IOException {

        final CheckoutRequest request = getCheckoutRequestPaymentWidgetFlow();
        final Cart cart = getPackageCartV2();
        final Order order = createOrderWithPendingStatusPackageV2(cart.getCartLineItems(), cart, request);
        cart.setPriceExpiresAt(ZonedDateTime.now().minusDays(1));
        List<Result<CheckoutResponse>> tasks = new ArrayList<>();
        tasks.add(checkoutResponse);

        final Result<CheckoutResponse> checkoutResult = new Result<>(new CheckoutResponse());

        new Expectations() {
            {
                cartHandler.getCart("id", null, Type.PACKAGE, Version.V2);
                result = cart;
            }
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = getPackageSessionResponse();
                times = 1;
            }
            {
                profileHandler.createGuestProfile((CheckoutRequest) any);
                result = "1234";
            }
            {
                itineraryHandler.create((GuestProfile) any, (Cart) any);
                result = "createdItineraryId";
            }
            {
                orders.createOrderWithPendingStatus((List<CartLineItem>) any, (Cart) any, (CheckoutRequest) any);
                result = order;
            }
            {
                checkoutResponse.getOutput();
                result = getCheckoutResponsePackageV2();
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = checkoutResult;
            }
        };
        CheckoutResponse checkOutResp = orderService.checkout(request);
        assertEquals(checkOutResp.getOrder().getStatus(), com.mgmresorts.order.dto.services.Order.Status.SUCCESS);
        assertEquals(checkOutResp.getOrder().getType(), Type.PACKAGE);
        assertEquals(checkOutResp.getOrder().getVersion(), Version.V2);
        assertNotNull(checkOutResp.getOrder().getPackageConfigDetails());
    }

    @Test
    void checkoutTest_Cart_DoesNotExist_InCartDataStore_CartAlready_CheckedOut(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
                                                                               @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                                               @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                                               @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                                               @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                                               @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setCartId("cartId123");

        com.mgmresorts.order.entity.Order orderEntity = new com.mgmresorts.order.entity.Order();
        orderEntity.setId("orderId123");
        orderEntity.setCartId("cartId123");
        orderEntity.setMgmId(null);
        orderEntity.setStatus(OrderStatus.SUCCESS);
        orderEntity.setType(com.mgmresorts.order.entity.Type.GLOBAL);
        orderEntity.setVersion(com.mgmresorts.order.entity.Version.V1);

        Order order = new Order();
        order.setCartId(orderEntity.getCartId());
        order.setId(orderEntity.getId());

        new Expectations() {
            {
                cartHandler.getCart("cartId123", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = new AppException(Errors.UNABLE_TO_GET_CART, new HttpFailureException(404, cartService404ErrorResponseWithCorrectErrorCode(), "Error while calling http endpoint: Resource Not Found"), "The cart id was: cartId123 and the mgmId was: null");
            }
            {
                orderAccess.getCheckedOutOrderByCartId("cartId123", (com.mgmresorts.order.entity.Type)any, (com.mgmresorts.order.entity.Version)any);
                result = orderEntity;
            }
            {
                cartToOrderTransformer.toLeft((com.mgmresorts.order.entity.Order) any);
                result = order;
            }
        };

        final CheckoutResponse response = assertDoesNotThrow(() -> orderService.checkout(request));
        assertEquals(response.getOrder().getId(), "orderId123");
        assertEquals(response.getOrder().getCartId(), "cartId123");
    }

    @Test
    void checkoutTest_Cart_Non404Error_FromCartService(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                       @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                       @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                       @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                       @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                       @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setCartId("cartId123");

        new Expectations() {
            {
                cartHandler.getCart("cartId123", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = new AppException(Errors.UNABLE_TO_GET_CART, new HttpFailureException(400, cartService400ErrorResponse(), "Error while calling http endpoint: Bad Request"), "The cart id was: cartId123 and the mgmId was: null");
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("Could not retrieve the cart"));
        assertEquals(exception.getCode(), Errors.UNABLE_TO_GET_CART);
    }

    @Test
    void checkoutTest_Cart_DoesNotExist_InCartDataStore_Cart_DoesNotExist_InOrderDataStore(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                                                           @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                                                           @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                                                           @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                                                           @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                                                           @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setCartId("cartId123");

        new Expectations() {
            {
                cartHandler.getCart("cartId123", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = new AppException(Errors.UNABLE_TO_GET_CART, new HttpFailureException(404, cartService404ErrorResponseWithCorrectErrorCode(), "Error while calling http endpoint: Resource Not Found"), "The cart id was: cartId123 and the mgmId was: null");
            }
            {
                orderAccess.getCheckedOutOrderByCartId("cartId123", (com.mgmresorts.order.entity.Type)any, (com.mgmresorts.order.entity.Version)any);
                result = null;
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("Could not retrieve the cart"));
        assertEquals(exception.getCode(), Errors.UNABLE_TO_GET_CART);
    }

    @Test
    void checkoutTest_Cart_404Error_FromCartService_ButDifferentErrorCode(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                                          @Injectable IProductHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                                          @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                                          @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
                                                                          @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                                          @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer
    ) throws AppException, HttpFailureException {

        CheckoutRequest request = getCheckoutRequest();
        request.setCartId("cartId123");

        new Expectations() {
            {
                cartHandler.getCart("cartId123", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = new AppException(Errors.UNABLE_TO_GET_CART, new HttpFailureException(404, cartService404ErrorResponseButDifferentErrorCode(), "Error while calling http endpoint: Resource Not Found"), "The cart id was: cartId123 and the mgmId was: null");
            }
        };

        AppException exception = assertThrows(AppException.class, () -> {
            orderService.checkout(request);
        });
        assertTrue(exception.getDescription().contains("Could not retrieve the cart"));
        assertEquals(exception.getCode(), Errors.UNABLE_TO_GET_CART);
    }
    
    @Test
    void checkoutTest_Cart_CheckedOut_NotInProgress(@Injectable CartHandler cartHandler,
            @Injectable ItineraryHandler itineraryHandler, @Injectable RoomHandler roomHandler,
            @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
            @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
            @Injectable OrderTransformer cartToOrderTransformer, @Injectable ProfileHandler profileHandler,
            @Injectable OrderFinancialImpactTransformer orderFiTransformer,
            @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
            @Injectable IPaymentSessionShowHandler paymentSessionShowHandler,
            @Injectable IDiningBookingAccess diningBookingAccess,
            @Injectable IPaymentSessionAccess paymentSessionAccess,
            @Injectable IPaymentProcessingHandler paymentProcessingHandler,
            @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer) throws AppException, HttpFailureException {
        
        CheckoutRequest request = getCheckoutRequest();
        request.setCartId("cartId123");
        
        Cart cart = getCart();
        cart.setCartId("cartId123");

        CheckoutResponse checkoutResponse = new CheckoutResponse();
        Order order = new Order();
        order.setId("orderId123");
        order.setCartId("cartId123");
        order.setStatus(Order.Status.SUCCESS);
        OrderLineItem orderLineItem = new OrderLineItem();
        order.getOrderLineItems().add(orderLineItem);
        checkoutResponse.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponse);

        new Expectations() {
            {
                cartHandler.getCart("cartId123", null, com.mgmresorts.order.dto.services.Type.GLOBAL,
                        com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }
        };
        final CheckoutResponse response = assertDoesNotThrow(() -> orderService.checkout(request));
        assertEquals(Order.Status.SUCCESS, response.getOrder().getStatus());
        assertEquals(response.getOrder().getId(), "orderId123");
        assertEquals(response.getOrder().getCartId(), "cartId123");
    }

    @Test
    void checkoutGlobalCartPaymentProcessingSuccess(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                    @Injectable RoomHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                    @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess, @Injectable OrderTransformer cartToOrderTransformer,
                                                    @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                    @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        request.setPaymentCaptured(true);
        Cart cart = getCart();
        cart.setPaymentSessionId("sessionId");
        cart.setPaymentRequired(true);
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";

        Map<String, String> claims = new HashMap<>();
        claims.put("com.mgm.gse.id", gseId);
        claims.put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().setJwt(new Jwts.Jwt("guestToken", "algo", new Date(), claims, null, null, null, null));
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.SUCCESS);
        order.setCustomerId(gseId);
        ;
        order.setCanRetryCheckout(false);
        checkoutResponseMock.setOrder(order);
        Result<CheckoutResponse> tasks = new Result<CheckoutResponse>(checkoutResponseMock);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = getSessionResponse();
                times = 1;
            }
            {
                executor.invoke((Task<CheckoutResponse>) any, anyInt);
                result = tasks;
            }

        };
        final CheckoutResponse response = orderService.checkout(request);
        assertEquals(response.getOrder().getCustomerId(), gseId);
        assertEquals(Order.Status.SUCCESS, response.getOrder().getStatus());
        assertFalse(response.getOrder().getCanRetryCheckout());
    }

    @Test
    void checkoutGlobalCartPaymentProcessingFailure(@Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                    @Injectable RoomHandler roomHandler, @Injectable IOrderAccess orderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                    @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess, @Injectable OrderTransformer cartToOrderTransformer,
                                                    @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                    @Injectable OrderCheckoutEmailEventTransformer orderCheckoutEmailEventTransformer) throws AppException, HttpFailureException {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());

        CheckoutRequest request = getCheckoutRequest();
        request.setPaymentCaptured(true);
        Cart cart = getCart();
        cart.setPaymentSessionId("sessionId");
        cart.setPaymentRequired(true);
        cart.setIsCheckoutEligible(true);
        String gseId = "gse";
        String mlife = "mlife";

        Map<String, String> claims = new HashMap<>();
        claims.put("com.mgm.gse.id", gseId);
        claims.put("com.mgm.mlife_number", mlife);
        ThreadContext.getContext().get().setJwt(new Jwts.Jwt("guestToken", "algo", new Date(), claims, null, null, null, null));
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
        CheckoutResponse checkoutResponseMock = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.SUCCESS);
        order.setCustomerId(gseId);
        order.setCanRetryCheckout(false);
        checkoutResponseMock.setOrder(order);

        new Expectations() {
            {
                cartHandler.getCart("id", null, com.mgmresorts.order.dto.services.Type.GLOBAL, com.mgmresorts.order.dto.services.Version.V1);
                result = cart;
            }
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = null;
                times = 1;
            }
        };

        assertThrows(AppException.class,() -> orderService.checkout(request));
    }

    private PaymentSessionBaseFields getSessionResponse() {
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setBillings(getBillings());
        final Map<String, PaymentAuthFields> paymentIdMapping = new HashMap<>();
        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentIdMapping.put("1234", paymentAuthFields);
        paymentSessionBaseFields.setPaymentAuthFieldsMap(paymentIdMapping);
        return paymentSessionBaseFields;
    }

    private PaymentSessionBaseFields getPackageSessionResponse() {
        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        paymentSessionBaseFields.setBillings(getBillings());
        GuestProfile guestProfile = new GuestProfile();
        guestProfile.setId("guestprofileid");
        guestProfile.setFirstName("first");
        guestProfile.setLastName("last");
        guestProfile.setPerpetualOfferEligible(false);
        paymentSessionBaseFields.setGuestProfile(guestProfile);
        final Map<String, PaymentAuthFields> paymentIdMapping = new HashMap<>();
        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentIdMapping.put("1234", paymentAuthFields);
        paymentSessionBaseFields.setPaymentAuthFieldsMap(paymentIdMapping);
        return paymentSessionBaseFields;
    }

    public Cart getShowPackageCart() {
        // Cart Response Mock Object Start
        List<CartLineItem> lineItems = new ArrayList<>();
        CartLineItem product = new CartLineItem();
        product.setProductId("id");
        product.setCartLineItemId("1234");
        product.setType(ItemType.SHOW);
        product.setPackageId("1234");
        product.setLineItemDeposit(100.00);
        product.setLineItemPrice(50.00);
        product.setUpgraded(false);
        product.setContent(
                "{\"eventDate\":\"2021-11-10\",\"eventTime\":\"8:00 PM\",\"eventTz\":\"2021-11-11T04:00Z\",\"seasonId\":\"7095c5b1-d871-4fbd-9bef-2aeb3a622bce\",\"showEventId\":\"2780bfde-a48e-463d-bdcb-753a07c98145\",\"comp\":false,\"hdePackage\":false,\"discounted\":false,\"permissibleDeliveryMethods\":[{\"default\":true,\"code\":\"TF\",\"amount\":0.0,\"name\":\"Ticket Fast\",\"active\":true,\"id\":\"5ccce20e-4cab-4f9c-aa0d-4ef30627e40e\"}],\"tickets\":[{\"priceCode\":\"AN\",\"priceCodeDescription\":\"Category A\",\"holdClass\":\"OPEN\",\"basePrice\":75.0,\"discountedPrice\":75.0,\"holdId\":\"100310525\",\"holdDuration\":1636144468392,\"state\":\"HELD\",\"showEventId\":\"2780bfde-a48e-463d-bdcb-753a07c98145\",\"ticketTypeCode\":\"_A\",\"seat\":{\"seatNumber\":4,\"sectionName\":\"SEC1\",\"rowName\":\"B\"}}],\"charges\":{\"discountedSubtotal\":75.0000,\"showSubtotal\":75.0000,\"let\":6.7500,\"deliveryFee\":0.0000,\"gratuity\":0.0000,\"reservationTotal\":91.2500,\"serviceCharge\":{\"amount\":9.5000,\"itemized\":{\"charge\":8.7200,\"tax\":0.7800}},\"transactionFee\":{\"amount\":0.0000,\"itemized\":{\"charge\":0.0000,\"tax\":0.0000}}}}");
        lineItems.add(product);

        CartLineItem roomProduct = new CartLineItem();
        roomProduct.setProductId("id");
        roomProduct.setCartLineItemId("5678");
        roomProduct.setType(ItemType.ROOM);
        roomProduct.setLineItemDeposit(100.00);
        roomProduct.setLineItemPrice(50.00);
        roomProduct.setUpgraded(false);
        roomProduct.setContent(
                "{\"propertyId\":\"e2704b04-d515-45b0-8afd-4fa1424ff0a8\",\"roomTypeId\":\"9401af33-8386-4958-9b8e-3d890b732b2a\",\"programId\":\"279188f0-2e78-4f54-a4c2-703a2c52d0e6\",\"customerId\":\"0\",\"guaranteeCode\":\"CC\",\"tripDetails\":{\"checkInDate\":\"2021-01-13\",\"checkOutDate\":\"2021-01-14\",\"numAdults\":1,\"numChildren\":0,\"numRooms\":1},\"bookings\":[{\"date\":\"2021-01-13\",\"basePrice\":100014.99,\"customerPrice\":100014.99,\"price\":100014.99,\"programIdIsRateTable\":false,\"overridePrice\":-1,\"overrideProgramIdIsRateTable\":false,\"isComp\":false,\"resortFeeIsSpecified\":false,\"resortFee\":0,\"programId\":\"279188f0-2e78-4f54-a4c2-703a2c52d0e6\",\"pricingRuleId\":\"fa1d4b3c-50bd-4fbf-8c9b-695c2c49f583\"}],\"chargesAndTaxes\":{\"charges\":[{\"date\":\"2021-01-13\",\"amount\":100059.99,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":100014.99,\"item\":\"Room Charge\"},{\"itemType\":\"ExtraGuestCharge\",\"amount\":0,\"item\":\"Extra Guest Charge\"},{\"itemType\":\"ResortFee\",\"amount\":45,\"item\":\"Resort Fee\"}]}],\"taxesAndFees\":[{\"date\":\"2021-01-13\",\"amount\":13388.03,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":13382.01,\"item\":\"Room Tax\"},{\"itemType\":\"ExtraGuestChargeTax\",\"amount\":0,\"item\":\"Extra Guest Tax\"},{\"itemType\":\"ResortFeeTax\",\"amount\":6.02,\"item\":\"Resort Fee Tax\"}]}]},\"amountDue\":0,\"ratesSummary\":{\"roomSubtotal\":100014.9900,\"programDiscount\":0.0000,\"discountedSubtotal\":100014.9900,\"roomRequestsTotal\":0.0000,\"adjustedRoomSubtotal\":100014.9900,\"resortFeeAndTax\":51.0200,\"roomChargeTax\":13382.0100,\"reservationTotal\":113448.0200,\"depositDue\":113397.0000,\"balanceUponCheckIn\":51.0200},\"depositDetails\":{\"dueDate\":\"2021-01-11\",\"amountPolicy\":\"Nights\",\"amount\":113397,\"forfeitDate\":\"2021-01-10\",\"forfeitAmount\":113397,\"overrideAmount\":-1,\"depositRuleCode\":\"1NT\",\"cancellationRuleCode\":\"72H\",\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":100014.99,\"item\":\"Room Charge\"},{\"itemType\":\"RoomChargeTax\",\"amount\":13382.01,\"item\":\"Room Tax\"}]},\"depositPolicy\":{\"depositRequired\":true,\"creditCardRequired\":true},\"markets\":[{\"date\":\"2021-01-13\",\"marketCode\":\"TFIT\",\"sourceCode\":\"TFITIWB\"}]}");
        lineItems.add(roomProduct);

        Cart cart = new Cart();
        cart.setCartId("id");
        cart.setType(CartType.PACKAGE);
        cart.setVersion(CartVersion.V1);
        // cart.setTotalBalanceDue(0.00);
        cart.setCartLineItems(lineItems);
        cart.setPriceDetails(new PriceDetails());
        cart.getPriceDetails().setTotalDeposit(200.00);
        OutHeader header = new OutHeader();
        header.setApiVersion("apiversion");
        header.setExecutionId("executionId");

        CartResponse cartResp = new CartResponse();
        cartResp.setCart(cart);
        cartResp.setHeader(header);
        // Cart Response Mock Object End
        return cart;
    }

    public Cart getPackageCartV2() throws IOException {
        String cart = Utils.readFileFromClassPath("data/packageCartV2.json");
        CartResponse cartResponse = mapper.readValue(cart, CartResponse.class);
        return cartResponse.getCart();
    }

    private Optional<PackageConfig[]> getPackageConfig() {
        List<PackageConfig> packageConfigs = new ArrayList<>();
        PackageConfig packageConfig = new PackageConfig();
        packageConfig.setActive(false);
        packageConfig.setPackageCategoryId("Bruno21-1");
        packageConfig.setPackageId("Bruno21-1-2021-10-14");
        packageConfig.setShowEventDate("2021-10-14");
        packageConfig.setShowProgramId("EDDC753A-F5D7-4E2B-915A-4157F19BF5A4");
        packageConfig.setSegmentId("T21O141P");
        packageConfig.setMinLos(2d);
        packageConfig.setPackageTier(1d);
        packageConfigs.add(packageConfig);

        return Optional.ofNullable(packageConfigs.toArray(new PackageConfig[packageConfigs.size()]));
    }

    private CheckoutResponse getCheckoutResposne() {
        CheckoutResponse cartResp = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.SUCCESS);
        cartResp.setOrder(order);
        return cartResp;
    }

    private CheckoutResponse getCheckoutResponsePackageV2() {
        CheckoutResponse checkoutResponse = new CheckoutResponse();
        Order order = new Order();
        order.setStatus(Order.Status.SUCCESS);
        order.setType(com.mgmresorts.order.dto.services.Type.PACKAGE);
        order.setVersion(Version.V2);
        PackageConfigDetails packageConfigDetails = new PackageConfigDetails();
        packageConfigDetails.setPackageCategoryId("Bruno21-1");
        packageConfigDetails.setPackagePriceBreakdown("BAYG");
        PackagePricingDetails packagePricingDetails = new PackagePricingDetails();
        packagePricingDetails.setPackageBaseTotal(500.0);
        packagePricingDetails.setPackageTotal(500.0);
        packagePricingDetails.setPackageStartingPrice(500.0);
        packagePricingDetails.setRoomModification(0.0);
        packagePricingDetails.setShowModification(0.0);
        packagePricingDetails.setRoomTotal(250.0);
        packagePricingDetails.setShowTotal(250.0);
        packagePricingDetails.setPackageInclusions(new ArrayList<>());
        packageConfigDetails.setPackagePricingDetails(packagePricingDetails);
        order.setPackageConfigDetails(packageConfigDetails);
        checkoutResponse.setOrder(order);
        return checkoutResponse;
    }

    public Order createOrderWithPendingStatus(List<CartLineItem> cartLineItems, Cart cart, CheckoutRequest request) {
        final Order order = new Order();
        order.setId(UUID.randomUUID().toString());

        for (CartLineItem cartLineItem : cartLineItems) {
            final OrderLineItem oli = new OrderLineItem();
            oli.setOrderLineItemId(UUID.randomUUID().toString());
            oli.setCartLineItemId(cartLineItem.getCartLineItemId());
            oli.setProductId(cartLineItem.getProductId());
            oli.setPropertyId(cartLineItem.getPropertyId());
            oli.setPackageId(cartLineItem.getPackageId());
            oli.setPackageLineItemId(cartLineItem.getPackageLineItemId());
            oli.setContent(cartLineItem.getContent());
            oli.setProductType(OrderLineItem.ProductType.fromValue(cartLineItem.getType().value()));
            oli.setStatus(OrderLineItem.Status.PENDING);
            oli.setEnableJwb(cartLineItem.getEnableJwb());
            oli.setUpgraded(cartLineItem.getUpgraded());
            order.getOrderLineItems().add(oli);
        }

        order.setCartId(cart.getCartId());
        order.setCustomerId(request.getGuestProfile().getId());
        order.setMgmId(cart.getMgmId());
        order.setEnableJwb(request.getEnableJwb());
        order.setType(com.mgmresorts.order.dto.services.Type.fromValue(cart.getType().value()));
        order.setVersion(com.mgmresorts.order.dto.services.Version.fromValue(cart.getVersion().value()));
        order.setStatus(Order.Status.PENDING);
        order.setCanRetryCheckout(false);

        return order;
    }

    public Order createOrderWithPendingStatusPackageV2(List<CartLineItem> cartLineItems, Cart cart, CheckoutRequest request) {
        final Order order = new Order();
        order.setId(UUID.randomUUID().toString());

        for (CartLineItem cartLineItem : cartLineItems) {
            final OrderLineItem oli = new OrderLineItem();
            oli.setOrderLineItemId(UUID.randomUUID().toString());
            oli.setCartLineItemId(cartLineItem.getCartLineItemId());
            oli.setProductId(cartLineItem.getProductId());
            oli.setPropertyId(cartLineItem.getPropertyId());
            oli.setPackageId(cartLineItem.getPackageId());
            oli.setPackageLineItemId(cartLineItem.getPackageLineItemId());
            oli.setContent(cartLineItem.getContent());
            oli.setProductType(OrderLineItem.ProductType.fromValue(cartLineItem.getType().value()));
            oli.setStatus(OrderLineItem.Status.PENDING);
            oli.setEnableJwb(cartLineItem.getEnableJwb());
            oli.setUpgraded(cartLineItem.getUpgraded());
            order.getOrderLineItems().add(oli);
        }

        PackageConfigDetails packageConfigDetails = new PackageConfigDetails();
        packageConfigDetails.setPackageCategoryId(cart.getPackageConfigDetails().getPackageCategoryId());
        packageConfigDetails.setPackagePriceBreakdown(cart.getPackageConfigDetails().getPackagePriceBreakdown());
        PackagePricingDetails packagePricingDetails = new PackagePricingDetails();
        packagePricingDetails.setPackageBaseTotal(cart.getPackageConfigDetails().getPackagePricingDetails().getPackageBaseTotal());
        packagePricingDetails.setPackageTotal(cart.getPackageConfigDetails().getPackagePricingDetails().getPackageTotal());
        packagePricingDetails.setPackageStartingPrice(cart.getPackageConfigDetails().getPackagePricingDetails().getPackageStartingPrice());
        packagePricingDetails.setRoomModification(cart.getPackageConfigDetails().getPackagePricingDetails().getRoomModification());
        packagePricingDetails.setShowModification(cart.getPackageConfigDetails().getPackagePricingDetails().getShowModification());
        packagePricingDetails.setRoomTotal(cart.getPackageConfigDetails().getPackagePricingDetails().getRoomTotal());
        packagePricingDetails.setShowTotal(cart.getPackageConfigDetails().getPackagePricingDetails().getShowTotal());
        packageConfigDetails.setPackagePricingDetails(packagePricingDetails);
        order.setPackageConfigDetails(packageConfigDetails);


        order.setCartId(cart.getCartId());
        order.setMgmId(cart.getMgmId());
        order.setEnableJwb(request.getEnableJwb());
        order.setType(com.mgmresorts.order.dto.services.Type.fromValue(cart.getType().value()));
        order.setVersion(com.mgmresorts.order.dto.services.Version.fromValue(cart.getVersion().value()));
        order.setStatus(Order.Status.PENDING);
        order.setCanRetryCheckout(false);

        return order;
    }

    private String cartService404ErrorResponseWithCorrectErrorCode() {
        return "{\r\n"
                + "    \"header\": {\r\n"
                + "        \"origin\": \"API\",\r\n"
                + "        \"mgmCorrelationId\": \"6a289bac-02d8-4f02-aa6e-5116af3a3781\",\r\n"
                + "        \"apiVersion\": \"1.53\",\r\n"
                + "        \"mgmTransactionId\": \"6a289bac-02d8-4f02-aa6e-5116af3a3781\",\r\n"
                + "        \"executionId\": \"882438d4-b843-4d68-b81c-0eea1f01c1f5\",\r\n"
                + "        \"status\": {\r\n"
                + "            \"code\": \"FAILURE\",\r\n"
                + "            \"messages\": [{\r\n"
                + "                    \"text\": \"Invalid parameters or resource not found\",\r\n"
                + "                    \"type\": \"ERROR\",\r\n"
                + "                    \"code\": \"123-1-1016\"\r\n"
                + "                }\r\n"
                + "            ]\r\n"
                + "        }\r\n"
                + "    }\r\n"
                + "}";
    }

    private String cartService404ErrorResponseButDifferentErrorCode() {
        return "{\r\n"
                + "    \"header\": {\r\n"
                + "        \"origin\": \"API\",\r\n"
                + "        \"mgmCorrelationId\": \"6a289bac-02d8-4f02-aa6e-5116af3a3781\",\r\n"
                + "        \"apiVersion\": \"1.53\",\r\n"
                + "        \"mgmTransactionId\": \"6a289bac-02d8-4f02-aa6e-5116af3a3781\",\r\n"
                + "        \"executionId\": \"882438d4-b843-4d68-b81c-0eea1f01c1f5\",\r\n"
                + "        \"status\": {\r\n"
                + "            \"code\": \"FAILURE\",\r\n"
                + "            \"messages\": [{\r\n"
                + "                    \"text\": \"Invalid parameters or resource not found\",\r\n"
                + "                    \"type\": \"ERROR\",\r\n"
                + "                    \"code\": \"123-2-3001\"\r\n"
                + "                }\r\n"
                + "            ]\r\n"
                + "        }\r\n"
                + "    }\r\n"
                + "}";
    }

    private String cartService400ErrorResponse() {
        return "{\r\n"
                + "    \"header\": {\r\n"
                + "        \"origin\": \"API\",\r\n"
                + "        \"mgmCorrelationId\": \"6a289bac-02d8-4f02-aa6e-5116af3a3781\",\r\n"
                + "        \"apiVersion\": \"1.53\",\r\n"
                + "        \"mgmTransactionId\": \"6a289bac-02d8-4f02-aa6e-5116af3a3781\",\r\n"
                + "        \"executionId\": \"882438d4-b843-4d68-b81c-0eea1f01c1f5\",\r\n"
                + "        \"status\": {\r\n"
                + "            \"code\": \"FAILURE\",\r\n"
                + "            \"messages\": [{\r\n"
                + "                    \"text\": \"Invalid parameters or resource not found\",\r\n"
                + "                    \"type\": \"ERROR\",\r\n"
                + "                    \"code\": \"123-2-3058\"\r\n"
                + "                }\r\n"
                + "            ]\r\n"
                + "        }\r\n"
                + "    }\r\n"
                + "}";
    }
}