package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.pps.model.PaymentRequest;
import com.mgmresorts.pps.model.PaymentResponse;

public interface IPaymentProcessingAccess extends CommonConfig {
    PaymentResponse capturePayment(final PaymentRequest captureRequest) throws AppException;

    PaymentResponse voidPayment(final PaymentRequest voidRequest) throws AppException;

    PaymentResponse refundPayment(final PaymentRequest voidRequest) throws AppException;
}
