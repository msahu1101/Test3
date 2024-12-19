package com.mgmresorts.order.service.task.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mgmresorts.order.dto.services.Version;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.concurrent.Executors;
import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.notification.SmtpEmailer;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.itineraries.dto.client.itinerary.ItineraryData;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.backend.access.IContentAccess;
import com.mgmresorts.order.backend.access.IShowBookingAccess;
import com.mgmresorts.order.backend.handler.IPaymentProcessingHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionShowHandler;
import com.mgmresorts.order.backend.handler.impl.CartHandler;
import com.mgmresorts.order.backend.handler.impl.ItineraryHandler;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.BillingAddress;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.dto.Payment;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.PreviewReservationRequest;
import com.mgmresorts.order.dto.services.RetrieveReservationResponse;
import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.consumer.IMergeConsumer;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.sbs.model.BillingInfo;
import com.mgmresorts.sbs.model.ErrorResponse;
import com.mgmresorts.sbs.model.ErrorResponseError;
import com.mgmresorts.sbs.model.ReservationProfile;
import com.mgmresorts.sbs.model.ShowReservationRequest;
import com.mgmresorts.sbs.model.ShowReservationResponse;
import com.mgmresorts.shopping.cart.dto.AgentInfo;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.DeliveryMethod;
import com.mgmresorts.shopping.cart.dto.ItemSelectionDetails;
import com.mgmresorts.shopping.cart.dto.ItemType;
import com.mgmresorts.shopping.cart.dto.ShowSelectionDetails;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@SuppressWarnings("unchecked")
public class ShowHandlerTest {
    @Tested
    ShowHandler showHandler;

