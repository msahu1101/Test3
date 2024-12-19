package com.mgmresorts.order.backend.access.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IRoomBookingAccess;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.rbs.model.ErrorResponse;
import com.mgmresorts.rbs.model.GetRoomReservationResponse;
import com.mgmresorts.rbs.model.ModifyCommitErrorResponse;
import com.mgmresorts.rbs.model.ModifyCommitPutRequest;
import com.mgmresorts.rbs.model.PremodifyPutRequest;
import com.mgmresorts.rbs.model.RefundCommitPutRequest;
import com.mgmresorts.rbs.model.UpdateRoomReservationResponse;
import com.mgmresorts.rbs.model.CancelRoomReservationV3Request;
import com.mgmresorts.rbs.model.CreateRoomReservationRequest;
import com.mgmresorts.rbs.model.CancelRoomReservationResponse;

public class RoomBookingAccess implements IRoomBookingAccess {
    /* RBS API specific access class */

    private final Logger logger = Logger.get(RoomBookingAccess.class);
    private final JSonMapper mapper = new JSonMapper();
    @Inject
    @Named("simulation.enabled")
    private IHttpService service;
    @Inject
    private OAuthTokenRegistry registry;
    
    @Override
    public String createRoomReservation(CreateRoomReservationRequest request) throws AppException, HttpFailureException {
        final String serviceToken = CommonConfig.getServiceToken(registry);
        final String callName = "room-reservation-create";
        return service.post(RBS_API_ENDPOINT + RBS_RESERVATION_CREATE, request, callName, callName, CommonConfig.getRoomHeaders(serviceToken));
    }
    
