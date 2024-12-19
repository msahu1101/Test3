package com.mgmresorts.order.function;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.mgmresorts.common.dto.services.InHeaderSupport;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.function.EventHandler;
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
import com.mgmresorts.common.security.scope.Scope;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.event.dto.OrderServiceEvent;
import com.mgmresorts.order.backend.handler.ILogHandler;
import com.mgmresorts.order.core.EnvironmentSetup;
import com.mgmresorts.order.core.ICartRole;
import com.mgmresorts.order.core.InjectionContext;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.CheckoutResponse;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.IOrderEventingService;
import com.mgmresorts.order.service.IOrderService;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

@SuppressWarnings("deprecation")
public class OrderFunctions {
    
    @Scope(ICartRole.CHECKOUT)
    @MGMRole({MGMRole.Role.ANNONYMOUS, MGMRole.Role.GUEST, MGMRole.Role.SERVICE})
    @ServiceRole(ServiceRole.Role.READ_WRITE)
    @FunctionName(IFunctions.NAME_CHECKOUT)
    @Doc(readme = "This service operation fulfills the cart. All the items in the cart will be processed for booking.", //
            response = CheckoutResponse.class, request = CheckoutRequest.class, specificationType = "apigee")
    @DocResult({ Errors.NO_CART_FOUND, Errors.EMPTY_CART, Errors.INVALID_REQUEST, Errors.INVALID_PAYLOAD, Errors.INSUFFICIENT_CHARGE_AMOUNT,
            Errors.UNABLE_TO_CREATE_CUSTOMER_PROFILE, Errors.UNABLE_TO_CREATE_ITINERARY, Errors.IDENTITY_SERVICE_FAILURE, Errors.UNABLE_TO_GET_CART, Errors.UNABLE_TO_BOOK_ROOM })
    public HttpResponseMessage checkout(//
            @HttpTrigger(name = "req", methods = HttpMethod.POST, authLevel = AuthorizationLevel.ANONYMOUS, route = IFunctions.URL_CHECKOUT) //
            final HttpRequestMessage<Optional<String>> request, //
            final ExecutionContext context) {
        final InjectionContext injection = InjectionContext.get();
        final Worker<CheckoutRequest, CheckoutResponse> work = (payload) -> injection.instanceOf(IOrderService.class).checkout(payload);
        final IAuthorizer authorizer = injection.instanceOf(IAuthorizer.class);
        return new FunctionHandler<CheckoutRequest, CheckoutResponse>().handle(context, request, work, authorizer, CheckoutRequest.class, CheckoutResponse.class,
                injection.instanceOf(ILogHandler.class).getRequestParamsLogDetails(request));
    }

    @Scope({ICartRole.READ, ICartRole.CHECKOUT})
    @MGMRole({MGMRole.Role.ANNONYMOUS, MGMRole.Role.GUEST, MGMRole.Role.SERVICE})
    @ServiceRole(ServiceRole.Role.READ_WRITE)
    @FunctionName(IFunctions.NAME_ORDER_READ)
    @Doc(readme = "This service operation retrieves a order information along with all the products associated with the cart.", //
            response = CheckoutResponse.class, specificationType = "apigee")
    @DocParams({
            @Param(name = "orderId", type = Type.Query, required = true, description = "<p>The orderId that need to be retrieved</p>"),
            @Param(name = "cartType", type = Type.Query, required = false, description = "<p>The type of cart; possible values are GLOBAL or PACKAGE. If nothing is passed"
                    + ", the type will be considered as GLOBAL</p>"),
            @Param(name = "cartVersion", type = Type.Query, required = false, description = "<p>The version of the cart; possible values are V1 or V2. If nothing is passed"
                    + ", the version will be considered as V1</p>"),
            @Param(name = "encryptedEmailAddress", type = Type.Query, required = false, description = "<p>The encrypted email address of the user</p>")})
    @DocResult(Errors.NO_ORDER_FOUND)
    public HttpResponseMessage readOrder(//
            @HttpTrigger(name = "req", methods = HttpMethod.GET, authLevel = AuthorizationLevel.ANONYMOUS, route = IFunctions.URL_ORDER_READ) //
            final HttpRequestMessage<Optional<String>> request, //
            final ExecutionContext context) {
        final InjectionContext injection = InjectionContext.get();
        final String orderId = request.getQueryParameters().get("orderId");
        final String cartType = request.getQueryParameters().get("cartType");
        final String cartVersion = request.getQueryParameters().get("cartVersion");
        final String encryptedEmailAddress = request.getQueryParameters().get("encryptedEmailAddress");
        final Worker<InHeaderSupport, CheckoutResponse> work = (couldBeNull) -> injection
                .instanceOf(IOrderService.class).read(orderId,
                        StringUtils.isNotEmpty(cartType) ? com.mgmresorts.order.dto.services.Type.fromValue(cartType)
                                : com.mgmresorts.order.dto.services.Type.GLOBAL,
                        StringUtils.isNotEmpty(cartVersion) ? com.mgmresorts.order.dto.services.Version.fromValue(cartVersion)
                                : com.mgmresorts.order.dto.services.Version.V1,
                        encryptedEmailAddress);
        final IAuthorizer authorizer = injection.instanceOf(IAuthorizer.class);
        return new FunctionHandler<InHeaderSupport, CheckoutResponse>().handle(context, request, work, authorizer, CheckoutResponse.class,
                injection.instanceOf(ILogHandler.class).getRequestParamsLogDetails(orderId, null, request));
    }

    @FunctionName(IFunctions.ORDER_EVENT_CONSUMER)
    public void orderEventListener(@EventGridTrigger(name = "event") String content, final ExecutionContext context) throws AppException {
        new EventHandler<OrderServiceEvent>().apply(context, payload -> InjectionContext.get().instanceOf(IOrderEventingService.class).orderEventListener(payload),
                OrderServiceEvent.class, content);
    }

    public static void main(String[] args) throws AppException, Exception {
        System.setProperty("runtime.environment", "local");
        EnvironmentSetup.init();
        ThreadContext.getContext().get().getJwt().getClaims().put("com.mgm.gse.id", "test");
        String authToken = ThreadContext.getContext().get().getJwt().getToken();
        String s = Utils.readAllBytes("data/checkout_room.json");
        final HttpResponseMessage call = FunctionRegistry.getRegistry().call(IFunctions.NAME_CHECKOUT, IFunctions.URL_CHECKOUT, s,
                new String[] { "authorization", "bearer " + authToken },
                new String[] { "x-mgm-source", "mgmri" },
                new String[] { "x-mgm-channel", "web" },
                new String[] { "x-mgm-transaction-id", "483b2d20-99ef-4c3f-84n6-19b1c3d1dba8" },
                new String[] { "x-mgm-correlation-id", "283b2d20-99ef-4h3f-84n6-19b1c3d1dba5" });
        System.out.println(call.getBody());
        System.exit(-1);
    }
}
