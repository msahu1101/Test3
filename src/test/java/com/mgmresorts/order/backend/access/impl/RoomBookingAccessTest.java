package com.mgmresorts.order.backend.access.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.mgmresorts.rbs.model.ModifyCommitErrorResponse;
import com.mgmresorts.rbs.model.ModifyCommitPutRequest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.http.IHttpService.HttpHeaders.HttpHeader;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.impl.RoomBookingAccess;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.rbs.model.ErrorResponse;
import com.mgmresorts.rbs.model.ErrorResponseError;
import com.mgmresorts.rbs.model.GetRoomReservationResponse;
import com.mgmresorts.rbs.model.PremodifyPutRequest;
import com.mgmresorts.rbs.model.PremodifyPutRequestTripDetails;
import com.mgmresorts.rbs.model.RefundCommitPutRequest;
import com.mgmresorts.rbs.model.RoomBillingDetails;
import com.mgmresorts.rbs.model.RoomBillingDetailsAddress;
import com.mgmresorts.rbs.model.RoomBillingDetailsPayment;
import com.mgmresorts.rbs.model.RoomReservationResponse;
import com.mgmresorts.rbs.model.UpdateRoomReservationResponse;
import com.mgmresorts.rbs.model.CancelRoomReservationV3Request;
import com.mgmresorts.rbs.model.CancelRoomReservationResponse;


import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@SuppressWarnings({ "unchecked", "unused" })
public class RoomBookingAccessTest {

    @Tested
    private RoomBookingAccess roomBookingAccess;

    @Injectable
    private IHttpService service;
    @Injectable
    private OAuthTokenRegistry registry;

