package com.mgmresorts.order.backend.access.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.http.IHttpService.HttpHeaders.HttpHeader;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.dbs.model.Response;
import com.mgmresorts.dbs.model.ResponseError;
import com.mgmresorts.dbs.model.SearchReservationResponse;
import com.mgmresorts.order.errors.Errors;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;

public class DiningBookingAccessTest {

    @Tested
    private DiningBookingAccess diningBookingAccess;

    @Injectable
    private IHttpService service;
    @Injectable
    private OAuthTokenRegistry registry;

    private final JSonMapper jsonMapper = new JSonMapper();
    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @BeforeEach
    public void before() {
        assertNotNull(service);
        ErrorManager.clean();
        new Errors();
    }

    @Test
    public void testSearchDiningReservation_SuccessResponse() throws Exception {
        String confirmationNumber = "MGMLV-1ALLB324G";
        new Expectations() {
            {
                service.post(anyString,  any, "dining-reservation-search", "dining-reservation-search", (HttpHeader[]) any);
                String diningReservationJson = Utils.readFileFromClassPath("data/search_dining_reservation_response.json");
                result =  diningReservationJson;
            }
        };
        SearchReservationResponse response = diningBookingAccess.searchDiningReservation(confirmationNumber, "Diana", "Nar");
        assertNotNull(response);
        assertNotNull(response.getRestaurantReservationList().get(0).getBookDate());
        assertEquals("2021-01-29T18:50:32.154+00:00", response.getRestaurantReservationList().get(0).getBookDate());
        assertEquals("40872441-efdd-4189-a81c-d9da7f3fa803", response.getRestaurantReservationList().get(0).getRestaurantId());
        assertEquals("16APE4S", response.getRestaurantReservationList().get(0).getSevenRoomsProfileId());
    }

    @Test
    public void testSearchDiningReservation_Returns500ErrorResponse() throws Exception {
        new Expectations() {
            {
                service.post(anyString, any, "dining-reservation-search", "dining-reservation-search", (HttpHeader[]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(reservationBackedErrorResponse()), "failed", new String[] { "header" });
            }
        };
        assertThrows(AppException.class, () -> diningBookingAccess.searchDiningReservation("MGMLV-1ALLB324G", "Diana", "Nar"));
    }

    @Test
    public void testSearchDiningReservation_Returns404ErrorResponse() throws Exception {
        new Expectations() {
            {
                service.post(anyString, any, "dining-reservation-search", "dining-reservation-search", (HttpHeader[]) any);
                result = new HttpFailureException(404, null, "header");
            }
        };
        assertThrows(AppException.class, () -> diningBookingAccess.searchDiningReservation("MGMLV-1ALLB324G", "Diana", "Nar"));
    }

    @Test
    public void testSearchDiningReservation_WhenResponseIsNull() throws Exception {
        new Expectations() {
            {
                service.post(anyString, any, "dining-reservation-search", "dining-reservation-search", (HttpHeader[]) any);
                result = null;
            }
        };
        assertThrows(AppException.class, () -> diningBookingAccess.searchDiningReservation("MGMLV-1ALLB324G", "Diana", "Nar"));
    }

    private Response reservationBackedErrorResponse() {
        Response responseError = new Response();
        ResponseError error = new ResponseError();
        error.setCode("632-1-400");
        error.setMessage("Requested Reservation with Confirmation Number 'XMY12GHBN' not found");
        responseError.setError(error);
        return responseError;
    }
}