    @Override
    public GetRoomReservationResponse getRoomReservation(String confirmationNumber, String firstName, String lastName) throws AppException {
        try {
            final String callName = "room-reservation-get";
            final String guestToken = CommonConfig.getGuestToken();

            final List<String[]> paramList = new ArrayList<>();
            paramList.add(new String[] { "firstName", firstName });
            paramList.add(new String[] { "lastName", lastName });
            paramList.add(new String[] { "confirmationNumber", confirmationNumber });

            final GetRoomReservationResponse response = service.get(
                    RBS_API_ENDPOINT + RBS_RESERVATION_GET, GetRoomReservationResponse.class,
                    callName, callName, Arrays.asList(CommonConfig.getRoomHeaders(guestToken)), paramList.toArray(new String[][]{})
            );

            if (response == null) {
                throw new AppException(Errors.UNABLE_TO_GET_ROOM_RESERVATION, "Could not get room reservation response.");
            }
            return response;
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.error("[Error from RBS] Get Room Reservation call failed. :  {}", errorPayload);
                final ErrorResponse errorResponse = mapper.readValue(e.getPayload(), ErrorResponse.class);
                final String code = getErrorCode(errorResponse, errorPayload, e);
                final String message = getErrorMessage(errorResponse, errorPayload, e);
                throw new SourceAppException(Errors.UNABLE_TO_GET_ROOM_RESERVATION, code, message, errorPayload);
            } else {
                logger.error("[Error from RBS] Something unexpected happened in get room reservation call.  :", e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not get room reservation. Unexpected error occurred." + e.getHttpCode() + ":" + e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from RBS] Get Room Reservation call failed with app exception. : ", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from RBS] Get Room Reservation call failed with unknown exception. : ", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }
    
    @Override
    public UpdateRoomReservationResponse previewRoomReservation(PremodifyPutRequest request) throws AppException {
        try {
            final String guestToken = CommonConfig.getGuestToken();
            final String callName = "room-reservation-preview";
            final String response = service.put(RBS_API_ENDPOINT + RBS_RESERVATION_PREVIEW, request, callName, callName,
                    CommonConfig.getRoomHeaders(guestToken));
            
            if (Utils.isEmpty(response)) {
                throw new AppException(Errors.UNABLE_TO_GET_RESERVATION_PREVIEW, "Could not get room reservation preview. No response from backend.");
            }
            
            return mapper.readValue(response, UpdateRoomReservationResponse.class);
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.error("[Error from RBS] Room reservation preview call failed. :  {}", errorPayload);
                final ErrorResponse errorResponse = mapper.readValue(e.getPayload(), ErrorResponse.class);
                final String code = getErrorCode(errorResponse, errorPayload, e);
                final String message = getErrorMessage(errorResponse, errorPayload, e);
                throw new SourceAppException(Errors.UNABLE_TO_GET_RESERVATION_PREVIEW, code, message, errorPayload);
            } else {
                logger.error("[Error from RBS] Something unexpected happened in room reservation preview call.  :", e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not get room reservation preview. Unexpected error occurred." + e.getHttpCode() + ":" + e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from RBS] Preview Room Reservation call failed with app exception. : ", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from RBS] Preview Room Reservation call failed with unknown exception. : ", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }

    @Override
    public ModifyCommitErrorResponse commitRoomReservation(ModifyCommitPutRequest request) throws AppException {
        // method that is calling this should check for response.getError() everytime, if it is null == 200
        // if it is not null, it is due to price change error and should return whole modifyCommitErrorResponse (error + res) to caller (GQL)
        try {
            final String guestToken = CommonConfig.getGuestToken();
            final String callName = "room-reservation-commit";
            final String response = service.put(RBS_API_ENDPOINT + RBS_RESERVATION_COMMIT, request, callName, callName, CommonConfig.getRoomHeaders(guestToken));

            if (Utils.isEmpty(response)) {
                throw new AppException(Errors.UNABLE_TO_UPDATE_ROOM_RESERVATION, "Could not commit room reservation. No response from backend.");
            }

            return mapper.readValue(response, ModifyCommitErrorResponse.class);
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.error("[Error from RBS] Room reservation commit call failed: {}", errorPayload);
                final ModifyCommitErrorResponse errorResponse = mapper.readValue(errorPayload, ModifyCommitErrorResponse.class);
                final String code = getReservationCommitErrorCode(errorResponse, e);
                final String message = getReservationCommitErrorMessage(errorResponse, e);

                // price change error code, return both error & room res to display on UI
                if (code.equalsIgnoreCase("632-2-259")) {
                    return errorResponse;
                }
                throw new SourceAppException(Errors.UNABLE_TO_UPDATE_ROOM_RESERVATION, code, message, errorPayload);
            } else {
                logger.error("[Error from RBS] Something unexpected happened in room reservation commit call: {}", e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not commit room reservation. Unexpected error occurred." + e.getHttpCode() + ":" + e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from RBS] Commit Room Reservation call failed with app exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from RBS] Commit Room Reservation call failed with unknown exception: {}", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }
    
    @Override
    public UpdateRoomReservationResponse refundCommitRoomReservation(RefundCommitPutRequest request) throws AppException {
        try {
            final String guestToken = CommonConfig.getGuestToken();
            final String callName = "room-reservation-refund-commit";
            final String response = service.put(RBS_API_ENDPOINT + RBS_RESERVATION_REFUND_COMMIT, request, callName, callName, CommonConfig.getRoomHeaders(guestToken));

            if (Utils.isEmpty(response)) {
                throw new AppException(Errors.UNABLE_TO_COMMIT_REFUND_ROOM_RESERVATION, "Could not commit refund for room reservation. No response from backend.");
            }

            return mapper.readValue(response, UpdateRoomReservationResponse.class);
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.error("[Error from RBS] Room reservation refund commit call failed. :  {}", errorPayload);
                final ErrorResponse errorResponse = mapper.readValue(e.getPayload(), ErrorResponse.class);
                final String code = getErrorCode(errorResponse, errorPayload, e);
                final String message = getErrorMessage(errorResponse, errorPayload, e);
                throw new SourceAppException(Errors.UNABLE_TO_COMMIT_REFUND_ROOM_RESERVATION, code, message, errorPayload);
            } else {
                logger.error("[Error from RBS] Something unexpected happened in room reservation refund commit call.  :", e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not commit refund for room reservation. Unexpected error occurred." + e.getHttpCode() + ":" + e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from RBS] Refund Commit Room Reservation call failed with app exception. : ", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from RBS] Refund Commit Room Reservation call failed with unknown exception. : ", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }
    
    public CancelRoomReservationResponse cancelRoomReservation(CancelRoomReservationV3Request request) throws AppException {
        try {
            final String guestToken = CommonConfig.getGuestToken();
            final String callName = "room-reservation-cancel";
            final String response = service.post(RBS_API_ENDPOINT + RBS_RESERVATION_V3_CANCEL, request, callName, callName,
                    CommonConfig.getRoomHeaders(guestToken));

            if (Utils.isEmpty(response)) {
                throw new AppException(Errors.UNABLE_TO_CANCEL_ROOM_RESERVATION, "Could not cancel room reservation. No response from backend.");
            }
            return mapper.readValue(response, CancelRoomReservationResponse.class);
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.error("[Error from RBS] Cancel room reservation call failed. :  {}", errorPayload);
                final ErrorResponse errorResponse = mapper.readValue(e.getPayload(), ErrorResponse.class);
                final String code = getErrorCode(errorResponse, errorPayload, e);
                final String message = getErrorMessage(errorResponse, errorPayload, e);
                throw new SourceAppException(Errors.UNABLE_TO_CANCEL_ROOM_RESERVATION, code, message, errorPayload);
            } else {
                logger.error("[Error from RBS] Something unexpected happened in cancel room reservation call.  :", e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not cancel room reservation. Unexpected error occurred." + e.getHttpCode() + ":" + e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from RBS] Cancel Room Reservation call failed with app exception. : ", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from RBS] Cancel Room Reservation call failed with unknown exception. : ", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }
    
    @Override
    public boolean releaseRoomReservation(String propertyId, String confirmationNumber, String holdId, boolean f1Package) throws AppException {
        try {
            final String serviceToken = CommonConfig.getServiceToken(registry);
            final String callName = "room-reservation-release";
            service.delete(RBS_API_ENDPOINT + RBS_RESERVATION_RELEASE, String.class, callName, callName,
                    Arrays.asList(CommonConfig.getRoomHeaders(serviceToken)),
                    createReleaseRoomReservationQueryParamList(propertyId, confirmationNumber, holdId, f1Package));
            return true;
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.warn("[Error from RBS] Release room reservation call failed. : {}", errorPayload);
                final ErrorResponse errorResponse = mapper.readValue(e.getPayload(), ErrorResponse.class);
                final String code = getErrorCode(errorResponse, errorPayload, e);
                final String message = getErrorMessage(errorResponse, errorPayload, e);
                throw new SourceAppException(Errors.UNABLE_TO_RELEASE_ROOM_RESERVATION, code, message, errorPayload);
            } else {
                logger.error("[Error from RBS] Something unexpected happened in release reservation call.  :", e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM,
                        "Could not release room reservation. Unexpected error occurred." + e.getHttpCode() + ":" + e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from RBS] Release Room Reservation call failed with app exception. : ", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from RBS] Release Room Reservation call failed with unknown exception. : ", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }
    
    private String[][] createReleaseRoomReservationQueryParamList(String propertyId, String confirmationNumber, String holdId, boolean f1Package) {
        final List<String[]> paramList = new ArrayList<>();
        paramList.add(new String[] { "propertyId", propertyId });
        Optional.ofNullable(confirmationNumber).ifPresent(confNum -> paramList.add(new String[] { "confirmationNumber", confNum }));
        if (f1Package) {
            paramList.add(new String[] { "holdId", holdId });
            paramList.add(new String[] { "f1Package", String.valueOf(f1Package) });
        }
        return paramList.toArray(new String[][] {});
    }

    private String getErrorCode(final ErrorResponse errorResponse, final String errorPayload,
                                final HttpFailureException exception) {
        final String sseCode;
        if (errorResponse != null && errorResponse.getError() != null) {
            sseCode = errorResponse.getError().getCode();
        } else {
            sseCode = exception != null ? String.valueOf(exception.getHttpCode()) : null;
        }
        return sseCode;
    }

    private String getErrorMessage(final ErrorResponse errorResponse, final String errorPayload,
                                   final HttpFailureException exception) {
        final String sseMessage;

        if (errorResponse != null && errorResponse.getError() != null) {
            sseMessage = errorResponse.getError().getMessage();
        } else {
            sseMessage = exception != null ? exception.getPayload() : null;
        }
        return sseMessage;
    }

    private String getReservationCommitErrorCode(final ModifyCommitErrorResponse errorResponse, final HttpFailureException exception) {
        final String sseCode;
        if (errorResponse != null && errorResponse.getError() != null) {
            sseCode = errorResponse.getError().getCode();
        } else {
            sseCode = exception != null ? String.valueOf(exception.getHttpCode()) : null;
        }
        return sseCode;
    }

    private String getReservationCommitErrorMessage(final ModifyCommitErrorResponse errorResponse, final HttpFailureException exception) {
        final String sseMessage;

        if (errorResponse != null && errorResponse.getError() != null) {
            sseMessage = errorResponse.getError().getMessage();
        } else {
            sseMessage = exception != null ? exception.getPayload() : null;
        }
        return sseMessage;
    }
}
