package com.mgmresorts.order.service;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.services.CancelReservationRequest;
import com.mgmresorts.order.dto.services.CancelReservationResponse;
import com.mgmresorts.order.dto.services.PreviewReservationRequest;
import com.mgmresorts.order.dto.services.PreviewReservationResponse;
import com.mgmresorts.order.dto.services.ReservationType;
import com.mgmresorts.order.dto.services.RetrieveReservationResponse;
import com.mgmresorts.order.dto.services.UpdateReservationRequest;
import com.mgmresorts.order.dto.services.UpdateReservationResponse;

public interface IReservationService {
    RetrieveReservationResponse getReservation(String confirmationNumber, String firstName, String lastName,
            ReservationType reservationType, boolean createPaymentSession, String paymentSessionId) throws AppException;
    
    PreviewReservationResponse previewReservation(PreviewReservationRequest request) throws AppException;

    UpdateReservationResponse updateReservation(UpdateReservationRequest request) throws AppException;
    
    CancelReservationResponse cancelReservation(CancelReservationRequest request) throws AppException;
}
