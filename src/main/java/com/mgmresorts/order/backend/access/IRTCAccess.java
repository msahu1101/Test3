package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.rtc.RtcReservationEvent;

public interface IRTCAccess extends CommonConfig {    
    String sendOrderCheckoutEmailEvent(final RtcReservationEvent rtcReservationEvent) throws AppException, HttpFailureException;
}
