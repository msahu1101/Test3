package com.mgmresorts.order.backend.access.impl;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IRTCAccess;
import com.mgmresorts.rtc.RtcReservationEvent;

public class RTCAccess implements IRTCAccess {
    /* RTC API specific access class */

    @Inject
    @Named("simulation.enabled")
    private IHttpService service;
    @Inject
    private OAuthTokenRegistry registry;

    @Override
    public String sendOrderCheckoutEmailEvent(final RtcReservationEvent rtcReservationEvent) throws AppException, HttpFailureException {
        final String serviceToken = CommonConfig.getServiceToken(registry);
        final String callName = "rtc-checkout-email-event";
        return service.post(RTC_API_ENDPOINT + RTC_CHECKOUT_EMAIL_EVENT, rtcReservationEvent, callName, callName, CommonConfig.getRoomHeaders(serviceToken));
    }
}
