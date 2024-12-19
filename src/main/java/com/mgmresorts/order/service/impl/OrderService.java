package com.mgmresorts.order.service.impl;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.mgmresorts.profile.dto.customer.Customer;
import com.mgmresorts.rtc.RtcReservationEvent;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;

import com.mgmresorts.common.concurrent.ChainExecutor;
import com.mgmresorts.common.concurrent.Executor;
import com.mgmresorts.common.concurrent.Executors;
import com.mgmresorts.common.concurrent.Pool;
import com.mgmresorts.common.concurrent.Result;
import com.mgmresorts.common.concurrent.Task;
import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.crypto.SecurityFactory;
import com.mgmresorts.common.dto.Message;
import com.mgmresorts.common.dto.Status.Code;
import com.mgmresorts.common.dto.services.OutHeaderSupport;
import com.mgmresorts.common.errors.HttpStatus;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.event.enterprise.publish.EnableEnterpriseFailureEvent;
import com.mgmresorts.common.event.enterprise.publish.EnableEnterpriseSuccessEvent;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.AppRuntimeException;
import com.mgmresorts.common.function.HeaderBuilder;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.security.Jwts;
import com.mgmresorts.common.security.Jwts.Claim;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.ThreadContext.TransactionContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.content.model.PackageConfig;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IContentAccess;
import com.mgmresorts.order.backend.access.IRTCAccess;
import com.mgmresorts.order.backend.handler.ICartHandler;
import com.mgmresorts.order.backend.handler.IItineraryHandler;
import com.mgmresorts.order.backend.handler.IPaymentHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionCommonHandler;
import com.mgmresorts.order.backend.handler.IProfileHandler;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.database.access.IOrderConfirmationAccess;
import com.mgmresorts.order.database.access.IOrderProgressAccess;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.CheckoutResponse;
import com.mgmresorts.order.dto.services.Order;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.OrderLineItem.Status;
import com.mgmresorts.order.entity.LineItem;
import com.mgmresorts.order.entity.OrderConfirmationMapping;
import com.mgmresorts.order.entity.OrderEvent;
import com.mgmresorts.order.entity.OrderStatus;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.logging.OrderFinancialImpact;
import com.mgmresorts.order.service.IEventType;
import com.mgmresorts.order.service.IOrderService;
import com.mgmresorts.order.service.task.OrderTaskFactory;
import com.mgmresorts.order.service.transformer.OrderCheckoutEventTransformer;
import com.mgmresorts.order.utils.Orders;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.CartType;
import com.mgmresorts.shopping.cart.dto.CartVersion;
import com.mgmresorts.shopping.cart.dto.GuestProfile;
import com.mgmresorts.shopping.cart.dto.ItemType;

public class OrderService implements IOrderService {
    private final Logger logger = Logger.get(OrderService.class);
    private static final int EXECUTION_TIMEOUT_PACKAGE = Runtime.get().getInt("order.package.pool.timeout.seconds", 25);
    private static final int EXECUTION_TIMEOUT_PROGRESSIVE = Runtime.get().getInt("order.common.pool.progressive.timeout.seconds", 22);
    private static final int EXECUTION_TIMEOUT_NON_PACKAGE = Runtime.get().getInt("order.non.package.pool.timeout.seconds", 120);
    private static final boolean PAYMENT_METHOD_VALIDATION_ENABLED = Boolean.parseBoolean(Runtime.get().getConfiguration("payment.method.validation.enabled"));
    private static final boolean ASYNC_ENABLED = Boolean.parseBoolean(Runtime.get().getConfiguration("async.enabled"));
    private static final String ORDER_PACKAGE_POOL = "order.package.pool";
    private static final String ORDER_NON_PACKAGE_POOL = "order.non.package.pool";
    private static final String ORDER_OTHER_SUB_POOL = "order.other.sub.pool";
    private static final String CART_NOT_FOUND_EXCEPTION_CODE = "123-1-1016";

    private final JSonMapper mapper = new JSonMapper();
    
    @Inject
    private ICartHandler cartHandler;
    @Inject
    private IOrderAccess orderAccess;
    @Inject
    private IOrderProgressAccess progressAccess;
    @Inject
    private IOrderConfirmationAccess orderConfirmationAccess;
    @Inject
    private ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer;
    @Inject
    private ITransformer<Order, OrderFinancialImpact> orderFiTransformer;
    @Inject
    private ITransformer<OrderEvent, RtcReservationEvent> orderCheckoutEmailEventTransformer;
    @Inject
    private OrderTaskFactory factory;
    @Inject
    private Executors executors;
    @Inject
    private IProfileHandler profileHandler;
    @Inject
    private IItineraryHandler itineraryHandler;
    @Inject
    private IContentAccess contentAccess;
    @Inject
    private Orders orders;
    @Inject
    private IPaymentHandler paymentHandler;
    @Inject
    private IPaymentSessionCommonHandler paymentSessionCommonHandler;
    @Inject
    private IRTCAccess rtcAccess;

    @Override
    public CheckoutResponse checkout(CheckoutRequest request) throws AppException {
        return checkoutCart(request);
    }

    private CheckoutResponse checkoutCart(CheckoutRequest request) throws AppException {
        final long startTime = System.currentTimeMillis();
        final ZonedDateTime orderInitiatedAt = ZonedDateTime.now(ZoneOffset.UTC);
        validateCheckoutRequest(request);
        String cartId = request.getCartId();
        final String mgmId = request.getMgmId();

        final String itineraryId = request.getItineraryId();

        /*
         * Retrieving the cart and validating it. The cart should: exist and not be
         * empty.
         */
        Cart cart = null;
        
        try {
            cart = cartHandler.getCart(cartId, mgmId,
                    request.getCartType() != null ? request.getCartType() : com.mgmresorts.order.dto.services.Type.GLOBAL,
                    request.getCartVersion() != null ? request.getCartVersion() : com.mgmresorts.order.dto.services.Version.V1);
        } catch (AppException e) {
            if (e.getCause() instanceof HttpFailureException
                    && ((HttpFailureException) e.getCause()).getHttpCode() == HttpStatus.NOT_FOUND.value()
                    && StringUtils.isNotBlank(((HttpFailureException) e.getCause()).getPayload())) {
                String code = null;
                final OutHeaderSupport errorResponse = mapper.readValue(((HttpFailureException) e.getCause()).getPayload(), OutHeaderSupport.class);
                if (errorResponse != null && errorResponse.getHeader() != null
                        && errorResponse.getHeader().getStatus() != null && errorResponse.getHeader().getStatus().getCode() == Code.FAILURE
                        && !Utils.isEmpty(errorResponse.getHeader().getStatus().getMessages())) {
                    code = errorResponse.getHeader().getStatus().getMessages().stream().findFirst().get().getCode();
                }
                if (StringUtils.isNotBlank(cartId) && CART_NOT_FOUND_EXCEPTION_CODE.equalsIgnoreCase(code)) {
                    final com.mgmresorts.order.entity.Order orderEntity = orderAccess.getCheckedOutOrderByCartId(cartId,
                            request.getCartType() != null
                                    ? com.mgmresorts.order.entity.Type.fromValue(request.getCartType().value())
                                    : com.mgmresorts.order.entity.Type.GLOBAL,
                            request.getCartVersion() != null
                                    ? com.mgmresorts.order.entity.Version.fromValue(request.getCartVersion().value())
                                    : com.mgmresorts.order.entity.Version.V1);
                    if (orderEntity != null) {
                        final CheckoutResponse response = new CheckoutResponse();
                        response.setHeader(HeaderBuilder.buildHeader());
                        final Order out = orderTransformer.toLeft(orderEntity);
                        response.setOrder(out);
                        return response;
                    } else {
                        throw e;
                    }
                } else {
                    throw e;
                }
            } else {
                throw e;
            }
        }
        
        cartHandler.validateCartResponse(cart, cartId, mgmId);
        
        if (StringUtils.isBlank(cartId) || !cartId.equalsIgnoreCase(cart.getCartId())) {
            cartId = cart.getCartId();
        }
        
        validateJwbCart(cart, request);

        final boolean paymentRequired = cart.getPaymentRequired() != null ? cart.getPaymentRequired() : true;

        final boolean paymentCaptured = request.getPaymentCaptured() != null ? request.getPaymentCaptured() : false;
        
        if (paymentCaptured && StringUtils.isBlank(cart.getPaymentSessionId())) {
            logger.error("The cart payment sessionid was not found for cartId: {}", cart.getCartId());
            throw new AppException(Errors.CART_PAYMENT_SESSION_ID_NOT_FOUND, cart.getCartId());
        }
        
        final boolean skipPaymentCapture = paymentCaptured && paymentRequired;
                
        Map<String, PaymentAuthFields> paymentAuthFieldsMap = null;
        String orderReferenceNumber = null;
        PaymentSessionBaseFields paymentSessionBaseFields = null;

        if (skipPaymentCapture) {
            try {
                paymentSessionBaseFields = paymentSessionCommonHandler.getPaymentAuthResults(cart.getPaymentSessionId());
            } catch (AppException e) {
                logger.error("Retrieval of payment session failed for cartId: {}", cart.getCartId());
                logger.error("Error Message: " + e.getMessage());
                throw new AppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "Retrieval of payment session failed.");
            } catch (Exception e) {
                logger.error("Retrieval of payment session failed for cartId: {}", cart.getCartId());
                logger.error("Error Message: " + e.getMessage());
                throw new AppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "Retrieval of payment session failed.");
            }
                
