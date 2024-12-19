package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.itineraries.dto.client.services.CreateItineraryRequest;

public interface IItineraryAccess extends CommonConfig {
    String createItinerary(CreateItineraryRequest request) throws AppException, HttpFailureException;
}
