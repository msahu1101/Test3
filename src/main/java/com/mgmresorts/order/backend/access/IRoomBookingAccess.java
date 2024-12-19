package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.rbs.model.GetRoomReservationResponse;
import com.mgmresorts.rbs.model.ModifyCommitErrorResponse;
import com.mgmresorts.rbs.model.ModifyCommitPutRequest;
import com.mgmresorts.rbs.model.PremodifyPutRequest;
import com.mgmresorts.rbs.model.RefundCommitPutRequest;
import com.mgmresorts.rbs.model.UpdateRoomReservationResponse;
import com.mgmresorts.rbs.model.CancelRoomReservationResponse;
import com.mgmresorts.rbs.model.CancelRoomReservationV3Request;
import com.mgmresorts.rbs.model.CreateRoomReservationRequest;

public interface IRoomBookingAccess extends CommonConfig {
    String createRoomReservation(CreateRoomReservationRequest request) throws AppException, HttpFailureException;
    
    GetRoomReservationResponse getRoomReservation(String confirmationNumber, String firstName, String lastName) throws AppException;
    
    UpdateRoomReservationResponse previewRoomReservation(PremodifyPutRequest request) throws AppException;

    ModifyCommitErrorResponse commitRoomReservation(ModifyCommitPutRequest request) throws AppException;
    
    UpdateRoomReservationResponse refundCommitRoomReservation(RefundCommitPutRequest request) throws AppException;
    
    CancelRoomReservationResponse cancelRoomReservation(CancelRoomReservationV3Request request) throws AppException;
    
    boolean releaseRoomReservation(String propertyId, String confirmationNumber, String holdId, boolean f1Package) throws AppException;
}
