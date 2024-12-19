package com.mgmresorts.order.backend.handler.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.notification.Email;
import com.mgmresorts.common.notification.SmtpEmailer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.backend.access.IPaymentProcessingAccess;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.Payment;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.pps.model.PaymentRequest;
import com.mgmresorts.pps.model.PaymentResponse;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@SuppressWarnings("unused")
public class PaymentProcessingHandlerTest {
    @Tested
    private PaymentProcessingHandler paymentProcessingHandler;
    @Injectable
    private IPaymentProcessingAccess paymentProcessingAccess;
    @Injectable
    private SmtpEmailer smtpEmailer;

    private final JSonMapper jsonMapper = new JSonMapper();
    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @BeforeEach
    public void before() {
        ErrorManager.clean();
        new Errors();
    }

    @Test
    public void testCaptureTransaction_SuccessResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.capturePayment((PaymentRequest)any);
                result = jsonMapper.readValue(Utils.readFileFromClassPath("data/payment_processing_capture_success_response.json"), PaymentResponse.class);
            }
        };

        assertDoesNotThrow(() -> paymentProcessingHandler.captureTransaction("123456", "123456", createPaymentAuthFields()));
    }

    @Test
    public void testCaptureTransaction_200FailureResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.capturePayment((PaymentRequest)any);
                result = jsonMapper.readValue(Utils.readFileFromClassPath("data/payment_processing_capture_200_failure_response.json"), PaymentResponse.class);
            }
            {
                smtpEmailer.send((Email)any);
            }
        };

        assertDoesNotThrow(() -> paymentProcessingHandler.captureTransaction("123456", "123456", createPaymentAuthFields()));
    }

    @Test
    public void testCaptureTransaction_PaymentExceptionFailureResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.capturePayment((PaymentRequest)any);
                result = new SourceAppException(Errors.UNABLE_TO_CAPTURE_PAYMENT, "1405", "unexpected error", Utils.readFileFromClassPath("data/payment_processing_capture_failure_response.json"));
            }
            {
                smtpEmailer.send((Email)any);
            }
        };
        
        assertThrows(AppException.class, () -> {
            paymentProcessingHandler.captureTransaction("123456", "123456", createPaymentAuthFields());
        });
    }

    @Test
    public void testCaptureTransaction_UnknownExceptionFailureResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.capturePayment((PaymentRequest)any);
                result = new AppException(Errors.UNEXPECTED_SYSTEM, "500", "unexpected error", "500 unexpected error from pps");
            }
            {
                smtpEmailer.send((Email)any);
            }
        };
        
        assertThrows(AppException.class, () -> {
            paymentProcessingHandler.captureTransaction("123456", "123456", createPaymentAuthFields());
        });
    }

    @Test
    public void testVoidTransaction_SuccessResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.voidPayment((PaymentRequest)any);
                result = jsonMapper.readValue(Utils.readFileFromClassPath("data/payment_processing_void_success_response.json"), PaymentResponse.class);
            }
        };

        assertDoesNotThrow(() -> paymentProcessingHandler.voidTransaction("123456", "123456", createPaymentAuthFields()));
    }

    @Test
    public void testVoidTransaction_200FailureResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.voidPayment((PaymentRequest)any);
                result = jsonMapper.readValue(Utils.readFileFromClassPath("data/payment_processing_void_200_failure_response.json"), PaymentResponse.class);
            }
            {
                smtpEmailer.send((Email)any);
            }
        };

        assertDoesNotThrow(() -> paymentProcessingHandler.voidTransaction("123456", "123456", createPaymentAuthFields()));
    }

    @Test
    public void testVoidTransaction_PaymentExceptionFailureResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.voidPayment((PaymentRequest)any);
                result = new SourceAppException(Errors.UNABLE_TO_CAPTURE_PAYMENT, "1405", "unexpected error", Utils.readFileFromClassPath("data/payment_processing_void_failure_response.json"));
            }
            {
                smtpEmailer.send((Email)any);
            }
        };
        
        assertDoesNotThrow(() -> paymentProcessingHandler.voidTransaction("123456", "123456", createPaymentAuthFields()));
    }

    @Test
    public void testVoidTransaction_UnknownExceptionFailureResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.voidPayment((PaymentRequest)any);
                result = new AppException(Errors.UNEXPECTED_SYSTEM, "500", "unexpected error", "500 unexpected error from pps");
            }
            {
                smtpEmailer.send((Email)any);
            }
        };
        
        assertDoesNotThrow(() -> paymentProcessingHandler.voidTransaction("123456", "123456", createPaymentAuthFields()));
        
    }

    @Test
    public void testRefundTransaction_SuccessResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.refundPayment((PaymentRequest)any);
                result = jsonMapper.readValue(Utils.readFileFromClassPath("data/payment_processing_refund_success_response.json"), PaymentResponse.class);
            }
        };

        PaymentAuthFields paymentAuthFields =  paymentProcessingHandler.refundTransaction("123456", "123456", "987654321", 100.00, createBillingList(), "123456", "123456");

        assertEquals(true, paymentAuthFields.isSuccess());
        assertEquals("OK253W", paymentAuthFields.getAuthorizationCode());
    }

    @Test
    public void testRefundTransaction_200FailureResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.refundPayment((PaymentRequest)any);
                result = jsonMapper.readValue(Utils.readFileFromClassPath("data/payment_processing_refund_200_failure_response.json"), PaymentResponse.class);
            }
            {
                smtpEmailer.send((Email)any);
            }
        };

        PaymentAuthFields paymentAuthFields = paymentProcessingHandler.refundTransaction("123456", "123456", "987654321", 100.00, createBillingList(), "123456", "123456");

        assertEquals(false, paymentAuthFields.isSuccess());
    }

    @Test
    public void testRefundTransaction_PaymentExceptionFailureResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.refundPayment((PaymentRequest)any);
                result = new SourceAppException(Errors.UNABLE_TO_CAPTURE_PAYMENT, "1405", "unexpected error", Utils.readFileFromClassPath("data/payment_processing_refund_failure_response.json"));
            }
            {
                smtpEmailer.send((Email)any);
            }
        };
        
        assertThrows(AppException.class, () -> {
            paymentProcessingHandler.refundTransaction("123456", "123456", "987654321", 100.00, createBillingList(), "123456", "123456");
        });
    }

    @Test
    public void testRefundTransaction_UnknownExceptionFailureResponse() throws Exception {
        new Expectations() {
            {
                paymentProcessingAccess.refundPayment((PaymentRequest)any);
                result = new AppException(Errors.UNEXPECTED_SYSTEM, "500", "unexpected error", "500 unexpected error from pps");
            }
            {
                smtpEmailer.send((Email)any);
            }
        };
        
        assertThrows(AppException.class, () -> {
            paymentProcessingHandler.refundTransaction("123456", "123456", "987654321", 100.00, createBillingList(), "123456", "123456");
        });
    }

    private List<Billing> createBillingList() {
        final List<Billing> billings = new ArrayList<>();
        final Billing billing = new Billing();
        final Payment payment = new Payment();
        payment.setExpiry("07/28");
        payment.setCcToken("4238904320fjljoz89fszfdzs09f8sd09fs8zf0sdz");
        return billings;
    }

    private PaymentAuthFields createPaymentAuthFields() {
        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setConfirmationNumber("1234567");
        paymentAuthFields.setPaymentId("192029");
        paymentAuthFields.setAuthorizationCode("OAK23");
        paymentAuthFields.setAmount(500.00);
        return paymentAuthFields;
    }
}
