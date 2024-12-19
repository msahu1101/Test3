package com.mgmresorts.order.backend.handler.impl;

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
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.order.backend.access.IPaymentAccess;
import com.mgmresorts.order.dto.Address;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.BillingAddress;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.dto.Payment;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.payments.model.PaymentsOrchestrationWorkFlowResquest;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;

public class PaymentHandlerTest {
    @Tested
    private PaymentHandler paymentHandler;
    
    @Injectable
    private IPaymentAccess paymentAccess;

    private JSonMapper jsonMapper = new JSonMapper();

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @BeforeEach
    public void before() {
        assertNotNull(paymentAccess);
        ErrorManager.clean();
        new Errors();
    }

    @Test
    void testPaymentValidateResponseSuccess() throws AppException, HttpFailureException {

        new Expectations() {
            {
                paymentAccess.validatePaymentMethod((PaymentsOrchestrationWorkFlowResquest) any);
                result = getPaymentServiceSuccessResponse();
            }
        };

        boolean skipAFS = paymentHandler.validatePaymentMethod(getPaymentsOrchestrationWorkFlowResquest());
        assertEquals(true, skipAFS);
    }
    
    @Test
    void testPaymentValidate_AFS_Invalid_CCToken() throws AppException, HttpFailureException {

        new Expectations() {
            {
                paymentAccess.validatePaymentMethod((PaymentsOrchestrationWorkFlowResquest) any);
                result = invalidCCToken_AFS_Response();
            }
        };
        
        assertThrows(AppException.class, () -> paymentHandler.validatePaymentMethod(getPaymentsOrchestrationWorkFlowResquest()));
    }
    
    @Test
    void testPaymentValidate_AFS_Invalid_CurrencyCode() throws AppException, HttpFailureException {

        new Expectations() {
            {
                paymentAccess.validatePaymentMethod((PaymentsOrchestrationWorkFlowResquest) any);
                result = invalidCurrencyCodeResponse();
            }
        };
        
        assertThrows(AppException.class, () -> paymentHandler.validatePaymentMethod(getPaymentsOrchestrationWorkFlowResquest()));
    }
    
    @Test
    void testPaymentValidate_AFS_Invalid_BookingType() throws AppException, HttpFailureException {

        new Expectations() {
            {
                paymentAccess.validatePaymentMethod((PaymentsOrchestrationWorkFlowResquest) any);
                result = invalidBookingTypeResponse();
            }
        };
        
        assertThrows(AppException.class, () -> paymentHandler.validatePaymentMethod(getPaymentsOrchestrationWorkFlowResquest()));
    }
    
    @Test
    void testPaymentValidate_AFS_Invalid_TransactionType() throws AppException, HttpFailureException {

        new Expectations() {
            {
                paymentAccess.validatePaymentMethod((PaymentsOrchestrationWorkFlowResquest) any);
                result = invalidTransactionTypeResponse();
            }
        };
        
        assertThrows(AppException.class, () -> paymentHandler.validatePaymentMethod(getPaymentsOrchestrationWorkFlowResquest()));
    }
    
    @Test
    void testPaymentValidate_AFS_No_GuestDetails() throws AppException, HttpFailureException {

        new Expectations() {
            {
                paymentAccess.validatePaymentMethod((PaymentsOrchestrationWorkFlowResquest) any);
                result = noGuestDetails_AFS_Response();
            }
        };
        
        assertThrows(AppException.class, () -> paymentHandler.validatePaymentMethod(getPaymentsOrchestrationWorkFlowResquest()));

    }
    
    @Test
    void testPaymentValidate_PPS_Invalid_CCToken() throws AppException, HttpFailureException {

        new Expectations() {
            {
                paymentAccess.validatePaymentMethod((PaymentsOrchestrationWorkFlowResquest) any);
                result = authorizationRejected_PPS_Response();
            }
        };
        
        assertThrows(AppException.class, () -> paymentHandler.validatePaymentMethod(getPaymentsOrchestrationWorkFlowResquest()));
    }
    
    @Test
    void testPaymentValidate_AFS_Invalid_MerchantId() throws AppException, HttpFailureException {

        new Expectations() {
            {
                paymentAccess.validatePaymentMethod((PaymentsOrchestrationWorkFlowResquest) any);
                result = invalid_Merchant_id_Response();
            }
        };
        
        assertThrows(AppException.class, () -> paymentHandler.validatePaymentMethod(getPaymentsOrchestrationWorkFlowResquest()));
    }
    