    private final JSonMapper jsonMapper = new JSonMapper();
    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();

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
    public void testGetReservation_SuccessResponse() throws Exception {
        String confirmationNumber = "15O3O23RLC";
        new Expectations() {
            {
                service.get(anyString, (Class<GetRoomReservationResponse>) any, "room-reservation-get", "room-reservation-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                String roomReservationJson = Utils.readFileFromClassPath("data/room_reservation_get_response.json");
                result =  jsonMapper.readValue(roomReservationJson, GetRoomReservationResponse.class);
            }
        };
        GetRoomReservationResponse response = roomBookingAccess.getRoomReservation(confirmationNumber, "Diana", "Nar");
        assertNotNull(response);
        assertNotNull(response.getRoomReservation().getBookDate());
        assertEquals("564654", response.getRoomReservation().getCustomerId());
    }

    @Test
    public void testGetReservation_Returns500ErrorResponse() throws Exception {
        new Expectations() {
            {
                service.get(anyString, (Class<GetRoomReservationResponse>) any, "room-reservation-get", "room-reservation-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(reservationBackedErrorResponse()), "failed", new String[] { "header" });
            }
        };
        assertThrows(AppException.class, () -> roomBookingAccess.getRoomReservation("15O3O23RLC", "Diana", "Nar"));
    }

    @Test
    public void testGetReservation_Returns404ErrorResponse() throws Exception {
        new Expectations() {
            {
                service.get(anyString, (Class<GetRoomReservationResponse>) any, "room-reservation-get", "room-reservation-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(404, null, "header");
            }
        };
        assertThrows(AppException.class, () -> roomBookingAccess.getRoomReservation("15O3O23RLC", "Diana", "Nar"));
    }

    @Test
    public void testGetReservation_WhenResponseIsNull() throws Exception {
        new Expectations() {
            {
                service.get(anyString, (Class<GetRoomReservationResponse>) any, "room-reservation-get", "room-reservation-get", (List<IHttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = null;
            }
        };
        assertThrows(AppException.class, () -> roomBookingAccess.getRoomReservation("15O3O23RLC", "Diana", "Nar"));
    }

    private GetRoomReservationResponse getReservationResponse(String confirmationNumber) {
        GetRoomReservationResponse getRoomReservationResponse = new GetRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setBookDate(LocalDate.now().toString());
        roomReservationResponse.setConfirmationNumber(confirmationNumber);
        roomReservationResponse.setCustomerId("564654");
        roomReservationResponse.setCustomerRank(2);
        getRoomReservationResponse.setRoomReservation(roomReservationResponse);
        return getRoomReservationResponse;
    }
    
    private ErrorResponse reservationBackedErrorResponse() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("632-1-400");
        error.setMessage("error");
        responseError.setError(error);
        return responseError;
    }

    @Test
    public void testRoomReservationPreviewSuccess() throws Exception {
        new Expectations() {
            {
                service.put(anyString, (Class<PremodifyPutRequest>) any, "room-reservation-preview", "room-reservation-preview", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/room_reservation_preview_response.json");
            }
        };

        PremodifyPutRequest premodifyPutRequest = createRoomReservationPreviewRequest();
        UpdateRoomReservationResponse updateRoomReservationResponse = roomBookingAccess.previewRoomReservation(premodifyPutRequest);

        assertNotNull(updateRoomReservationResponse);
        assertEquals("M083E2631", updateRoomReservationResponse.getRoomReservation().getConfirmationNumber());
        assertEquals("2024-02-16", updateRoomReservationResponse.getRoomReservation().getTripDetails().getCheckInDate());
        assertEquals("2024-02-18", updateRoomReservationResponse.getRoomReservation().getTripDetails().getCheckOutDate());
        assertEquals("Raman", updateRoomReservationResponse.getRoomReservation().getProfile().getFirstName());
        assertEquals("Lamba", updateRoomReservationResponse.getRoomReservation().getProfile().getLastName());
        assertNotNull(updateRoomReservationResponse.getRoomReservation().getBookings());
        assertNotNull(updateRoomReservationResponse.getRoomReservation().getChargesAndTaxes());
        assertNotNull(updateRoomReservationResponse.getRoomReservation().getDepositDetails());
        assertNotNull(updateRoomReservationResponse.getRoomReservation().getRatesSummary());
    }

    @Test
    public void testRoomReservationPreviewErrorResponse_DatesUnavailable() throws Exception {
        new Expectations() {
            {
                service.put(anyString, (Class<PremodifyPutRequest>) any, "room-reservation-preview", "room-reservation-preview", (HttpHeader[]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(reservationPreviewBackedErrorResponse_DatesUnavailable()), "failed", new String[] { "header" });
            }
        };

        PremodifyPutRequest premodifyPutRequest = createRoomReservationPreviewRequest();
        assertThrows(SourceAppException.class, () -> roomBookingAccess.previewRoomReservation(premodifyPutRequest));
    }

    @Test
    public void testRoomReservationPreviewErrorResponse_UnableToPrice() throws Exception {
        new Expectations() {
            {
                service.put(anyString, (Class<PremodifyPutRequest>) any, "room-reservation-preview", "room-reservation-preview", (HttpHeader[]) any);
                result = new HttpFailureException(400, jsonMapper.writeValueAsString(reservationPreviewBackedErrorResponse_UnableToPrice()), "failed", new String[] { "header" });
            }
        };

        PremodifyPutRequest premodifyPutRequest = createRoomReservationPreviewRequest();
        assertThrows(SourceAppException.class, () -> roomBookingAccess.previewRoomReservation(premodifyPutRequest));
    }

    @Test
    public void testRoomReservationPreviewErrorResponse_Invalid_JWT() throws Exception {
        final String payload = "{\"message\":\"JWT is not valid, Please provide a valid Token\"}";

        new Expectations() {
            {
                service.put(anyString, (Class<PremodifyPutRequest>) any, "room-reservation-preview", "room-reservation-preview", (HttpHeader[]) any);
                result = new HttpFailureException(401, payload, "Error while calling http endpoint");
            }
        };

        PremodifyPutRequest premodifyPutRequest = createRoomReservationPreviewRequest();

        try {
            roomBookingAccess.previewRoomReservation(premodifyPutRequest);
        } catch (SourceAppException e) {
            assertEquals(e.getSourceMessage(), payload);
        }
    }

    @Test
    public void testRoomReservationPreviewErrorResponse_Null() throws Exception {

        new Expectations() {
            {
                service.put(anyString, (Class<PremodifyPutRequest>) any, "room-reservation-preview", "room-reservation-preview", (HttpHeader[]) any);
                result = null;
            }
        };

        PremodifyPutRequest premodifyPutRequest = createRoomReservationPreviewRequest();

        assertThrows(AppException.class, () -> roomBookingAccess.previewRoomReservation(premodifyPutRequest));
    }

    @Test
    public void testRoomReservationPreviewErrorResponse_BackendServerError() throws Exception {
        new Expectations() {
            {
                service.put(anyString, (Class<PremodifyPutRequest>) any, "room-reservation-preview", "room-reservation-preview", (HttpHeader[]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(reservationPreviewBackedErrorResponse_BackendServerError()), "failed", new String[] { "header" });
            }
        };

        PremodifyPutRequest premodifyPutRequest = createRoomReservationPreviewRequest();
        assertThrows(SourceAppException.class, () -> roomBookingAccess.previewRoomReservation(premodifyPutRequest));
    }

    private PremodifyPutRequest createRoomReservationPreviewRequest() {
        PremodifyPutRequest request = new PremodifyPutRequest();
        request.setConfirmationNumber("M083E2631");
        request.setFirstName("Raman");
        request.lastName("Lamba");
        PremodifyPutRequestTripDetails tripDetails = new PremodifyPutRequestTripDetails();
        tripDetails.setCheckInDate("2024-02-16");
        tripDetails.setCheckOutDate("2024-02-18");
        request.setTripDetails(tripDetails);
        request.setRoomRequests(new ArrayList<>());
        return request;
    }

    private ErrorResponse reservationPreviewBackedErrorResponse_DatesUnavailable() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("632-2-146");
        error.setMessage("<_dates_not_available>[ One of more dates are not available ]");
        responseError.setError(error);
        return responseError;
    }

    private ErrorResponse reservationPreviewBackedErrorResponse_UnableToPrice() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("632-2-146");
        error.setMessage("<_dates_not_available>[ One of more dates are not available ]");
        responseError.setError(error);
        return responseError;
    }

    private ErrorResponse reservationPreviewBackedErrorResponse_BackendServerError() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("632-3-101");
        error.setMessage("_system_error");
        responseError.setError(error);
        return responseError;
    }

    @Test
    public void testRoomReservationCommit_SuccessResponse() throws Exception {
        new Expectations() {
            {
                service.put(anyString, (Class<ModifyCommitPutRequest>) any, "room-reservation-commit", "room-reservation-commit", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/room_reservation_commit_success_response.json");
            }
        };

        ModifyCommitErrorResponse response = roomBookingAccess.commitRoomReservation(createRoomReservationCommitRequest());

        assertNotNull(response);
        assertNull(response.getError());
        assertNotNull(response.getRoomReservation());
        assertNotNull(response.getRoomReservation().getConfirmationNumber());
        assertEquals("M083E2635", response.getRoomReservation().getConfirmationNumber());
        assertNotNull(response.getRoomReservation().getTripDetails());
        assertNotNull(response.getRoomReservation().getTripDetails().getCheckInDate());
        assertNotNull(response.getRoomReservation().getTripDetails().getCheckOutDate());
        assertEquals("2024-02-16", response.getRoomReservation().getTripDetails().getCheckInDate());
        assertEquals("2024-02-18", response.getRoomReservation().getTripDetails().getCheckOutDate());
        assertNotNull(response.getRoomReservation().getProfile());
        assertNotNull(response.getRoomReservation().getProfile().getFirstName());
        assertNotNull(response.getRoomReservation().getProfile().getLastName());
        assertEquals("John", response.getRoomReservation().getProfile().getFirstName());
        assertEquals("Doe", response.getRoomReservation().getProfile().getLastName());
        assertNotNull(response.getRoomReservation().getBookings());
        assertNotNull(response.getRoomReservation().getChargesAndTaxes());
        assertNotNull(response.getRoomReservation().getDepositDetails());
        assertNotNull(response.getRoomReservation().getRatesSummary());
    }

    @Test
    public void testRoomReservationCommit_400FailureResponsePriceChange() throws Exception {
        new Expectations() {
            {
                service.put(anyString, (Class<ModifyCommitPutRequest>) any, "room-reservation-commit", "room-reservation-commit", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/room_reservation_commit_400_failure_response_price_change.json");
            }
        };

        ModifyCommitErrorResponse response = roomBookingAccess.commitRoomReservation(createRoomReservationCommitRequest());

        assertNotNull(response);
        assertNotNull(response.getError());
        assertNotNull(response.getError().getCode());
        assertEquals("632-2-259", response.getError().getCode());
        assertNotNull(response.getRoomReservation());
        assertNotNull(response.getRoomReservation().getConfirmationNumber());
        assertEquals("M083E2635", response.getRoomReservation().getConfirmationNumber());
        assertNotNull(response.getRoomReservation().getTripDetails());
        assertNotNull(response.getRoomReservation().getTripDetails().getCheckInDate());
        assertNotNull(response.getRoomReservation().getTripDetails().getCheckOutDate());
        assertEquals("2024-02-16", response.getRoomReservation().getTripDetails().getCheckInDate());
        assertEquals("2024-02-18", response.getRoomReservation().getTripDetails().getCheckOutDate());
        assertNotNull(response.getRoomReservation().getProfile());
        assertNotNull(response.getRoomReservation().getProfile().getFirstName());
        assertNotNull(response.getRoomReservation().getProfile().getLastName());
        assertEquals("John", response.getRoomReservation().getProfile().getFirstName());
        assertEquals("Doe", response.getRoomReservation().getProfile().getLastName());
        assertNotNull(response.getRoomReservation().getBookings());
        assertNotNull(response.getRoomReservation().getChargesAndTaxes());
        assertNotNull(response.getRoomReservation().getChargesAndTaxes().getCharges());
        assertNotNull(response.getRoomReservation().getChargesAndTaxes().getTaxesAndFees());
        assertEquals(1, response.getRoomReservation().getChargesAndTaxes().getCharges().size());
        assertEquals(1, response.getRoomReservation().getChargesAndTaxes().getTaxesAndFees().size());
        assertNotNull(response.getRoomReservation().getChargesAndTaxes().getCharges().get(0));
        assertNotNull(response.getRoomReservation().getChargesAndTaxes().getTaxesAndFees().get(0));
        assertNotNull(response.getRoomReservation().getChargesAndTaxes().getCharges().get(0).getAmount());
        assertNotNull(response.getRoomReservation().getChargesAndTaxes().getTaxesAndFees().get(0).getAmount());
        assertEquals(new BigDecimal(10), response.getRoomReservation().getChargesAndTaxes().getCharges().get(0).getAmount());
        assertEquals(new BigDecimal(5), response.getRoomReservation().getChargesAndTaxes().getTaxesAndFees().get(0).getAmount());
        assertNotNull(response.getRoomReservation().getDepositDetails());
        assertNotNull(response.getRoomReservation().getRatesSummary());
    }

    @Test
    public void testRoomReservationCommit_400FailureResponse() throws Exception {
        new Expectations() {
            {
                service.put(anyString, (Class<ModifyCommitPutRequest>) any, "room-reservation-commit", "room-reservation-commit", (HttpHeader[]) any);
                result = new HttpFailureException(400, Utils.readFileFromClassPath("data/room_reservation_commit_400_failure_response.json"), "failed", new String[]{"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> roomBookingAccess.commitRoomReservation(createRoomReservationCommitRequest()));
    }

    @Test
    public void testRoomReservationCommit_401FailureResponse() throws Exception {
        final String payload = "{\"message\":\"JWT is not valid, Please provide a valid Token\"}";

        new Expectations() {
            {
                service.put(anyString, (Class<ModifyCommitPutRequest>) any, "room-reservation-commit", "room-reservation-commit", (HttpHeader[]) any);
                result = new HttpFailureException(401, payload, "Unauthorized");
            }
        };

        assertThrows(AppException.class, () -> roomBookingAccess.commitRoomReservation(createRoomReservationCommitRequest()));
    }

    @Test
    public void testRoomReservationCommit_500FailureResponse() throws Exception {
        new Expectations() {

            {
                service.put(anyString, (Class<ModifyCommitPutRequest>) any, "room-reservation-commit", "room-reservation-commit", (HttpHeader[]) any);
                result = new HttpFailureException(500, Utils.readFileFromClassPath("data/room_reservation_commit_500_failure_response.json"), "failed", new String[]{"header"});
            }
        };
        assertThrows(SourceAppException.class, () -> roomBookingAccess.commitRoomReservation(createRoomReservationCommitRequest()));
    }

    @Test
    public void testRoomReservationCommit_NullResponse() throws Exception {

        new Expectations() {
            {
                service.put(anyString, (Class<PremodifyPutRequest>) any, "room-reservation-commit", "room-reservation-commit", (HttpHeader[]) any);
                result = null;
            }
        };
        assertThrows(AppException.class, () -> roomBookingAccess.commitRoomReservation(createRoomReservationCommitRequest()));
    }

    private ModifyCommitPutRequest createRoomReservationCommitRequest() {
        ModifyCommitPutRequest request = new ModifyCommitPutRequest();
        request.setConfirmationNumber("M083E2635");
        request.setFirstName("John");
        request.setLastName("Doe");
        PremodifyPutRequestTripDetails tripDetails = new PremodifyPutRequestTripDetails();
        tripDetails.setCheckInDate("2024-02-16");
        tripDetails.setCheckOutDate("2024-02-18");
        request.setTripDetails(tripDetails);
        List<String> roomRequests = new ArrayList<>();
        roomRequests.add("a197e21b-ef75-45aa-b9d3-3480f557c77a");
        request.setRoomRequests(roomRequests);
        request.setPreviewReservationDeposit(136.4);
        request.setPreviewReservationTotal(236.4);
        request.setCvv("123");
        return request;
    }
    
    @Test
    public void testRoomReservationRefundCommitSuccess() throws Exception {
        new Expectations() {
            {
                service.put(anyString, (Class<RefundCommitPutRequest>) any, "room-reservation-refund-commit", "room-reservation-refund-commit", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/room_reservation_refund_commit_success_response.json");
            }
        };

        RefundCommitPutRequest refundCommitPutRequest = createRoomReservationRefundCommitRequest();
        UpdateRoomReservationResponse updateRoomReservationResponse = roomBookingAccess.refundCommitRoomReservation(refundCommitPutRequest);

        assertNotNull(updateRoomReservationResponse);
        assertEquals("M083E2635", updateRoomReservationResponse.getRoomReservation().getConfirmationNumber());
        assertNotNull(updateRoomReservationResponse.getRoomReservation().getBookings());
        assertNotNull(updateRoomReservationResponse.getRoomReservation().getChargesAndTaxes());
        assertNotNull(updateRoomReservationResponse.getRoomReservation().getDepositDetails());
        assertNotNull(updateRoomReservationResponse.getRoomReservation().getRatesSummary());
    }
    
    @Test
    public void testRoomReservationRefundCommitErrorResponse_Invalid_JWT() throws Exception {
        final String payload = "{\"message\":\"JWT is not valid, Please provide a valid Token\"}";

        new Expectations() {
            {
                service.put(anyString, (Class<RefundCommitPutRequest>) any, "room-reservation-refund-commit", "room-reservation-refund-commit", (HttpHeader[]) any);
                result = new HttpFailureException(401, payload, "Error while calling http endpoint");
            }
        };

        RefundCommitPutRequest refundCommitPutRequest = createRoomReservationRefundCommitRequest();

        try {
            roomBookingAccess.refundCommitRoomReservation(refundCommitPutRequest);
        } catch (SourceAppException e) {
            assertEquals(e.getSourceMessage(), payload);
        }
    }
    
    @Test
    public void testRoomReservationRefundCommitErrorResponse_Null() throws Exception {

        new Expectations() {
            {
                service.put(anyString, (Class<RefundCommitPutRequest>) any, "room-reservation-refund-commit", "room-reservation-refund-commit", (HttpHeader[]) any);
                result = null;
            }
        };

        RefundCommitPutRequest refundCommitPutRequest = createRoomReservationRefundCommitRequest();

        assertThrows(AppException.class, () -> roomBookingAccess.refundCommitRoomReservation(refundCommitPutRequest));
    }
    
    @Test
    public void testRoomReservationRefundCommitErrorResponse_BackendServerError() throws Exception {
        new Expectations() {
            {
                service.put(anyString, (Class<RefundCommitPutRequest>) any, "room-reservation-refund-commit", "room-reservation-refund-commit", (HttpHeader[]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(reservationRefundCommitBackedErrorResponse_BackendServerError()), "failed", new String[] { "header" });
            }
        };

        RefundCommitPutRequest refundCommitPutRequest = createRoomReservationRefundCommitRequest();
        assertThrows(SourceAppException.class, () -> roomBookingAccess.refundCommitRoomReservation(refundCommitPutRequest));
    }
    
    private RefundCommitPutRequest createRoomReservationRefundCommitRequest() {
        RefundCommitPutRequest request = new RefundCommitPutRequest();
        request.setConfirmationNumber("M083E2631");
        request.setFirstName("Raman");
        request.lastName("Lamba");
        
        RoomBillingDetails rb = new RoomBillingDetails();
        RoomBillingDetailsPayment payment = new RoomBillingDetailsPayment();
        RoomBillingDetailsAddress address = new RoomBillingDetailsAddress();
        payment.setAmount(BigDecimal.valueOf( 100.0));
        payment.setFirstName("John");
        payment.setLastName("Doe");
        payment.setCardHolder("John Doe");
        payment.setCcToken("123456789");
        payment.setExpiry("12/2028");
        rb.setPayment(payment);
        address.setStreet1("123 Main St");
        address.setCity("Test City");
        address.setState("TT");
        address.setCountry("US");
        rb.setAddress(address);
        return request;
    }
    
    private ErrorResponse reservationRefundCommitBackedErrorResponse_BackendServerError() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("632-3-101");
        error.setMessage("_system_error");
        responseError.setError(error);
        return responseError;
    }

    @Test
    public void testCancelRoomReservationSuccess() throws Exception {
        new Expectations() {
            {
                service.post(anyString, (Class<CancelRoomReservationV3Request>) any, "room-reservation-cancel", "room-reservation-cancel", (HttpHeader[]) any);
                result = Utils.readFileFromClassPath("data/cancel_room_reservation_response.json");
            }
        };

        CancelRoomReservationV3Request cancelRoomReservationV3Request = cancelRoomReservationRequest();
        CancelRoomReservationResponse cancelRoomReservationResponse = roomBookingAccess.cancelRoomReservation(cancelRoomReservationV3Request);

        assertNotNull(cancelRoomReservationResponse);
        assertEquals("M08407982", cancelRoomReservationResponse.getRoomReservation().getConfirmationNumber());
        assertEquals("abcd", cancelRoomReservationResponse.getRoomReservation().getProfile().getFirstName());
        assertEquals("defg", cancelRoomReservationResponse.getRoomReservation().getProfile().getLastName());
        assertEquals(RoomReservationResponse.StateEnum.CANCELLED, cancelRoomReservationResponse.getRoomReservation().getState());
        assertNotNull(cancelRoomReservationResponse.getRoomReservation().getBookings());
        assertNotNull(cancelRoomReservationResponse.getRoomReservation().getChargesAndTaxes());
        assertNotNull(cancelRoomReservationResponse.getRoomReservation().getDepositDetails());
        assertNotNull(cancelRoomReservationResponse.getRoomReservation().getRatesSummary());
    }

    @Test
    public void testCancelRoomReservationErrorResponse_Null() throws Exception {

        new Expectations() {
            {
                service.post(anyString, (Class<CancelRoomReservationV3Request>) any, "room-reservation-cancel", "room-reservation-cancel", (HttpHeader[]) any);
                result = null;
            }
        };

        CancelRoomReservationV3Request cancelRoomReservationV3Request = cancelRoomReservationRequest();

        assertThrows(AppException.class, () -> roomBookingAccess.cancelRoomReservation(cancelRoomReservationV3Request));
    }

    @Test
    public void testCancelRoomReservationErrorResponse_BackendServerError() throws Exception {
        new Expectations() {
            {
                service.post(anyString, (Class<CancelRoomReservationV3Request>) any, "room-reservation-cancel", "room-reservation-cancel", (HttpHeader[]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(reservationPreviewBackedErrorResponse_BackendServerError()), "failed", new String[] { "header" });
            }
        };

        CancelRoomReservationV3Request cancelRoomReservationV3Request = cancelRoomReservationRequest();
        assertThrows(AppException.class, () -> roomBookingAccess.cancelRoomReservation(cancelRoomReservationV3Request));
    }

    private CancelRoomReservationV3Request cancelRoomReservationRequest() {
        CancelRoomReservationV3Request request = new CancelRoomReservationV3Request();
        request.setConfirmationNumber("M08407982");
        request.setFirstName("abcd");
        request.lastName("defg");
        CancelRoomReservationResponse cancelRoomReservationResponse = new CancelRoomReservationResponse();
        RoomReservationResponse resp = new RoomReservationResponse();
        resp.setState(RoomReservationResponse.StateEnum.CANCELLED);
        cancelRoomReservationResponse.setRoomReservation(resp);
        return request;
    }
    
    @Test
    public void testRoomReservationReleaseSuccess() throws Exception {
        String propertyId = "prop1";
        String confNum = "conf1";
        String holdId = null;
        boolean f1Package = false;
        new Expectations() {
            {
                service.delete(anyString, null, "room-reservation-release", "room-reservation-release", (List<HttpHeader>) any, (String[][]) any);
            }
        };
        assertTrue(roomBookingAccess.releaseRoomReservation(propertyId, confNum, holdId, f1Package));
    }
    
    @Test
    public void testRoomReservationReleaseNotFoundError() throws Exception {
        String propertyId = "prop1";
        String confNum = "conf1";
        String holdId = null;
        boolean f1Package = false;
        new Expectations() {
            {
                service.delete(anyString, null, "room-reservation-release", "room-reservation-release", (List<HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(reservationReleaseErrorResponse()), "failed", new String[] { "header" });
            }
        };
        assertThrows(SourceAppException.class, () -> roomBookingAccess.releaseRoomReservation(propertyId, confNum, holdId, f1Package));
    }
    
    @Test
    public void testRoomReservationReleaseError() throws Exception {
        String propertyId = "prop1";
        String confNum = "conf1";
        String holdId = null;
        boolean f1Package = false;
        new Expectations() {
            {
                ErrorResponse er = reservationReleaseErrorResponse();
                er.getError().setCode("632-1-140");
                service.delete(anyString, null, "room-reservation-release", "room-reservation-release", (List<HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(er), "failed", new String[] { "header" });
            }
        };
        assertThrows(SourceAppException.class, () -> roomBookingAccess.releaseRoomReservation(propertyId, confNum, holdId, f1Package));
    }
    
    @Test
    public void testRoomReservationReleaseUnexpectedError() throws Exception {
        String propertyId = "prop1";
        String confNum = "conf1";
        String holdId = null;
        boolean f1Package = false;
        new Expectations() {
            {
                service.delete(anyString, null, "room-reservation-release", "room-reservation-release", (List<HttpHeader>) any, (String[][]) any);
                result = new NullPointerException();
            }
        };
        assertThrows(AppException.class, () -> roomBookingAccess.releaseRoomReservation(propertyId, confNum, holdId, f1Package));
    }
    
    @Test
    public void testRoomReservationF1HoldReleaseSuccess() throws Exception {
        String propertyId = "prop1";
        String confNum = "conf1";
        String holdId = "1234";
        boolean f1Package = true;
        new Expectations() {
            {
                service.delete(anyString, null, "room-reservation-release", "room-reservation-release", (List<HttpHeader>) any, (String[][]) any);
            }
        };
        assertTrue(roomBookingAccess.releaseRoomReservation(propertyId, confNum, holdId, f1Package));
    }
    
    @Test
    public void testRoomReservationF1HoldNotFoundError() throws Exception {
        String propertyId = "prop1";
        String confNum = "conf1";
        String holdId = "1234";
        boolean f1Package = true;
        new Expectations() {
            {
                service.delete(anyString, null, "room-reservation-release", "room-reservation-release", (List<HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(reservationReleaseErrorResponse()), "failed", new String[] { "header" });
            }
        };
        assertThrows(SourceAppException.class, () -> roomBookingAccess.releaseRoomReservation(propertyId, confNum, holdId, f1Package));
    }
    
    private ErrorResponse reservationReleaseErrorResponse() {
        ErrorResponse responseError = new ErrorResponse();
        ErrorResponseError error = new ErrorResponseError();
        error.setCode("632-2-140");
        error.setMessage("Reservation not found");
        responseError.setError(error);
        return responseError;
    }
}
