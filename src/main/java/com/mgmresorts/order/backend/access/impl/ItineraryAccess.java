package com.mgmresorts.order.backend.access.impl;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exec.Retry;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.itineraries.dto.client.services.CreateItineraryRequest;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IItineraryAccess;

public class ItineraryAccess implements IItineraryAccess {
    /* Itinerary API specific access class */

    @Inject
    @Named("simulation.enabled")
    private IHttpService service;
    @Inject
    private OAuthTokenRegistry registry;

    @Override
    public String createItinerary(CreateItineraryRequest request) throws AppException, HttpFailureException {
        final String serviceToken = CommonConfig.getServiceToken(registry);
        final String callName = "itinerary-create";
        return Retry.of(3, String.class, HttpFailureException.class).exeute(() -> {
            return service.post(ITINERARY_API_ENDPOINT + ITINERARY_CREATE, request, callName, callName, CommonConfig.getStandardHeaders(serviceToken));
        }, condition -> {
            return condition.getHttpCode() == 500 || condition.getHttpCode() == 502 || condition.getHttpCode() == 503 || condition.getHttpCode() == 504;
        });
    }
}