    @Test
    void testCheckoutFailureDuringPayment() throws AppException, HttpFailureException {
        new Expectations() {
            {
                paymentAccess.validatePaymentMethod((PaymentsOrchestrationWorkFlowResquest) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(paymentFailureTokenResponse()), "Error while calling http endpoint",
                        new String[] { "header" });
                minTimes = 1;
            }
        };

        assertThrows(AppException.class, () -> paymentHandler.validatePaymentMethod(getPaymentsOrchestrationWorkFlowResquest()));
    }


    private CheckoutRequest getPaymentsOrchestrationWorkFlowResquest() {
        Payment payment = new Payment();
        payment.setFirstName("firstname");
        payment.setLastName("lastname");
        payment.setAmount(1000.00);
        payment.setCardHolder("first last");
        payment.setCcToken("4111110A001DQXFHY79D9XCZ1111");
        payment.setCvv("123");
        payment.setExpiry("10/2024");
        payment.setType(Payment.Type.VISA);
        payment.setMaskedNumber("XXXXXXXXXXXXXXXXX1111");

        BillingAddress addressReq = new BillingAddress();
        addressReq.setStreet1("123 Main St");
        addressReq.setStreet2("Apt 1092");
        addressReq.setCity("Las Vegas");
        addressReq.setPostalCode("89118");
        addressReq.setCountry("US");

        Billing billingReq = new Billing();
        billingReq.setAddress(addressReq);
        billingReq.setPayment(payment);
        final List<Billing> creditcards = new ArrayList<>();
        creditcards.add(billingReq);

        GuestProfile guestProfile = new GuestProfile();
        guestProfile.setId("121212121");
        guestProfile.setMlifeNo("2323232");
        guestProfile.setFirstName("first");
        guestProfile.setLastName("last");
        
        final List<Address> addressList = new ArrayList<>();
        Address profileAddress = new Address();
        profileAddress.setStreet1("123 Main St");
        profileAddress.setStreet2("Apt 1092");
        profileAddress.setCity("Las Vegas");
        profileAddress.setPostalCode("89118");
        profileAddress.setCountry("US");
        profileAddress.setType(Address.Type.HOME);
        
        addressList.add(profileAddress);
        guestProfile.setAddresses(addressList);

        CheckoutRequest request = new CheckoutRequest();
        request.setCartId("cartId");
        request.setMgmId("mgmId");
        request.setBillings(creditcards);
        request.setGuestProfile(guestProfile);
        request.setEnableJwb(true);
        request.setItineraryId("itineraryid");
        request.setCartType(Type.GLOBAL);

        // Request Object Set End
        return request;
    }

    private String invalidCCToken_AFS_Response() {
        return "{\"workflowResponse\":[{\"functionName\":\"AFSAuthorize\",\"statusCode\":400,\"statusDesc\":\"BadRequest\",\"body\":{\"type\":\"error\",\"code\":\"_invalid_token\","
                + "\"msg\":\"Credit card token supplied is invalid\"}},{\"functionName\":\"PPSAuthorize\",\"statusCode\":200,\"statusDesc\":\"OK\","
                + "\"body\":{\"statusMessage\":\"APPROVED\",\"decision\":\"APPROVED\",\"amount\":\"123.58\",\"authRequestId\":\"01Z6KEN81Q97U6C30IU0APAMTHGTGG3S\",\"reasonCode\":\"100\"}}]}";
    }
    
    private String invalidCurrencyCodeResponse() {
        return "{\"workflowResponse\":[{\"functionName\":\"AFSAuthorize\",\"statusCode\":400,\"statusDesc\":\"BadRequest\",\"body\":{\"type\":\"error\","
                + "\"code\":\"_invalid_transaction_paymentMethods_currencyCode\",\"msg\":\"Currency code not recognized\"}},{\"functionName\":\"PPSAuthorize\","
                + "\"statusCode\":200,\"statusDesc\":\"OK\",\"body\":{\"statusMessage\":\"APPROVED\",\"decision\":\"APPROVED\",\"amount\":\"123.58\","
                + "\"authRequestId\":\"01Z6KEN8MN97U6C30K0HNV85BSK7NCN5\",\"reasonCode\":\"100\"}}]}";
    }
    
    private String invalidBookingTypeResponse() {
        return "{\"workflowResponse\":[{\"functionName\":\"AFSAuthorize\",\"statusCode\":400,\"statusDesc\":\"BadRequest\",\"body\":{\"type\":\"error\","
                + "\"code\":\"_invalid_transaction_bookingType\",\"msg\":\"Booking Type not recognized\"}},{\"functionName\":\"PPSAuthorize\","
                + "\"statusCode\":200,\"statusDesc\":\"OK\",\"body\":{\"statusMessage\":\"APPROVED\",\"decision\":\"APPROVED\","
                + "\"amount\":\"123.58\",\"authRequestId\":\"01Z6KEN8SB97U6C30KB1SRVNKHAVL6RR\",\"reasonCode\":\"100\"}}]}";
    }
    