            if (paymentSessionBaseFields ==  null) {
                throw new AppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "Payment session base details are not found in payment session.");
            }
            
            paymentAuthFieldsMap = paymentSessionBaseFields.getPaymentAuthFieldsMap();
            
            if (Utils.isEmpty(paymentAuthFieldsMap)) {
                throw new AppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "Payment auth fields are not found in the payment session.");
            }
            
            if (Utils.isEmpty(CommonConfig.getTokenMlifeNumber()) && paymentSessionBaseFields.getGuestProfile() != null) {
                request.setGuestProfile(paymentSessionBaseFields.getGuestProfile());
            }
            
            if (!Utils.isEmpty(paymentSessionBaseFields.getBillings())) {
                request.setBillings(paymentSessionBaseFields.getBillings());
            }
            
            if (!Utils.isEmpty(request.getBillings())) {
                request.getBillings().forEach(b -> {
                    if (request.getGuestProfile() != null) {
                        b.getPayment().setFirstName(request.getGuestProfile().getFirstName());
                        b.getPayment().setLastName(request.getGuestProfile().getLastName());
                    }
                });
            }
            orderReferenceNumber = paymentSessionBaseFields.getOrderReferenceNumber();
        }
        
        if (request.getGuestProfile() == null) {
            logger.error("The guest profile was null.");
            throw new AppException(Errors.INVALID_REQUEST, "The guest profile is required.");
        }
        
        if (Utils.isEmpty(request.getGuestProfile().getFirstName(), request.getGuestProfile().getLastName())) {
            logger.error("Either the first name or last name in the guest profile was missing.");
            throw new AppException(Errors.INVALID_REQUEST, "First name and last name are required fields in the guest profile.");
        }
        
        if (paymentRequired && Utils.isEmpty(request.getBillings())) {
            logger.error("The billings array was null or empty.");
            throw new AppException(Errors.INVALID_REQUEST, "At least one billing method is required.");
        }        
        
        final AppliedBillings billable = paymentRequired ? new AppliedBillings(request.getBillings()) : null;

        /* First step to check if cart is in grace period. */
        final long checkoutGracePeriod = Long.parseLong(Runtime.get().getConfiguration("order.checkout.grace.seconds"));
        final long jwbCheckoutGracePeriod = Long.parseLong(Runtime.get().getConfiguration("order.jwb.checkout.grace.seconds"));
        if (!cart.getIsCheckoutEligible()) {
            if (cart.getPriceExpiresAt() != null && cart.getPriceExpiresAt().plusSeconds(checkoutGracePeriod).isAfter(ZonedDateTime.now())
                    || (request.getEnableJwb() != null && !request.getEnableJwb() && cart.getPriceExpiresAt().plusSeconds(jwbCheckoutGracePeriod).isAfter(ZonedDateTime.now()))) {
                // cart in grace
                logger.info("Cart is ineligible for checkout but in grace period, continuing execution.");
                if (!Utils.isEmpty(cart.getCartLineItems())) {
                    if (cart.getCartLineItems().stream().anyMatch(
                            cartLineItem -> !(cartLineItem.getStatus().equals(CartLineItem.Status.SAVED) || cartLineItem.getStatus().equals(CartLineItem.Status.PRICE_EXPIRED)))) {
                        logger.error("Some cart items are in checkout ineligible states that is, other than saved or price expired.");
                        throw new AppException(ApplicationError.NO_CHECKOUT_ELIGIBLE_INELIGIBLE_ITEMS, cart.getCartId(), cart.getMgmId());
                    }
                }
            } else {
                logger.error("Cart is checkout ineligible and also past the grace period.");
                throw new AppException(ApplicationError.NO_CHECKOUT_ELIGIBLE);
            }
        }

        /*
         * validating that the package is still active in case of package cart
         */
        final boolean isPackage = cart.getType() == CartType.PACKAGE;
        final boolean isV1 = cart.getVersion() == CartVersion.V1;
        if (isPackage && isV1) {
            final Optional<PackageConfig[]> packageConfig = contentAccess.getPackageConfigDetails(cart.getCartLineItems().get(0).getPackageId());
            if (!packageConfig.isPresent() || (packageConfig.isPresent() && !packageConfig.get()[0].getActive())) {
                throw new AppException(ApplicationError.PACKAGE_NOT_ACTIVE);
            }
            validatePackageItems(cart.getCartLineItems());
        }

        /*
         * Validating that the billings can cover the deposit amount.
         */
        if (paymentRequired && cart.getPriceDetails() != null && cart.getPriceDetails().getTotalDeposit() != null
                && cart.getPriceDetails().getTotalDeposit() > billable.getTotalCharges()) {
            logger.error("The billing total charge amount" + billable.getTotalCharges() + " is less than the deposit " + cart.getPriceDetails().getTotalDeposit() + ".");
            throw new AppException(ApplicationError.INSUFFICIENT_CHARGE_AMOUNT);
        }
        
        boolean skipAFS = false; 

        if (PAYMENT_METHOD_VALIDATION_ENABLED && paymentRequired && !Utils.isEmpty(request.getBillings())) {
            skipAFS = paymentHandler.validatePaymentMethod(request);
        }
        
        Order order = null;
        
        boolean locked = false;
        try {
            locked = progressAccess.tryLock(cartId);
        } catch (AppException e) {
            if (e.getCode() == Errors.CHECKOUT_IN_PROGRESS) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                final com.mgmresorts.order.entity.Order orderEntity = orderAccess.getPendingOrderByCartId(cartId,
                        request.getCartType() != null
                                ? com.mgmresorts.order.entity.Type.fromValue(request.getCartType().value())
                                : com.mgmresorts.order.entity.Type.GLOBAL,
                        request.getCartVersion() != null
                                ? com.mgmresorts.order.entity.Version.fromValue(request.getCartVersion().value())
                                : com.mgmresorts.order.entity.Version.V1);
                if (orderEntity != null) {
                    final CheckoutResponse response = new CheckoutResponse();
                    response.setHeader(HeaderBuilder.buildHeader());
                    order = orderTransformer.toLeft(orderEntity);
                    response.setOrder(order);
                    return response;
                } else {
                    throw new AppException(Errors.CHECKOUT_IN_PROGRESS);
                }
            }
        }
        
        final List<CartLineItem> cartLineItems = cart.getCartLineItems();
        order = orders.createOrderWithPendingStatus(cartLineItems, cart, request);
        orders.calculateOrderPrice(order);
        storeOrderInformation(cart, order, request, false, orderInitiatedAt);
        
        /*
         * Checking whether the guest is anonymous or logged in based on the token's gse
         * id claim. If the customer's token doesn't have a gse id, a new profile will
         * be created.
         */

        if (Utils.isEmpty(CommonConfig.getTokenMlifeNumber())) {
            logger.info("No guest information in the token. A new profile will be created for this anonymous guest.");
            try {
                request.getGuestProfile().setId(profileHandler.createGuestProfile(request));
            } catch (AppException e) {
                progressAccess.release(cartId);
                orderAccess.delete(order.getId());
                throw e;
            }
        } else {
            if (!Utils.isEmpty(CommonConfig.getTokenGseId())) {
                request.getGuestProfile().setId(CommonConfig.getTokenGseId());
            }
            request.getGuestProfile().setMlifeNo(CommonConfig.getTokenMlifeNumber());
            setProfileFieldsFromClaimsIfPresent(request, null, true);
        }
        
        //set the guest's perpetual eligibility
        request.getGuestProfile().setPerpetualOfferEligible(CommonConfig.getTokenPerpetualFlag());

        /*
         * Checking whether the incoming request has itinerary id, if not then a new
         * itinerary will be created.
         */
        if (Utils.isEmpty(itineraryId)) {
            if (!Utils.isEmpty(request.getGuestProfile().getId())) {
                logger.info("No itinerary id in the request. A new itinerary will be created for this order.");
                try {
                    request.setItineraryId(itineraryHandler.create(request.getGuestProfile(), cart));
                } catch (AppException e) {
                    if (!Utils.isEmpty(CommonConfig.getTokenMlifeNumber()) && e.getCode() == ApplicationError.UNABLE_TO_CREATE_ITINERARY_INVALID_GSE_ID) {
                        try {
                            final Customer customer = profileHandler.getGuestProfile(CommonConfig.getTokenMlifeNumber());
                            if (customer.getExternalId() != null && customer.getProfile() != null && customer.getMemberships() != null
                                    && (customer.getMemberships().getIsActive() != null && customer.getMemberships().getIsActive())) {
                                request.getGuestProfile().setId(customer.getExternalId().getGseCustomerId());
                                request.getGuestProfile().setMlifeNo(customer.getExternalId().getPatronId());
                                setProfileFieldsFromClaimsIfPresent(request, customer, false);
                                request.setItineraryId(itineraryHandler.create(request.getGuestProfile(), cart));
                            } else {
                                progressAccess.release(cartId);
                                orderAccess.delete(order.getId());
                                throw e;
                            }
                        } catch (AppException ae) {
                            progressAccess.release(cartId);
                            orderAccess.delete(order.getId());
                            throw ae;
                        }
                    } else {
                        progressAccess.release(cartId);
                        orderAccess.delete(order.getId());
                        throw e;
                    }
                }
            } else {
                progressAccess.release(cartId);
                orderAccess.delete(order.getId());
                throw new AppException(ApplicationError.NO_GSE_ID_FOUND, request.getGuestProfile().getId() + "|" + request.getGuestProfile().getMlifeNo());
            }
        }
        
        order.setCustomerId(request.getGuestProfile().getId());
        
        if (!isPackage) {
            return processNonPackageOrder(locked, request, billable, cart, order, isPackage, startTime,
                    orderInitiatedAt, skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
        } else {
            if (isPackage && isV1) {
                return processPackageOrderV1(locked, request, billable, cart, order, isPackage, startTime,
                        orderInitiatedAt, skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
            } else {
                return processPackageOrderV2(locked, request, billable, cart, order, isPackage, startTime,
                        orderInitiatedAt, skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
            }
        }
    }

    private CheckoutResponse processPackageOrderV1(boolean locked, CheckoutRequest request, final AppliedBillings billable, final Cart cart, final Order order,
                                                   final boolean isPackage, final long startTime, final ZonedDateTime orderInitiatedAt,
                                                   final boolean skipAFS, final boolean skipPaymentCapture, final Map<String, PaymentAuthFields> paymentAuthFieldsMap,
                                                   final String orderReferenceNumber) throws AppException {
        final int timeoutValue = (request.getProgressiveCheckout() != null && request.getProgressiveCheckout()) ? EXECUTION_TIMEOUT_PROGRESSIVE : EXECUTION_TIMEOUT_PACKAGE;
        final int processingTimeout = (int) (timeoutValue * 1000 - (System.currentTimeMillis() - startTime));
        final Executor executor = executors.get(Pool.getOrCreate(ORDER_PACKAGE_POOL));
        final TransactionContext transactionContext = ThreadContext.getContext().get().copy();
        final Result<CheckoutResponse> result = executor.invoke(new Task<CheckoutResponse>() {
            @Override
            protected CheckoutResponse execute() throws Exception {
                try {
                    ThreadContext.getContext().set(transactionContext);
                    return doAsyncPackageV1(request, cart, billable, order, orderInitiatedAt,
                            skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
                } finally {
                    if (locked) {
                        progressAccess.release(cart.getCartId());
                    }
                }
            }
        }, processingTimeout > 0 ? processingTimeout : 1);

        ThreadContext.getContext().get().setFinancialImpact(transactionContext.getFinancialImpact());

        if (result.getThrowable() != null && result.getThrowable() instanceof TimeoutException) {
            return createTimeoutResponse(order.getId(), order.getType(), order.getVersion(),
                    isPackage, cart, request, order.getEncryptedEmailAddress(), orderInitiatedAt);
        }
        return result.getOutput();
    }

    private CheckoutResponse doAsyncPackageV1(CheckoutRequest request, final Cart cart, final AppliedBillings billable,
                                            final Order order, final ZonedDateTime orderInitiatedAt, final boolean skipAFS,
                                            final boolean skipPaymentCapture, final Map<String, PaymentAuthFields> paymentAuthFieldsMap,
                                            final String orderReferenceNumber) throws AppException {

        final CheckoutResponse response = new CheckoutResponse();
        response.setHeader(HeaderBuilder.buildHeader());

        final CartLineItem roomCartLineItem = cart.getCartLineItems().stream().filter(e -> e.getType() == ItemType.ROOM).findAny().get();
        final OrderLineItem roomOrderLineItem = order.getOrderLineItems().stream()
                .filter(li -> roomCartLineItem.getCartLineItemId().equalsIgnoreCase(li.getCartLineItemId())).findFirst().get();

        final CartLineItem showCartLineItem = cart.getCartLineItems().stream().filter(e -> e.getType() == ItemType.SHOW).findAny().get();
        final OrderLineItem showOrderLineItem = order.getOrderLineItems().stream()
                .filter(li -> showCartLineItem.getCartLineItemId().equalsIgnoreCase(li.getCartLineItemId())).findFirst().get();

        try {
            factory.get(roomCartLineItem.getType()).checkout(request, roomCartLineItem, roomOrderLineItem, billable,
                    order.getId(), cart.getAgentInfo(), skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
        } catch (Exception e) {
            setResponseErrorMessage(response, e.getMessage());
        }

        if (roomOrderLineItem.getStatus() != Status.SUCCESS) {
            if (roomOrderLineItem.getStatus() == Status.PAYMENT_FAILURE) {
                showOrderLineItem.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
                order.setStatus(Order.Status.PAYMENT_FAILURE);
            } else {
                showOrderLineItem.setStatus(OrderLineItem.Status.FAILURE);
                order.setStatus(Order.Status.FAILURE);
            }
            if (skipPaymentCapture && (order.getStatus().equals(Order.Status.FAILURE) || order.getStatus().equals(Order.Status.PAYMENT_FAILURE))) {
                createPaymentSession(cart, order, request);
            }
            order.setCanRetryCheckout(true);
            orders.calculateOrderPrice(order);
            storeOrderInformation(cart, order, request, false, orderInitiatedAt);
            populateOrderFinancialImpact(cart, order);
            response.setOrder(order);

            return response;
        }

        orders.calculateOrderPrice(order);
        storeOrderInformation(cart, order, request, false, orderInitiatedAt);

        showCartLineItem.setConfirmationNumber(roomOrderLineItem.getConfirmationNumber());
        showCartLineItem.setOperaHotelCode(roomOrderLineItem.getOperaHotelCode());
        showCartLineItem.setOperaConfirmationNumber(roomOrderLineItem.getOperaConfirmationNumber());

        try {
            factory.get(showCartLineItem.getType()).checkout(request, showCartLineItem, showOrderLineItem, billable,
                    order.getId(), cart.getAgentInfo(), skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
        } catch (Exception e) {
            setResponseErrorMessage(response, e.getMessage());
        }

        final List<String> pendingProducts = order.getOrderLineItems().stream().filter(o -> OrderLineItem.Status.PENDING == o.getStatus()).map(item -> item.getCartLineItemId())
                .collect(Collectors.toList());
        if (ASYNC_ENABLED && request.getProgressiveCheckout() != null && request.getProgressiveCheckout() && !Utils.isEmpty(pendingProducts)) {
            order.setStatus(Order.Status.PENDING);
            order.setCanRetryCheckout(false);
            storeOrderInformation(cart, order, request, false, orderInitiatedAt);
            response.setOrder(order);
            return response;
        }

        final List<String> failedProducts = order.getOrderLineItems().stream().filter(o -> OrderLineItem.Status.SUCCESS != o.getStatus()).map(item -> item.getCartLineItemId())
                .collect(Collectors.toList());

        try {
            final String newCartId = cartHandler.handleCheckout(cart.getCartId(), new ArrayList<>(), request.getEnableJwb() != null ? request.getEnableJwb() : false);
            if (!Utils.isEmpty(newCartId)) {
                order.setNewCartId(newCartId);
            }
        } catch (AppException e) {
            logger.warn("Unable to create new cart for checkout failures {}", e.getMessage());
            setResponseWarningMessage(response, e.getDescription(), e.getDisplayCode());
        }

        if (failedProducts.size() == 0) {
            order.setStatus(Order.Status.SUCCESS);
            order.setCanRetryCheckout(false);
        } else {
            order.setStatus(Order.Status.PARTIAL);
            order.setCanRetryCheckout(false);
        }

        orders.calculateOrderPrice(order);
        if (ASYNC_ENABLED && request.getProgressiveCheckout() != null && request.getProgressiveCheckout()) {
            com.mgmresorts.order.entity.Order orderEntity = orderAccess.read(order.getId());
            storeOrderInformation(cart, order, request, orderEntity.isComplete(), orderInitiatedAt);
        } else {
            storeOrderInformation(cart, order, request, false, orderInitiatedAt);
        }
        populateOrderFinancialImpact(cart, order);
        toPublish(cart, order, request, orderInitiatedAt);
        response.setOrder(order);
        return response;
    }
    
    private CheckoutResponse processPackageOrderV2(boolean locked, CheckoutRequest request, final AppliedBillings billable, final Cart cart, final Order order,
                                                   final boolean isPackage, final long startTime, final ZonedDateTime orderInitiatedAt,
                                                   final boolean skipAFS, final boolean skipPaymentCapture, final Map<String, PaymentAuthFields> paymentAuthFieldsMap,
                                                   final String orderReferenceNumber) throws AppException {
        final int timeoutValue = (request.getProgressiveCheckout() != null && request.getProgressiveCheckout()) ? EXECUTION_TIMEOUT_PROGRESSIVE : EXECUTION_TIMEOUT_PACKAGE;
        final int processingTimeout = (int) (timeoutValue * 1000 - (System.currentTimeMillis() - startTime));
        final Executor executor = executors.get(Pool.getOrCreate(ORDER_PACKAGE_POOL));
        final TransactionContext transactionContext = ThreadContext.getContext().get().copy();
        final Result<CheckoutResponse> result = executor.invoke(new Task<CheckoutResponse>() {
            @Override
            protected CheckoutResponse execute() throws Exception {
                try {
                    ThreadContext.getContext().set(transactionContext);
                    return doAsyncPackageV2(request, cart, billable, order, orderInitiatedAt,
                            skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
                } finally {
                    if (locked) {
                        progressAccess.release(cart.getCartId());
                    }
                }
            }
        }, processingTimeout > 0 ? processingTimeout : 1);

        ThreadContext.getContext().get().setFinancialImpact(transactionContext.getFinancialImpact());

        if (result.getThrowable() instanceof TimeoutException) {
            return createTimeoutResponse(order.getId(), order.getType(), order.getVersion(),
                    isPackage, cart, request, order.getEncryptedEmailAddress(), orderInitiatedAt);
        }
        return result.getOutput();
    }

    private CheckoutResponse doAsyncPackageV2(CheckoutRequest request, final Cart cart, final AppliedBillings billable,
                                              final Order order, final ZonedDateTime orderInitiatedAt, final boolean skipAFS,
                                              final boolean skipPaymentCapture, final Map<String, PaymentAuthFields> paymentAuthFieldsMap,
                                              final String orderReferenceNumber) throws AppException {

        final CheckoutResponse response = new CheckoutResponse();
        response.setHeader(HeaderBuilder.buildHeader());

        final CartLineItem roomCartLineItem = cart.getCartLineItems().stream().filter(e -> e.getType() == ItemType.ROOM).findAny().get();
        final OrderLineItem roomOrderLineItem = order.getOrderLineItems().stream()
                .filter(li -> roomCartLineItem.getCartLineItemId().equalsIgnoreCase(li.getCartLineItemId())).findFirst().get();

        final CartLineItem showCartLineItem = cart.getCartLineItems().stream().filter(e -> e.getType() == ItemType.SHOW).findAny().get();
        final OrderLineItem showOrderLineItem = order.getOrderLineItems().stream()
                .filter(li -> showCartLineItem.getCartLineItemId().equalsIgnoreCase(li.getCartLineItemId())).findFirst().get();

        try {
            factory.get(roomCartLineItem.getType()).checkout(request, roomCartLineItem, roomOrderLineItem, billable,
                    order.getId(), cart.getAgentInfo(), skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
        } catch (Exception e) {
            setResponseErrorMessage(response, e.getMessage());
        }

        if (roomOrderLineItem.getStatus() != Status.SUCCESS) {
            if (roomOrderLineItem.getStatus() == Status.PAYMENT_FAILURE) {
                showOrderLineItem.setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
                order.setStatus(Order.Status.PAYMENT_FAILURE);
            } else {
                showOrderLineItem.setStatus(OrderLineItem.Status.FAILURE);
                order.setStatus(Order.Status.FAILURE);
            }
            if (skipPaymentCapture && (order.getStatus().equals(Order.Status.FAILURE) || order.getStatus().equals(Order.Status.PAYMENT_FAILURE))) {
                createPaymentSession(cart, order, request);
            }
            order.setCanRetryCheckout(true);
            orders.calculateOrderPrice(order);
            storeOrderInformation(cart, order, request, false, orderInitiatedAt);
            populateOrderFinancialImpact(cart, order);
            response.setOrder(order);

            return response;
        }

        orders.calculateOrderPrice(order);
        storeOrderInformation(cart, order, request, false, orderInitiatedAt);

        showCartLineItem.setConfirmationNumber(roomOrderLineItem.getConfirmationNumber());
        showCartLineItem.setOperaHotelCode(roomOrderLineItem.getOperaHotelCode());
        showCartLineItem.setOperaConfirmationNumber(roomOrderLineItem.getOperaConfirmationNumber());

        try {
            factory.get(showCartLineItem.getType()).checkout(request, showCartLineItem, showOrderLineItem, billable,
                    order.getId(), cart.getAgentInfo(), skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
        } catch (Exception e) {
            setResponseErrorMessage(response, e.getMessage());
        }

        final List<String> failedProducts = order.getOrderLineItems().stream().filter(o -> OrderLineItem.Status.SUCCESS != o.getStatus()).map(item -> item.getCartLineItemId())
                .collect(Collectors.toList());

        try {
            final String newCartId = cartHandler.handleCheckout(cart.getCartId(), new ArrayList<>(), request.getEnableJwb() != null ? request.getEnableJwb() : false);
            if (!Utils.isEmpty(newCartId)) {
                order.setNewCartId(newCartId);
            }
        } catch (AppException e) {
            logger.warn("Unable to create new cart for checkout failures {}", e.getMessage());
            setResponseWarningMessage(response, e.getDescription(), e.getDisplayCode());
        }

        if (Utils.isEmpty(failedProducts)) {
            order.setStatus(Order.Status.SUCCESS);
            order.setCanRetryCheckout(false);
        } else {
            order.setStatus(Order.Status.PARTIAL);
            order.setCanRetryCheckout(false);
        }

        orders.calculateOrderPrice(order);
        storeOrderInformation(cart, order, request, false, orderInitiatedAt);
        populateOrderFinancialImpact(cart, order);
        toPublish(cart, order, request, orderInitiatedAt);
        response.setOrder(order);
        return response;
    }

    private CheckoutResponse processNonPackageOrder(boolean locked, CheckoutRequest request, final AppliedBillings billable, final Cart cart, final Order order,
                                                    final boolean isPackage, final long startTime, final ZonedDateTime orderInitiatedAt,
                                                    final boolean skipAFS, final boolean skipPaymentCapture, final Map<String, PaymentAuthFields> paymentAuthFieldsMap,
                                                    final String orderReferenceNumber) throws AppException {
        final int timeoutValue = (request.getProgressiveCheckout() != null && request.getProgressiveCheckout()) ? EXECUTION_TIMEOUT_PROGRESSIVE : EXECUTION_TIMEOUT_NON_PACKAGE;
        final int processingTimeout = (int) (timeoutValue * 1000 - (System.currentTimeMillis() - startTime));
        final Executor executor = executors.get(Pool.getOrCreate(ORDER_NON_PACKAGE_POOL));
        final TransactionContext transactionContext = ThreadContext.getContext().get().copy();
        final Result<CheckoutResponse> result = executor.invoke(new Task<CheckoutResponse>() {
            @Override
            protected CheckoutResponse execute() throws Exception {
                try {
                    ThreadContext.getContext().set(transactionContext);
                    return doAsyncNonPackage(request, cart, billable, order, orderInitiatedAt, skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
                } finally {
                    if (locked) {
                        progressAccess.release(cart.getCartId());
                    }
                }
            }
        }, processingTimeout > 0 ? processingTimeout : 1);

        ThreadContext.getContext().get().setFinancialImpact(transactionContext.getFinancialImpact());
        
        if (result.getThrowable() != null && result.getThrowable() instanceof TimeoutException) {
            return createTimeoutResponse(order.getId(), order.getType(), order.getVersion(),
                    isPackage, cart, request, order.getEncryptedEmailAddress(), orderInitiatedAt);
        }
        return result.getOutput();
    }

    private CheckoutResponse doAsyncNonPackage(CheckoutRequest request, final Cart cart, final AppliedBillings billable, final Order pendingOrder, 
                                                final ZonedDateTime orderInitiatedAt, final boolean skipAFS, final boolean skipPaymentCapture, final Map<String,
                                                PaymentAuthFields> paymentAuthFieldsMap, final String orderReferenceNumber) throws AppException {

        final CheckoutResponse response = new CheckoutResponse();
        response.setHeader(HeaderBuilder.buildHeader());
        final List<Task<OrderLineItem>> showTasks = new ArrayList<>();
        final List<Task<?>> otherTasks = new ArrayList<>();
        Optional.ofNullable(cart.getCartLineItems()).ifPresent(items -> items.forEach(cartLineItem -> {
            final OrderLineItem orderLineItem = pendingOrder.getOrderLineItems().stream()
                    .filter(li -> cartLineItem.getCartLineItemId().equalsIgnoreCase(li.getCartLineItemId())).findFirst()
                    .get();
            if (cartLineItem.getType() == ItemType.SHOW) {
                showTasks.add(factory.create(request, pendingOrder.getId(), cart, cartLineItem, orderLineItem, billable,
                        skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber));
            } else {
                otherTasks.add(factory.create(request, pendingOrder.getId(), cart, cartLineItem, orderLineItem, billable,
                        skipAFS, skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber));
            }
        }));
        final List<Result<OrderLineItem>> results = ChainExecutor.of(-1, OrderLineItem.class)
                .registerSingle(showTasks)
                .register(ORDER_OTHER_SUB_POOL, otherTasks)
                .execute();
        for (Result<OrderLineItem> result : results) {
            if (result.getOutput() == null) {
                setResponseWarningMessage(response, result.getThrowable().getMessage(), null);
            }
        }
        final com.mgmresorts.order.entity.Order orderEntity = orderAccess.read(pendingOrder.getId());
        if (orderEntity == null) {
            throw new AppException(SystemError.REQUESTED_RESOURCE_NOT_FOUND);
        }
        final Order order = orderTransformer.toLeft(orderEntity);
        
        order.setCustomerId(pendingOrder.getCustomerId());

        final List<String> showProducts = order.getOrderLineItems().stream().filter(o -> o.getProductType() == OrderLineItem.ProductType.SHOW).map(item -> item.getCartLineItemId())
                .collect(Collectors.toList());
        final List<String> pendingProducts = order.getOrderLineItems().stream().filter(o -> OrderLineItem.Status.PENDING == o.getStatus()).map(item -> item.getCartLineItemId())
                .collect(Collectors.toList());
        if (ASYNC_ENABLED && request.getProgressiveCheckout() != null && request.getProgressiveCheckout() && !Utils.isEmpty(showProducts) && !Utils.isEmpty(pendingProducts)) {
            order.setStatus(Order.Status.PENDING);
            order.setCanRetryCheckout(false);
            storeOrderInformation(cart, order, request, false, orderInitiatedAt);
            response.setOrder(order);
            return response;
        }

        final List<String> failedProducts = order.getOrderLineItems().stream().filter(o -> OrderLineItem.Status.SUCCESS != o.getStatus()).map(item -> item.getCartLineItemId())
                .collect(Collectors.toList());
        boolean fullPaymentFailure = false;
        final List<OrderLineItem.Status> failedProductStatuses = order.getOrderLineItems().stream().filter(o -> OrderLineItem.Status.SUCCESS != o.getStatus())
                .map(item -> item.getStatus()).collect(Collectors.toList());
        if (!Utils.isEmpty(failedProductStatuses)) {
            fullPaymentFailure = failedProductStatuses.stream().allMatch(o -> OrderLineItem.Status.PAYMENT_FAILURE == o);
        }
        // The handle checkout API will handle success, partial failure and failure
        // cases.
        try {
            final String newCartId = cartHandler.handleCheckout(cart.getCartId(), failedProducts, request.getEnableJwb() != null ? request.getEnableJwb() : false);
            if (!Utils.isEmpty(newCartId)) {
                order.setNewCartId(newCartId);
            }
        } catch (AppException e) {
            logger.warn("Unable to create new cart for checkout failures {}", e.getMessage());
            setResponseWarningMessage(response, e.getDescription(), e.getDisplayCode());
        } catch (AppRuntimeException e) {
            logger.warn("Unable to create new cart for checkout failures {}", e.getMessage());
            setResponseWarningMessage(response, e.getDescription(), e.getDisplayCode());
        } catch (Exception e) {
            logger.warn("Unable to create new cart for checkout failures {}", e.getMessage());
            setResponseWarningMessage(response, e.getMessage(), "500");
        }
        if (Utils.isEmpty(failedProducts)) {
            order.setStatus(Order.Status.SUCCESS);
            order.setCanRetryCheckout(false);
        } else if (failedProducts.size() == order.getOrderLineItems().size() && !fullPaymentFailure) {
            order.setStatus(Order.Status.FAILURE);
            order.setCanRetryCheckout(true);
        } else if (failedProducts.size() == order.getOrderLineItems().size() && fullPaymentFailure) {
            order.setStatus(Order.Status.PAYMENT_FAILURE);
            order.setCanRetryCheckout(true);
        } else {
            order.setStatus(Order.Status.PARTIAL);
            order.setCanRetryCheckout(!Utils.isEmpty(order.getNewCartId()));
        }
        
        if (skipPaymentCapture && (order.getStatus().equals(Order.Status.FAILURE) || order.getStatus().equals(Order.Status.PAYMENT_FAILURE))) {
            createPaymentSession(cart, order, request);
        }

        storeOrderInformation(cart, order, request, orderEntity.isComplete(), orderInitiatedAt);
        populateOrderFinancialImpact(cart, order);
        toPublish(cart, order, request, orderInitiatedAt);
        response.setOrder(order);
        return response;
    }

    @EnableEnterpriseSuccessEvent(eventType = IEventType.EVENT_CART_CHECKOUT, transformer = OrderCheckoutEventTransformer.class)
    @EnableEnterpriseFailureEvent(eventType = IEventType.EVENT_CART_CHECKOUT_FAILURE, exceptions = AppException.class)
    public OrderEvent toPublish(Cart cart, Order order, CheckoutRequest checkoutRequest, final ZonedDateTime orderInitiatedAt) throws AppException {
        logger.trace("Order event created for : {}", order.getId());
        final OrderEvent orderEvent = new OrderEvent();
        orderEvent.setOrder(order);
        orderEvent.setCart(cart);
        orderEvent.setCheckoutRequest(checkoutRequest);
        orderEvent.setOrderInitiatedAt(orderInitiatedAt);
        orderEvent.setOrderUpdatedAt(orderInitiatedAt);
        toPublishReservationEmail(orderEvent);
        return orderEvent;
    }
    
    public void toPublishReservationEmail(final OrderEvent orderEvent) throws AppException {
        final RtcReservationEvent rtcReservationEvent = orderCheckoutEmailEventTransformer.toRight(orderEvent);
        try {
            rtcAccess.sendOrderCheckoutEmailEvent(rtcReservationEvent);
        } catch (HttpFailureException e) {
            final String errorPayload = e.getPayload();
            if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && e.getHttpCode() <= 500) {
                logger.error("[Error from RTC] Send checkout email event call failed. :  {}", errorPayload);
            } else {
                logger.error("[Error from RTC] Something unexpected happened in send checkout email event call.  :", e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from RTC] Send checkout email event call failed with app exception. : ", e.getMessage());
        } catch (Exception e) {
            logger.error("[Error from RBS] Send checkout email event call failed with unknown exception. : ", e.getMessage());
        }
    }

    @EnableEnterpriseSuccessEvent(eventType = IEventType.EVENT_CART_ORDER_CHECKOUT_TIMEOUT, transformer = OrderCheckoutEventTransformer.class)
    @EnableEnterpriseFailureEvent(eventType = IEventType.EVENT_CART_ORDER_CHECKOUT_TIMEOUT_FAILURE, exceptions = AppException.class)
    public OrderEvent toCartOrderCheckoutTimeoutPublish(Cart cart, Order order, CheckoutRequest checkoutRequest, final ZonedDateTime orderInitiatedAt) throws AppException {
        logger.trace("Cart order checkout timeout event created for : {}", order.getId());
        final OrderEvent orderEvent = new OrderEvent();
        orderEvent.setOrder(order);
        orderEvent.setCart(cart);
        orderEvent.setCheckoutRequest(checkoutRequest);
        orderEvent.setOrderInitiatedAt(orderInitiatedAt);
        orderEvent.setOrderUpdatedAt(orderInitiatedAt);
        return orderEvent;
    }

    private void storeOrderInformation(Cart cart, Order order, CheckoutRequest request, Boolean orderComplete, final ZonedDateTime orderInitiatedAt) throws AppException {
        final com.mgmresorts.order.entity.Order entity = orderTransformer.toRight(order);
        cart.setMgmId(ThreadContext.getContext().get().getJwtClaim(Jwts.Claim.MGM_ID));
        entity.setRawCart(mapper.writeValueAsString(cart));
        entity.setOrderInitiatedAt(orderInitiatedAt);
        entity.setOrderUpdatedAt(orderInitiatedAt);
        if (ASYNC_ENABLED && request.getProgressiveCheckout() != null && request.getProgressiveCheckout() && !orderComplete) {
            final String plainTextCheckoutRequestpayload = mapper.asJsonString(request);
            final String encryptedCheckoutRequestpayload = SecurityFactory.get().encrypt(plainTextCheckoutRequestpayload);
            entity.setCheckoutRequest(encryptedCheckoutRequestpayload);
        } else if (ASYNC_ENABLED && request.getProgressiveCheckout() != null && request.getProgressiveCheckout() && orderComplete) {
            entity.setComplete(false);
        }
        
        orderAccess.update(entity);

        if (entity.getStatus() == OrderStatus.SUCCESS || entity.getStatus() == OrderStatus.PARTIAL) {
            final List<LineItem> lineItems = entity.getLineItems();

            for (LineItem lineItem : lineItems) {
                if (lineItem.getStatus().equalsIgnoreCase(Status.SUCCESS.toString())) {
                    if (StringUtils.isNotBlank(lineItem.getConfirmationNumber())) {
                        final OrderConfirmationMapping orderConfirmationMapping = new OrderConfirmationMapping();
                        orderConfirmationMapping.setId(entity.getId());
                        orderConfirmationMapping.setCartId(entity.getCartId());
                        orderConfirmationMapping.setMgmId(entity.getMgmId());
                        orderConfirmationMapping.setType(entity.getType());
                        orderConfirmationMapping.setVersion(entity.getVersion());
                        orderConfirmationMapping.setConfirmationNumber(lineItem.getConfirmationNumber());
                        orderConfirmationAccess.update(orderConfirmationMapping);
                    }
                }
            }
        }
    }
    
    private void createPaymentSession(Cart cart, Order order, CheckoutRequest request) throws AppException {
        final GuestProfile guestProfile = Utils.cloneByJson(mapper, GuestProfile.class, request.getGuestProfile());
        final String paymentSessionId = cartHandler.manageCartPaymentSession(cart.getCartId(), cart.getType(), cart.getVersion(), guestProfile);
        if (!Utils.isEmpty(paymentSessionId)) {
            order.setPaymentSessionId(paymentSessionId);
        }
    }

    private void populateOrderFinancialImpact(Cart cart, Order order) throws AppException {
        final OrderFinancialImpact orderFi = orderFiTransformer.toRight(order);
        final com.mgmresorts.order.logging.PriceDetails priceDetails = orderFi.getPriceDetails();
        if (priceDetails == null) {
            final com.mgmresorts.order.logging.PriceDetails pd = new com.mgmresorts.order.logging.PriceDetails();
            pd.setTotalPrice(cart.getPriceDetails() != null ? cart.getPriceDetails().getTotalPrice() : null);
            orderFi.setPriceDetails(pd);
        }
        ThreadContext.getContext().get().setFinancialImpact(orderFi);
    }

    private void validateCheckoutRequest(CheckoutRequest request) throws AppException {
        if (request == null) {
            logger.error("The request payload was null.");
            throw new AppException(Errors.INVALID_PAYLOAD);
        }
        if (StringUtils.isBlank(request.getMgmId()) && StringUtils.isBlank(request.getCartId())) {
            logger.error("The cart id and mgm id were both null or empty in the request payload.");
            throw new AppException(Errors.INVALID_REQUEST, "Either cart id or mgm id is required.");
        }
    }

    private void setProfileFieldsFromClaimsIfPresent(CheckoutRequest request, Customer customer, boolean readFromClaims) throws AppException {
        /*
        The below block is being commented out to address a Prod issue where user's
        first name and last name in JWT is having "-".
        Once identity fixes the issue, please revert the below code.
      */
        final String firstName = readFromClaims ? CommonConfig.getFirstName() : customer.getProfile().getFirstName();
        final String lastName = readFromClaims ? CommonConfig.getLastName() : customer.getProfile().getLastName();
        if (!Utils.isEmpty(firstName) && !Utils.isEmpty(lastName)) {
              if (!firstName.equals("-") && !lastName.equals("-")) {
                  request.getGuestProfile().setFirstName(firstName);
                  request.getGuestProfile().setLastName(lastName);
              } else {
                  logger.error("Names in JWT are presented as hyphens");
              }
        }
        final String dateOfBirth = readFromClaims ? CommonConfig.getDateOfBirth()
                : customer.getProfile().getDateOfBirth() != null ? customer.getProfile().getDateOfBirth().toString() : null;
        if (!Utils.isEmpty(dateOfBirth)) {
            request.getGuestProfile().setDateOfBirth(convertDateTo_yyyy_MM_dd_Format(dateOfBirth));
        }
        final String tier = readFromClaims ? CommonConfig.getTier() : customer.getMemberships().getTier() != null ? customer.getMemberships().getTier().value() : null;
        if (!Utils.isEmpty(tier)) {
            request.getGuestProfile().setTier(tier);
        }
        final String dateOfEnrollment = readFromClaims ? CommonConfig.getDateOfEnrollment()
                : customer.getMemberships().getDateOfEnrollment() != null ? customer.getMemberships().getDateOfEnrollment().toString() : null;
        if (!Utils.isEmpty(dateOfEnrollment)) {
            request.getGuestProfile().setDateOfEnrollment(convertDateTo_yyyy_MM_dd_Format(dateOfEnrollment));
        }
    }

    private void validateJwbCart(Cart cart, CheckoutRequest request) throws AppException {
        final List<CartLineItem> jwbEnabledItem = cart.getCartLineItems().stream().filter(li -> (li.getEnableJwb() != null && li.getEnableJwb())).collect(Collectors.toList());
        final String mgmId = ThreadContext.getContext().get().getJwt().getClaim(Claim.MGM_ID);

        if (!Utils.isEmpty(jwbEnabledItem) && (request.getEnableJwb() != null && !request.getEnableJwb())) {
            logger.error("Join while booking is required for this cart.");
            throw new AppException(Errors.INVALID_REQUEST, "Join while booking is required for this cart.");
        } else if (((request.getEnableJwb() != null && request.getEnableJwb()) || !Utils.isEmpty(jwbEnabledItem)) && Utils.isEmpty(mgmId)) {
            logger.error("The guest must join to checkout this cart.");
            throw new AppException(Errors.INVALID_REQUEST, "The guest must join to checkout this cart.");
        } else if ((request.getEnableJwb() != null && request.getEnableJwb()) && !Utils.isEmpty(cart.getMgmId())) {
            logger.error("Join while booking is not allowed for existing members.");
            throw new AppException(Errors.INVALID_REQUEST, "Join while booking is not allowed for existing members.");
        }
    }

    private String convertDateTo_yyyy_MM_dd_Format(final String dateString) {
        try {
            logger.info("Input date string is : " + dateString);
            final Date parsedDate = DateUtils.parseDate(dateString, new String[] { "yyyy-MM-dd", "yyyy-M-d", "M/d/yyyy", "MM/dd/yyyy" });
            return parsedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception ex) {
            logger.error("Exception occured while converting to date format(yyyy-MM-dd) from input date string : " + dateString);
        }
        return null;
    }

    @Override
    public CheckoutResponse read(String orderId, com.mgmresorts.order.dto.services.Type cartType,
                                 com.mgmresorts.order.dto.services.Version cartVersion, String encryptedEmailAddress) throws AppException {
        return readOrder(orderId, cartType, cartVersion, encryptedEmailAddress);
    }

    private CheckoutResponse readOrder(String orderId, com.mgmresorts.order.dto.services.Type cartType,
            com.mgmresorts.order.dto.services.Version cartVersion, String encryptedEmailAddress) throws AppException {
        if (StringUtils.isBlank(orderId)) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "order id is mandatory");
        }

        final com.mgmresorts.order.entity.Order orderEntity = orderAccess.read(orderId);

        final boolean isEncryptedEmailRequired = StringUtils.isNotBlank(encryptedEmailAddress) && StringUtils.isNotBlank(orderEntity.getEncryptedEmailAddress());

        if (isEncryptedEmailRequired && !encryptedEmailAddress.equals(orderEntity.getEncryptedEmailAddress())) {
            throw new AppException(Errors.GET_ORDER_EMAIL_MISMATCH, orderId);
        }

        if (orderEntity == null) {
            throw new AppException(SystemError.REQUESTED_RESOURCE_NOT_FOUND);
        }

        if (orderEntity.getType() != com.mgmresorts.order.entity.Type.fromValue(cartType != null ? cartType.value() : com.mgmresorts.order.dto.services.Type.GLOBAL.value())) {
            throw new AppException(ApplicationError.CROSS_CARTTYPE_FORBIDDEN_READ, orderId);
        }
        
        if (orderEntity.getVersion() != com.mgmresorts.order.entity.Version.fromValue(
                cartVersion != null ? cartVersion.value() : com.mgmresorts.order.dto.services.Version.V1.value())) {
            throw new AppException(ApplicationError.CROSS_CARTVERSION_FORBIDDEN_READ, orderId);
        }

        final String orderMgmId = orderEntity.getMgmId();
        final String jwtMgmId = ThreadContext.getContext().get().getJwtClaim(Claim.MGM_ID);

        if (!Utils.isEmpty(orderMgmId)) {
            if (Utils.isEmpty(jwtMgmId) || !jwtMgmId.equals(orderMgmId)) {
                throw new AppException(Errors.UNAUTHORIZED_ORDER_ACCESS);
            }
        } else if (!Utils.isEmpty(jwtMgmId)) {
            throw new AppException(Errors.UNAUTHORIZED_ORDER_ACCESS);
        }

        if (orderEntity.isComplete()) {
            final Order order = orderTransformer.toLeft(orderEntity);
            final List<String> failedProducts = order.getOrderLineItems().stream().filter(o -> OrderLineItem.Status.SUCCESS != o.getStatus()).map(item -> item.getCartLineItemId())
                    .collect(Collectors.toList());
            // The handle checkout API will handle success, partial failure and failure cases.
            try {
                final String newCartId = cartHandler.handleCheckout(orderEntity.getCartId(), failedProducts, false);
                if (!Utils.isEmpty(newCartId)) {
                    order.setNewCartId(newCartId);
                }
            } catch (AppException e) {
                logger.warn("Unable to create new cart for checkout failures {}", e.getMessage());
            }
            if (Utils.isEmpty(failedProducts)) {
                order.setStatus(Order.Status.SUCCESS);
                order.setCanRetryCheckout(false);
            } else if (failedProducts.size() == order.getOrderLineItems().size()) {
                order.setStatus(Order.Status.FAILURE);
                order.setCanRetryCheckout(true);
            } else {
                order.setStatus(Order.Status.PARTIAL);
                order.setCanRetryCheckout(!Utils.isEmpty(order.getNewCartId()));
            }

            final Cart cart = mapper.readValue(orderEntity.getRawCart(), Cart.class);
            final ZonedDateTime orderInitiatedAt = orderEntity.getOrderInitiatedAt();
            final String encryptedCheckoutRequestpayload = orderEntity.getCheckoutRequest();
            final String privateDecryptKey = SecurityFactory.get().getKey();
            final String decryptedCheckoutRequestpayload = SecurityFactory.get().decrypt(encryptedCheckoutRequestpayload, privateDecryptKey);
            final CheckoutRequest decryptedCheckoutRequest = mapper.readValue(decryptedCheckoutRequestpayload, CheckoutRequest.class);
            storeOrderInformation(cart, order, decryptedCheckoutRequest, orderEntity.isComplete(), orderInitiatedAt);
            populateOrderFinancialImpact(cart, order);
            toPublish(cart, order, decryptedCheckoutRequest, orderInitiatedAt);

            final CheckoutResponse response = new CheckoutResponse();
            response.setHeader(HeaderBuilder.buildHeader());
            response.setOrder(order);
            return response;
        }

        final CheckoutResponse response = new CheckoutResponse();
        response.setHeader(HeaderBuilder.buildHeader());
        final Order out = orderTransformer.toLeft(orderEntity);
        response.setOrder(out);
        return response;
    }
    
    private Message createTimeoutMessage(boolean isPackage) {      
        final Message message = new Message();
        message.setType(com.mgmresorts.common.dto.Message.Type.WARNING);
        if (isPackage) {
            message.setText("Checkout for some of the package items is still in progress");
        } else {
            message.setText("Checkout for some of the cart items is still in progress");
        }
        return message;
    }

    private void validatePackageItems(List<CartLineItem> cartLineItems) throws AppException {
        final List<ItemType> itemTypes = cartLineItems.stream().map(CartLineItem::getType).collect(Collectors.toList());
        if (!itemTypes.contains(ItemType.ROOM) || !itemTypes.contains(ItemType.SHOW)) {
            throw new AppException(ApplicationError.PACKAGE_CART_NOT_HAVE_REQUIRED_ITEM, "Room or Show item is missing");
        }
        final long roomsCount = cartLineItems.stream().filter(e -> e.getType() == ItemType.ROOM).count();
        if (roomsCount != 1) {
            throw new AppException(ApplicationError.PACKAGE_CART_NOT_HAVE_REQUIRED_ITEM, "Room type should be exactly one");
        }
        final long showCount = cartLineItems.stream().filter(e -> e.getType() == ItemType.SHOW).count();
        if (showCount != 1) {
            throw new AppException(ApplicationError.PACKAGE_CART_NOT_HAVE_REQUIRED_ITEM, "Show type should be exactly one");
        }
    }

    private CheckoutResponse createTimeoutResponse(String id, com.mgmresorts.order.dto.services.Type type,
            com.mgmresorts.order.dto.services.Version version, boolean isPackage, Cart cart, CheckoutRequest request,
            String encryptedEmailAddress, final ZonedDateTime orderInitiatedAt) throws AppException {
        final CheckoutResponse checkoutResponse = read(id, type, version, encryptedEmailAddress);
        if (checkoutResponse != null && checkoutResponse.getOrder() != null && checkoutResponse.getOrder().getStatus() == Order.Status.PENDING) {
            if (!Utils.isEmpty(checkoutResponse.getHeader().getStatus().getMessages())) {
                checkoutResponse.getHeader().getStatus().setMessages(new ArrayList<Message>());
            }
            final Message timeoutMessage = createTimeoutMessage(isPackage);
            checkoutResponse.getHeader().getStatus().getMessages().add(timeoutMessage);
            toCartOrderCheckoutTimeoutPublish(cart, checkoutResponse.getOrder(), request, orderInitiatedAt);
        }
        return checkoutResponse;
    }

    private void setResponseWarningMessage(CheckoutResponse response, String text, String code) {
        if (!Utils.isEmpty(response.getHeader().getStatus().getMessages())) {
            response.getHeader().getStatus().setMessages(new ArrayList<Message>());
        }
        final Message msg = new Message();
        msg.setType(com.mgmresorts.common.dto.Message.Type.WARNING);
        msg.setText(text);
        msg.setCode(code);
        response.getHeader().getStatus().getMessages().add(msg);
    }

    private void setResponseErrorMessage(CheckoutResponse response, String text) {
        if (!Utils.isEmpty(response.getHeader().getStatus().getMessages())) {
            response.getHeader().getStatus().setMessages(new ArrayList<Message>());
        }
        final Message msg = new Message();
        msg.setType(com.mgmresorts.common.dto.Message.Type.ERROR);
        msg.setText(text);
        response.getHeader().getStatus().getMessages().add(msg);
    }
}
