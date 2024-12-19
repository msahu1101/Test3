package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.dbs.model.CreateReservationRequest;
import com.mgmresorts.dbs.model.SearchReservationResponse;

public interface IDiningBookingAccess extends CommonConfig {
    String createDiningReservation(CreateReservationRequest request) throws AppException, HttpFailureException;

    SearchReservationResponse searchDiningReservation(String confirmationNumber, String firstName, String lastName) throws AppException;
}

 