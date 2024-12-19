package com.mgmresorts.order.function;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.mgmresorts.common.dto.services.InHeaderSupport;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.function.FunctionHandler;
import com.mgmresorts.common.lambda.Worker;
import com.mgmresorts.common.openapi.Doc;
import com.mgmresorts.common.openapi.DocParams;
import com.mgmresorts.common.openapi.DocParams.Param;
import com.mgmresorts.common.openapi.DocParams.Type;
import com.mgmresorts.common.openapi.DocResult;
import com.mgmresorts.common.registry.FunctionRegistry;
import com.mgmresorts.common.security.IAuthorizer;
import com.mgmresorts.common.security.role.MGMRole;
import com.mgmresorts.common.security.role.ServiceRole;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.handler.ILogHandler;
import com.mgmresorts.order.core.InjectionContext;
import com.mgmresorts.order.dto.services.CancelReservationRequest;
import com.mgmresorts.order.dto.services.CancelReservationResponse;
import com.mgmresorts.order.dto.services.PreviewReservationRequest;
import com.mgmresorts.order.dto.services.PreviewReservationResponse;
import com.mgmresorts.order.dto.services.ReservationType;
import com.mgmresorts.order.dto.services.RetrieveReservationResponse;
import com.mgmresorts.order.dto.services.UpdateReservationRequest;
import com.mgmresorts.order.dto.services.UpdateReservationResponse;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.IReservationService;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

public class ReservationFunctions {

    @MGMRole({ MGMRole.Role.ANNONYMOUS, MGMRole.Role.GUEST, MGMRole.Role.SERVICE })
    @ServiceRole(ServiceRole.Role.READ_WRITE)
    @FunctionName(IFunctions.NAME_RESERVATION_RETRIEVE)
    @Doc(readme = "This service operation retrieves a reservation from the booking system along with creating a payment session.", //
            response = RetrieveReservationResponse.class, specificationType = "apigee")
    @DocParams({
            @Param(name = "confirmationNumber", type = Type.Query, required = true, description = "<p>The confirmationNumber that need to be retrieved</p>"),
            @Param(name = "firstName", type = Type.Query, required = true, description = "<p>The first name of the customer</p>"),
            @Param(name = "lastName", type = Type.Query, required = true, description = "<p>The last name of the customer</p>"),
            @Param(name = "reservationType", type = Type.Query, required = true, description = "<p>The type of reservation, possible values are ROOM, SHOW, DINING</p>"),
            @Param(name = "createPaymentSession", type = Type.Query, required = false, description = "<p>Flag to indicate whether to create a payment session, default=false</p>"),
            @Param(name = "paymentSessionId", type = Type.Query, required = false, description = "<p>The payment session id if there was one already created</p>") })
    @DocResult({ Errors.NO_RESERVATION_FOUND, Errors.UNEXPECTED_EXCEPTION_DURING_GET_RESERVATION })
    public HttpResponseMessage getReservation(//
            @HttpTrigger(name = "req", methods = HttpMethod.GET, authLevel = AuthorizationLevel.ANONYMOUS, route = IFunctions.URL_RESERVATION_RETRIEVE) //
            final HttpRequestMessage<Optional<String>> request, //
            final ExecutionContext context) {
        final InjectionContext injection = InjectionContext.get();
        final String confirmationNumber = request.getQueryParameters().get("confirmationNumber");
        final String firstName = request.getQueryParameters().get("firstName");
        final String lastName = request.getQueryParameters().get("lastName");
        final String reservationType = request.getQueryParameters().get("reservationType");
        final String createPaymentSession =  request.getQueryParameters().get("createPaymentSession");
        final String paymentSessionId = request.getQueryParameters().get("paymentSessionId");
        final Worker<InHeaderSupport, RetrieveReservationResponse> work = (couldBeNull) -> injection
                .instanceOf(IReservationService.class)
                .getReservation(confirmationNumber, firstName, lastName, ReservationType.fromValue(reservationType),
                        StringUtils.isNotBlank(createPaymentSession) ? Boolean.parseBoolean(createPaymentSession) : false, paymentSessionId);
        final IAuthorizer authorizer = injection.instanceOf(IAuthorizer.class);
        return new FunctionHandler<InHeaderSupport, RetrieveReservationResponse>().handle(context, request, work,
                authorizer, RetrieveReservationResponse.class,
                injection.instanceOf(ILogHandler.class).getRequestParamsLogDetails(request));
    }

