package com.mgmresorts.order.backend.handler;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.CommonConfig;

public interface IPaymentSessionCommonHandler extends CommonConfig {
    PaymentSessionBaseFields getPaymentAuthResults(String sessionId) throws AppException;
}
