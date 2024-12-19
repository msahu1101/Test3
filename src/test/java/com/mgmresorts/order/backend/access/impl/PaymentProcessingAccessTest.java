package com.mgmresorts.order.backend.access.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.http.IHttpService.HttpHeaders.HttpHeader;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.pps.model.Amount;
import com.mgmresorts.pps.model.Payment;
import com.mgmresorts.pps.model.PaymentRequest;
import com.mgmresorts.pps.model.PaymentResponse;
import com.mgmresorts.pps.model.TenderDetails;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@SuppressWarnings("unused")
public class PaymentProcessingAccessTest {
    @Tested
    private PaymentProcessingAccess paymentProcessingAccess;
    @Injectable
    private IHttpService service;
    @Injectable
    private OAuthTokenRegistry registry;

    private final JSonMapper jsonMapper = new JSonMapper();
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
    public void testCapturePayment_200SuccessResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-capture", "payment-processing-capture", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/payment_processing_capture_success_response.json");
            }
        };

        PaymentResponse response = paymentProcessingAccess.capturePayment(createCaptureRequest());

        assertNotNull(response);
        assertNotNull(response.getStatusCode());
        assertNotNull(response.getResults());
        assertEquals("200", response.getStatusCode());
        assertEquals(1, response.getResults().size());
        assertNotNull(response.getResults().get(0).getGatewayResult());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertEquals("001", response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertEquals("SUCCESS", response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
        assertEquals("192029", response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
    }

    @Test
    public void testCapturePayment_200FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-capture", "payment-processing-capture", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/payment_processing_capture_200_failure_response.json");
            }
        };

        PaymentResponse response = paymentProcessingAccess.capturePayment(createCaptureRequest());

        assertNotNull(response);
        assertNotNull(response.getStatusCode());
        assertNotNull(response.getResults());
        assertEquals("200", response.getStatusCode());
        assertEquals(1, response.getResults().size());
        assertNotNull(response.getResults().get(0).getGatewayResult());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertEquals("002", response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertEquals("FAILURE", response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
        assertEquals("192029", response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
    }

    @Test
    public void testCapturePayment_400FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-capture", "payment-processing-capture", (HttpHeader[]) any);
                result = new HttpFailureException(400, Utils.readFileFromClassPath("data/payment_processing_capture_failure_response.json"), "failed", new String[] {"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> paymentProcessingAccess.capturePayment(createCaptureRequest()));
    }

    @Test
    public void testCapturePayment_401FailureResponse() throws Exception {
        final String payload = "{\"message\":\"JWT is not valid, Please provide a valid Token\"}";
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-capture", "payment-processing-capture", (HttpHeader[]) any);
                result = new HttpFailureException(401, payload, "Unauthorized");
            }
        };
        assertThrows(AppException.class, () -> paymentProcessingAccess.capturePayment(createCaptureRequest()));

    }

    @Test
    public void testCapturePayment_404FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-capture", "payment-processing-capture", (HttpHeader[]) any);
                result = new HttpFailureException(404, Utils.readFileFromClassPath("data/payment_processing_capture_failure_response.json"), "failed",
                        new String[] {"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> paymentProcessingAccess.capturePayment(createCaptureRequest()));
    }

    @Test
    public void testCapturePayment_500FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-capture", "payment-processing-capture", (HttpHeader[]) any);
                result = new HttpFailureException(500, Utils.readFileFromClassPath("data/payment_processing_capture_failure_response.json"), "failed",
                        new String[] {"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> paymentProcessingAccess.capturePayment(createCaptureRequest()));
    }

    @Test
    public void testCapturePayment_NullResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-capture", "payment-processing-capture", (HttpHeader[]) any);
                result = null;
            }
        };
        assertThrows(AppException.class, () -> paymentProcessingAccess.capturePayment(createCaptureRequest()));
    }

    @Test
    public void testVoidPayment_200SuccessResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-void", "payment-processing-void", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/payment_processing_void_success_response.json");
            }
        };

        PaymentResponse response = paymentProcessingAccess.voidPayment(createVoidRequest());

        assertNotNull(response);
        assertNotNull(response.getStatusCode());
        assertNotNull(response.getResults());
        assertEquals("200", response.getStatusCode());
        assertEquals(1, response.getResults().size());
        assertNotNull(response.getResults().get(0).getGatewayResult());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertEquals("001", response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertEquals("SUCCESS", response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
        assertEquals("192029", response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
    }

    @Test
    public void testVoidPayment_200FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-void", "payment-processing-void", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/payment_processing_void_200_failure_response.json");
            }
        };

        PaymentResponse response = paymentProcessingAccess.voidPayment(createVoidRequest());

        assertNotNull(response);
        assertNotNull(response.getStatusCode());
        assertNotNull(response.getResults());
        assertEquals("200", response.getStatusCode());
        assertEquals(1, response.getResults().size());
        assertNotNull(response.getResults().get(0).getGatewayResult());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertEquals("002", response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertEquals("FAILURE", response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
        assertEquals("192029", response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
    }

    @Test
    public void testVoidPayment_400FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-void", "payment-processing-void", (HttpHeader[]) any);
                result = new HttpFailureException(400, Utils.readFileFromClassPath("data/payment_processing_void_failure_response.json"), "failed",
                        new String[] {"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> paymentProcessingAccess.voidPayment(createVoidRequest()));
    }

    @Test
    public void testVoidPayment_401FailureResponse() throws Exception {
        final String payload = "{\"message\":\"JWT is not valid, Please provide a valid Token\"}";
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-void", "payment-processing-void", (HttpHeader[]) any);
                result = new HttpFailureException(401, payload, "Unauthorized");
            }
        };
        assertThrows(AppException.class, () -> paymentProcessingAccess.voidPayment(createVoidRequest()));
    }

    @Test
    public void testVoidPayment_404FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-void", "payment-processing-void", (HttpHeader[]) any);
                result = new HttpFailureException(404, Utils.readFileFromClassPath("data/payment_processing_void_failure_response.json"), "failed",
                        new String[] {"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> paymentProcessingAccess.voidPayment(createVoidRequest()));
    }

    @Test
    public void testVoidPayment_500FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-void", "payment-processing-void", (HttpHeader[]) any);
                result = new HttpFailureException(500, Utils.readFileFromClassPath("data/payment_processing_void_failure_response.json"), "failed",
                        new String[] {"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> paymentProcessingAccess.voidPayment(createVoidRequest()));
    }

    @Test
    public void testVoidPayment_NullResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-void", "payment-processing-void", (HttpHeader[]) any);
                result = null;
            }
        };
        assertThrows(AppException.class, () -> paymentProcessingAccess.voidPayment(createVoidRequest()));
    }

    @Test
    public void testRefundPayment_200SuccessResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-refund", "payment-processing-refund", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/payment_processing_refund_success_response.json");
            }
        };

        PaymentResponse response = paymentProcessingAccess.refundPayment(createRefundRequest());

        assertNotNull(response);
        assertNotNull(response.getStatusCode());
        assertNotNull(response.getResults());
        assertEquals("200", response.getStatusCode());
        assertEquals(1, response.getResults().size());
        assertNotNull(response.getResults().get(0).getGatewayResult());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertEquals("001", response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertEquals("SUCCESS", response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
        assertEquals("192029", response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
    }

    @Test
    public void testRefundPayment_200FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-refund", "payment-processing-refund", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/payment_processing_refund_200_failure_response.json");
            }
        };

        PaymentResponse response = paymentProcessingAccess.refundPayment(createRefundRequest());

        assertNotNull(response);
        assertNotNull(response.getStatusCode());
        assertNotNull(response.getResults());
        assertEquals("200", response.getStatusCode());
        assertEquals(1, response.getResults().size());
        assertNotNull(response.getResults().get(0).getGatewayResult());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertEquals("002", response.getResults().get(0).getGatewayResult().getTransactionCode());
        assertEquals("FAILURE", response.getResults().get(0).getGatewayResult().getTransactionStatus());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction());
        assertNotNull(response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
        assertEquals("192029", response.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
    }

    @Test
    public void testRefundPayment_400FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-refund", "payment-processing-refund", (HttpHeader[]) any);
                result = new HttpFailureException(400, Utils.readFileFromClassPath("data/payment_processing_refund_failure_response.json"), "failed",
                        new String[] {"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> paymentProcessingAccess.refundPayment(createRefundRequest()));
    }

    @Test
    public void testRefundPayment_401FailureResponse() throws Exception {
        final String payload = "{\"message\":\"JWT is not valid, Please provide a valid Token\"}";
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-refund", "payment-processing-refund", (HttpHeader[]) any);
                result = new HttpFailureException(401, payload, "Unauthorized");
            }
        };
        assertThrows(AppException.class, () -> paymentProcessingAccess.refundPayment(createRefundRequest()));
    }

    @Test
    public void testRefundPayment_404FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-refund", "payment-processing-refund", (HttpHeader[]) any);
                result = new HttpFailureException(404, Utils.readFileFromClassPath("data/payment_processing_refund_failure_response.json"), "failed",
                        new String[] {"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> paymentProcessingAccess.refundPayment(createRefundRequest()));
    }

    @Test
    public void testRefundPayment_500FailureResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-refund", "payment-processing-refund", (HttpHeader[]) any);
                result = new HttpFailureException(500, Utils.readFileFromClassPath("data/payment_processing_refund_failure_response.json"), "failed",
                        new String[] {"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> paymentProcessingAccess.refundPayment(createRefundRequest()));
    }

    @Test
    public void testRefundPayment_NullResponse() throws Exception {
        new Expectations() {
            {
                registry.getAccessToken(anyString, anyString, anyString, (String[]) any);
            }
            {
                service.post(anyString, (PaymentRequest) any, "payment-processing-refund", "payment-processing-refund", (HttpHeader[]) any);
                result = null;
            }
        };
        assertThrows(AppException.class, () -> paymentProcessingAccess.refundPayment(createRefundRequest()));
    }


    private PaymentRequest createCaptureRequest() {
        final PaymentRequest request = new PaymentRequest();
        request.setClientReferenceNumber("987654321");
        request.setPaymentId("00cab191-0d96-48c3-a8d0-c415f05093a2");

        final List<Amount> amountList = new ArrayList<>();
        final Amount total = new Amount();
        total.setName("total");
        total.setValue(BigDecimal.valueOf(100.00));

        final Amount tax = new Amount();
        tax.setName("tax");
        tax.setValue(BigDecimal.valueOf(10.00));

        amountList.add(total);
        amountList.add(tax);
        request.setAmount(amountList);

        return request;
    }

    private PaymentRequest createVoidRequest() {
        final PaymentRequest request = new PaymentRequest();
        request.setClientReferenceNumber("123456");
        request.setPaymentId("00cab191-0d96-48c3-a8d0-c415f05093a2");

        final List<Amount> amountList = new ArrayList<>();
        final Amount total = new Amount();
        total.setName("total");
        total.setValue(BigDecimal.valueOf(100.00));

        final Amount tax = new Amount();
        tax.setName("tax");
        tax.setValue(BigDecimal.valueOf(10.00));

        amountList.add(total);
        amountList.add(tax);
        request.setAmount(amountList);

        return request;
    }

    private PaymentRequest createRefundRequest() {
        final PaymentRequest request = new PaymentRequest();
        request.setClientReferenceNumber("987654321");

        final List<Amount> amountList = new ArrayList<>();
        final Amount total = new Amount();
        total.setName("total");
        total.setValue(BigDecimal.valueOf(100.00));

        final Amount tax = new Amount();
        tax.setName("tax");
        tax.setValue(BigDecimal.valueOf(10.00));

        amountList.add(total);
        amountList.add(tax);
        request.setAmount(amountList);

        final Payment payment = new Payment();
        final TenderDetails tenderDetails = new TenderDetails();
        tenderDetails.setMgmToken("4238904320fjljoz89fszfdzs09f8sd09fs8zf0sdz");
        tenderDetails.setExpireYear("30");
        tenderDetails.setExpireMonth("10");
        payment.setTenderDetails(tenderDetails);
        request.setPayment(payment);

        return request;
    }
}
