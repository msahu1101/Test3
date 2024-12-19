package com.mgmresorts.order.backend.access.impl;

import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.exec.Retry;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IPaymentSessionAccess;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.psm.model.EnableSessionRequest;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.psm.model.RetrieveSessionResponse;
import com.mgmresorts.psm.model.SessionError;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;

public class PaymentSessionAccess implements IPaymentSessionAccess {

    private final Logger logger = Logger.get(PaymentSessionAccess.class);
    private final JSonMapper mapper = new JSonMapper();
    @Inject
    @Named("simulation.enabled")
    private IHttpService service;
    @Inject
    private OAuthTokenRegistry registry;

    @Override
    public EnableSessionResponse managePaymentSession(EnableSessionRequest enableSessionRequest, CallType callType) throws AppException {
        try {

            final String serviceToken = CommonConfig.getServiceToken(registry);

            final String callName = getCallName(callType);
            final String url = PSM_API_ENDPOINT + PSM_PAYMENT_SESSION_CREATE_UPDATE;
            final String response = service.put(url,
                    enableSessionRequest,
                    callName,
                    callName,
                    CommonConfig.getPaymentSessionHeaders(serviceToken));
            if (Utils.isEmpty(response)) {
                if (callType == CallType.CREATE) {
                    throw new AppException(ApplicationError.UNABLE_TO_CREATE_PAYMENT_SESSION, "Could not create payment session. No response from backend.");
                } else {
                    throw new AppException(ApplicationError.UNABLE_TO_UPDATE_PAYMENT_SESSION, "Could not update payment session. No response from backend.");
                }
            }
            return mapper.readValue(response, EnableSessionResponse.class);
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                final SessionError sessionError = mapper.readValue(e.getPayload(), SessionError.class);
                final String code = getErrorCode(sessionError, e);
                final String message = getErrorMessage(sessionError, e);
                if (callType == CallType.CREATE) {
                    logger.error("[Error from PSM] PUT create payment session call failed. :  {}", errorPayload);
                    throw new SourceAppException(ApplicationError.UNABLE_TO_CREATE_PAYMENT_SESSION, code, message, errorPayload);
                } else {
                    logger.error("[Error from PSM] PUT update payment session call failed. :  {}", errorPayload);
                    throw new SourceAppException(ApplicationError.UNABLE_TO_UPDATE_PAYMENT_SESSION, code, message, errorPayload);
                }
            } else {
                logger.error("[Error from PSM] Something unexpected happened in PUT " + callType.value() + " payment session call.  :", e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not " + callType.value() + " payment session. Unexpected error occurred." + e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from PSM] PUT " + callType.value() + "payment session call failed with app exception. : ", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from PSM] PUT " + callType.value() + " payment session call failed with unknown exception. : ", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }

    private static String getCallName(CallType callType) {
        if (callType == CallType.CREATE) {
            return "payment-session-create";
        } else {
            return "payment-session-update";
        }
    }

    public RetrieveSessionResponse getPaymentSession(String sessionId) throws AppException {
        try {
            final String serviceToken = CommonConfig.getServiceToken(registry);
            final String callName = "payment-session-get";

            
            final RetrieveSessionResponse response = Retry.of(3, RetrieveSessionResponse.class, HttpFailureException.class).exeute(() -> {
                return service.get(PSM_API_ENDPOINT + PSM_PAYMENT_SESSION_GET + PATH_SEPARATOR + sessionId, RetrieveSessionResponse.class,
                        callName, callName, Arrays.asList(CommonConfig.getPaymentSessionHeaders(serviceToken)));
            }, condition -> {
                return condition.getHttpCode() == 500 || condition.getHttpCode() == 502 || condition.getHttpCode() == 503 || condition.getHttpCode() == 504;
            });
            
            if (response == null) {
                throw new AppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "Could not get payment session. No response from backend.");
            }
            return response;
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.error("[Error from PSM] Get Payment Session call failed. : {}", errorPayload);
                final SessionError sessionError = mapper.readValue(e.getPayload(), SessionError.class);
                final String code = getErrorCode(sessionError, e);
                final String message = getErrorMessage(sessionError, e);
                throw new SourceAppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, code, message, errorPayload);
            } else {
                logger.error("[Error from PSM] Something unexpected happened in get payment session call. :", e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not get payment session. Unexpected error occurred." + e.getMessage());
            }
        }
    }

    private String getErrorCode(final SessionError sessionError, final HttpFailureException exception) {
        if (sessionError != null) {
            return sessionError.getErrorCode();
        } else {
            return exception != null ? String.valueOf(exception.getHttpCode()) : null;
        }
    }

    private String getErrorMessage(final SessionError sessionError, final HttpFailureException exception) {
        if (sessionError != null) {
            return sessionError.getErrorMessage();
        } else {
            return exception != null ? exception.getPayload() : null;
        }
    }
}
