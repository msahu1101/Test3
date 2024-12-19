package com.mgmresorts.order.backend.access.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.IShowBookingAccess;
import com.mgmresorts.order.backend.access.impl.ShowBookingAccess;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.sbs.model.ErrorResponse;
import com.mgmresorts.sbs.model.ErrorResponseError;
import com.mgmresorts.sbs.model.ShowReservationResponse;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;

@SuppressWarnings({ "unchecked", "unused" })
public class ShowBookingAccessTest {

    @Tested
    private ShowBookingAccess showBookingAccess;

    @Injectable
    private IHttpService service;
    @Injectable
    private OAuthTokenRegistry registry;

    @Injectable
    private IShowBookingAccess iShowBookingAccess;

    private JSonMapper jsonMapper = new JSonMapper();

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
    public void findReservationSuccessTest() throws Exception {
        String confirmationNumber = "100812688";
        String firstName = "Adithya";
        String lastName = "In";
        new Expectations() {
//            {
//                registry.getAccessToken(anyString,anyString,anyString,(String[]) any );
//            }
            {
                service.get(anyString, ShowReservationResponse.class, anyString, "show-reservation-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                String showReservationJson = Utils
                        .readFileFromClassPath("data/show_reservation_get_response.json");
                result = jsonMapper.readValue(showReservationJson, ShowReservationResponse.class);
            }
        };
        ShowReservationResponse response = showBookingAccess.getShowReservation(confirmationNumber, firstName,
                lastName);
        assertNotNull(response);
        assertNotNull(response.getBookDate());
        assertNotNull(response.getCharges());

    }

    @Test
    public void findReservationEmptyConfirmationNumberTest() throws AppException, HttpFailureException {
        String confirmationNumber = "";
        String firstName = "Adithya";
        String lastName = "In";
        new Expectations() {
            {
                service.get(anyString, ShowReservationResponse.class, anyString, anyString, (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new AppException(3031, "show reservation not found with give information");
            }
        };
        assertThrows(AppException.class, () -> {
            showBookingAccess.getShowReservation(confirmationNumber, firstName, lastName);
        });
    }

    @Test
    public void findReservationEmptyFirstNameTest() throws AppException, HttpFailureException {
        String confirmationNumber = "100812688";
        String firstName = "";
        String lastName = "In";
        new Expectations() {
            {
                service.get(anyString, ShowReservationResponse.class, anyString, anyString, (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new AppException(3031, "show reservation not found with give information");
            }
        };
        assertThrows(AppException.class, () -> {
            showBookingAccess.getShowReservation(confirmationNumber, firstName, lastName);
        });
    }

    @Test
    public void findReservationEmptyLastNameTest() throws AppException, HttpFailureException {
        String confirmationNumber = "100812688";
        String firstName = "Adithya";
        String lastName = "";
        new Expectations() {
            {
                service.get(anyString, ShowReservationResponse.class, anyString, anyString, (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new AppException(3031, "show reservation not found with give information");
            }
        };
        assertThrows(AppException.class, () -> {
            showBookingAccess.getShowReservation(confirmationNumber, firstName, lastName);
        });
    }

    @Test
    public void findReservationWrongConfirmationNumberTest() throws AppException, HttpFailureException {
        String confirmationNumber = "100812688XZ";
        String firstName = "Adithya";
        String lastName = "In";
        new Expectations() {
            {
                service.get(anyString, ShowReservationResponse.class, anyString, anyString, (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new AppException(3031, "show reservation not found with give information");
            }
        };
        assertThrows(AppException.class, () -> {
            showBookingAccess.getShowReservation(confirmationNumber, firstName, lastName);
        });
    }

    @Test
    public void findReservationWrongFirstNameTest() throws AppException, HttpFailureException {
        String confirmationNumber = "100812688";
        String firstName = "AdithyaXZ";
        String lastName = "In";
        new Expectations() {
            {
                service.get(anyString, ShowReservationResponse.class, anyString, anyString, (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new AppException(3031, "show reservation not found with give information");
            }
        };
        assertThrows(AppException.class, () -> {
            showBookingAccess.getShowReservation(confirmationNumber, firstName, lastName);
        });
    }

    @Test
    public void findReservationWrongLastNameTest() throws AppException, HttpFailureException {
        String confirmationNumber = "100812688";
        String firstName = "Adithya";
        String lastName = "InXZ";
        new Expectations() {
            {
                service.get(anyString, ShowReservationResponse.class, anyString, anyString, (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new AppException(3031, "show reservation not found with give information");
            }
        };
        assertThrows(AppException.class, () -> {
            showBookingAccess.getShowReservation(confirmationNumber, firstName, lastName);
        });
    }

    @Test
    public void findReservationWrongRequestTest() throws AppException, HttpFailureException {
        String confirmationNumber = "100812688XZ";
        String firstName = "AdithyaXZ";
        String lastName = "InXZ";
        new Expectations() {
            {
                service.get(anyString, ShowReservationResponse.class, anyString, anyString, (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new AppException(3031, "show reservation not found with give information");
            }
        };
        assertThrows(AppException.class, () -> {
            showBookingAccess.getShowReservation(confirmationNumber, firstName, lastName);
        });
    }

    @Test
    public void findReservationNullResponseTest() throws AppException, HttpFailureException {
        String confirmationNumber = "100812688XZ";
        String firstName = "AdithyaXZ";
        String lastName = "InXZ";
        new Expectations() {
            {
                service.get(anyString, ShowReservationResponse.class, anyString, anyString, (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = null;
            }
        };
        assertThrows(AppException.class, () -> {
            showBookingAccess.getShowReservation(confirmationNumber, firstName, lastName);
        });
    }

    private ErrorResponse findReservationBackendErrorResponse_ReservationNotFound() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("620-2-214");
        error.setMessage("<_reservation_not_found>[ Not able to retrieve reservation with given information ]");
        responseError.setError(error);
        return responseError;
    }

    private ErrorResponse findReservationBackendErrorResponse_MissingMandatoryFields() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("620-1-227");
        error.setMessage(
                "confirmation number along with first name/last name or guest token is mandatory to fetch reservation");
        responseError.setError(error);
        return responseError;
    }
}
