package com.mgmresorts.order.backend.access.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.http.IHttpService.HttpHeaders.HttpHeader;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.rtc.RtcReservationEvent;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;

public class RTCAccessTest {

    @Tested
    private RTCAccess rtcAccess;

    @Injectable
    private IHttpService service;
    @Injectable
    private OAuthTokenRegistry registry;

    private final JSonMapper jsonMapper = new JSonMapper();
    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @BeforeEach
    public void before() {
        assertNotNull(service);
        ErrorManager.clean();
        new Errors();
    }

    @Test
    public void testSendRTCCheckoutEmailEvent_SuccessResponse() throws Exception {
        String rtcCheckoutEmailEvent = Utils.readFileFromClassPath("data/rtc_checkout_email_event_request.json");
        new Expectations() {
            {
                service.post(anyString,  any, "rtc-checkout-email-event", "rtc-checkout-email-event", (HttpHeader[]) any);
                String rtcResponse = "{\r\n"
                        + "    \"status\": \"SUCCESS\",\r\n"
                        + "    \"message\": \"Email Notification sent successfully.\"\r\n"
                        + "}";
                result =  rtcResponse;
            }
        };
        String response = rtcAccess.sendOrderCheckoutEmailEvent(jsonMapper.readValue(rtcCheckoutEmailEvent, RtcReservationEvent.class));
        assertNotNull(response);
        assertTrue(response.contains("SUCCESS"));
    }

    @Test
    public void testSendRTCCheckoutEmailEvent_Returns500ErrorResponse() throws Exception {
        String rtcCheckoutEmailEvent = Utils.readFileFromClassPath("data/rtc_checkout_email_event_request.json");
        new Expectations() {
            {
                service.post(anyString,  any, "rtc-checkout-email-event", "rtc-checkout-email-event", (HttpHeader[]) any);
                result = new HttpFailureException(500, "{\r\n"
                        + "    \"message\": \"Internal server error\"\r\n"
                        + "}", "failed", new String[] { "header" });
            }
        };
        assertThrows(HttpFailureException.class, () -> rtcAccess.sendOrderCheckoutEmailEvent(jsonMapper.readValue(rtcCheckoutEmailEvent, RtcReservationEvent.class)));
    }

    @Test
    public void testSendRTCCheckoutEmailEvent_Returns400ErrorResponse() throws Exception {
        String rtcCheckoutEmailEvent = Utils.readFileFromClassPath("data/rtc_checkout_email_event_request.json");
        new Expectations() {
            {
                service.post(anyString,  any, "rtc-checkout-email-event", "rtc-checkout-email-event", (HttpHeader[]) any);
                result = new HttpFailureException(400, "{\r\n"
                        + "    \"status\": \"ERROR\",\r\n"
                        + "    \"errors\": [\r\n"
                        + "        \"reservationEvent: must not be null\"\r\n"
                        + "    ]\r\n"
                        + "}", "header");
            }
        };
        assertThrows(HttpFailureException.class, () -> rtcAccess.sendOrderCheckoutEmailEvent(jsonMapper.readValue(rtcCheckoutEmailEvent, RtcReservationEvent.class)));
    }

}
