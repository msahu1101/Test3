package com.mgmresorts.order.service.task.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.concurrent.Executors;
import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.dbs.model.CreateReservationRequest;
import com.mgmresorts.dbs.model.Response;
import com.mgmresorts.dbs.model.ResponseError;
import com.mgmresorts.dbs.model.SearchReservationResponse;
import com.mgmresorts.dbs.model.SearchReservationResponseRestaurantReservationList;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.backend.access.IDiningBookingAccess;
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
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.consumer.IMergeConsumer;
import com.mgmresorts.shopping.cart.dto.AddOnComponent;
import com.mgmresorts.shopping.cart.dto.AgentInfo;
import com.mgmresorts.shopping.cart.dto.CartLineItem;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@SuppressWarnings("unchecked")
public class DiningHandlerTest {
    @Tested
    DiningHandler diningHandler;

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
    private IDiningBookingAccess diningBookingAccess;

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
    void testCheckout(@Injectable ItineraryHandler itinerary,
            @Injectable ITransformer<AgentInfo, com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo> agentTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                diningBookingAccess.createDiningReservation((CreateReservationRequest) any);
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
        newOrderLineItem.setConfirmationNumber("conf123");

        OrderLineItem orderLineItem = diningHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false, null, null);
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
    void testCheckout400Error(@Injectable ItineraryHandler itinerary,
            @Injectable ITransformer<AgentInfo, com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo> agentTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                diningBookingAccess.createDiningReservation((CreateReservationRequest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(invalidCCTokenResponse()), "Error while calling http endpoint", new String[] { "header" });
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
        newOrderLineItem.setConfirmationNumber("conf123");

        OrderLineItem orderLineItem = diningHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false,null, null);
        assertEquals("FAILURE", orderLineItem.getStatus().toString());

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
    void testCheckoutAppExceptionError(@Injectable ItineraryHandler itinerary,
            @Injectable ITransformer<AgentInfo, com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo> agentTransformer, @Injectable CartHandler cart,
            @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer) throws AppException, HttpFailureException {

        new Expectations() {
            {
                diningBookingAccess.createDiningReservation((CreateReservationRequest) any);
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
        newOrderLineItem.setConfirmationNumber("conf123");

        assertThrows(AppException.class,
                () -> diningHandler.checkout(getCheckoutRequest(false), cartLineItem, newOrderLineItem, appliedBillings, "cartId", agentInfo, false, false,null, null));
    }

    @Test
    void testGetReservationSuccess(@Injectable ItineraryHandler itinerary,
                                   @Injectable ITransformer<AgentInfo, com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo> agentTransformer, @Injectable CartHandler cart,
                                   @Injectable ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer) throws AppException{
        SearchReservationResponse searchReservationResponse = new SearchReservationResponse();
        SearchReservationResponseRestaurantReservationList searchReservationResponseRestaurantReservationList = new SearchReservationResponseRestaurantReservationList();
        searchReservationResponseRestaurantReservationList.setConfirmationNumber("1234");
        searchReservationResponseRestaurantReservationList.setFirstName("John");
        searchReservationResponseRestaurantReservationList.setLastName("Doe");
        List<SearchReservationResponseRestaurantReservationList> reservationLists = new ArrayList<>();
        reservationLists.add(searchReservationResponseRestaurantReservationList);
        searchReservationResponse.setRestaurantReservationList(reservationLists);

        new Expectations() {
            {
                diningBookingAccess.searchDiningReservation(anyString, anyString, anyString);
                result = searchReservationResponse;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = diningHandler.getReservation("1234", "John", "Doe", true, null);
        assertEquals(retrieveBookingResponse.getDiningReservationResponse().getRestaurantReservationList().get(0).getConfirmationNumber(), "1234");
        assertEquals(retrieveBookingResponse.getDiningReservationResponse().getRestaurantReservationList().get(0).getFirstName(), "John");
        assertEquals(retrieveBookingResponse.getDiningReservationResponse().getRestaurantReservationList().get(0).getLastName(), "Doe");
    }

    @Test
    void testPreviewReservationSuccess(@Injectable ITransformer<AgentInfo, com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo> agentTransformer) throws AppException {
        assertThrows(AppException.class, () -> diningHandler.previewReservation(new PreviewReservationRequest()));
    }

    private Response invalidCCTokenResponse() {
        Response responseError = new Response();
        ResponseError error = new ResponseError();
        error.setCode("600-5-404");
        error.setMessage("<AUTHENTICATION_ERROR>[Requested resource not found]");
        responseError.setError(error);
        return responseError;
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

    final String getReservationResp() {
        return "{\"restaurantReservation\":{\"id\":\"86bade06-4cc4-48d4-9517-73409c4ee8ad\","
                + "\"itineraryId\":\"9658511874\",\"restaurantId\":\"40872441-efdd-4189-a81c-d9da7f3fa803\","
                + "\"propertyId\":\"66964e2b-2550-4476-84c3-1a4c0c5c067f\",\"customerId\":\"846193885186\","
                + "\"sevenRoomsProfileId\":\"JNAJFX\",\"bookDate\":\"2022-07-15T01:38:17.788+00:00\","
                + "\"reservationDateTime\":\"2022-07-27 12:00:00\",\"numAdults\":2,\"numChildren\":0,\"state\":"
                + "\"Booked\",\"hostStatus\":\"BOOKED\",\"channel\":\"web\",\"source\":\"mgmri\",\"confirmationNumber\":"
                + "\"MGMLV-6CG5VJBBJ\",\"createdAt\":\"2022-07-15T01:38:17.788+00:00\",\"updatedAt\":\"2022-07-15T01:38:17.788+00:00\",\"isCancellable\":true}}";
    }

    final String getHoldContent() {
        return "{\"restaurantId\":\"40872441-efdd-4189-a81c-d9da7f3fa803\",\"reservationDateTime\":\"2022-07-27 12:00:00\",\"partySize\":\"2\","
                + "\"reservationHoldId\":\"1657850765.342432\","
                + "\"accessPersistentId\":\"ahhzfnNldmVucm9vbXMtc2VjdXJlLWRlbW9yHAsSD25pZ2h0bG9vcF9WZW51ZRiAgJDQtOO6Cgw-1533844648.73-0.776669926593\","
                + "\"shiftPersistentId\":\"ahhzfnNldmVucm9vbXMtc2VjdXJlLWRlbW9yHAsSD25pZ2h0bG9vcF9WZW51ZRiAgJDQtOO6Cgw-LUNCH-1533676506.45\","
                + "\"holdDurationSec\":1800,\"propertyId\":\"66964e2b-2550-4476-84c3-1a4c0c5c067f\"}";
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
}
