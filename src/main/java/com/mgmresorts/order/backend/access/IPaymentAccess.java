package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.payments.model.PaymentsOrchestrationWorkFlowResquest;

public interface IPaymentAccess extends CommonConfig {
    String validatePaymentMethod(PaymentsOrchestrationWorkFlowResquest request) throws AppException, HttpFailureException;
}
