package com.mgmresorts.order.backend.access.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.HttpService;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.handler.IPaymentSessionRoomHandler;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.psm.model.EnableSessionRequest;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.psm.model.RetrieveSessionResponse;
import com.mgmresorts.psm.model.SessionError;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;

@SuppressWarnings("unchecked")
public class PaymentSessionAccessTest {
    @Tested
    private PaymentSessionAccess paymentSessionAccess;
    private final JSonMapper mapper = new JSonMapper();
    @Injectable
    private IHttpService service;
    @Injectable
    private OAuthTokenRegistry registry;

    @Injectable
    private IPaymentSessionRoomHandler paymentSessionHandler;

    private final JSonMapper jsonMapper = new JSonMapper();

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @BeforeEach
    public void before() {
        assertNotNull(service);
        assertNotNull(registry);
        assertNotNull(paymentSessionHandler);
        ErrorManager.clean();
        new Errors();
    }

    @Test
    void enablePaymentSessionSuccessTest() throws AppException, HttpFailureException {

        new Expectations() {
            {
                service.put(anyString, any, anyString, anyString, (HttpService.HttpHeaders.HttpHeader[]) any);
                result = getEnablePaymentSessionResponse();
            }
        };
        String token = "token";
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, anyString, anyString);
                result = token;
            }
        };
        EnableSessionResponse response = paymentSessionAccess.managePaymentSession(new EnableSessionRequest(), null);
        assertNotNull(response);
        assertNotNull(response.getSessionId());
        assertNotNull(response.getMessage());
        assertNotNull(response.getSessionStatus());
        assertNotNull(response.getSessionExpiresOn());
        assertEquals("5f25a891-0fc4-4b2a-814f-35ffdbf28ec1", response.getSessionId());
        assertEquals("SESSION_CREATED", response.getMessage());
        assertEquals("New", response.getSessionStatus());
        assertEquals("2023-12-05T02:04:43.447327200", response.getSessionExpiresOn());
    }

    private String getEnablePaymentSessionResponse() {
        return "{\n" +
                "\t\"sessionId\": \"5f25a891-0fc4-4b2a-814f-35ffdbf28ec1\",\n" +
                "\t\"sessionExpiresOn\": \"2023-12-05T02:04:43.447327200\", \n" +
                "    \"sessionStatus\": \"New\",\n" +
                "\t\"message\": \"SESSION_CREATED\"\n" +
                "}";
    }

    @Test
    void enablePaymentSession400FailureTest() throws AppException, HttpFailureException {
        new Expectations() {
            {
                service.put(anyString, any, anyString, anyString, (HttpService.HttpHeaders.HttpHeader[]) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(paymentSessionErrorResponse()), "header");
            }
        };
        assertThrows(AppException.class, () -> paymentSessionAccess.managePaymentSession(new EnableSessionRequest(), CallType.CREATE));
    }

    @Test
    void enablePaymentSession500FailureTest() throws AppException, HttpFailureException {
        new Expectations() {
            {
                service.put(anyString, any, anyString, anyString, (HttpService.HttpHeaders.HttpHeader[]) any);
                result = new AppException(500, jsonMapper.writeValueAsString(paymentSessionErrorResponse()), "header");
            }
        };
        assertThrows(AppException.class, () -> paymentSessionAccess.managePaymentSession(new EnableSessionRequest(), CallType.CREATE));
    }

    @Test
    void enablePaymentSessionUnknownExceptionFailureTest() throws AppException, HttpFailureException {
        new Expectations() {
            {
                service.put(anyString, any, anyString, anyString, (HttpService.HttpHeaders.HttpHeader[]) any);
                result = new Exception();
            }
        };
        assertThrows(AppException.class, () -> paymentSessionAccess.managePaymentSession(new EnableSessionRequest(), CallType.CREATE));
    }

    @Test
    void enablePaymentSessionFailureTest() throws AppException, HttpFailureException {
        new Expectations() {
            {
                service.put(anyString, any, anyString, anyString, (HttpService.HttpHeaders.HttpHeader[]) any);
                result = null;
            }
        };
        assertThrows(AppException.class, () -> paymentSessionAccess.managePaymentSession(new EnableSessionRequest(), CallType.CREATE));
    }

    private com.mgmresorts.content.model.Error paymentSessionErrorResponse() {
        com.mgmresorts.content.model.Error responseError = new com.mgmresorts.content.model.Error();
        List<com.mgmresorts.content.model.Message> errorMsgList = new ArrayList<>();
        com.mgmresorts.content.model.Message errorMsg = new com.mgmresorts.content.model.Message();
        errorMsg.setCode("_system_error");
        errorMsg.setType("error");
        errorMsg.setMsg("system unavailable");
        errorMsgList.add(errorMsg);
        responseError.setMessages(errorMsgList);
        return responseError;
    }

    void testGetPaymentSession_SuccessResponse() throws Exception {
        new Expectations() {
            {
                service.get(anyString, (Class<RetrieveSessionResponse>) any, "payment-session-get", "payment-session-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                String paymentSessionJson = Utils.readFileFromClassPath("data/payment_session_get_response_success.json");
                result = mapper.readValue(paymentSessionJson, RetrieveSessionResponse.class);
            }
        };
        RetrieveSessionResponse response = paymentSessionAccess.getPaymentSession("79742");
        assertNotNull(response);
        assertNotNull(response.getGuestDetails());
        assertNotNull(response.getGuestDetails().getAddress());
        assertNotNull(response.getCardDetails());
        assertNotNull(response.getCardDetails().getBillingAddress());
        assertNotNull(response.getOrderItems());
        assertNotNull(response.getOrderItems().getItemAuthGroups());
        assertNotNull(response.getOrderItems().getItemAuthGroups().get(0));
        assertNotNull(response.getOrderItems().getItemAuthGroups().get(0).getPaymentAuthResults());
        assertNotNull(response.getOrderItems().getItemAuthGroups().get(0).getPaymentAuthResults().get(0));
        assertNotNull(response.getOrderItems().getItemAuthGroups().get(0).getPaymentAuthResults().get(0).getPaymentId());
        ;
        assertEquals("5875GT", response.getOrderItems().getItemAuthGroups().get(0).getPaymentAuthResults().get(0).getPaymentId());
        assertEquals("2268TY", response.getOrderItems().getItemAuthGroups().get(0).getPaymentAuthResults().get(0).getAuthorizationCode());
        assertEquals("86495f06-d98a-4934-b014-b59ed8f9e5ca", response.getOrderItems().getOrderReferenceNumber());
    }

    @Test
    void testGetPaymentSessionErrorResponse_ServerError() throws Exception {
        new Expectations() {
            {
                service.get(anyString, (Class<RetrieveSessionResponse>) any, "payment-session-get", "payment-session-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(500, mapper.writeValueAsString(getPaymentSession500InternalServerErrorResponse()), "failed", new String[]{"header"});
            }
        };
        assertThrows(AppException.class, () -> paymentSessionAccess.getPaymentSession("79742"));
    }

    @Test
    void testGetPaymentSessionErrorResponse_ExpiredSession() throws Exception {
        new Expectations() {
            {
                service.get(anyString, (Class<RetrieveSessionResponse>) any, "payment-session-get", "payment-session-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(404, mapper.writeValueAsString(getPaymentSession404ExpiredSessionErrorResponse()), "failed", new String[]{"header"});
            }
        };
        assertThrows(AppException.class, () -> paymentSessionAccess.getPaymentSession("79742"));
    }

    @Test
    void testGetPaymentSessionErrorResponse_InvalidSession() throws Exception {
        new Expectations() {
            {
                service.get(anyString, (Class<RetrieveSessionResponse>) any, "payment-session-get", "payment-session-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(404, mapper.writeValueAsString(getPaymentSession404InvalidSessionErrorResponse()), "failed", new String[]{"header"});
            }
        };
        assertThrows(AppException.class, () -> paymentSessionAccess.getPaymentSession("79742"));
    }

    @Test
    void testGetPaymentSessionErrorResponse_MissingHeaderParams() throws Exception {
        new Expectations() {
            {
                service.get(anyString, (Class<RetrieveSessionResponse>) any, "payment-session-get", "payment-session-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(400, mapper.writeValueAsString(getPaymentSession400MissingHeaderParamsErrorResponse()), "failed", new String[]{"header"});
            }
        };
        assertThrows(AppException.class, () -> paymentSessionAccess.getPaymentSession("79742"));
    }

    @Test
    void testGetPaymentSession_WhenResponseIsNull() throws Exception {
        new Expectations() {
            {
                service.get(anyString, (Class<RetrieveSessionResponse>) any, "payment-session-get", "payment-session-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = null;
            }
        };
        assertThrows(AppException.class, () -> paymentSessionAccess.getPaymentSession("79742"));
    }

    private SessionError getPaymentSession500InternalServerErrorResponse() {
        SessionError response = new SessionError();
        response.setErrorCode("00041-0001-0-0500");
        response.setErrorMessage("Internal Server Error");
        return response;
    }

    private SessionError getPaymentSession404ExpiredSessionErrorResponse() {
        SessionError response = new SessionError();
        response.setErrorCode("00041-0001-0-0101");
        response.setErrorMessage("Session Expired");
        return response;
    }

    private SessionError getPaymentSession404InvalidSessionErrorResponse() {
        SessionError response = new SessionError();
        response.setErrorCode("00041-0001-0-0201");
        response.setErrorMessage("Invalid Session");
        return response;
    }

    private SessionError getPaymentSession400MissingHeaderParamsErrorResponse() {
        SessionError response = new SessionError();
        response.setErrorCode("00041-0001-0-0400");
        response.setErrorMessage("Missing Required Parameters");
        return response;
    }
}