    @Injectable
    private IHttpService service;
    @Injectable
    private OAuthTokenRegistry registry;
    @Injectable
    Executors executors;
    @Injectable
    private SmtpEmailer smtpEmailer;
    @Injectable
    private IContentAccess contentAccess;
    @Injectable
    private IOrderAccess orderAccess;
    @Injectable
    private IMergeConsumer mergeConsumer;
    @Injectable
    private IPaymentProcessingHandler paymentProcessingHandler;
    @Injectable
    private IShowBookingAccess showBookingAccess;
    @Injectable
    private IPaymentSessionShowHandler paymentSessionShowHandler;

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
    void testCheckout(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setPaymentRequired(true);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(false, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
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
                ShowReservationRequest c = new ShowReservationRequest();
                showBookingAccess.createShowReservation(c = withCapture());
                assertNotNull(c.getTickets().get(0).getPriceCodeDescription());
                assertNotNull(c.getTickets().get(0).isAdaCompanion());
                assertNotNull(c.getTickets().get(0).isAdaCompanion());
                assertEquals(c.getInAuthTransactionId(), "12345678");
            }
        };

    }

    @Test
    void testCheckoutAsync(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                      @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
                      @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setPaymentRequired(true);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(false, true), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
        assertEquals("PENDING", orderLineItem.getStatus().toString());

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
    void testCheckoutInvalidCCToken(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(invalidCCTokenResponse()), "Error while calling http endpoint", new String[] { "header" });
            }
        };

        CartLineItem cartLineItem = new CartLineItem();
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setPaymentRequired(true);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        ItemSelectionDetails selectionDetails = new ItemSelectionDetails();
        ShowSelectionDetails showSelectionDetails = new ShowSelectionDetails();
        showSelectionDetails.setNumberOfTickets(2);
        selectionDetails.setShowSelectionDetails(showSelectionDetails);
        cartLineItem.setItemSelectionDetails(selectionDetails);
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(false, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
    }
    
    @Test
    void testCheckoutInvalidBillingPostalCode(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(invalidBillingPostalCodeResponse()), "Error while calling http endpoint", new String[] { "header" });
            }
        };

        CartLineItem cartLineItem = new CartLineItem();
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setPaymentRequired(true);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        ItemSelectionDetails selectionDetails = new ItemSelectionDetails();
        ShowSelectionDetails showSelectionDetails = new ShowSelectionDetails();
        showSelectionDetails.setNumberOfTickets(2);
        selectionDetails.setShowSelectionDetails(showSelectionDetails);
        cartLineItem.setItemSelectionDetails(selectionDetails);
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(false, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
    }

    @Test
    void testCheckoutAuthorizationRejected(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(authorizationRejectedResponse()), "Error while calling http endpoint",
                        new String[] { "header" });
            }
        };

        CartLineItem cartLineItem = new CartLineItem();
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setPaymentRequired(true);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        ItemSelectionDetails selectionDetails = new ItemSelectionDetails();
        ShowSelectionDetails showSelectionDetails = new ShowSelectionDetails();
        showSelectionDetails.setNumberOfTickets(2);
        selectionDetails.setShowSelectionDetails(showSelectionDetails);
        cartLineItem.setItemSelectionDetails(selectionDetails);
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(false, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
    }

    @Test
    void testCheckoutFailureDuringPayment(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {
        new Expectations() {
            {
                itinerary.create((GuestProfile) any, (Cart) any);
                result = "test";
                minTimes = 0;
            }

            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(paymentFailureTokenResponse()), "Error while calling http endpoint",
                        new String[] { "header" });
                minTimes = 0;
            }
        };

        CartLineItem cartLineItem = new CartLineItem();
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setPaymentRequired(true);
        ItemSelectionDetails selectionDetails = new ItemSelectionDetails();
        ShowSelectionDetails showSelectionDetails = new ShowSelectionDetails();
        showSelectionDetails.setNumberOfTickets(2);
        selectionDetails.setShowSelectionDetails(showSelectionDetails);
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

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(false, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
    }

    @Test
    void testCheckoutWithProductType_ProductId_PropertyId_programId(@Injectable ItineraryHandler itinerary,
            @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setProductId("showEventId1");
        cartLineItem.setType(ItemType.SHOW);
        cartLineItem.setPropertyId("propertyId1");
        cartLineItem.setPackageId("packageId1");
        cartLineItem.setPackageLineItemId("packageLineItemId1");
        cartLineItem.setPaymentRequired(true);
        ShowSelectionDetails showDetails = new ShowSelectionDetails();
        showDetails.setProgramId("programId1");
        cartLineItem.setItemSelectionDetails(new ItemSelectionDetails());
        cartLineItem.getItemSelectionDetails().setShowSelectionDetails(showDetails);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(true, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());
        assertEquals("programId1", orderLineItem.getProgramId());

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
    void testCheckoutMergeAndUpdateFailure(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
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
        cartLineItem.setPaymentRequired(true);
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
                () -> showHandler.checkout(getCheckoutRequest(false, false), cartLineItem, newOrderLineItem, appliedBillings,
                        "orderId1", agentInfo, false, false, null, null));

    }
    
    @Test
    void testPremiumPackageCheckout(@Injectable ItineraryHandler itinerary,
            @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product1");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setProductId("showEventId1");
        cartLineItem.setType(ItemType.SHOW);
        cartLineItem.setPropertyId("propertyId1");
        cartLineItem.setPackageId("packageId1");
        cartLineItem.setPackageLineItemId("packageLineItemId1");
        cartLineItem.setOperaConfirmationNumber("903917303");
        cartLineItem.setConfirmationNumber("M03D61661");
        cartLineItem.setOperaHotelCode("001");
        cartLineItem.setPaymentRequired(true);
        ShowSelectionDetails showDetails = new ShowSelectionDetails();
        showDetails.setProgramId("programId1");
        showDetails.setHdePackage(true);
        cartLineItem.setItemSelectionDetails(new ItemSelectionDetails());
        cartLineItem.getItemSelectionDetails().setShowSelectionDetails(showDetails);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(true, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());
        assertEquals("programId1", orderLineItem.getProgramId());
        assertEquals("product1", orderLineItem.getCartLineItemId());

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
    void testPremiumPackageAsyncCheckout(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                           @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
                           @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product1");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setProductId("showEventId1");
        cartLineItem.setType(ItemType.SHOW);
        cartLineItem.setPropertyId("propertyId1");
        cartLineItem.setPackageId("packageId1");
        cartLineItem.setPackageLineItemId("packageLineItemId1");
        cartLineItem.setOperaConfirmationNumber("903917303");
        cartLineItem.setConfirmationNumber("M03D61661");
        cartLineItem.setOperaHotelCode("001");
        cartLineItem.setPaymentRequired(true);
        ShowSelectionDetails showDetails = new ShowSelectionDetails();
        showDetails.setProgramId("programId1");
        showDetails.setHdePackage(true);
        cartLineItem.setItemSelectionDetails(new ItemSelectionDetails());
        cartLineItem.getItemSelectionDetails().setShowSelectionDetails(showDetails);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(true, true), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
        assertEquals("PENDING", orderLineItem.getStatus().toString());
        assertEquals("programId1", orderLineItem.getProgramId());
        assertEquals("product1", orderLineItem.getCartLineItemId());

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
    void testPaymentProcessingGlobalCheckout_Success(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                                                     @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
                                                     @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        BillingInfo billingInfo = podamFactoryImpl.manufacturePojo(BillingInfo.class);
        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = getReservationResp();
            }
            {
                showBillingTransformer.toRight((Billing) any);
                result = billingInfo;
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(5.0);
        cartLineItem.setCartLineItemId("product1");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setProductId("showEventId1");
        cartLineItem.setType(ItemType.SHOW);
        cartLineItem.setPropertyId("propertyId1");
        cartLineItem.setPackageId("packageId1");
        cartLineItem.setPackageLineItemId("packageLineItemId1");
        cartLineItem.setOperaConfirmationNumber("903917303");
        cartLineItem.setConfirmationNumber("M03D61661");
        cartLineItem.setOperaHotelCode("001");
        cartLineItem.setPaymentRequired(true);
        ShowSelectionDetails showDetails = new ShowSelectionDetails();
        showDetails.setProgramId("programId1");
        showDetails.setHdePackage(true);
        cartLineItem.setItemSelectionDetails(new ItemSelectionDetails());
        cartLineItem.getItemSelectionDetails().setShowSelectionDetails(showDetails);
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

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(false, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());
        assertEquals("programId1", orderLineItem.getProgramId());
        assertEquals("product1", orderLineItem.getCartLineItemId());

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
                ShowReservationRequest c = new ShowReservationRequest();
                showBookingAccess.createShowReservation(c = withCapture());
                assertEquals(c.getBilling().getPayment().getPaymentId(), "payId");
                assertEquals(c.getBilling().getPayment().getAuthId(), "authCode");
            }
            {
                String str = null;
                PaymentAuthFields c = null;
                paymentProcessingHandler.captureTransaction(anyString, str = withCapture(), c = withCapture() );
                times = 1;
                assertEquals("orderRef", str);
                assertNotNull(c);
                assertEquals("authCode", c.getAuthorizationCode());
                assertEquals("payId", c.getPaymentId());
                assertTrue(c.isSuccess());
            }
        };

    }

     @Test
    void testPaymentProcessingGlobalCheckout_AuthFail(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                                                      @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
                                                      @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {


        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(5.0);
        cartLineItem.setCartLineItemId("product1");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setProductId("showEventId1");
        cartLineItem.setType(ItemType.SHOW);
        cartLineItem.setPropertyId("propertyId1");
        cartLineItem.setPackageId("packageId1");
        cartLineItem.setPackageLineItemId("packageLineItemId1");
        cartLineItem.setOperaConfirmationNumber("903917303");
        cartLineItem.setConfirmationNumber("M03D61661");
        cartLineItem.setOperaHotelCode("001");
        cartLineItem.setPaymentRequired(true);
        ShowSelectionDetails showDetails = new ShowSelectionDetails();
        showDetails.setProgramId("programId1");
        showDetails.setHdePackage(true);
        cartLineItem.setItemSelectionDetails(new ItemSelectionDetails());
        cartLineItem.getItemSelectionDetails().setShowSelectionDetails(showDetails);
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
        paymentAuthFields.setSuccess(false);
        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(false, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
        assertEquals("programId1", orderLineItem.getProgramId());
        assertEquals("product1", orderLineItem.getCartLineItemId());

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
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                times = 0;
            }
        };

    }

    @Test
    void testPaymentProcessingGlobalCheckout_MissingFields(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                                                           @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
                                                           @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {


        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(5.0);
        cartLineItem.setCartLineItemId("product1");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setProductId("showEventId1");
        cartLineItem.setType(ItemType.SHOW);
        cartLineItem.setPropertyId("propertyId1");
        cartLineItem.setPackageId("packageId1");
        cartLineItem.setPackageLineItemId("packageLineItemId1");
        cartLineItem.setOperaConfirmationNumber("903917303");
        cartLineItem.setConfirmationNumber("M03D61661");
        cartLineItem.setOperaHotelCode("001");
        cartLineItem.setPaymentRequired(true);
        ShowSelectionDetails showDetails = new ShowSelectionDetails();
        showDetails.setProgramId("programId1");
        showDetails.setHdePackage(true);
        cartLineItem.setItemSelectionDetails(new ItemSelectionDetails());
        cartLineItem.getItemSelectionDetails().setShowSelectionDetails(showDetails);
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
        paymentAuthFields.setAuthorizationCode(null);
        paymentAuthFields.setPaymentId(null);
        paymentAuthFields.setSuccess(true);
        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(false, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
        assertEquals("programId1", orderLineItem.getProgramId());
        assertEquals("product1", orderLineItem.getCartLineItemId());

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
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                times = 0;
            }
        };

    }

    @Test
    void testPaymentProcessingPackageCheckout_Success(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                                                      @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
                                                      @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        BillingInfo billingInfo = podamFactoryImpl.manufacturePojo(BillingInfo.class);
        
        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = getReservationResp();
            }
            {
                showBillingTransformer.toRight((Billing) any);
                result = billingInfo;
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(5.0);
        cartLineItem.setCartLineItemId("product1");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setProductId("showEventId1");
        cartLineItem.setType(ItemType.SHOW);
        cartLineItem.setPropertyId("propertyId1");
        cartLineItem.setPackageId("packageId1");
        cartLineItem.setPackageLineItemId("packageLineItemId1");
        cartLineItem.setOperaConfirmationNumber("903917303");
        cartLineItem.setConfirmationNumber("M03D61661");
        cartLineItem.setOperaHotelCode("001");
        cartLineItem.setPaymentRequired(true);
        ShowSelectionDetails showDetails = new ShowSelectionDetails();
        showDetails.setProgramId("programId1");
        showDetails.setHdePackage(true);
        cartLineItem.setItemSelectionDetails(new ItemSelectionDetails());
        cartLineItem.getItemSelectionDetails().setShowSelectionDetails(showDetails);
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

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(true, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());
        assertEquals("programId1", orderLineItem.getProgramId());
        assertEquals("product1", orderLineItem.getCartLineItemId());

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
                ShowReservationRequest c = new ShowReservationRequest();
                showBookingAccess.createShowReservation(c = withCapture());
                assertEquals(c.getBilling().getPayment().getPaymentId(), "payId");
                assertEquals(c.getBilling().getPayment().getAuthId(), "authCode");
            }
            {
                String str = null;
                PaymentAuthFields c = null;
                paymentProcessingHandler.captureTransaction(anyString, str = withCapture(), c = withCapture() );
                times = 1;
                assertEquals("orderRef", str);
                assertNotNull(c);
                assertEquals("authCode", c.getAuthorizationCode());
                assertEquals("payId", c.getPaymentId());
                assertTrue(c.isSuccess());
            }
        };

    }


    @Test
    void testPaymentProcessingPackageCheckout_AuthFail(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                                                       @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
                                                       @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setLineItemDeposit(5.0);
        cartLineItem.setCartLineItemId("product1");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setProductId("showEventId1");
        cartLineItem.setType(ItemType.SHOW);
        cartLineItem.setPropertyId("propertyId1");
        cartLineItem.setPackageId("packageId1");
        cartLineItem.setPackageLineItemId("packageLineItemId1");
        cartLineItem.setOperaConfirmationNumber("903917303");
        cartLineItem.setConfirmationNumber("M03D61661");
        cartLineItem.setOperaHotelCode("001");
        cartLineItem.setPaymentRequired(true);
        ShowSelectionDetails showDetails = new ShowSelectionDetails();
        showDetails.setProgramId("programId1");
        showDetails.setHdePackage(true);
        cartLineItem.setItemSelectionDetails(new ItemSelectionDetails());
        cartLineItem.getItemSelectionDetails().setShowSelectionDetails(showDetails);
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
        paymentAuthFields.setSuccess(false);
        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(true, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
        assertEquals("programId1", orderLineItem.getProgramId());
        assertEquals("product1", orderLineItem.getCartLineItemId());

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
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                times = 0;
            }
        };

    }

    @Test
    void testPaymentProcessingPackageCheckout_MissingFields(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                                                            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
                                                            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product1");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setProductId("showEventId1");
        cartLineItem.setType(ItemType.SHOW);
        cartLineItem.setPropertyId("propertyId1");
        cartLineItem.setPackageId("packageId1");
        cartLineItem.setPackageLineItemId("packageLineItemId1");
        cartLineItem.setOperaConfirmationNumber("903917303");
        cartLineItem.setConfirmationNumber("M03D61661");
        cartLineItem.setOperaHotelCode("001");
        cartLineItem.setPaymentRequired(true);
        ShowSelectionDetails showDetails = new ShowSelectionDetails();
        showDetails.setProgramId("programId1");
        showDetails.setHdePackage(true);
        cartLineItem.setItemSelectionDetails(new ItemSelectionDetails());
        cartLineItem.getItemSelectionDetails().setShowSelectionDetails(showDetails);
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
        paymentAuthFields.setAuthorizationCode(null);
        paymentAuthFields.setPaymentId(null);
        paymentAuthFields.setSuccess(true);
        Map<String,PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        paymentAuthFieldsMap.put(cartLineItem.getCartLineItemId(),paymentAuthFields);

        OrderLineItem orderLineItem = showHandler.checkout(getCheckoutRequest(true, false), cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, true, paymentAuthFieldsMap, "orderRef");
        assertEquals("PAYMENT_FAILURE", orderLineItem.getStatus().toString());
        assertEquals("programId1", orderLineItem.getProgramId());
        assertEquals("product1", orderLineItem.getCartLineItemId());

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
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                times = 0;
            }
        };

    }

    @Test
    void testCheckoutShowWithPaymentChargedToRoom(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                      @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
                      @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setPaymentRequired(true);
        ItemSelectionDetails selectionDetails = new ItemSelectionDetails();
        ShowSelectionDetails showSelectionDetails = new ShowSelectionDetails();
        showSelectionDetails.setNumberOfTickets(2);
        showSelectionDetails.setShowPaymentChargedTo(ShowSelectionDetails.ShowPaymentChargedTo.ROOM_ONLY);
        selectionDetails.setShowSelectionDetails(showSelectionDetails);
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

        CheckoutRequest checkoutRequest = getCheckoutRequest(true, false);
        checkoutRequest.setCartVersion(Version.V2);

        OrderLineItem orderLineItem = showHandler.checkout(checkoutRequest, cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
        assertEquals("SUCCESS", orderLineItem.getStatus().toString());

        new Verifications() {
            {
                ShowReservationRequest c = new ShowReservationRequest();
                showBookingAccess.createShowReservation(c = withCapture());
                assert c != null;
                assertEquals(ShowReservationRequest.ShowPaymentChargedToEnum.ROOM_ONLY, c.getShowPaymentChargedTo() );
                assertEquals(true, c.isSkipEmailNotification());
            }
        };
    }

    @Test
    void testGetReservation(@Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
                            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer
    ) throws AppException {
        ShowReservationResponse showReservationResponse = new ShowReservationResponse();
        showReservationResponse.setConfirmationNumber("1234");
        showReservationResponse.setProfile(new com.mgmresorts.sbs.model.ReservationProfile());
        showReservationResponse.getProfile().setFirstName("John");
        showReservationResponse.getProfile().setLastName("Doe");
        EnableSessionResponse enableSessionResponse = new EnableSessionResponse();
        enableSessionResponse.setSessionId("1234");

        new Expectations() {
            {
                showBookingAccess.getShowReservation(anyString,anyString,anyString);
                result = showReservationResponse;
            }
            {
                paymentSessionShowHandler.managePaymentSessionForShowReservation((ShowReservationResponse) any, anyString, (CallType) any);
                result = enableSessionResponse;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = showHandler.getReservation("1234", "John", "Doe", true, null);
        assertEquals(retrieveBookingResponse.getShowReservationResponse().getConfirmationNumber(), "1234");
        assertEquals(retrieveBookingResponse.getShowReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(retrieveBookingResponse.getShowReservationResponse().getProfile().getLastName(), "Doe");
        assertEquals(retrieveBookingResponse.getPaymentSessionId(), "1234");
    }


    @Test
    void testPreviewReservationSuccess(@Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
                                       @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer,
                                       @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException {
        assertThrows(AppException.class, () -> showHandler.previewReservation(new PreviewReservationRequest()));
    }
    
    @Test
    void testCheckoutWithItineraryInfo(@Injectable ItineraryHandler itinerary, @Injectable ITransformer<GuestProfile, ReservationProfile> showProfileTransformer,
            @Injectable ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                showBookingAccess.createShowReservation((ShowReservationRequest) any);
                result = getReservationResp();
            }
        };

        CartLineItem cartLineItem = podamFactoryImpl.manufacturePojo(CartLineItem.class);
        cartLineItem.setCartLineItemId("product");
        cartLineItem.setStatus(CartLineItem.Status.SAVED);
        cartLineItem.setContent(getHoldContent());
        cartLineItem.setPaymentRequired(true);
        List<Billing> billings = new ArrayList<>();
        billings.add(getBillings());
        AppliedBillings appliedBillings = new AppliedBillings(billings);
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentType("Travel");
        agentInfo.setAgentId("351");
        OrderLineItem newOrderLineItem = new OrderLineItem();
        newOrderLineItem.setOrderLineItemId(UUID.randomUUID().toString());
        newOrderLineItem.setStatus(OrderLineItem.Status.PENDING);
        
        CheckoutRequest checkoutRequest = getCheckoutRequest(false, false);
        
        ItineraryData itineraryData = podamFactoryImpl.manufacturePojo(ItineraryData.class);
        checkoutRequest.getGuestProfile().setItineraryData(itineraryData);

        OrderLineItem orderLineItem = showHandler.checkout(checkoutRequest, cartLineItem, newOrderLineItem, appliedBillings, "orderId1", agentInfo, false, false, null, null);
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
                ShowReservationRequest c = new ShowReservationRequest();
                showBookingAccess.createShowReservation(c = withCapture());
                assertNotNull(c.getItineraryInfo());
                assertEquals(c.getItineraryInfo().getItineraryId(), checkoutRequest.getGuestProfile().getItineraryData().getItineraryId());
                assertEquals(c.getItineraryInfo().getItineraryName(), checkoutRequest.getGuestProfile().getItineraryData().getItineraryName());
                assertEquals(c.getItineraryInfo().getTripsArrivalDate(), checkoutRequest.getGuestProfile().getItineraryData().getTripParams().getArrivalDate().toString());
                assertEquals(c.getItineraryInfo().getTripsDepartureDate(), checkoutRequest.getGuestProfile().getItineraryData().getTripParams().getDepartureDate().toString());
            }
        };

    }

    private Billing getBillings() {
        Payment payment = new Payment();
        payment.setAmount(400.00);
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

    private CheckoutRequest getCheckoutRequest(boolean isPackage, boolean progressiveCheckout) {
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
        request.setProgressiveCheckout(progressiveCheckout);
        request.setInAuthTransactionId("12345678");

        // Request Object Set End
        return request;
    }

    private ErrorResponse invalidCCTokenResponse() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("620-2-240");
        error.setMessage("<_fraud_check_failed>[ Fraud check failed ]");
        responseError.setError(error);
        return responseError;
    }

    private ErrorResponse authorizationRejectedResponse() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("620-2-242");
        error.setMessage("<_auth_rejected>[Authorization rejected]");
        responseError.setError(error);
        return responseError;
    }

    private ErrorResponse paymentFailureTokenResponse() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("620-2-241");
        error.setMessage("<_auth_failed>[ Payment authorization failed ]");
        responseError.setError(error);
        return responseError;
    }
    
    private ErrorResponse invalidBillingPostalCodeResponse() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("620-1-244");
        error.setMessage("Billing Details is missing or invalid");
        responseError.setError(error);
        return responseError;
    }

    final String getHoldContent() {
        return "{\"eventDate\":\"2021-10-30\",\"eventTime\":\"8:00 PM\",\"eventTz\":\"2021-10-31T03:00Z\",\"seasonId\":\"7095c5b1-d871-4fbd-9bef-2aeb3a622bce\","
                + "\"showEventId\":\"0f6a243d-f90e-437d-9d51-db3ff49ba0f6\",\"comp\":false,\"hdePackage\":false,\"discounted\":true,"
                + "\"programId\":\"9e6dac4a-fb05-4843-9a11-20f19c11d385\",\"propertyId\":\"607c07e7-3e31-4e4c-a4e1-f55dca66fea2\","
                + "\"permissibleDeliveryMethods\":[{\"code\":\"LL\",\"amount\":7.0,\"default\":false,\"name\":\"Will Call LX\",\"active\":true,"
                + "\"id\":\"61d03039-1087-48a9-97a4-94aec8bd8476\"},{\"code\":\"MM\",\"amount\":7.0,\"default\":false,\"name\":\"MGM WC FEE\",\"active\":true,"
                + "\"id\":\"501fc6ca-bfd6-4417-91eb-b0f642b31e6b\"},{\"code\":\"TF\",\"amount\":0.0,\"default\":true,\"name\":\"Ticket Fast\",\"active\":true,"
                + "\"id\":\"5ccce20e-4cab-4f9c-aa0d-4ef30627e40e\"}],\"tickets\":[{\"priceCode\":\"AN\",\"priceCodeDescription\":\"Catagory A\",\"holdClass\":\"OPEN\",\"basePrice\":75.0,\"discountedPrice\":60.0,"
                + "\"holdId\":\"98434039\",\"holdDuration\":1634781527937,\"state\":\"HELD\",\"showEventId\":\"0f6a243d-f90e-437d-9d51-db3ff49ba0f6\", \"accessible\":false,\"adaCompanion\":false,\"ticketTypeCode\":\"_GR\","
                + "\"seat\":{\"seatNumber\":14,\"sectionName\":\"SEC1\",\"rowName\":\"B\"}},{\"priceCode\":\"AN\",\"holdClass\":\"OPEN\",\"basePrice\":75.0,"
                + "\"discountedPrice\":60.0,\"holdId\":\"98434039\",\"holdDuration\":1634781527937,\"state\":\"HELD\",\"showEventId\":\"0f6a243d-f90e-437d-9d51-db3ff49ba0f6\",\"accessible\": false,\"adaCompanion\":false,"
                + "\"ticketTypeCode\":\"_GR\",\"seat\":{\"seatNumber\":15,\"sectionName\":\"SEC1\",\"rowName\":\"B\"}}],\"charges\":{\"discountedSubtotal\":120.0,\"showSubtotal\":150.0,"
                + "\"let\":10.79,\"deliveryFee\":0.0,\"gratuity\":0.0,\"reservationTotal\":149.79,\"serviceCharge\":{\"amount\":19.0,\"itemized\":{\"charge\":17.43,\"tax\":1.57}},"
                + "\"transactionFee\":{\"amount\":0.0,\"itemized\":{\"charge\":0.0,\"tax\":0.0}}}}";

    }

    final String getReservationResp() {
        return "{\"bookDate\":\"2021-10-20\",\"confirmationNumber\":\"98434043\",\"propertyId\":\"607c07e7-3e31-4e4c-a4e1-f55dca66fea2\","
                + "\"seasonId\":\"7095c5b1-d871-4fbd-9bef-2aeb3a622bce\",\"showEventId\":\"0f6a243d-f90e-437d-9d51-db3ff49ba0f6\",\"deliveryMethodCode\":\"TF\",\"comp\":false,"
                + "\"hdePackage\":false,\"resendEmailAllowed\":true,\"tickets\":[{\"priceCode\":\"AN\",\"holdClass\":\"OPEN\",\"basePrice\":75.0,\"discountedPrice\":60.0,"
                + "\"showEventId\":\"0f6a243d-f90e-437d-9d51-db3ff49ba0f6\",\"ticketTypeCode\":\"_GR\",\"seat\":{\"sectionName\":\"SEC1\",\"rowName\":\"B\",\"seatNumber\":14}},"
                + "{\"priceCode\":\"AN\",\"holdClass\":\"OPEN\",\"basePrice\":75.0,\"discountedPrice\":60.0,\"showEventId\":\"0f6a243d-f90e-437d-9d51-db3ff49ba0f6\","
                + "\"ticketTypeCode\":\"_GR\",\"seat\":{\"sectionName\":\"SEC1\",\"rowName\":\"B\",\"seatNumber\":15}}],\"charges\":{\"discountedSubtotal\":120.0,"
                + "\"showSubtotal\":150.0,\"let\":10.79,\"deliveryFee\":0.0,\"gratuity\":0.0,\"reservationTotal\":149.79,\"serviceCharge\":{\"amount\":19.0,"
                + "\"itemized\":{\"charge\":17.43,\"tax\":1.57}},\"transactionFee\":{\"amount\":0.0,\"itemized\":{\"charge\":0.0,\"tax\":0.0}}},\"profile\":{\"archticsId\":\"65978918\","
                + "\"firstName\":\"Mike\",\"lastName\":\"Hedden\",\"phoneNumbers\":[{\"type\":\"Home\",\"number\":\"7026929138\"}],\"emailAddress1\":\"tray@mgmresorts.com\","
                + "\"addresses\":[{\"type\":\"Home\",\"preferred\":false,\"street1\":\"123 Main St\",\"city\":\"Las Vegas\",\"state\":\"NV\",\"country\":\"US\","
                + "\"postalCode\":\"89118\"}]}}";
    }
}
