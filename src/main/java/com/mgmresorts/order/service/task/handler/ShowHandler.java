package com.mgmresorts.order.service.task.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.dto.services.Version;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.function.HeaderBuilder;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.logging.masker.MaskLogger;
import com.mgmresorts.common.notification.Email;
import com.mgmresorts.common.notification.Emailer;
import com.mgmresorts.common.notification.SmtpEmailer;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.content.model.ShowEvent;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.IContentAccess;
import com.mgmresorts.order.backend.access.IShowBookingAccess;
import com.mgmresorts.order.backend.handler.IPaymentProcessingHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionShowHandler;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.dto.services.CancelReservationRequest;
import com.mgmresorts.order.dto.services.CancelReservationResponse;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.Message;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.OrderLineItem.Status;
import com.mgmresorts.order.dto.services.PreviewReservationRequest;
import com.mgmresorts.order.dto.services.PreviewReservationResponse;
import com.mgmresorts.order.dto.services.RetrieveReservationResponse;
import com.mgmresorts.order.dto.services.SourceSystemError;
import com.mgmresorts.order.dto.services.UpdateReservationRequest;
import com.mgmresorts.order.dto.services.UpdateReservationResponse;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.consumer.IMergeConsumer;
import com.mgmresorts.order.service.task.IProductHandler;
import com.mgmresorts.pps.model.PaymentExceptionResponse;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.psm.model.SessionError;
import com.mgmresorts.sbs.model.CostDetails;
import com.mgmresorts.sbs.model.ErrorResponse;
import com.mgmresorts.sbs.model.ItineraryInfo;
import com.mgmresorts.sbs.model.Rates;
import com.mgmresorts.sbs.model.ReservationProfile;
import com.mgmresorts.sbs.model.ShowChargesResponse;
import com.mgmresorts.sbs.model.ShowReservationRequest;
import com.mgmresorts.sbs.model.ShowReservationRequestPackageMetadata;
import com.mgmresorts.sbs.model.ShowReservationResponse;
import com.mgmresorts.sbs.model.ShowTicket;
import com.mgmresorts.sbs.model.ShowTicketWithDM;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.DeliveryMethod;
import com.mgmresorts.shopping.cart.dto.ShowSelectionDetails;

public class ShowHandler implements IProductHandler {

    private final Logger logger = Logger.get(ShowHandler.class);
    private final JSonMapper mapper = new JSonMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();
    private static final boolean IS_ASYNC_ENABLED = Boolean.parseBoolean(Runtime.get().getConfiguration("async.enabled"));
    
    @Inject
    private ITransformer<GuestProfile, ReservationProfile> showProfileTransformer;
    @Inject
    private ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> showBillingTransformer;
    @Inject
    private ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer;
    @Inject
    private SmtpEmailer smtpEmailer;
    @Inject
    private IContentAccess contentAccess;
    @Inject
    private IOrderAccess orderAccess;
    @Inject
    private IMergeConsumer mergeConsumer;
    @Inject
    private IPaymentProcessingHandler paymentProcessingHandler;
    @Inject
    private IShowBookingAccess showBookingAccess;
    @Inject
    private IPaymentSessionShowHandler paymentSessionShowHandler;

