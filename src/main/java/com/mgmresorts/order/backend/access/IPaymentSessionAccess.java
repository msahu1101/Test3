package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.psm.model.EnableSessionRequest;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.psm.model.RetrieveSessionResponse;

public interface IPaymentSessionAccess extends CommonConfig {
    EnableSessionResponse managePaymentSession(EnableSessionRequest enableSessionRequest, CallType callType) throws AppException;

    RetrieveSessionResponse getPaymentSession(String sessionId) throws AppException;

}
