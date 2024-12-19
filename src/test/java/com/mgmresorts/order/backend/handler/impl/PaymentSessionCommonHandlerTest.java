package com.mgmresorts.order.backend.handler.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.IPaymentSessionAccess;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.psm.model.RetrieveSessionResponse;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;


public class PaymentSessionCommonHandlerTest {
    @Tested
    private PaymentSessionCommonHandler paymentSessionHandler;
    private final JSonMapper mapper = new JSonMapper();
    @Injectable
    private IPaymentSessionAccess paymentSessionAccess;

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @BeforeEach
    public void before() {
        assertNotNull(paymentSessionAccess);
        ErrorManager.clean();
        new Errors();
    }

    @Test
    void testGetPaymentAuthResults_Success() throws Exception {
        new Expectations() {
            {
                paymentSessionAccess.getPaymentSession("79742");
                String paymentSessionJson = Utils.readFileFromClassPath("data/payment_session_get_response_success.json");
                result =  mapper.readValue(paymentSessionJson, RetrieveSessionResponse.class);
            }
        };
        PaymentSessionBaseFields paymentSessionBaseFields = paymentSessionHandler.getPaymentAuthResults("79742");
        assertNotNull(paymentSessionBaseFields);
        assertNotNull(paymentSessionBaseFields.getGuestProfile());
        assertNotNull(paymentSessionBaseFields.getBillings());
        assertNotNull(paymentSessionBaseFields.getPaymentAuthFieldsMap());
        Map<String, PaymentAuthFields> paymentAuthFields = paymentSessionBaseFields.getPaymentAuthFieldsMap();
        assertEquals("GT8756Y", paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").getPaymentId());
        assertEquals("HDB456H", paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").getAuthorizationCode());
        assertEquals(250.00, paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").getAmount());
        assertEquals(true, paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").isSuccess());
        assertEquals("6875GT", paymentAuthFields.get("81e7412c-9692-42d5-9582-3e9bba473db9").getPaymentId());
        assertEquals("3268TY", paymentAuthFields.get("81e7412c-9692-42d5-9582-3e9bba473db9").getAuthorizationCode());
        assertEquals(255.67, paymentAuthFields.get("81e7412c-9692-42d5-9582-3e9bba473db9").getAmount());
        assertEquals(true, paymentAuthFields.get("81e7412c-9692-42d5-9582-3e9bba473db9").isSuccess());
        assertEquals("e5d3f1c9-833a-83f1-e053-d303fe0ad83c", paymentAuthFields.get("a62b794f-af55-46fb-bfe4-c33c1c1641f4").getPropertyId());
        assertEquals("c9b0ee08-3eff-421d-a141-80f8013e8e53", paymentAuthFields.get("a62b794f-af55-46fb-bfe4-c33c1c1641f4").getItemId());
        assertEquals("86495f06-d98a-4934-b014-b59ed8f9e5ca", paymentSessionBaseFields.getOrderReferenceNumber());


        assertEquals("Test", paymentSessionBaseFields.getGuestProfile().getFirstName());
        assertEquals("User", paymentSessionBaseFields.getGuestProfile().getLastName());
        assertEquals("702", paymentSessionBaseFields.getGuestProfile().getPhoneNumbers().get(0).getNumber());
        assertEquals("Las Vegas", paymentSessionBaseFields.getGuestProfile().getAddresses().get(0).getCity());

        assertEquals("Test User", paymentSessionBaseFields.getBillings().get(0).getPayment().getCardHolder());
        assertEquals("XXXXXXXXXXXX4111", paymentSessionBaseFields.getBillings().get(0).getPayment().getMaskedNumber());
        assertEquals("Visa", paymentSessionBaseFields.getBillings().get(0).getPayment().getType().toString());
        assertEquals("Henderson", paymentSessionBaseFields.getBillings().get(0).getAddress().getCity());
        assertEquals("NV", paymentSessionBaseFields.getBillings().get(0).getAddress().getState());
        assertEquals("US", paymentSessionBaseFields.getBillings().get(0).getAddress().getCountry());
        assertEquals(605.67, paymentSessionBaseFields.getBillings().get(0).getPayment().getAmount());
    }
    
    @Test
    void testGetPaymentAuthResults_FraudFailure() throws Exception {
        new Expectations() {
            {
                paymentSessionAccess.getPaymentSession("79742");
                String paymentSessionJson = Utils.readFileFromClassPath("data/payment_session_get_response_fraud_failure.json");
                result =  mapper.readValue(paymentSessionJson, RetrieveSessionResponse.class);
            }
        };
        PaymentSessionBaseFields paymentSessionBaseFields = paymentSessionHandler.getPaymentAuthResults("79742");
        assertNotNull(paymentSessionBaseFields);
        assertNotNull(paymentSessionBaseFields.getGuestProfile());
        assertNotNull(paymentSessionBaseFields.getBillings());
        assertNotNull(paymentSessionBaseFields.getPaymentAuthFieldsMap());
        Map<String, PaymentAuthFields> paymentAuthFields = paymentSessionBaseFields.getPaymentAuthFieldsMap();
        assertEquals(false, paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").isSuccess());
        assertEquals("00041-0001-0-0201", paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").getErrorCode());
        assertEquals("Fraud Failure", paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").getErrorMessage());

        assertEquals("Test", paymentSessionBaseFields.getGuestProfile().getFirstName());
        assertEquals("User", paymentSessionBaseFields.getGuestProfile().getLastName());
        assertEquals("702", paymentSessionBaseFields.getGuestProfile().getPhoneNumbers().get(0).getNumber());
        assertEquals("Las Vegas", paymentSessionBaseFields.getGuestProfile().getAddresses().get(0).getCity());

        assertEquals("Test User", paymentSessionBaseFields.getBillings().get(0).getPayment().getCardHolder());
        assertEquals("XXXXXXXXXXXX4111", paymentSessionBaseFields.getBillings().get(0).getPayment().getMaskedNumber());
        assertEquals("Visa", paymentSessionBaseFields.getBillings().get(0).getPayment().getType().toString());
        assertEquals("Henderson", paymentSessionBaseFields.getBillings().get(0).getAddress().getCity());
        assertEquals("NV", paymentSessionBaseFields.getBillings().get(0).getAddress().getState());
        assertEquals("US", paymentSessionBaseFields.getBillings().get(0).getAddress().getCountry());
        assertEquals(0.0, paymentSessionBaseFields.getBillings().get(0).getPayment().getAmount());
    }

    @Test
    void testGetPaymentAuthResults_AuthFailure() throws Exception {
        new Expectations() {
            {
                paymentSessionAccess.getPaymentSession("79742");
                String paymentSessionJson = Utils.readFileFromClassPath("data/payment_session_get_response_auth_failure.json");
                result =  mapper.readValue(paymentSessionJson, RetrieveSessionResponse.class);
            }
        };
        PaymentSessionBaseFields paymentSessionBaseFields = paymentSessionHandler.getPaymentAuthResults("79742");
        assertNotNull(paymentSessionBaseFields);
        assertNotNull(paymentSessionBaseFields.getGuestProfile());
        assertNotNull(paymentSessionBaseFields.getBillings());
        assertNotNull(paymentSessionBaseFields.getPaymentAuthFieldsMap());
        Map<String, PaymentAuthFields> paymentAuthFields = paymentSessionBaseFields.getPaymentAuthFieldsMap();
        assertEquals(false, paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").isSuccess());
        assertEquals("00041-0001-0-0101", paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").getErrorCode());
        assertEquals("Auth Failure", paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").getErrorMessage());

        assertEquals("Test", paymentSessionBaseFields.getGuestProfile().getFirstName());
        assertEquals("User", paymentSessionBaseFields.getGuestProfile().getLastName());
        assertEquals("702", paymentSessionBaseFields.getGuestProfile().getPhoneNumbers().get(0).getNumber());
        assertEquals("Las Vegas", paymentSessionBaseFields.getGuestProfile().getAddresses().get(0).getCity());

        assertEquals("Test User", paymentSessionBaseFields.getBillings().get(0).getPayment().getCardHolder());
        assertEquals("XXXXXXXXXXXX4111", paymentSessionBaseFields.getBillings().get(0).getPayment().getMaskedNumber());
        assertEquals("Visa", paymentSessionBaseFields.getBillings().get(0).getPayment().getType().toString());
        assertEquals("Henderson", paymentSessionBaseFields.getBillings().get(0).getAddress().getCity());
        assertEquals("NV", paymentSessionBaseFields.getBillings().get(0).getAddress().getState());
        assertEquals("US", paymentSessionBaseFields.getBillings().get(0).getAddress().getCountry());
        assertEquals(0.0, paymentSessionBaseFields.getBillings().get(0).getPayment().getAmount());
    }

    @Test
    void testGetPaymentAuthResults_AuthFailureNoAuthAmount() throws Exception {
        new Expectations() {
            {
                paymentSessionAccess.getPaymentSession("79742");
                String paymentSessionJson = Utils.readFileFromClassPath("data/payment_session_get_response_auth_failure.json");
                RetrieveSessionResponse retrieveSessionResponse = mapper.readValue(paymentSessionJson, RetrieveSessionResponse.class);
                retrieveSessionResponse.getOrderItems().getItemAuthGroups().get(0).getPaymentAuthResults().get(0).setAuthorizedAmount(null);
                result = retrieveSessionResponse;
            }
        };
        assertDoesNotThrow(() -> paymentSessionHandler.getPaymentAuthResults("79742"));
    }

    @Test
    void testGetPaymentAuthResults_PartialSuccess() throws Exception {
        new Expectations() {
            {
                paymentSessionAccess.getPaymentSession("79742");
                String paymentSessionJson = Utils.readFileFromClassPath("data/payment_session_get_response_partial_success.json");
                result =  mapper.readValue(paymentSessionJson, RetrieveSessionResponse.class);
            }
        };
        PaymentSessionBaseFields paymentSessionBaseFields = paymentSessionHandler.getPaymentAuthResults("79742");
        assertNotNull(paymentSessionBaseFields);
        assertNotNull(paymentSessionBaseFields.getGuestProfile());
        assertNotNull(paymentSessionBaseFields.getBillings());
        assertNotNull(paymentSessionBaseFields.getPaymentAuthFieldsMap());
        Map<String, PaymentAuthFields> paymentAuthFields = paymentSessionBaseFields.getPaymentAuthFieldsMap();
        assertEquals(false, paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").isSuccess());
        assertEquals("00041-0001-0-0101", paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").getErrorCode());
        assertEquals("Auth Failure", paymentAuthFields.get("71e7412c-9692-42d5-9582-3e9bba473db9").getErrorMessage());
        assertEquals("6875GT", paymentAuthFields.get("81e7412c-9692-42d5-9582-3e9bba473db9").getPaymentId());
        assertEquals("3268TY", paymentAuthFields.get("81e7412c-9692-42d5-9582-3e9bba473db9").getAuthorizationCode());
        assertEquals(255.67, paymentAuthFields.get("81e7412c-9692-42d5-9582-3e9bba473db9").getAmount());
        assertEquals(true, paymentAuthFields.get("81e7412c-9692-42d5-9582-3e9bba473db9").isSuccess());
        assertEquals("86495f06-d98a-4934-b014-b59ed8f9e5ca", paymentSessionBaseFields.getOrderReferenceNumber());

        assertEquals("Test", paymentSessionBaseFields.getGuestProfile().getFirstName());
        assertEquals("User", paymentSessionBaseFields.getGuestProfile().getLastName());
        assertEquals("702", paymentSessionBaseFields.getGuestProfile().getPhoneNumbers().get(0).getNumber());
        assertEquals("Las Vegas", paymentSessionBaseFields.getGuestProfile().getAddresses().get(0).getCity());

        assertEquals("Test User", paymentSessionBaseFields.getBillings().get(0).getPayment().getCardHolder());
        assertEquals("XXXXXXXXXXXX4111", paymentSessionBaseFields.getBillings().get(0).getPayment().getMaskedNumber());
        assertEquals("Visa", paymentSessionBaseFields.getBillings().get(0).getPayment().getType().toString());
        assertEquals("Henderson", paymentSessionBaseFields.getBillings().get(0).getAddress().getCity());
        assertEquals("NV", paymentSessionBaseFields.getBillings().get(0).getAddress().getState());
        assertEquals("US", paymentSessionBaseFields.getBillings().get(0).getAddress().getCountry());
        assertEquals(255.67, paymentSessionBaseFields.getBillings().get(0).getPayment().getAmount());
    }

    @Test
    void testGetPaymentAuthResults_RetrievePaymentSessionResponseIsNull() throws Exception {
        new Expectations() {
            {
                paymentSessionAccess.getPaymentSession("79742");
                result =  null;
            }
        };
        PaymentSessionBaseFields paymentSessionBaseFields = paymentSessionHandler.getPaymentAuthResults("79742");
        assertNull(paymentSessionBaseFields);
    }
}
