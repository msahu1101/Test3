package com.mgmresorts.order.backend.handler;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.dto.Billing;

import java.util.List;

public interface IPaymentProcessingHandler extends CommonConfig {

     void captureTransaction(final String orderId, final String orderReferenceNumber, final PaymentAuthFields paymentAuthFields) throws AppException;

     void voidTransaction(final String orderId, final String orderReferenceNumber, final PaymentAuthFields paymentAuthFields) throws AppException;

     PaymentAuthFields refundTransaction(final String orderId, final String orderReferenceNumber, final String confirmationNumber, final double amount,
                            final List<Billing> billings, final String sessionId, final String itemId) throws AppException;
}
