package com.mgmresorts.order.backend.handler.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.security.Jwts;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.IPaymentSessionAccess;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.utils.PaymentSessionUtil;
import com.mgmresorts.psm.model.EnableSessionRequest;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.sbs.model.ShowReservationResponse;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;

public class PaymentSessionShowHandlerTest {

    private final JSonMapper mapper = new JSonMapper();
    @Tested
    private PaymentSessionShowHandler paymentSessionShowHandler;
    @Injectable
    private IPaymentSessionAccess paymentSessionAccess;

    @Injectable
    private IHttpService service;

    @Injectable
    PaymentSessionUtil paymentSessionUtil;

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @BeforeEach
    public void before() {
        assertNotNull(service);
        assertNotNull(paymentSessionAccess);
        ErrorManager.clean();
        new Errors();
    }

    @Test
    void createPaymentSessionRequestSuccessTest1() throws AppException, IOException {
        setJwtWithMgmId();
        String showReservationJson = Utils.readFileFromClassPath("data/show_reservation_get_response_psm1.json");
        ShowReservationResponse showReservationResponse = mapper.readValue(showReservationJson, ShowReservationResponse.class);

        EnableSessionRequest enableSessionRequest = paymentSessionShowHandler.createPaymentSessionRequest(showReservationResponse, "sessionId", CallType.CREATE);

        assertMandatoryFieldsInRequest(enableSessionRequest);
    }

    @Test
    void createPaymentSessionRequestSuccessTest2() throws AppException, IOException {
        setJwtWithMgmId();
        String showReservationJson = Utils.readFileFromClassPath("data/show_reservation_get_response_psm2.json");
        ShowReservationResponse showReservationResponse = mapper.readValue(showReservationJson, ShowReservationResponse.class);

        EnableSessionRequest enableSessionRequest = paymentSessionShowHandler.createPaymentSessionRequest(showReservationResponse, "sessionId", CallType.CREATE);

        assertMandatoryFieldsInRequest(enableSessionRequest);
    }

    @Test
    void createPaymentSessionRequestSuccessTest3() throws AppException, IOException {
        setJwtWithMgmId();
        String showReservationJson = Utils.readFileFromClassPath("data/show_reservation_get_response_psm3.json");
        ShowReservationResponse showReservationResponse = mapper.readValue(showReservationJson, ShowReservationResponse.class);

        EnableSessionRequest enableSessionRequest = paymentSessionShowHandler.createPaymentSessionRequest(showReservationResponse, "sessionId", CallType.CREATE);

        assertMandatoryFieldsInRequest(enableSessionRequest);
    }

    private static void assertMandatoryFieldsInRequest(EnableSessionRequest enableSessionRequest) {
        assertNotNull(enableSessionRequest);
        assertNotNull(enableSessionRequest.getCardDetails());
        assertNotNull(enableSessionRequest.getTransaction());
        assertNotNull(enableSessionRequest.getTransaction().getCartType());
        assertNotNull(enableSessionRequest.getTransaction().getCheckoutTime());
        assertNotNull(enableSessionRequest.getTransaction().getOrderStatus());
        assertNotNull(enableSessionRequest.getTransaction().getTimeToLive());
        assertNotNull(enableSessionRequest.getGuestDetails());
        assertNotNull(enableSessionRequest.getOrderItems());
        assertNotNull(enableSessionRequest.getOrderItems().getItemAuthGroups());
        assertNotNull(enableSessionRequest.getOrderItems().getItemAuthGroups().get(0));
        assertNotNull(enableSessionRequest.getOrderItems().getItemAuthGroups().get(0).getItemsGroupTotal());
        assertNotNull(enableSessionRequest.getOrderItems().getItemAuthGroups().get(0).getItemsGroupTotal().get(0));
        assertNotNull(enableSessionRequest.getOrderItems().getItemAuthGroups().get(0).getItemsGroupTotal().get(1));
        assertNotNull(enableSessionRequest.getOrderItems().getItemAuthGroups().get(0).getItems());
        assertNotNull(enableSessionRequest.getOrderItems().getItemAuthGroups().get(0).getItems().get(0));
    }

