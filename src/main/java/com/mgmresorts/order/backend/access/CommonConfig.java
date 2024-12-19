package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.function.HeaderBuilder;
import com.mgmresorts.common.http.HttpService;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.security.Jwts.Claim;
import com.mgmresorts.common.security.Jwts.Jwt;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.errors.Errors;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface CommonConfig {

    final Logger logger = Logger.get(CommonConfig.class);
    public String PATH_SEPARATOR = "/";
    
    /*Scopes.*/
    public String ALL_API_SCOPES = Runtime.get().getConfiguration("all.api.scopes");
    public String SERVICE_ROLE_SCOPE = Runtime.get().getConfiguration("service.role.scope");
    
    /*Identity Authorization Service common endpoints*/
    String IDENTITY_AUTH_ENDPOINT = Runtime.get().getConfiguration("identity.auth.apigee.endpoint");
    String IDENTITY_AUTH_SVC_TOKEN = Runtime.get().getConfiguration("identity.auth.svc.token");
    String IDENTITY_AUTH_CLIENT_ID = Runtime.get().getConfiguration("identity.auth.client.id");
    String IDENTITY_AUTH_CLIENT_SECRET = Runtime.get().getConfiguration("identity.auth.client.secret@secure");
    
    /*Shopping Cart endpoints.*/
    public String SHOPPING_CART_API_ENDPOINT = Runtime.get().getConfiguration("shopping.cart.apigee.endpoint");
    public String SHOPPING_CART_READ = Runtime.get().getConfiguration("shopping.cart.read");
    public String SHOPPING_CART_UPDATE = Runtime.get().getConfiguration("shopping.cart.update");
    public String SHOPPING_CART_PAYMENT_SESSION = Runtime.get().getConfiguration("shopping.cart.payment.session");
    
    /*Itinerary Service endpoints.*/
    public String ITINERARY_API_ENDPOINT = Runtime.get().getConfiguration("itinerary.apigee.endpoint");
    public String ITINERARY_CREATE = Runtime.get().getConfiguration("itinerary.create");
    
    /*Profile Core Service endpoints.*/
    public String PROFILE_CORE_API_ENDPOINT = Runtime.get().getConfiguration("profile.core.apigee.endpoint");
    public String PROFILE_CORE_CREATE = Runtime.get().getConfiguration("profile.core.create");
    public String PROFILE_CORE_GET = Runtime.get().getConfiguration("profile.core.get");
    
    /*RBS endpoints.*/
    public String RBS_API_ENDPOINT = Runtime.get().getConfiguration("rbs.apigee.endpoint");
    public String RBS_RESERVATION_CREATE = Runtime.get().getConfiguration("rbs.reservation.create");
    public String RBS_RESERVATION_GET = Runtime.get().getConfiguration("rbs.reservation.get");
    public String RBS_RESERVATION_PREVIEW = Runtime.get().getConfiguration("rbs.reservation.preview");
    public String RBS_RESERVATION_COMMIT = Runtime.get().getConfiguration("rbs.reservation.commit");
    public String RBS_RESERVATION_REFUND_COMMIT = Runtime.get().getConfiguration("rbs.reservation.refund.commit");
    public String RBS_RESERVATION_V3_CANCEL = Runtime.get().getConfiguration("rbs.reservation.v3.cancel");
    public String RBS_RESERVATION_RELEASE = Runtime.get().getConfiguration("rbs.reservation.release");
    
    /*SBS endpoints*/
    public String SBS_API_ENDPOINT = Runtime.get().getConfiguration("sbs.apigee.endpoint");
    public String SBS_RESERVATION_CREATE = Runtime.get().getConfiguration("sbs.reservation.create");
    public String SBS_RESERVATION_GET = Runtime.get().getConfiguration("sbs.reservation.get");
    public String SBS_HEALTH = Runtime.get().getConfiguration("sbs.health");
    
    /*DBS endpoints*/
    public String DBS_API_ENDPOINT = Runtime.get().getConfiguration("dbs.apigee.endpoint");
    public String DBS_RESERVATION_CREATE = Runtime.get().getConfiguration("dbs.reservation.create");
    public String DBS_RESERVATION_SEARCH = Runtime.get().getConfiguration("dbs.reservation.search");
    
    /*Legacy PPS endpoints*/
    public String PPS_LEGACY_API_ENDPOINT = Runtime.get().getConfiguration("pps.legacy.apigee.endpoint");
    public String PPS_LEGACY_PAYMENT_VALIDATION = Runtime.get().getConfiguration("pps.legacy.payment.validation");
    
    /*PD PPS endpoints*/
    public String PPS_API_ENDPOINT = Runtime.get().getConfiguration("pps.apigee.endpoint");
    public String PPS_PAYMENT_CAPTURE = Runtime.get().getConfiguration("pps.payment.capture");
    public String PPS_PAYMENT_VOID = Runtime.get().getConfiguration("pps.payment.void");
    public String PPS_PAYMENT_REFUND = Runtime.get().getConfiguration("pps.payment.refund");
    
    /*PD PSM endpoints*/
    public String PSM_API_ENDPOINT = Runtime.get().getConfiguration("psm.apigee.endpoint");
    public String PSM_PAYMENT_SESSION_GET = Runtime.get().getConfiguration("psm.payment.session.get");
    public String PSM_PAYMENT_SESSION_CREATE_UPDATE = Runtime.get().getConfiguration("psm.payment.session.create-update");
    public String PSM_CLIENT_ID = Runtime.get().getConfiguration("psm.client.id");

    /*Content API endpoints*/
    public String CONTENT_API_ENDPOINT = Runtime.get().getConfiguration("content.api.url");
    public String CONTENT_API_GET_PACKAGE_CONFIG = Runtime.get().getConfiguration("content.api.get.package.config");
    public String CONTENT_API_GET_SHOW_EVENT = Runtime.get().getConfiguration("content.api.get.show.event");

    /*PIM API endpoints*/
    public String PIM_API_ENDPOINT = Runtime.get().getConfiguration("pim.api.url");
    public String PIM_API_GET_PACKAGE_CONFIG = Runtime.get().getConfiguration("pim.api.get.package.config");
    
    /*RTC API endpoints*/
    public String RTC_API_ENDPOINT = Runtime.get().getConfiguration("rtc.api.url");
    public String RTC_CHECKOUT_EMAIL_EVENT = Runtime.get().getConfiguration("rtc.checkout.email.event");

    /*Package Booking Failure DL*/
    public String SBS_SEND_EMAIL_TO_LIST = Runtime.get().getConfiguration("sbs.reservation.send.email.to");
    public String SBS_SEND_EMAIL_CC_LIST = Runtime.get().getConfiguration("sbs.reservation.send.email.cc");
    public String SBS_SEND_EMAIL_BCC_LIST = Runtime.get().getConfiguration("sbs.reservation.send.email.bcc");

    /*PPS Capture/Void/Refund Failure DL*/
    public String PPS_SEND_EMAIL_TO_LIST = Runtime.get().getConfiguration("pps.payment.failure.send.email.to");
    public String PPS_SEND_EMAIL_CC_LIST = Runtime.get().getConfiguration("pps.payment.failure.send.email.cc");
    public String PPS_SEND_EMAIL_BCC_LIST = Runtime.get().getConfiguration("pps.payment.failure.send.email.bcc");
    
    /*Itinerary Create Failure DL*/
    public String IDENTITY_SEND_EMAIL_TO_LIST = Runtime.get().getConfiguration("identity.merge.account.failure.send.email.to");
    public String IDENTITY_SEND_EMAIL_CC_LIST = Runtime.get().getConfiguration("identity.merge.account.failure.send.email.cc");
    public String IDENTITY_SEND_EMAIL_BCC_LIST = Runtime.get().getConfiguration("identity.merge.account.failure.send.email.bcc");

    /**
     * Returns the guest token that is passed to the service from the client.
     */
    static String getGuestToken() throws AppException {
        final Jwt jwt = ThreadContext.getContext().get().getJwt();
        return jwt != null ? jwt.getToken() : null;
    }

    /**
     * Returns the service token generated from the API required scopes.
     */
    static String getServiceToken(OAuthTokenRegistry registry) throws AppException {
        String token;
        try {
            token = registry.getAccessToken(IDENTITY_AUTH_ENDPOINT + IDENTITY_AUTH_SVC_TOKEN, IDENTITY_AUTH_CLIENT_ID,
                    IDENTITY_AUTH_CLIENT_SECRET, ALL_API_SCOPES, SERVICE_ROLE_SCOPE);
        } catch (Exception e) {
            logger.error("There was an issue retrieving the service token.");
            throw new AppException(Errors.IDENTITY_SERVICE_FAILURE, e.getMessage(), e);
        }
        return token;
    }

    static boolean getTokenPerpetualFlag() throws AppException {
        final Jwt jwt = ThreadContext.getContext().get().getJwt();
        return Boolean.parseBoolean(jwt != null ? jwt.getClaim(Claim.PO_ELIGIBILITY) : null);
    }

    static String getTokenGseId() throws AppException {
        final Jwt jwt = ThreadContext.getContext().get().getJwt();
        return jwt != null ? jwt.getClaim(Claim.GSE_ID) : null;
    }

    static String getTokenMlifeNumber() throws AppException {
        final Jwt jwt = ThreadContext.getContext().get().getJwt();
        return jwt != null ? jwt.getClaim(Claim.MLIFE_NUMBER) : null;
    }
    
    static String getTokenMgmId() throws AppException {
        final Jwt jwt = ThreadContext.getContext().get().getJwt();
        return jwt != null ? jwt.getClaim(Claim.MGM_ID) : null;
    }

    static String getFirstName() throws AppException {
        final Jwt jwt = ThreadContext.getContext().get().getJwt();
        return jwt != null ? jwt.getClaim(Claim.FIRST_NAME) : null;
    }

    static String getLastName() throws AppException {
        final Jwt jwt = ThreadContext.getContext().get().getJwt();
        return jwt != null ? jwt.getClaim(Claim.LAST_NAME) : null;
    }

    static String getDateOfBirth() {
        final Jwt jwt = ThreadContext.getContext().get().getJwt();
        return jwt != null ? jwt.getClaim(Claim.BIRTH_DATE) : null;
    }

    static String getTier() {
        final Jwt jwt = ThreadContext.getContext().get().getJwt();
        return jwt != null ? jwt.getClaim(Claim.TIER) : null;
    }

    static String getDateOfEnrollment() {
        final Jwt jwt = ThreadContext.getContext().get().getJwt();
        return jwt != null ? jwt.getClaim(Claim.MLIFE_ENROLLMENT_DATE) : null;
    }
    
    static HttpService.HttpHeaders.HttpHeader[] getStandardHeaders(String token) {
        final List<HttpService.HttpHeaders.HttpHeader> headers = generateCommonHeaders(token);
        return headers.toArray(new HttpService.HttpHeaders.HttpHeader[] {});
    }

    static HttpService.HttpHeaders.HttpHeader[] getRoomHeaders(String token) {
        final List<HttpService.HttpHeaders.HttpHeader> headers = generateCommonHeaders(token);
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_USER_AGENT,
                getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_USER_AGENT)));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_X_FORWARDED_FOR,
                getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_X_FORWARDED_FOR)));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_FORTER_FRAUD_AGENT_TOKEN,
                getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_FORTER_FRAUD_AGENT_TOKEN)));

        return headers.toArray(new HttpService.HttpHeaders.HttpHeader[]{});
    }

    static HttpService.HttpHeaders.HttpHeader[] getShowHeaders(String token) {
        final List<HttpService.HttpHeaders.HttpHeader> headers = generateCommonHeaders(token);
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_USER_AGENT,
                getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_USER_AGENT)));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_X_FORWARDED_FOR,
                getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_X_FORWARDED_FOR)));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_FORTER_FRAUD_AGENT_TOKEN,
                getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_FORTER_FRAUD_AGENT_TOKEN)));
        return headers.toArray(new HttpService.HttpHeaders.HttpHeader[]{});
    }

    static HttpService.HttpHeaders.HttpHeader[] getPaymentSessionHeaders(String token) {
        final List<HttpService.HttpHeaders.HttpHeader> headers = generateCommonHeaders(token);
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_JOURNEY_ID, getJourneyId()));
        return headers.toArray(new HttpService.HttpHeaders.HttpHeader[] {});
    }

    static HttpService.HttpHeaders.HttpHeader[] getPaymentProcessingHeaders(String token) {
        final List<HttpService.HttpHeaders.HttpHeader> headers = generateCommonHeaders(token);
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_JOURNEY_ID, getJourneyId()));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_USER_AGENT, getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_USER_AGENT)));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_CLIENT_ID, Runtime.get().getConfiguration("pps.client.id")));
        return headers.toArray(new HttpService.HttpHeaders.HttpHeader[] {});
    }

    static HttpService.HttpHeaders.HttpHeader[] getDiningHeaders(String token) {
        final List<HttpService.HttpHeaders.HttpHeader> headers = generateCommonHeaders(token);
        return headers.toArray(new HttpService.HttpHeaders.HttpHeader[]{});
    }
    
    static HttpService.HttpHeaders.HttpHeader[] getContentHeaders() {
        final List<HttpService.HttpHeaders.HttpHeader> headers = generateCommonHeaders(null);
        return headers.toArray(new HttpService.HttpHeaders.HttpHeader[] {});
    }
    
    static HttpService.HttpHeaders.HttpHeader[] getLegacyPaymentServiceHeaders(String token) {
        final List<HttpService.HttpHeaders.HttpHeader> headers = generateCommonHeaders(token);
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_USER_AGENT,
                getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_USER_AGENT)));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_X_FORWARDED_FOR,
                getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_X_FORWARDED_FOR)));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_FORTER_FRAUD_AGENT_TOKEN,
                getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_FORTER_FRAUD_AGENT_TOKEN)));

        return headers.toArray(new HttpService.HttpHeaders.HttpHeader[] {});
    }
    
    private static List<HttpService.HttpHeaders.HttpHeader> generateCommonHeaders(final String token) {
        final List<HttpService.HttpHeaders.HttpHeader> headers = new ArrayList<>();
        headers.add(HttpService.HttpHeaders.APPLICATION_JSON);
        if (StringUtils.isNotBlank(token)) {
            headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_AUTHORIZATION, HeaderBuilder.BEARER + token));
        }
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_SOURCE, ThreadContext.getContext().get().getSource()));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_CHANNEL, ThreadContext.getContext().get().getChannel()));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_TRANSACTION_ID, getTransactionId()));
        headers.add(new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_CORRELATION_ID, ThreadContext.getContext().get().getCorrelationId()));
        return headers;
    }
    
    static String getTransactionId() {
        String transactionId = ThreadContext.getContext().get().getTransactionId();
        if (StringUtils.isBlank(transactionId) && StringUtils.isNotBlank(ThreadContext.getContext().get().getCorrelationId())) {
            final String correlationId = ThreadContext.getContext().get().getCorrelationId();
            if (correlationId.contains("|")) {
                final String[] parts = correlationId.split("\\|");
                transactionId = parts[1];
            } else {
                transactionId = correlationId;
            }
        }
        return transactionId;
    }
    
    static String getJourneyId() {
        String journeyId = getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_JOURNEY_ID);
        if (StringUtils.isBlank(journeyId) && StringUtils.isNotBlank(ThreadContext.getContext().get().getCorrelationId())) {
            final String correlationId = ThreadContext.getContext().get().getCorrelationId();
            if (correlationId.contains("|")) {
                final String[] parts = correlationId.split("\\|");
                journeyId = parts[0];
            } else {
                journeyId = correlationId;
            }
        }
        return journeyId;
    }
    
    static String getHeaderValue(String headerKey) {
        final Map<String, String> requestContextHeaders = ThreadContext.getContext().get().getHeaders();
        if (!Utils.isEmpty(requestContextHeaders)) {
            return requestContextHeaders.get(headerKey);
        }
        return null;
    }
}
