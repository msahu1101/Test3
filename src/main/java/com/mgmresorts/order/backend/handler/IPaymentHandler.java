package com.mgmresorts.order.backend.handler;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.dto.services.CheckoutRequest;

public interface IPaymentHandler extends CommonConfig {

    boolean validatePaymentMethod(final CheckoutRequest request) throws AppException;
}
