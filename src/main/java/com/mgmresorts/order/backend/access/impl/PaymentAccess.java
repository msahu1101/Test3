package com.mgmresorts.order.backend.access.impl;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IPaymentAccess;
import com.mgmresorts.payments.model.PaymentsOrchestrationWorkFlowResquest;

public class PaymentAccess implements IPaymentAccess {
    /* Payment API specific access class */

    @Inject
    @Named("simulation.enabled")
    private IHttpService service;
    @Inject
    private OAuthTokenRegistry registry;

    @Override
    public String validatePaymentMethod(PaymentsOrchestrationWorkFlowResquest request) throws AppException, HttpFailureException {
        final String serviceToken = CommonConfig.getServiceToken(registry);
        final String callName = "payment-method-validate";
        return service.post(PPS_LEGACY_API_ENDPOINT + PPS_LEGACY_PAYMENT_VALIDATION, request, callName, callName, CommonConfig.getLegacyPaymentServiceHeaders(serviceToken));
    }
}
