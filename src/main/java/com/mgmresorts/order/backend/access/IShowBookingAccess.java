package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.sbs.model.ShowReservationRequest;
import com.mgmresorts.sbs.model.ShowReservationResponse;

public interface IShowBookingAccess extends CommonConfig {
    String createShowReservation(ShowReservationRequest request) throws AppException, HttpFailureException;
    
    ShowReservationResponse getShowReservation(String confirmationNumber, String firstName, String lastName) throws AppException;
    
    String checkShowReservationServiceHealth() throws AppException, HttpFailureException;
}
