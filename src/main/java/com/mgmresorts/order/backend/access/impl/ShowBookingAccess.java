package com.mgmresorts.order.backend.access.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

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
import com.mgmresorts.order.backend.access.IShowBookingAccess;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.sbs.model.ErrorResponse;
import com.mgmresorts.sbs.model.ShowReservationRequest;
import com.mgmresorts.sbs.model.ShowReservationResponse;



public class ShowBookingAccess implements IShowBookingAccess {
    /* SBS API specific access class */

    private final Logger logger = Logger.get(ShowBookingAccess.class);
    private final JSonMapper mapper = new JSonMapper();
    @Inject
    @Named("simulation.enabled")
    private IHttpService service;
    @Inject
    private OAuthTokenRegistry registry;
    
    @Override
    public String createShowReservation(ShowReservationRequest request) throws AppException, HttpFailureException {
        final String serviceToken = CommonConfig.getServiceToken(registry);
        final String callName = "show-reservation-create";
        return service.post(SBS_API_ENDPOINT + SBS_RESERVATION_CREATE, request, callName, callName, CommonConfig.getShowHeaders(serviceToken));
    }

    public ShowReservationResponse getShowReservation(String confirmationNumber, String firstName, String lastName) throws AppException {
        final String guestToken = CommonConfig.getGuestToken();
        final String callName = "show-reservation-get";
        final List<String[]> paramList = new ArrayList<>();
        paramList.add(new String[]{"confirmationNumber", confirmationNumber});
        paramList.add(new String[]{"firstName", firstName});
        paramList.add(new String[]{"lastName", lastName});
        try {
            final ShowReservationResponse response = service.get(SBS_API_ENDPOINT + SBS_RESERVATION_GET, ShowReservationResponse.class, callName,callName,
                    Arrays.asList(CommonConfig.getShowHeaders(guestToken)), paramList.toArray(new String[][]{}));
            
            if (response == null) {
                throw new AppException(ApplicationError.UNABLE_TO_GET_SHOW_RESERVATION, "Could not get show reservation response.");
            }
            
            return response;
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.error("[Error from SBS] Get Show Reservation call failed. :  {}", errorPayload);
                final ErrorResponse errorResponse = mapper.readValue(e.getPayload(), ErrorResponse.class);
                final String code = getErrorCode(errorResponse, errorPayload, e);
                final String message = getErrorMessage(errorResponse, errorPayload, e);
                throw new SourceAppException(Errors.UNABLE_TO_GET_SHOW_RESERVATION, code, message, errorPayload);
            } else {
                logger.error("[Error from SBS] Something unexpected happened in get show reservation call.  Message: {}", e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not get show reservation. Unexpected error occurred." + e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from SBS] Get Show Reservation call failed with app exception.  Message: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from SBS] Get Show Reservation call failed with unknown exception.  Message: {}", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }
    
    @Override
    public String checkShowReservationServiceHealth() throws AppException, HttpFailureException {
        final String callName = "show-reservation-health";
        return Retry.of(3, String.class, HttpFailureException.class).exeute(() -> {
            return service.get(SBS_API_ENDPOINT + SBS_HEALTH, String.class, callName, callName, Collections.emptyList(), new String[][] {});
        }, condition -> {
            return condition.getHttpCode() == 404 || condition.getHttpCode() == 500 || condition.getHttpCode() == 502
                    || condition.getHttpCode() == 503 || condition.getHttpCode() == 504;
        });
    }

    private String getErrorCode(final ErrorResponse errorResponse, final String errorPayload, final HttpFailureException exception) {
        final String sseCode;
        if (errorResponse != null && errorResponse.getError() != null) {
            sseCode = errorResponse.getError().getCode();
        } else {
            sseCode = exception != null ? String.valueOf(exception.getHttpCode()) : null;
        }
        return sseCode;
    }

    private String getErrorMessage(final ErrorResponse errorResponse, final String errorPayload, final HttpFailureException exception) {
        final String sseMessage;

        if (errorResponse != null && errorResponse.getError() != null) {
            sseMessage = errorResponse.getError().getMessage();
        } else {
            sseMessage = exception != null ? exception.getPayload() : null;
        }
        return sseMessage;
    }
}