    @Test
    void createPaymentSessionRequestChargesValidationTest() throws IOException {
        String showReservationJson = Utils.readFileFromClassPath("data/show_reservation_get_response_psm2.json");

        ShowReservationResponse showReservationResponse = mapper.readValue(showReservationJson, ShowReservationResponse.class);
        showReservationResponse.setCharges(null);

        assertThrows(AppException.class, () -> paymentSessionShowHandler.createPaymentSessionRequest(showReservationResponse, "sessionId", CallType.CREATE));
    }

    @Test
    void createPaymentSessionRequestTicketsValidationTest() throws IOException {
        String showReservationJson = Utils.readFileFromClassPath("data/show_reservation_get_response_psm2.json");

        ShowReservationResponse showReservationResponse = mapper.readValue(showReservationJson, ShowReservationResponse.class);
        showReservationResponse.setTickets(null);

        assertThrows(AppException.class, () -> paymentSessionShowHandler.createPaymentSessionRequest(showReservationResponse, "sessionId", CallType.CREATE));
    }

    @Test
    void createPaymentSessionRequestBillingValidationTest() throws IOException {
        String showReservationJson = Utils.readFileFromClassPath("data/show_reservation_get_response_psm2.json");

        ShowReservationResponse showReservationResponse = mapper.readValue(showReservationJson, ShowReservationResponse.class);
        showReservationResponse.setBilling(null);

        assertThrows(AppException.class, () -> paymentSessionShowHandler.createPaymentSessionRequest(showReservationResponse, null, CallType.CREATE));
    }

    @Test
    void createPaymentSessionRequestProfileValidationTest() throws IOException {
        String showReservationJson = Utils.readFileFromClassPath("data/show_reservation_get_response_psm2.json");

        ShowReservationResponse showReservationResponse = mapper.readValue(showReservationJson, ShowReservationResponse.class);
        showReservationResponse.setProfile(null);

        assertThrows(AppException.class, () -> paymentSessionShowHandler.createPaymentSessionRequest(showReservationResponse, "sessionId", CallType.CREATE));
    }

    @Test
    void createRequestAndCallEnablePaymentSessionSuccessTest() throws AppException, IOException {
        setJwtWithMgmId();

        String showReservationJson = Utils.readFileFromClassPath("data/show_reservation_get_response_psm3.json");

        ShowReservationResponse showReservationResponse = mapper.readValue(showReservationJson, ShowReservationResponse.class);

        EnableSessionResponse enableSessionResponse = new EnableSessionResponse();
        enableSessionResponse.setSessionId("5f25a891-0fc4-4b2a-814f-35ffdbf28ec1");
        enableSessionResponse.setSessionExpiresOn("2023-12-05T02:04:43.447327200");
        enableSessionResponse.setSessionStatus("New");
        enableSessionResponse.setMessage("SESSION_CREATED");

        new Expectations() {
            {
                paymentSessionAccess.managePaymentSession((EnableSessionRequest) any, CallType.CREATE);
                result = enableSessionResponse;
            }
        };

        EnableSessionResponse response = paymentSessionShowHandler.managePaymentSessionForShowReservation(showReservationResponse, null, CallType.CREATE);

        assertNotNull(response);
        assertNotNull(response.getSessionId());
        assertEquals("5f25a891-0fc4-4b2a-814f-35ffdbf28ec1", response.getSessionId());
        assertNotNull(response.getSessionStatus());
        assertEquals("New", response.getSessionStatus());
        assertNotNull(response.getSessionExpiresOn());
        assertEquals("2023-12-05T02:04:43.447327200", response.getSessionExpiresOn());
        assertNotNull(response.getMessage());
        assertEquals("SESSION_CREATED", response.getMessage());
    }

    private static void setJwtWithMgmId() {
        ThreadContext.getContext().get().setJwt(Jwts.Jwt.empty());
        ThreadContext.getContext().get().setJwt(new Jwts.Jwt("guestToken", "algo", new Date(), new HashMap<>(), null, null, null, null));
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.id", "123");
    }
}