    public Collection<OrderLineItem> reserve(CheckoutRequest request, List<CartLineItem> showCartLineItems,
            List<OrderLineItem> showOrderLineItems, AppliedBillings billable, String orderId,
            com.mgmresorts.shopping.cart.dto.AgentInfo agentInfo, boolean skipAFS, boolean skipPaymentCapture,
            Map<String, PaymentAuthFields> paymentAuthFieldsMap, String orderReferenceNumber) throws AppException {
        return showCartLineItems.stream().map(show -> {
            try {
                final OrderLineItem orderLineItem = showOrderLineItems.stream().filter(li -> show.getCartLineItemId().equalsIgnoreCase(li.getCartLineItemId())).findFirst().get();
                return checkout(request, show, orderLineItem, billable, orderId, agentInfo, skipAFS,
                        skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
            } catch (AppException e) {
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());
    }

    @Override
    public OrderLineItem checkout(CheckoutRequest request, CartLineItem cartLineItem, OrderLineItem orderLineItem,
            AppliedBillings billable, String orderId, com.mgmresorts.shopping.cart.dto.AgentInfo agentInfo,
            boolean skipAFS, boolean skipPaymentCapture, Map<String, PaymentAuthFields> paymentAuthFieldsMap,
            String orderReferenceNumber) throws AppException {
        final ShowSelectionDetails showDetails = getShowSelectionDetails(cartLineItem);
        if (showDetails != null) {
            orderLineItem.setNumberOfTickets(showDetails.getNumberOfTickets());
            orderLineItem.setProgramId(showDetails.getProgramId());
        }
        
        final boolean isPackage = request.getCartType() == com.mgmresorts.order.dto.services.Type.PACKAGE;
        
        PaymentAuthFields paymentAuthFields = null;
        if (skipPaymentCapture && (cartLineItem.getPaymentRequired() != null && cartLineItem.getPaymentRequired())) {
            paymentAuthFields = paymentAuthFieldsMap.get(cartLineItem.getCartLineItemId());
            if (paymentAuthFields == null || !paymentAuthFields.isSuccess() || StringUtils.isBlank(paymentAuthFields.getAuthorizationCode())) {
                addPaymentFailure(cartLineItem, orderLineItem,
                        paymentAuthFields != null ? paymentAuthFields.getErrorMessage() : "No payment auth found for item id: " + cartLineItem.getCartLineItemId(),
                        paymentAuthFields != null ? paymentAuthFields.getErrorCode() : "404");
                if (!isPackage) {
                    final Consumer<Order> merger = mergeConsumer.create(orderLineItem);
                    orderAccess.mergeAndUpdate(orderId, Order.class, merger);
                }
                return orderLineItem;
            }
        }

        final ShowChargesResponse showCharge = mapper.readValue(cartLineItem.getContent(), ShowChargesResponse.class);
        
        try {
            final String itineraryId = request.getItineraryId();
            final ShowReservationRequest showReservationRequest = generateShowReservationRequest(request,
                    orderLineItem, orderId, cartLineItem, showCharge, billable, agentInfo, itineraryId, isPackage, skipAFS, paymentAuthFields, skipPaymentCapture);
            orderLineItem.setItineraryId(itineraryId);

            if (isPackage) {
                final boolean healthResponse = checkSBSHealth();
                if (!healthResponse) {
                    sendEmail(showCharge, showReservationRequest, cartLineItem);
                    addShowFailure(cartLineItem, orderLineItem, "{\"error\":{\"code\":\"620-1-503\",\"message\":\"SBS is down!\"}}", Errors.UNABLE_TO_BOOK_SHOW, null);
                    return orderLineItem;
                }
            }

            final String showReservationResponse = showBookingAccess.createShowReservation(showReservationRequest);

            if (skipPaymentCapture && (cartLineItem.getPaymentRequired() != null && cartLineItem.getPaymentRequired())
                    && paymentAuthFields != null && paymentAuthFields.getAmount() > 0) {
                final ShowReservationResponse resvResp = mapper.readValue(showReservationResponse, ShowReservationResponse.class);
                paymentAuthFields.setConfirmationNumber(resvResp.getConfirmationNumber());
                try {
                    paymentProcessingHandler.captureTransaction(orderId, orderReferenceNumber, paymentAuthFields);
                } catch (SourceAppException e) {
                    final PaymentExceptionResponse ppsErrorResponse = mapper.readValue(e.getRaw(), PaymentExceptionResponse.class);
                    if (!Utils.anyNull(ppsErrorResponse, ppsErrorResponse.getErrorCode(), ppsErrorResponse.getErrorMessage())) {
                        logger.error("Error message from PPS capture call: {}", e.getRaw());
                    }
                } catch (AppException e) {
                    logger.error("Unexpected exception occurred during room reservation payment capture call: {}", e.getDescription());
                }
            }

            addSuccess(cartLineItem.getCartLineItemId(), orderLineItem, showReservationResponse, request);
            copySelectedDeliveryMethod(cartLineItem, orderLineItem, showReservationResponse);

        } catch (HttpFailureException e) {
            logger.error("[Error from SBS] Create Reservation call failed : ", e.getMessage());
            final String errorPayload = e.getPayload();
            if (skipPaymentCapture && (cartLineItem.getPaymentRequired() != null && cartLineItem.getPaymentRequired())
                    && paymentAuthFields != null && paymentAuthFields.getAmount() > 0) {
                paymentProcessingHandler.voidTransaction(orderId, orderReferenceNumber, paymentAuthFields);
            }
            addShowFailure(cartLineItem, orderLineItem, errorPayload, Errors.UNABLE_TO_BOOK_SHOW, e);
        } catch (AppException e) {
            logger.error("[Error from SBS] Create Reservation call failed : ", e.getMessage());
            if (skipPaymentCapture && (cartLineItem.getPaymentRequired() != null && cartLineItem.getPaymentRequired())
                    && paymentAuthFields != null && paymentAuthFields.getAmount() > 0) {
                paymentProcessingHandler.voidTransaction(orderId, orderReferenceNumber, paymentAuthFields);
            }
            addShowFailure(cartLineItem, orderLineItem, e.getDescription(), e.getCode(), null);
        } catch (Exception e) {
            logger.error("[Error from SBS] Create Reservation call failed : ", e.getMessage());
            if (skipPaymentCapture && (cartLineItem.getPaymentRequired() != null && cartLineItem.getPaymentRequired())
                    && paymentAuthFields != null && paymentAuthFields.getAmount() > 0) {
                paymentProcessingHandler.voidTransaction(orderId, orderReferenceNumber, paymentAuthFields);
            }
            addShowFailure(cartLineItem, orderLineItem, e.getMessage(), 500, null);
        }
        
        if (!isPackage) {
            final Consumer<Order> merger = mergeConsumer.create(orderLineItem);
            orderAccess.mergeAndUpdate(orderId, Order.class, merger);
        }        
        return orderLineItem;
    }

    private void addSuccess(final String cartLineItemId, final OrderLineItem output, final String reservation, final CheckoutRequest request) {
        final ShowReservationResponse resvResp = mapper.readValue(reservation, ShowReservationResponse.class);
        output.setCartLineItemId(cartLineItemId);
        output.setConfirmationNumber(resvResp.getConfirmationNumber());
        if (IS_ASYNC_ENABLED && request.getProgressiveCheckout() != null && request.getProgressiveCheckout()) {
            output.setStatus(Status.PENDING);
        } else {
            output.setStatus(Status.SUCCESS);
            final String showReservationMaskedGroup = "show-reservation-content";
            final String maskedShowReservationContent = logger.getJsonLogger().doMask(reservation,
                    MaskLogger.MASKABLE_FIELDS.getOrDefault(showReservationMaskedGroup, new ArrayList<String>()));
            output.setContent(maskedShowReservationContent);
        }

        final Rates resvRateSummary = resvResp.getCharges();
        output.setLineItemCharge(resvRateSummary.getDiscountedSubtotal());
        output.setLineItemPrice(resvRateSummary.getReservationTotal());
        output.setLineItemDeposit(resvRateSummary.getReservationTotal());
        output.setLineItemDiscount(Utils.roundTwoDecimalPlaces(resvRateSummary.getShowSubtotal() - resvRateSummary.getDiscountedSubtotal()));
        output.setLineItemBalance(0d);

        final CostDetails serviceCharge = resvRateSummary.getServiceCharge();
        final CostDetails transactionFee = resvRateSummary.getTransactionFee();

        double serviceChargeFee = 0d;
        double serviceChargeTax = 0d;
        double serviceChargeFeeAndTax = 0d;
        double transactionFeeCharge = 0d;
        double transactionFeeTax = 0d;

        if (serviceCharge != null && serviceCharge.getItemized() != null) {
            serviceChargeFee += Optional.ofNullable(serviceCharge.getItemized().getCharge()).orElse(0d);
            serviceChargeTax += Optional.ofNullable(serviceCharge.getItemized().getTax()).orElse(0d);
            serviceChargeFeeAndTax += Optional.ofNullable(serviceCharge.getAmount()).orElse(0d);
        }
        
        output.setLineItemServiceChargeFee(serviceChargeFee);
        output.setLineItemServiceChargeTax(serviceChargeTax);
        output.setLineItemServiceChargeFeeAndTax(serviceChargeFeeAndTax);

        if (transactionFee != null && transactionFee.getItemized() != null) {
            transactionFeeCharge += Optional.ofNullable(transactionFee.getItemized().getCharge()).orElse(0d);
            transactionFeeTax += Optional.ofNullable(transactionFee.getItemized().getTax()).orElse(0d);
        }
        
        output.setLineItemTransactionFee(transactionFeeCharge);
        output.setLineItemTransactionFeeTax(transactionFeeTax);

        output.setLineItemDeliveryMethodFee(Optional.ofNullable(resvRateSummary.getDeliveryFee()).orElse(0d));
        output.setLineItemGratuity(Optional.ofNullable(resvRateSummary.getGratuity()).orElse(0d));
        final double let = Optional.ofNullable(resvRateSummary.getLet()).orElse(0d);
        output.setLineItemLet(let);

        final double discountedSubtotal = Optional.ofNullable(resvRateSummary.getDiscountedSubtotal()).orElse(0d);
        final double totalCharge = discountedSubtotal;
        final double adjustedItemSubtotal = totalCharge + let + transactionFeeCharge + transactionFeeTax;
        output.setLineItemAdjustedItemSubtotal(adjustedItemSubtotal);

        final double totalTax = let + transactionFeeTax + serviceChargeTax;
        output.setLineItemTax(Utils.roundTwoDecimalPlaces(totalTax));
    }

    private void addFailure(final CartLineItem input, final OrderLineItem output, final Message msg, final boolean paymentFailure) {
        output.setContent(input.getContent());
        output.setCartLineItemId(input.getCartLineItemId());
        
        output.setLineItemCharge(input.getLineItemTotalCharges());
        output.setLineItemPrice(input.getLineItemPrice());
        output.setLineItemDeposit(input.getLineItemDeposit());
        output.setLineItemDiscount(input.getLineItemDiscount());
        output.setLineItemBalance(input.getLineItemBalance());
        
        final ShowChargesResponse showCharge = mapper.readValue(input.getContent(), ShowChargesResponse.class);
        
        final CostDetails serviceCharge = showCharge.getCharges().getServiceCharge();
        final CostDetails transactionFee = showCharge.getCharges().getTransactionFee();

        double serviceChargeFee = 0d;
        double serviceChargeTax = 0d;
        double serviceChargeFeeAndTax = 0d;
        double transactionFeeCharge = 0d;
        double transactionFeeTax = 0d;
        
        if (transactionFee != null && transactionFee.getItemized() != null) {
            transactionFeeCharge += Optional.ofNullable(transactionFee.getItemized().getCharge()).orElse(0d);
            transactionFeeTax += Optional.ofNullable(transactionFee.getItemized().getTax()).orElse(0d);
        }
        
        output.setLineItemTransactionFee(transactionFeeCharge);
        output.setLineItemTransactionFeeTax(transactionFeeTax);

        if (serviceCharge != null && serviceCharge.getItemized() != null) {
            serviceChargeFee += Optional.ofNullable(serviceCharge.getItemized().getCharge()).orElse(0d);
            serviceChargeTax += Optional.ofNullable(serviceCharge.getItemized().getTax()).orElse(0d);
            serviceChargeFeeAndTax += Optional.ofNullable(serviceCharge.getAmount()).orElse(0d);
        }
        
        output.setLineItemServiceChargeFee(serviceChargeFee);
        output.setLineItemServiceChargeTax(serviceChargeTax);
        output.setLineItemServiceChargeFeeAndTax(serviceChargeFeeAndTax);

        output.setLineItemDeliveryMethodFee(Optional.ofNullable(showCharge.getCharges().getDeliveryFee()).orElse(0d));
        output.setLineItemGratuity(Optional.ofNullable(showCharge.getCharges().getGratuity()).orElse(0d));
        final double let = Optional.ofNullable(showCharge.getCharges().getLet()).orElse(0d);
        output.setLineItemLet(let);

        final double discountedSubtotal = Optional.ofNullable(showCharge.getCharges().getDiscountedSubtotal()).orElse(0d);
        final double totalCharge = discountedSubtotal;
        final double adjustedItemSubtotal = totalCharge + let + transactionFeeCharge + transactionFeeTax;
        output.setLineItemAdjustedItemSubtotal(adjustedItemSubtotal);

        final double totalTax = let + transactionFeeTax + serviceChargeTax;
        output.setLineItemTax(Utils.roundTwoDecimalPlaces(totalTax));
        try {
            copySelectedDeliveryMethod(input, output, null);
        } catch (AppException e) {
            // ignore the exception
        }
        
        if (paymentFailure) {
            output.setStatus(Status.PAYMENT_FAILURE);
        } else {
            output.setStatus(Status.FAILURE);
        }
        output.getMessages().add(msg);
    }

    private void addShowFailure(final CartLineItem input, final OrderLineItem output, final String errorPayload, final int code, final HttpFailureException exception) {
        final Message msg = new Message();
        msg.setType(Message.Type.ERROR);
        msg.setCode(new AppException(code).getDisplayCode());
        final SourceSystemError sse = new SourceSystemError();
        boolean paymentFailure = false;
        if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && exception.getHttpCode() <= 500) {
            try {
                final ErrorResponse errorResponse = mapper.readValue(errorPayload, ErrorResponse.class);
                final String sseCode;
                final String sseMessage;
                if (errorResponse != null && errorResponse.getError() != null) {
                    sseCode = errorResponse.getError().getCode();
                    sseMessage = errorResponse.getError().getMessage();
                    if (StringUtils.equals(sseCode, "620-2-240") || StringUtils.equals(sseCode, "620-2-241")
                            || StringUtils.equals(sseCode, "620-2-242") || StringUtils.equals(sseCode, "620-3-242") || StringUtils.equals(sseCode, "620-2-258")
                            || StringUtils.equals(sseCode, "620-3-258") || StringUtils.equals(sseCode, "620-1-244")) {
                        paymentFailure = true;
                    }
                    sse.setSourceSystemCode(sseCode);
                    sse.setSourceSystemMessage(sseMessage);
                } else {
                    sseCode = exception != null ? String.valueOf(exception.getHttpCode()) : null;
                    sseMessage = errorPayload;
                    sse.setSourceSystemCode(sseCode);
                    sse.setSourceSystemMessage(sseMessage);
                }
            } catch (Exception e) {
                sse.setSourceSystemMessage(errorPayload);
                sse.setSourceSystemCode(exception != null ? String.valueOf(exception.getHttpCode()) : null);
            }
        } else {
            sse.setSourceSystemMessage(exception != null ? exception.getMessage() : null);
            sse.setSourceSystemCode(exception != null ? String.valueOf(exception.getHttpCode()) : null);
        }

        msg.setSourceSystemError(sse);

        addFailure(input, output, msg, paymentFailure);
    }

    private void addPaymentFailure(final CartLineItem input, final OrderLineItem output, final String errorMessage, final String errorCode) {
        final Message msg = new Message();
        msg.setType(Message.Type.ERROR);
        msg.setCode(new AppException(Errors.UNABLE_TO_GET_PAYMENT_SESSION, "Retrieval of payment session failed").getDisplayCode());

        final SourceSystemError sse = new SourceSystemError();
        sse.setSourceSystemMessage(errorMessage);
        sse.setSourceSystemCode(errorCode);

        msg.setSourceSystemError(sse);

        addFailure(input, output, msg, true);
    }

    private ShowReservationRequest generateShowReservationRequest(final CheckoutRequest request, final OrderLineItem orderLineItem,
        final String orderId, final CartLineItem input, final ShowChargesResponse showCharge, final AppliedBillings billable,
            final com.mgmresorts.shopping.cart.dto.AgentInfo agentInfo, final String itineraryId, boolean isPackage,
            boolean skipAFS, PaymentAuthFields paymentAuthFields, boolean skipPaymentCapture) throws AppException {
        final ShowReservationRequest showReservationRequest = new ShowReservationRequest();
        showReservationRequest.setInAuthTransactionId(request.getInAuthTransactionId());
        showReservationRequest.setEventDate(showCharge.getEventDate());
        showReservationRequest.setEventTime(showCharge.getEventTime());
        showReservationRequest.setSeasonId(showCharge.getSeasonId());
        showReservationRequest.setShowEventId(showCharge.getShowEventId());
        showReservationRequest.setProgramId(showCharge.getProgramId());
        showReservationRequest.setPropertyId(showCharge.getPropertyId());
        showReservationRequest.setMlifeNo(request.getGuestProfile().getMlifeNo());
        final boolean skipEmailNotification = request.getCartVersion() != null && request.getCartVersion().equals(Version.V2)
                && request.getCartType() != null && request.getCartType().equals(Type.PACKAGE);
        showReservationRequest.skipEmailNotification(skipEmailNotification);
        final ShowReservationRequest.ShowPaymentChargedToEnum showPaymentChargedTo = getShowPaymentChargedToEnum(input);
        showReservationRequest.setShowPaymentChargedTo(showPaymentChargedTo);
        
        if (request.getGuestProfile().getItineraryData() != null) {
            final ItineraryInfo itineraryInfo = new ItineraryInfo();
            itineraryInfo.setItineraryId(request.getGuestProfile().getItineraryData().getItineraryId());
            itineraryInfo.setItineraryName(request.getGuestProfile().getItineraryData().getItineraryName());
            itineraryInfo.setTripsArrivalDate(request.getGuestProfile().getItineraryData().getTripParams().getArrivalDate().toString());
            itineraryInfo.setTripsDepartureDate(request.getGuestProfile().getItineraryData().getTripParams().getDepartureDate().toString());
            showReservationRequest.setItineraryInfo(itineraryInfo);
        }
        
        showReservationRequest.setSkipFraudCheck(skipAFS);
        if (IS_ASYNC_ENABLED && request.getProgressiveCheckout() != null && request.getProgressiveCheckout()) {
            showReservationRequest.setSynchronous(false);
            showReservationRequest.setOrderLineItemId(orderLineItem.getOrderLineItemId());
            showReservationRequest.setOrderId(orderId);
        }
        if (!Utils.isEmpty(input.getOperaConfirmationNumber())) {
            showReservationRequest.setHotelCode(input.getOperaHotelCode());
            showReservationRequest.setOperaConfirmationNumber(input.getOperaConfirmationNumber());
        }
        
        if (isPackage && !Utils.isEmpty(input.getConfirmationNumber())) {
            final ShowReservationRequestPackageMetadata showPackageMetadata = new ShowReservationRequestPackageMetadata();
            showPackageMetadata.setRoomConfirmationNumber(input.getConfirmationNumber());
            showPackageMetadata.setRoomHotelCode(input.getOperaHotelCode());
            showReservationRequest.setPackageMetadata(showPackageMetadata);
        }

        final ShowSelectionDetails showDetails = getShowSelectionDetails(input);
        if (showDetails != null) {
            showReservationRequest.setRedemptionCode(showDetails.getMyVegasCode());
            showReservationRequest.setComp(showDetails.getMyVegasComp());
            final Optional<String> selectedDeliveryMethod = showDetails.getPermissibleDeliveryMethods().stream().filter(dm -> dm.getSelected()).map(item -> item.getCode())
                    .findFirst();
            if (selectedDeliveryMethod.isPresent()) {
                showReservationRequest.setDeliveryMethodCode(selectedDeliveryMethod.get());
            }
            if (showDetails.getHdePackage() != null && showDetails.getHdePackage()) {
                showReservationRequest.setHdePackage(true);
            }
        }

        showReservationRequest.setTickets(mapShowTickets(showCharge.getTickets()));
        showReservationRequest.setCharges(showCharge.getCharges());
        showReservationRequest.setProfile(showProfileTransformer.toRight(request.getGuestProfile()));
        if (input.getPaymentRequired() != null && input.getPaymentRequired()) {
            showReservationRequest.setBilling(showBillingTransformer.toRight(request.getBillings().stream().findFirst().get()));
            if (skipPaymentCapture && paymentAuthFields != null) {
                showReservationRequest.setSkipFraudCheck(true);
                showReservationRequest.setSkipPaymentProcess(true);
                if (showReservationRequest.getBilling() != null) {
                    showReservationRequest.getBilling().getPayment().setAuthId(paymentAuthFields.getAuthorizationCode());
                    showReservationRequest.getBilling().getPayment().setPaymentId(paymentAuthFields.getPaymentId());
                }
            }
        }
        return showReservationRequest;
    }

    private static ShowReservationRequest.ShowPaymentChargedToEnum getShowPaymentChargedToEnum(CartLineItem input) {
        ShowReservationRequest.ShowPaymentChargedToEnum showPaymentChargedTo = null;
        if (input.getItemSelectionDetails() != null
                && input.getItemSelectionDetails().getShowSelectionDetails() != null
                && input.getItemSelectionDetails().getShowSelectionDetails().getShowPaymentChargedTo() != null) {
            showPaymentChargedTo = ShowReservationRequest.ShowPaymentChargedToEnum.fromValue(
                    input.getItemSelectionDetails().getShowSelectionDetails().getShowPaymentChargedTo().value());
        }
        return showPaymentChargedTo;
    }

    public RetrieveReservationResponse getReservation(final String confirmationNumber, final String firstName,
            final String lastName, final boolean createPaymentSession, final String paymentSessionId)
            throws AppException {
        
        final RetrieveReservationResponse retrieveReservationResponse = new RetrieveReservationResponse();
        retrieveReservationResponse.setHeader(HeaderBuilder.buildHeader());
        
        try {
            final ShowReservationResponse showReservationResponse = showBookingAccess.getShowReservation(confirmationNumber, firstName, lastName);
            retrieveReservationResponse.setShowReservationResponse(showReservationResponse);
            
            if (createPaymentSession) {
                final EnableSessionResponse enableSessionResponse = paymentSessionShowHandler.managePaymentSessionForShowReservation(showReservationResponse,
                        paymentSessionId, CallType.CREATE);
                retrieveReservationResponse.setPaymentSessionId(enableSessionResponse.getSessionId());
            }
        } catch (SourceAppException e) {
            retrieveReservationResponse.setShowReservationResponse(null);
            final com.mgmresorts.sbs.model.ErrorResponse sbsErrorResponse = mapper.readValue(e.getRaw(), com.mgmresorts.sbs.model.ErrorResponse.class);
            if (sbsErrorResponse != null && sbsErrorResponse.getError() != null) {
                logger.error("Error message from SBS Get Show reservation call: {}", e.getRaw());
                retrieveReservationResponse.setErrorShowReservationResponse(sbsErrorResponse);
            } else {
                final SessionError psmErrorResponse = mapper.readValue(e.getRaw(), SessionError.class);
                if (!Utils.anyNull(psmErrorResponse, psmErrorResponse.getErrorCode(), psmErrorResponse.getErrorMessage())) {
                    logger.error("Error message from PSM Get payment session call: {}", e.getRaw());
                    retrieveReservationResponse.setErrorPaymentSessionResponse(psmErrorResponse);
                }
            }

        } catch (AppException e) {
            logger.error("Unexpected exception occurred during get show reservation/payment session call: {}", e.getDescription());
            retrieveReservationResponse.setShowReservationResponse(null);
            throw new AppException(Errors.UNEXPECTED_EXCEPTION_DURING_GET_RESERVATION, e, "Unexpected exception occurred during get show reservation.");
        }
        return retrieveReservationResponse;
    }

    @Override
    public PreviewReservationResponse previewReservation(PreviewReservationRequest request) throws AppException {
        throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Item type is currently not supported.");
    }

    public UpdateReservationResponse updateReservation(final UpdateReservationRequest request,
                                                       final PaymentSessionBaseFields paymentSessionBaseFields) throws AppException {

        //Not yet supported for this item type
        throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Item type is currently not supported.");
    }

    @Override
    public CancelReservationResponse cancelReservation(CancelReservationRequest request, PaymentSessionBaseFields paymentSessionBaseFields) throws AppException {
        throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Item type is currently not supported.");
    }

    private void copySelectedDeliveryMethod(final CartLineItem input, final OrderLineItem output, final String reservation) throws AppException {
        final ShowReservationResponse resvResp = !Utils.isEmpty(reservation) ? mapper.readValue(reservation, ShowReservationResponse.class) : null;
        final ShowSelectionDetails showDetails = getShowSelectionDetails(input);
        if (showDetails != null && !Utils.isEmpty(showDetails.getPermissibleDeliveryMethods())) {
            Optional<DeliveryMethod> selectedDeliveryMethod = Optional.empty();
            if (resvResp != null) {
                selectedDeliveryMethod = showDetails.getPermissibleDeliveryMethods().stream()
                        .filter(dm -> dm.getCode().equalsIgnoreCase(resvResp.getDeliveryMethodCode())).findFirst();
            } else {
                selectedDeliveryMethod = showDetails.getPermissibleDeliveryMethods().stream()
                        .filter(dm -> dm.getSelected() == true).findFirst();
            }
            if (selectedDeliveryMethod.isPresent()) {
                output.setSelectedDeliveryMethod(deliveryMethodTransformer.toRight(selectedDeliveryMethod.get()));
            }
        }
    }

    private ShowSelectionDetails getShowSelectionDetails(final CartLineItem input) {
        if (input.getItemSelectionDetails() != null && input.getItemSelectionDetails().getShowSelectionDetails() != null) {
            return input.getItemSelectionDetails().getShowSelectionDetails();
        }
        return null;
    }

    private List<ShowTicket> mapShowTickets(final List<ShowTicketWithDM> showTicketsWithDM) {
        final List<ShowTicket> showTickets = new ArrayList<>();
        if (!Utils.isEmpty(showTicketsWithDM)) {
            showTicketsWithDM.forEach(showTicketWithDM -> {
                final ShowTicket showTicket = new ShowTicket();
                showTicket.setPriceCode(showTicketWithDM.getPriceCode());
                showTicket.setPriceCodeDescription(showTicketWithDM.getPriceCodeDescription());
                showTicket.setHoldClass(showTicketWithDM.getHoldClass());
                showTicket.setBasePrice(showTicketWithDM.getBasePrice());
                showTicket.setDiscountedPrice(showTicketWithDM.getDiscountedPrice());
                showTicket.setHoldId(showTicketWithDM.getHoldId());
                showTicket.setHoldDuration(showTicketWithDM.getHoldDuration());
                showTicket.setDescription(showTicketWithDM.getDescription());
                showTicket.setState(showTicketWithDM.getState());
                showTicket.setShowEventId(showTicketWithDM.getShowEventId());
                showTicket.setTicketTypeCode(showTicketWithDM.getTicketTypeCode());
                showTicket.setTicketTypeCodeDescription(showTicketWithDM.getTicketTypeCodeDescription());
                showTicket.setAccessible(showTicketWithDM.isAccessible());
                showTicket.setAdaCompanion(showTicketWithDM.isAdaCompanion());
                showTicket.setSeat(showTicketWithDM.getSeat());
                showTickets.add(showTicket);
            });
        }
        return showTickets;
    }
    
    private boolean checkSBSHealth() {
        try {
            showBookingAccess.checkShowReservationServiceHealth();
            return true;
        } catch (HttpFailureException e) {
            logger.error("[Error from SBS] Something unexpected happened in show-reservation-health call. Error code:" + e.getHttpCode());
        } catch (AppException e) {
            logger.error("[Error from SBS] Something unexpected happened in show-reservation-health call. Error code:" + e.getCode());
        }
        return false;
    }
    
    private void sendEmail(final ShowChargesResponse showCharge, final ShowReservationRequest showReservationRequest, final CartLineItem input) {
        ShowEvent showEvent = null;
        try {
            showEvent = contentAccess.getShowEventDetailsByEventId(showCharge.getShowEventId());
        } catch (AppException e) {
            logger.error("Exception occurred while fetching show details from content API : ", e);
        }
        
        final String showName = showEvent != null ? showEvent.getShowId() : "";
        final String lowerEnvTag = !StringUtils.equalsAnyIgnoreCase(Runtime.get().readableEnvironment(), "PROD") ? "[NON-PROD]" : "";
        try {
            final Email emailReq = Emailer.wrap(lowerEnvTag + " Urgent! SBS is unresponsive! Incomplete Booking [" + showName + " on " + showCharge.getEventDate() + ", "
                            + showCharge.getEventTime() + "]" + " for Premium Package!",
                    Arrays.asList(!Utils.isEmpty(SBS_SEND_EMAIL_TO_LIST) ? SBS_SEND_EMAIL_TO_LIST.split(",") : new String[] { }), 
                    Arrays.asList(!Utils.isEmpty(SBS_SEND_EMAIL_CC_LIST) ? SBS_SEND_EMAIL_CC_LIST.split(",") : new String[] { }),
                    Arrays.asList(!Utils.isEmpty(SBS_SEND_EMAIL_BCC_LIST) ? SBS_SEND_EMAIL_BCC_LIST.split(",") : new String[] { }));

            final String emailContentBody = createPremiumPackSeatReservationEmailBody(showCharge, showReservationRequest, showEvent != null ? showEvent.getEventCode() : "", input);

            logger.info("Premium package failure email body content generated : {}", emailContentBody);
            emailReq.setHtmlBody(Email.Html.init().body(emailContentBody).build());
            smtpEmailer.send(emailReq);
        } catch (AppException e) {
            logger.error("Exception occurred while sending the email : {}", e.getMessage());
        }
    }

    private String createPremiumPackSeatReservationEmailBody(final ShowChargesResponse showCharge,
            final ShowReservationRequest showReservationRequest, final String eventCode, final CartLineItem input) {
        final String entertainmentOpsEmailTemplate = "<b><u>Show Event Date & Time</u> :</b> %s \n "
                + "<b><u>Order Number</u> :</b> %s \n <b><u>Show Event Code</u> :</b> %s \n <b><u>ShowEventId</u> :</b> %s \n "
                + "<b><u>GSE Confirmation #</u> :</b> %s \n <b><u>Hotel Code</u> :</b> %s \n "
                + "<b><u>Tickets</u> :</b> %s \n <b><u>Charges</u> :</b> %s \n <b><u>Profile</u> :</b> %s \n <b><u>Correlation id</u> :</b> %s";

        final String holdId = Utils.isEmpty(showCharge.getTickets()) ? "MISSING" : showCharge.getTickets().stream().findFirst().get().getHoldId();
        final String tickets = mapper.writeValueAsString(showCharge.getTickets());
        final String charges = mapper.writeValueAsString(showCharge.getCharges());
        final String profile = mapper.writeValueAsString(showReservationRequest.getProfile());
        String jsonAsYamlTickets = "";
        String jsonAsYamlCharges = "";
        String jsonAsYamlProfile = "";
        
        try {
            jsonAsYamlTickets = yamlMapper.writeValueAsString(mapper.readTree(tickets));
            jsonAsYamlCharges = yamlMapper.writeValueAsString(mapper.readTree(charges));
            jsonAsYamlProfile = yamlMapper.writeValueAsString(mapper.readTree(profile));
        } catch (JsonMappingException e) {
            // Do nothing
        } catch (JsonProcessingException e) {
            // Do nothing
        }

        final String showEventDateTime = showCharge.getEventDate() + ", " + showCharge.getEventTime();
        final String paddedJsonAsYamlTickets = "<p style=\"margin-left: 80px\">" + jsonAsYamlTickets + "</p>";
        final String paddedJsonAsYamlCharges = "<p style=\"margin-left: 80px\">" + jsonAsYamlCharges + "</p>";
        final String paddedJsonAsYamlProfile = "<p style=\"margin-left: 80px\">" + jsonAsYamlProfile + "</p>";
        return String
                .format(entertainmentOpsEmailTemplate, showEventDateTime, holdId, eventCode,
                        showCharge.getShowEventId(), input.getConfirmationNumber(), input.getOperaHotelCode(),
                        paddedJsonAsYamlTickets, paddedJsonAsYamlCharges, paddedJsonAsYamlProfile, ThreadContext.getContext().get().getCorrelationId())
                .replace("\n", "<br/><br/>").replace("---", "");
    }
}