    @MGMRole({ MGMRole.Role.ANNONYMOUS, MGMRole.Role.GUEST, MGMRole.Role.SERVICE })
    @ServiceRole(ServiceRole.Role.READ_WRITE)
    @FunctionName(IFunctions.NAME_RESERVATION_PREVIEW)
    @Doc(readme = "This service operation gets a preview of the reservation for modify reservation flow from web channel and updates the payment session.", //
            response = PreviewReservationResponse.class, request = PreviewReservationRequest.class, specificationType = "apigee")
    @DocResult({ Errors.INVALID_REQUEST, Errors.INVALID_PAYLOAD, Errors.UNABLE_TO_GET_RESERVATION_PREVIEW,
            Errors.UNABLE_TO_UPDATE_PAYMENT_SESSION, Errors.UNEXPECTED_EXCEPTION_DURING_RESERVATION_PREVIEW })
    public HttpResponseMessage previewReservation(//
            @HttpTrigger(name = "req", methods = HttpMethod.PUT, authLevel = AuthorizationLevel.ANONYMOUS, route = IFunctions.URL_RESERVATION_PREVIEW) //
            final HttpRequestMessage<Optional<String>> request, //
            final ExecutionContext context) {
        final InjectionContext injection = InjectionContext.get();
        final Worker<PreviewReservationRequest, PreviewReservationResponse> work = (payload) -> injection
                .instanceOf(IReservationService.class).previewReservation(payload);
        final IAuthorizer authorizer = injection.instanceOf(IAuthorizer.class);
        return new FunctionHandler<PreviewReservationRequest, PreviewReservationResponse>().handle(context, request,
                work, authorizer, PreviewReservationRequest.class, PreviewReservationResponse.class,
                injection.instanceOf(ILogHandler.class).getRequestParamsLogDetails(request));
    }

    @MGMRole({ MGMRole.Role.ANNONYMOUS, MGMRole.Role.GUEST, MGMRole.Role.SERVICE })
    @ServiceRole(ServiceRole.Role.READ_WRITE)
    @FunctionName(IFunctions.NAME_RESERVATION_UPDATE)
    @Doc(readme = "This service operation updates the reservation in the booking system for modify reservation flow from web channel.", //
            response = UpdateReservationResponse.class, request = UpdateReservationRequest.class, specificationType = "apigee")
    @DocResult({ Errors.INVALID_REQUEST_INFORMATION, Errors.UNABLE_TO_GET_PAYMENT_SESSION,
            Errors.UNABLE_TO_UPDATE_ROOM_RESERVATION, Errors.UNABLE_TO_UPDATE_PAYMENT_SESSION })
    public HttpResponseMessage updateReservation(//
            @HttpTrigger(name = "req", methods = HttpMethod.PUT, authLevel = AuthorizationLevel.ANONYMOUS, route = IFunctions.URL_RESERVATION_UPDATE) //
            final HttpRequestMessage<Optional<String>> request, //
            final ExecutionContext context) {
        final InjectionContext injection = InjectionContext.get();
        final Worker<UpdateReservationRequest, UpdateReservationResponse> work = (payload) -> injection
                .instanceOf(IReservationService.class).updateReservation(payload);
        final IAuthorizer authorizer = injection.instanceOf(IAuthorizer.class);
        return new FunctionHandler<UpdateReservationRequest, UpdateReservationResponse>().handle(context, request, work,
                authorizer, UpdateReservationRequest.class, UpdateReservationResponse.class,
                injection.instanceOf(ILogHandler.class).getRequestParamsLogDetails(request));
    }
    
    @MGMRole({ MGMRole.Role.ANNONYMOUS, MGMRole.Role.GUEST, MGMRole.Role.SERVICE })
    @ServiceRole(ServiceRole.Role.READ_WRITE)
    @FunctionName(IFunctions.NAME_RESERVATION_CANCEL)
    @Doc(readme = "This service operation cancels a reservation from the booking system and refunds the customer.", //
            response = CancelReservationResponse.class, request = CancelReservationRequest.class, specificationType = "apigee")
    @DocResult({ Errors.INVALID_REQUEST, Errors.INVALID_PAYLOAD, Errors.UNABLE_TO_CANCEL_ROOM_RESERVATION,
            Errors.UNABLE_TO_GET_PAYMENT_SESSION, Errors.UNEXPECTED_EXCEPTION_DURING_RESERVATION_CANCEL })
    public HttpResponseMessage cancelReservation(//
            @HttpTrigger(name = "req", methods = HttpMethod.POST, authLevel = AuthorizationLevel.ANONYMOUS, route = IFunctions.URL_RESERVATION_CANCEL) //
            final HttpRequestMessage<Optional<String>> request, //
            final ExecutionContext context) {
        final InjectionContext injection = InjectionContext.get();
        final Worker<CancelReservationRequest, CancelReservationResponse> work = (payload) -> injection
                .instanceOf(IReservationService.class).cancelReservation(payload);
        final IAuthorizer authorizer = injection.instanceOf(IAuthorizer.class);
        return new FunctionHandler<CancelReservationRequest, CancelReservationResponse>().handle(context, request, work,
                authorizer, CancelReservationRequest.class, CancelReservationResponse.class,
                injection.instanceOf(ILogHandler.class).getRequestParamsLogDetails(request));
    }

    public static void main(String[] args) throws AppException, Exception {
        System.setProperty("runtime.environment", "local");
        String token = "";
        String s = Utils.readAllBytes("data/update_room_reservation.json");
        final HttpResponseMessage call = FunctionRegistry.getRegistry().call(IFunctions.NAME_RESERVATION_UPDATE,
                IFunctions.URL_RESERVATION_UPDATE, s, new String[] { "authorization", "bearer " + token },
                new String[] { "x-mgm-source", "mgmri" }, new String[] { "x-mgm-channel", "web" },
                new String[] { "x-mgm-transaction-id", "783b2d20-99ef-4c3f-84a6-19b1c3d1dba1" });
        System.out.println(call.getBody());
        System.exit(-1);
    }
}