    private String invalidTransactionTypeResponse() {
        return "{\"workflowResponse\":[{\"functionName\":\"AFSAuthorize\",\"statusCode\":400,\"statusDesc\":\"BadRequest\",\"body\":{\"type\":\"error\","
                + "\"code\":\"_invalid_transaction_transactionType\",\"msg\":\"Transaction Type not recognized\"}},{\"functionName\":\"PPSAuthorize\","
                + "\"statusCode\":200,\"statusDesc\":\"OK\",\"body\":{\"statusMessage\":\"APPROVED\",\"decision\":\"APPROVED\","
                + "\"amount\":\"123.58\",\"authRequestId\":\"01Z6KEN8VK97U6C30KGL7RND8THMPS44\",\"reasonCode\":\"100\"}}]}";
    }
    
    private String noGuestDetails_AFS_Response() {
        return "{\"workflowResponse\":[{\"functionName\":\"AFSAuthorize\",\"statusCode\":400,\"statusDesc\":\"BadRequest\",\"body\":{\"type\":\"error\","
                + "\"code\":\"_invalid_transaction_loggedIn_status\",\"msg\":\"Logged-in status for guest is required\"}},{\"functionName\":\"PPSAuthorize\","
                + "\"statusCode\":200,\"statusDesc\":\"OK\",\"body\":{\"statusMessage\":\"APPROVED\",\"decision\":\"APPROVED\",\"amount\":\"123.58\","
                + "\"authRequestId\":\"01Z6KEN8G897U6C30JM52MJNEK0R2OUG\",\"reasonCode\":\"100\"}}]}";
    }

    private String authorizationRejected_PPS_Response() {
        return "{\"workflowResponse\":[{\"functionName\":\"AFSAuthorize\",\"statusCode\":200,\"statusDesc\":\"OK\",\"body\":{\"authorized\":true,"
                + "\"transactionId\":\"7c441b5d-2839-4cc5-a987-5c3a152754bb\",\"reference\":\"bf16a743-2384-4618-933b-21883d14db91\",\"authorizationRemarks\":\"\","
                + "\"recommendationCode\":\"ACCEPT\"}},{\"functionName\":\"PPSAuthorize\",\"statusCode\":200,\"statusDesc\":\"OK\","
                + "\"body\":{\"statusMessage\":\"REJECT Invalid token\",\"decision\":\"REJECT\",\"amount\":\"0.00\",\"reasonCode\":\"431\"}}]}";
    }
    
    private String invalid_Merchant_id_Response() {
        return "{\"workflowResponse\":[{\"functionName\":\"AFSAuthorize\",\"statusCode\":200,\"statusDesc\":\"OK\",\"body\":{\"authorized\":true,"
                + "\"transactionId\":\"7c441b5d-2839-4cc5-a987-5c3a152754bb\",\"reference\":\"e7c6f54d-ed04-4647-aa87-2f717e861a6d\",\"authorizationRemarks\":\"\","
                + "\"recommendationCode\":\"ACCEPT\"}},{\"functionName\":\"PPSAuthorize\",\"statusCode\":400,\"statusDesc\":\"BadRequest\","
                + "\"body\":{\"statusMessage\":\"ERROR: Bad Request: Merchant ID required\"}}]}";
    }

    private String paymentFailureTokenResponse() {
        return "Resource Not Found";
    }


    final String getPaymentServiceSuccessResponse() {
        return "{\"workflowResponse\":[{\"functionName\":\"AFSAuthorize\",\"statusCode\":200,\"statusDesc\":\"OK\",\"body\":{\"authorized\":true,"
                + "\"transactionId\":\"498cc970-22e3-4ee9-b320-fb873150ba06\",\"reference\":\"6a6ae5c3-1883-45ed-a348-a5b515cf165a\",\"authorizationRemarks\":\"\","
                + "\"recommendationCode\":\"ACCEPT\"}},{\"functionName\":\"PPSAuthorize\",\"statusCode\":200,\"statusDesc\":\"OK\",\"body\":{\"statusMessage\":\"APPROVED\","
                + "\"decision\":\"APPROVED\",\"amount\":\"379930.28\",\"authRequestId\":\"01Z6KEL3I897U6C2T10P8IHJVUFO84CF\",\"reasonCode\":\"100\"}}]}";
    }
}
