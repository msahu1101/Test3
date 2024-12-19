package com.mgmresorts.order.backend.access.impl;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.config.Runtime;
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
import com.mgmresorts.order.backend.access.IPaymentProcessingAccess;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.pps.model.PaymentExceptionResponse;
import com.mgmresorts.pps.model.PaymentRequest;
import com.mgmresorts.pps.model.PaymentResponse;

public class PaymentProcessingAccess implements IPaymentProcessingAccess {

    private final Logger logger = Logger.get(PaymentProcessingAccess.class);
    final JSonMapper mapper = new JSonMapper();

    @Inject
    @Named("simulation.enabled")
    private IHttpService service;

    @Inject
    private OAuthTokenRegistry registry;

    @Override
    public PaymentResponse capturePayment(final PaymentRequest captureRequest) throws AppException {
        return processPayment(captureRequest, "Capture", PPS_API_ENDPOINT + PPS_PAYMENT_CAPTURE, Errors.UNABLE_TO_CAPTURE_PAYMENT);
    }

    @Override
    public PaymentResponse voidPayment(final PaymentRequest voidRequest) throws AppException {
        return processPayment(voidRequest, "Void", PPS_API_ENDPOINT + PPS_PAYMENT_VOID, Errors.UNABLE_TO_VOID_PAYMENT);
    }

    @Override
    public PaymentResponse refundPayment(PaymentRequest refundRequest) throws AppException {
        return processPayment(refundRequest, "Refund", PPS_API_ENDPOINT + PPS_PAYMENT_REFUND, Errors.UNABLE_TO_REFUND_PAYMENT);
    }

    private PaymentResponse processPayment(final PaymentRequest request, final String processName, final String endpoint, final int applicationError) throws AppException {
        try {
            final String serviceToken = CommonConfig.getServiceToken(registry);
            final String callName = "payment-processing-" + processName.toLowerCase();
            final String response =
                    Retry.of(Runtime.get().getInt("pps.payment." + processName.toLowerCase() + ".retry.count", 3), String.class, HttpFailureException.class)
                            .exeute(() -> {
                                return service.post(endpoint, request, callName, callName, CommonConfig.getPaymentProcessingHeaders(serviceToken));
                            }, condition -> {
                                return condition.getHttpCode() == 502 || condition.getHttpCode() == 503 || condition.getHttpCode() == 403;
                            });
            if (Utils.isEmpty(response)) {
                throw new AppException(applicationError, "Could not " + processName.toLowerCase() + " payment, no response from PPS backend.");
            }
            return mapper.readValue(response, PaymentResponse.class);
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.error("[Error from PPS] {} payment call failed: {}.", processName, errorPayload);
                final PaymentExceptionResponse errorResponse = mapper.readValue(e.getPayload(), PaymentExceptionResponse.class);
                final String code = getErrorCode(errorResponse, e);
                final String message = getErrorMessage(errorResponse, e);
                throw new SourceAppException(applicationError, code, message, errorPayload);
            } else {
                logger.error("[Error from PPS] Something unexpected happened in {} payment call: {}", processName.toLowerCase(), e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not " + processName.toLowerCase() + " payment. Unexpected error occurred: {}" + e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from PPS] {} payment call failed with app exception: {}", processName, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from PPS] {} payment call failed with unknown exception: {}", processName, e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }

    private String getErrorCode(final PaymentExceptionResponse errorResponse, final HttpFailureException exception) {
        final String sseCode;
        if (errorResponse != null && errorResponse.getErrorCode() != null) {
            sseCode = errorResponse.getErrorCode();
        } else {
            sseCode = exception != null ? String.valueOf(exception.getHttpCode()) : null;
        }
        return sseCode;
    }

    private String getErrorMessage(final PaymentExceptionResponse errorResponse, final HttpFailureException exception) {
        final String sseMessage;
        if (errorResponse != null && errorResponse.getErrorMessage() != null) {
            sseMessage = errorResponse.getErrorMessage();
        } else {
            sseMessage = exception != null ? exception.getPayload() : null;
        }
        return sseMessage;
    }
}
