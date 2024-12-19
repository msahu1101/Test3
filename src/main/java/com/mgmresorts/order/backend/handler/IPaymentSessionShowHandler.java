package com.mgmresorts.order.backend.handler;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.sbs.model.ShowReservationResponse;

public interface IPaymentSessionShowHandler extends CommonConfig {
    EnableSessionResponse managePaymentSessionForShowReservation(ShowReservationResponse showReservationResponse,
                                                                 final String sessionId, final CallType callType) throws AppException;
}
