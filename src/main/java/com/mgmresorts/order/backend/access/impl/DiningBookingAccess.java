package com.mgmresorts.order.backend.access.impl;

import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.dbs.model.CreateReservationRequest;
import com.mgmresorts.dbs.model.Response;
import com.mgmresorts.dbs.model.SearchReservationRequest;
import com.mgmresorts.dbs.model.SearchReservationResponse;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IDiningBookingAccess;
import com.mgmresorts.order.errors.Errors;

import javax.inject.Inject;
import javax.inject.Named;

public class DiningBookingAccess implements IDiningBookingAccess {
    /* DBS API specific access class */

    private final Logger logger = Logger.get(DiningBookingAccess.class);
    private final JSonMapper mapper = new JSonMapper();
    @Inject
    @Named("simulation.enabled")
    private IHttpService service;
    @Inject
    private OAuthTokenRegistry registry;
    
    @Override
    public String createDiningReservation(CreateReservationRequest request) throws AppException, HttpFailureException {
        final String serviceToken = CommonConfig.getServiceToken(registry);
        final String callName = "dining-reservation-create";
        return service.post(DBS_API_ENDPOINT + DBS_RESERVATION_CREATE, request, callName, callName, CommonConfig.getDiningHeaders(serviceToken));
    }
    
    @Override
    public SearchReservationResponse searchDiningReservation(String confirmationNumber, String firstName, String lastName) throws AppException {
        try {
            final String callName = "dining-reservation-search";
            final String serviceToken = CommonConfig.getServiceToken(registry);

            SearchReservationRequest request = new SearchReservationRequest();
            request.setConfirmationNumber(confirmationNumber);
            request.setFirstName(firstName);
            request.setLastName(lastName);

            final String diningResponse = service.post(
                    DBS_API_ENDPOINT + DBS_RESERVATION_SEARCH, request,
                    callName, callName, CommonConfig.getDiningHeaders(serviceToken)
            );

            final SearchReservationResponse response = mapper.readValue(diningResponse, SearchReservationResponse.class);

            if (response == null) {
                throw new AppException(Errors.UNABLE_TO_GET_DINING_RESERVATION, "Could not get dining reservation response.");
            }
            return response;
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.error("[Error from DBS] Get dining Reservation call failed. :  {}", errorPayload);
                final Response errorResponse = mapper.readValue(e.getPayload(), Response.class);
                final String code = getErrorCode(errorResponse, errorPayload, e);
                final String message = getErrorMessage(errorResponse, errorPayload, e);
                throw new SourceAppException(Errors.UNABLE_TO_GET_DINING_RESERVATION, code, message, errorPayload);
            } else {
                logger.error("[Error from DBS] Something unexpected happened in get dining reservation call.  :", e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not get dining reservation. Unexpected error occurred." + e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from DBS] Get dining Reservation call failed with app exception. : ", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from DBS] Get dining Reservation call failed with unknown exception. : ", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }

    private String getErrorCode(final Response errorResponse, final String errorPayload, final HttpFailureException exception) {
        final String sseCode;
        if (errorResponse != null && errorResponse.getError() != null) {
            sseCode = errorResponse.getError().getCode();
        } else {
            sseCode = exception != null ? String.valueOf(exception.getHttpCode()) : null;
        }
        return sseCode;
    }

    private String getErrorMessage(final Response errorResponse, final String errorPayload, final HttpFailureException exception) {
        final String sseMessage;

        if (errorResponse != null && errorResponse.getError() != null) {
            sseMessage = errorResponse.getError().getMessage();
        } else {
            sseMessage = exception != null ? exception.getPayload() : null;
        }
        return sseMessage;
    }
}
