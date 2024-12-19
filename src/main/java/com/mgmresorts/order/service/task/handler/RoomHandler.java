package com.mgmresorts.order.service.task.handler;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.order.dto.services.CancelReservationRequest;
import com.mgmresorts.order.dto.services.CancelReservationResponse;
import com.mgmresorts.rbs.model.CalculateReservationChargesResponseChargesAndTaxes;
import com.mgmresorts.rbs.model.CalculateRoomChargesResponse;
import com.mgmresorts.rbs.model.CancelRoomReservationResponse;
import com.mgmresorts.rbs.model.RefundCommitPutRequest;
import org.apache.commons.lang3.StringUtils;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.function.HeaderBuilder;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.logging.masker.MaskLogger;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IRoomBookingAccess;
import com.mgmresorts.order.backend.handler.IPaymentProcessingHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionRoomHandler;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.database.access.IOrderConfirmationAccess;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.Message;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.OrderLineItem.Status;
import com.mgmresorts.order.dto.services.PreviewReservationRequest;
import com.mgmresorts.order.dto.services.PreviewReservationResponse;
import com.mgmresorts.order.dto.services.RetrieveReservationResponse;
import com.mgmresorts.order.dto.services.SourceSystemError;
import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.dto.services.UpdateReservationRequest;
import com.mgmresorts.order.dto.services.UpdateReservationResponse;
import com.mgmresorts.order.dto.services.Version;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.order.entity.LineItem;
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.entity.OrderConfirmationMapping;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.consumer.IMergeConsumer;
import com.mgmresorts.order.service.task.IProductHandler;
import com.mgmresorts.order.utils.Orders;
import com.mgmresorts.pps.model.PaymentExceptionResponse;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.psm.model.SessionError;
import com.mgmresorts.rbs.model.CreateRoomReservationRequest;
import com.mgmresorts.rbs.model.CreateRoomReservationResponse;
import com.mgmresorts.rbs.model.ErrorResponse;
import com.mgmresorts.rbs.model.GetRoomReservationResponse;
import com.mgmresorts.rbs.model.ModifyCommitErrorResponse;
import com.mgmresorts.rbs.model.ModifyCommitPutRequest;
import com.mgmresorts.rbs.model.PurchasedComponents;
import com.mgmresorts.rbs.model.PurchasedComponentsPrices;
import com.mgmresorts.rbs.model.RatesSummary;
import com.mgmresorts.rbs.model.ReservationProfile;
import com.mgmresorts.rbs.model.RoomBillingDetails;
import com.mgmresorts.rbs.model.RoomBillingDetailsPayment;
import com.mgmresorts.rbs.model.RoomBillingDetailsResponse;
import com.mgmresorts.rbs.model.RoomPrice;
import com.mgmresorts.rbs.model.RoomReservationRequest;
import com.mgmresorts.rbs.model.RoomReservationResponse;
import com.mgmresorts.rbs.model.TripDetails;
import com.mgmresorts.rbs.model.UpdateRoomReservationResponse;
import com.mgmresorts.shopping.cart.dto.AddOnComponent;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.RoomSelectionDetails;

public class RoomHandler implements IProductHandler {
    private static final String TCOLV_PROPERTY_ID = Runtime.get().getConfiguration("tcolv.property.id");
    private static final boolean TCOLV_SKIP_PAYMENT_PROCESSING = Boolean.parseBoolean(Runtime.get().getConfiguration("tcolv.skip.payment.processing"));

    private final Logger logger = Logger.get(RoomHandler.class);
    private final JSonMapper mapper = new JSonMapper();
    
    @Inject
    private ITransformer<GuestProfile, ReservationProfile> roomProfileTransformer;
    @Inject
    private ITransformer<AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> addOnComponentTransformer;
    @Inject
    private ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> roomBillingTransformer;
    @Inject
    private ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> agentTransformer;
    @Inject
    private IOrderAccess orderAccess;
    @Inject
    private IMergeConsumer mergeConsumer;
    @Inject
    private IRoomBookingAccess roomBookingAccess;
    @Inject
    private IPaymentProcessingHandler paymentProcessingHandler;
    @Inject
    private IPaymentSessionRoomHandler paymentSessionRoomHandler;
    @Inject
    private IOrderConfirmationAccess orderConfirmationAccess;
    @Inject
    private Orders orders;
    @Inject
    private ITransformer<com.mgmresorts.order.dto.services.Order, Order> orderTransformer;
    
    public Collection<OrderLineItem> reserve(CheckoutRequest request, List<CartLineItem> roomCartLineItems,
            List<OrderLineItem> roomOrderLineItems, AppliedBillings billable, String orderId,
            com.mgmresorts.shopping.cart.dto.AgentInfo agentInfo, boolean skipAFS, boolean skipPaymentCapture,
            Map<String, PaymentAuthFields> paymentAuthFieldsMap, String orderReferenceNumber) throws AppException {
        return roomCartLineItems.stream().map(room -> {
            try {
                final OrderLineItem orderLineItem = roomOrderLineItems.stream().filter(li -> room.getCartLineItemId().equalsIgnoreCase(li.getCartLineItemId())).findFirst().get();
                return checkout(request, room, orderLineItem, billable, orderId, agentInfo, skipAFS, skipPaymentCapture,
                        paymentAuthFieldsMap, orderReferenceNumber);
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
        final boolean isPackage = request.getCartType() == com.mgmresorts.order.dto.services.Type.PACKAGE;
        final boolean skipTCOLVPaymentProcessing = TCOLV_SKIP_PAYMENT_PROCESSING && (StringUtils.isNotBlank(cartLineItem.getPropertyId())
                ? cartLineItem.getPropertyId().equalsIgnoreCase(TCOLV_PROPERTY_ID) : false);
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

        try {
            final String itineraryId = request.getItineraryId();
            orderLineItem.setItineraryId(itineraryId);
            copySelectedAddOns(cartLineItem, orderLineItem);
            final RoomSelectionDetails roomDetails = getRoomSelectionDetails(cartLineItem);
            if (roomDetails != null) {
                orderLineItem.setSpecialRequests(roomDetails.getSpecialRequests());
                orderLineItem.setNumberOfNights(roomDetails.getNumberOfNights());
                orderLineItem.setProgramId(roomDetails.getProgramId());
            }

            final CalculateRoomChargesResponse roomCharge = mapper.readValue(cartLineItem.getContent(), CalculateRoomChargesResponse.class);
            final CreateRoomReservationRequest roomReservationRequest = generateCreateRoomReservationRequest(
                    request, cartLineItem, roomCharge, billable, agentInfo, itineraryId, orderId, orderLineItem, skipAFS, paymentAuthFields, skipPaymentCapture);
            final String roomReservationResponse = roomBookingAccess.createRoomReservation(roomReservationRequest);
            if (skipPaymentCapture && (cartLineItem.getPaymentRequired() != null && cartLineItem.getPaymentRequired()) && paymentAuthFields != null
                    && paymentAuthFields.getAmount() > 0 && !skipTCOLVPaymentProcessing) {
                final CreateRoomReservationResponse resvResp = mapper.readValue(roomReservationResponse, CreateRoomReservationResponse.class);
                paymentAuthFields.setConfirmationNumber(resvResp.getRoomReservation().getConfirmationNumber());
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
            addSuccess(cartLineItem, orderLineItem, roomReservationResponse);
        } catch (HttpFailureException e) {
            logger.error("[Error from RBS] Create Reservation call failed : ", e.getMessage());
            final String errorPayload = e.getPayload();
            if (skipPaymentCapture && (cartLineItem.getPaymentRequired() != null && cartLineItem.getPaymentRequired()) && paymentAuthFields != null
                    && paymentAuthFields.getAmount() > 0 && !skipTCOLVPaymentProcessing) {
                paymentProcessingHandler.voidTransaction(orderId, orderReferenceNumber, paymentAuthFields);
            }
            addRoomFailure(cartLineItem, orderLineItem, errorPayload, Errors.UNABLE_TO_BOOK_ROOM, e);
        } catch (AppException e) {
            logger.error("[Error from RBS] Create Reservation call failed : ", e.getMessage());
            if (skipPaymentCapture && (cartLineItem.getPaymentRequired() != null && cartLineItem.getPaymentRequired()) && paymentAuthFields != null
                    && paymentAuthFields.getAmount() > 0 && !skipTCOLVPaymentProcessing) {
                paymentProcessingHandler.voidTransaction(orderId, orderReferenceNumber, paymentAuthFields);
            }
            addRoomFailure(cartLineItem, orderLineItem, e.getDescription(), e.getCode(), null);
        } catch (Exception e) {
            logger.error("[Error from RBS] Create Reservation call failed : ", e.getMessage());
            if (skipPaymentCapture && (cartLineItem.getPaymentRequired() != null && cartLineItem.getPaymentRequired()) && paymentAuthFields != null
                    && paymentAuthFields.getAmount() > 0 && !skipTCOLVPaymentProcessing) {
                paymentProcessingHandler.voidTransaction(orderId, orderReferenceNumber, paymentAuthFields);
            }
            addRoomFailure(cartLineItem, orderLineItem, e.getMessage(), 500, null);
        }
        
        if (!isPackage) {
            final Consumer<Order> merger = mergeConsumer.create(orderLineItem);
            orderAccess.mergeAndUpdate(orderId, Order.class, merger);
        }        
        return orderLineItem;
    }

    private void addSuccess(final CartLineItem input, final OrderLineItem output, final String reservation) {
        final CreateRoomReservationResponse resvResp = mapper.readValue(reservation, CreateRoomReservationResponse.class);
        final String roomReservationMaskedGroup = "room-reservation-content";
        final String maskedRoomReservationContent = logger.getJsonLogger().doMask(reservation,
                MaskLogger.MASKABLE_FIELDS.getOrDefault(roomReservationMaskedGroup, new ArrayList<String>()));
        output.setContent(maskedRoomReservationContent);
        output.setCartLineItemId(input.getCartLineItemId());
        output.setConfirmationNumber(resvResp.getRoomReservation().getConfirmationNumber());
        output.setOperaHotelCode(resvResp.getRoomReservation().getOperaHotelCode());
        output.setOperaConfirmationNumber(resvResp.getRoomReservation().getOperaConfirmationNumber());
        output.setStatus(Status.SUCCESS);
        final RatesSummary resvRateSummary = resvResp.getRoomReservation().getRatesSummary();
        output.setLineItemCharge(resvRateSummary.getRoomSubtotal());
        output.setLineItemPrice(resvRateSummary.getReservationTotal());
        output.setLineItemDeposit(resvRateSummary.getDepositDue());
        output.setLineItemDiscount(resvRateSummary.getProgramDiscount());
        output.setLineItemBalance(resvRateSummary.getBalanceUponCheckIn());
        output.setLineItemTourismFeeAndTax(resvRateSummary.getTourismFeeAndTax());
        output.setLineItemResortFeePerNight(resvRateSummary.getResortFeePerNight());
        output.setLineItemOccupancyFee(resvRateSummary.getOccupancyFee());
        output.setLineItemResortFeeAndTax(resvRateSummary.getResortFeeAndTax());
        final List<RoomPrice> bookings = resvResp.getRoomReservation().getBookings();
        final double adjustedRoomSubtotal = Optional.ofNullable(resvRateSummary.getAdjustedRoomSubtotal()).orElse(0d);
        final double averagePricePerNight = Utils.isEmpty(bookings) ? 0d : adjustedRoomSubtotal / bookings.size();
        output.setAveragePricePerNight(averagePricePerNight);
        output.setLineItemAdjustedItemSubtotal(resvRateSummary.getAdjustedRoomSubtotal());
        output.setLineItemTripSubtotal(resvRateSummary.getTripSubtotal());
        output.setLineItemCasinoSurcharge(resvRateSummary.getCasinoSurcharge());
        output.setLineItemCasinoSurchargeAndTax(resvRateSummary.getCasinoSurchargeAndTax());

        output.setF1Package(resvResp.getRoomReservation().isF1Package() != null ? resvResp.getRoomReservation().isF1Package() : false);

        final double roomChargeTax = Optional.ofNullable(resvRateSummary.getRoomChargeTax()).orElse(0d);

        // will need to revisit totalTax for MLOB cart. For now, clients want
        // roomEstimatedTax to be
        // the summation for totalTax
        // final double totalTax = roomEstimatedTax + resortFeeAndTax + tourismFeeAndTax
        // - resortFee - tourismFee;
        output.setLineItemTax(Utils.roundTwoDecimalPlaces(roomChargeTax));
        
        output.setAddOnsPrice(input.getAddOnsPrice());
        output.setAddOnsTax(input.getAddOnsTax());
    }

    private void addRoomFailure(final CartLineItem input, final OrderLineItem output, final String errorPayload, final int code, final HttpFailureException exception) {
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
                    if (StringUtils.equals(sseCode, "632-2-159") || StringUtils.equals(sseCode, "632-2-160")
                            || (StringUtils.equals(sseCode, "632-2-242")
                                    && (sseMessage.toLowerCase().contains("creditcard") || sseMessage.toLowerCase().contains("credit card")))
                            || (StringUtils.equals(sseCode, "632-2-243")
                                    && sseMessage.toLowerCase().contains("payment")) || StringUtils.equals(sseCode, "632-1-279")) {
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
        msg.setCode(new AppException(Errors.UNABLE_TO_GET_PAYMENT_SESSION).getDisplayCode());

        final SourceSystemError sse = new SourceSystemError();
        sse.setSourceSystemMessage(errorMessage);
        sse.setSourceSystemCode(errorCode);

        msg.setSourceSystemError(sse);

        addFailure(input, output, msg, true);
    }

    private void addFailure(final CartLineItem input, final OrderLineItem output, final Message msg, final boolean paymentFailure) {

        output.setContent(input.getContent());
        output.setCartLineItemId(input.getCartLineItemId());
        output.setConfirmationNumber(input.getConfirmationNumber());

        output.setLineItemCharge(input.getLineItemTotalCharges());
        output.setLineItemPrice(input.getLineItemPrice());
        output.setLineItemDeposit(input.getLineItemDeposit());
        output.setLineItemDiscount(input.getLineItemDiscount());
        output.setLineItemBalance(input.getLineItemBalance());
        output.setAddOnsPrice(input.getAddOnsPrice());
        output.setAddOnsTax(input.getAddOnsTax());
        if (input.getItemSelectionDetails().getRoomSelectionDetails() != null && input.getItemSelectionDetails().getRoomSelectionDetails().getRatesSummary() != null) {
            output.setLineItemTourismFeeAndTax(input.getItemSelectionDetails().getRoomSelectionDetails().getRatesSummary().getTourismFeeAndTax());
            output.setLineItemResortFeePerNight(input.getItemSelectionDetails().getRoomSelectionDetails().getRatesSummary().getResortFeePerNight());
            output.setLineItemOccupancyFee(input.getItemSelectionDetails().getRoomSelectionDetails().getRatesSummary().getOccupancyFee());
            output.setLineItemResortFeeAndTax(input.getItemSelectionDetails().getRoomSelectionDetails().getRatesSummary().getResortFeeAndTax());
            output.setAveragePricePerNight(input.getItemSelectionDetails().getRoomSelectionDetails().getRatesSummary().getAveragePricePerNight());
            output.setLineItemTripSubtotal(input.getItemSelectionDetails().getRoomSelectionDetails().getRatesSummary().getTripSubtotal());
            output.setLineItemCasinoSurcharge(input.getItemSelectionDetails().getRoomSelectionDetails().getRatesSummary().getCasinoSurcharge());
            output.setLineItemCasinoSurchargeAndTax(input.getItemSelectionDetails().getRoomSelectionDetails().getRatesSummary().getCasinoSurchargeAndTax());
            final Boolean f1Package = input.getItemSelectionDetails().getRoomSelectionDetails().getF1Package();
            output.setF1Package(f1Package != null ? f1Package : false);

            final double roomChargeTax = Optional.ofNullable(input.getItemSelectionDetails().getRoomSelectionDetails().getRatesSummary().getRoomChargeTax()).orElse(0d);

            // will need to revisit totalTax for MLOB cart. For now, clients want
            // roomEstimatedTax to be
            // the summation for totalTax
            // final double totalTax = roomEstimatedTax + resortFeeAndTax + tourismFeeAndTax
            // - resortFee - tourismFee;
            output.setLineItemTax(Utils.roundTwoDecimalPlaces(roomChargeTax));
        }
        output.setLineItemAdjustedItemSubtotal(input.getAdjustedItemSubtotal());

        if (paymentFailure) {
            output.setStatus(Status.PAYMENT_FAILURE);
        } else {
            output.setStatus(Status.FAILURE);
        }
        output.getMessages().add(msg);
    }

    private CreateRoomReservationRequest generateCreateRoomReservationRequest(CheckoutRequest request,
            CartLineItem input, CalculateRoomChargesResponse roomCharge, AppliedBillings billable,
            com.mgmresorts.shopping.cart.dto.AgentInfo agentInfo, String itineraryId, String orderId,
            OrderLineItem orderLineItem, boolean skipAFS, PaymentAuthFields paymentAuthFields, boolean skipPaymentCapture) throws AppException {
        final RoomReservationRequest roomReservationRequest = new RoomReservationRequest();
        roomReservationRequest.setId(UUID.randomUUID().toString());
        roomReservationRequest.setPropertyId(roomCharge.getPropertyId());
        roomReservationRequest.setItineraryId(itineraryId);
        roomReservationRequest.setRoomTypeId(roomCharge.getRoomTypeId());
        roomReservationRequest.setProgramId(roomCharge.getProgramId());
        roomReservationRequest.setCustomerId(request.getGuestProfile().getId());
        roomReservationRequest.setGuaranteeCode(roomCharge.getGuaranteeCode());

        roomReservationRequest.setHoldId(input.getCartLineItemId());
        roomReservationRequest.setOrderId(orderId);
        roomReservationRequest.setOrderLineItemId(orderLineItem.getOrderLineItemId());

        roomReservationRequest.setPerpetualPricing(CommonConfig.getTokenPerpetualFlag());
        roomReservationRequest.setConfirmationNumber(!Utils.isEmpty(input.getConfirmationNumber()) ? input.getConfirmationNumber() : null);

        roomReservationRequest.setSpecialRequests(new ArrayList<String>());
        final RoomSelectionDetails roomDetails = getRoomSelectionDetails(input);
        if (roomDetails != null) {
            roomReservationRequest.setMyVegasPromoCode(!Utils.isEmpty(roomDetails.getMyVegasCode()) ? roomDetails.getMyVegasCode() : null);

            if (!Utils.isEmpty(roomDetails.getAddOnComponents())) {
                final List<String> addonIds = roomDetails.getAddOnComponents().stream().filter(addOn -> addOn.getSelected()).map(item -> item.getId()).collect(Collectors.toList());
                if (!Utils.isEmpty(addonIds)) {
                    roomReservationRequest.getSpecialRequests().addAll(addonIds);
                }
            }

            if (!Utils.isEmpty(roomDetails.getSpecialRequests())) {
                final List<String> splRequests = new ArrayList<>();
                splRequests.addAll(roomDetails.getSpecialRequests());
                roomReservationRequest.setComments(splRequests.stream().findFirst().get());
                splRequests.remove(0);
                roomReservationRequest.setAdditionalComments(splRequests);
            }

            if (!Utils.isEmpty(roomCharge.getPkgComponents())) {
                roomReservationRequest.setPkgComponents(roomCharge.getPkgComponents());
            }
        }

        if (!Utils.isEmpty(roomCharge.getSpecialRequests())) {
            roomReservationRequest.getSpecialRequests().addAll(roomCharge.getSpecialRequests());
        }
        roomReservationRequest.setBookings(roomCharge.getBookings());
        final TripDetails tripDetails = roomCharge.getTripDetails();
        if (tripDetails.getNumRooms() == null || tripDetails.getNumRooms() == 0) {
            tripDetails.setNumRooms(1);
        }
        roomReservationRequest.setTripDetails(tripDetails);
        roomReservationRequest.setProfile(roomProfileTransformer.toRight(request.getGuestProfile()));
        final double deposit = (input.getLineItemDeposit() != null) ? input.getLineItemDeposit().doubleValue() : 0;
        final Collection<Billing> bill = billable.bill(deposit);
        roomReservationRequest.setBilling((List<RoomBillingDetails>) roomBillingTransformer.toRight(bill));
        if (skipPaymentCapture && (input.getPaymentRequired() != null && input.getPaymentRequired()) && paymentAuthFields != null) {
            roomReservationRequest.setSkipPaymentProcess(true);
            roomReservationRequest.setSkipFraudCheck(true);
            if (!Utils.isEmpty(roomReservationRequest.getBilling())) {
                roomReservationRequest.getBilling().forEach(billing -> billing.getPayment().setAuthId(paymentAuthFields.getAuthorizationCode()));
            }
        }
        roomReservationRequest.setChargesAndTaxes(Utils.cloneByJson(CalculateReservationChargesResponseChargesAndTaxes.class,roomCharge.getChargesAndTaxes()));
        roomReservationRequest.setDepositDetails(roomCharge.getDepositDetails());
        roomReservationRequest.setAgentInfo(agentTransformer.toRight(agentInfo));
        roomReservationRequest.setNrgStatus(false);
        roomReservationRequest.setGuaranteeCode(roomCharge.getGuaranteeCode());
        roomReservationRequest.setInAuthTransactionId(request.getInAuthTransactionId());
        roomReservationRequest.setDepositPolicy(roomCharge.getDepositPolicy());

        if (!Utils.isEmpty(roomCharge.getPromo())) {
            roomReservationRequest.setPromo(roomCharge.getPromo());
        } else if (!Utils.isEmpty(input.getPromo())) {
            roomReservationRequest.setPromo(input.getPromo());
        }
        
        final boolean skipEmailNotification = request.getCartVersion() != null && request.getCartVersion().equals(Version.V2)
                && request.getCartType() != null && request.getCartType().equals(Type.PACKAGE);
        roomReservationRequest.setSkipCustomerNotification(skipEmailNotification);

        final CreateRoomReservationRequest createRoomReservationRequest = new CreateRoomReservationRequest();
        createRoomReservationRequest.setRoomReservation(roomReservationRequest);
        return createRoomReservationRequest;
    }

    private void copySelectedAddOns(final CartLineItem input, final OrderLineItem output) throws AppException {
        final RoomSelectionDetails roomDetails = getRoomSelectionDetails(input);
        if (roomDetails != null && !Utils.isEmpty(roomDetails.getAddOnComponents())) {
            final List<AddOnComponent> selectedAddOns = roomDetails.getAddOnComponents().stream().filter(addOn -> addOn.getSelected()).collect(Collectors.toList());
            final Collection<com.mgmresorts.order.dto.AddOnComponent> addons = addOnComponentTransformer.toRight(selectedAddOns);
            output.setAddOnComponents(!Utils.isEmpty(addons) ? (List<com.mgmresorts.order.dto.AddOnComponent>) addons : new ArrayList<>());
        }
    }

    private RoomSelectionDetails getRoomSelectionDetails(final CartLineItem input) {
        if (input.getItemSelectionDetails() != null && input.getItemSelectionDetails().getRoomSelectionDetails() != null) {
            return input.getItemSelectionDetails().getRoomSelectionDetails();
        }
        return null;
    }

    public RetrieveReservationResponse getReservation(final String confirmationNumber, final String firstName,
            final String lastName, final boolean createPaymentSession, final String paymentSessionId)
            throws AppException {
        
        final RetrieveReservationResponse retrieveReservationResponse = new RetrieveReservationResponse();
        retrieveReservationResponse.setHeader(HeaderBuilder.buildHeader());

        try {
            final GetRoomReservationResponse getRoomReservationResponse = roomBookingAccess.getRoomReservation(confirmationNumber, firstName, lastName);
            final RoomReservationResponse roomReservationResponse = getRoomReservationResponse.getRoomReservation();
            retrieveReservationResponse.setRoomReservationResponse(roomReservationResponse);
            
            if (createPaymentSession) {
                final EnableSessionResponse enableSessionResponse = paymentSessionRoomHandler.managePaymentSessionForRoomReservation(roomReservationResponse,
                        paymentSessionId, CallType.CREATE);
                retrieveReservationResponse.setPaymentSessionId(enableSessionResponse.getSessionId());
            }
        } catch (SourceAppException e) {
            retrieveReservationResponse.setRoomReservationResponse(null);
            final com.mgmresorts.rbs.model.ErrorResponse rbsErrorResponse = mapper.readValue(e.getRaw(), com.mgmresorts.rbs.model.ErrorResponse.class);
            if (rbsErrorResponse != null && rbsErrorResponse.getError() != null) {
                logger.error("Error message from RBS Get Room reservation call: {}", e.getRaw());
                retrieveReservationResponse.setErrorRoomReservationResponse(rbsErrorResponse);

            } else {
                final SessionError psmErrorResponse = mapper.readValue(e.getRaw(), SessionError.class);
                if (!Utils.anyNull(psmErrorResponse, psmErrorResponse.getErrorCode(), psmErrorResponse.getErrorMessage())) {
                    logger.error("Error message from PSM Get payment session call: {}", e.getRaw());
                    retrieveReservationResponse.setErrorPaymentSessionResponse(psmErrorResponse);
                }
            }
        } catch (AppException e) {
            logger.error("Unexpected exception occurred during get room reservation/payment session call: {}", e.getDescription());
            retrieveReservationResponse.setRoomReservationResponse(null);
            throw new AppException(Errors.UNEXPECTED_EXCEPTION_DURING_GET_RESERVATION, e, "Unexpected exception occurred during get room reservation.");
        }
        return retrieveReservationResponse;
    }

    @Override
    public PreviewReservationResponse previewReservation(PreviewReservationRequest request) throws AppException {
        final PreviewReservationResponse previewReservationResponse = new PreviewReservationResponse();
        previewReservationResponse.setHeader(HeaderBuilder.buildHeader());
        previewReservationResponse.setPaymentSessionId(request.getPaymentSessionId());
        try {
            final UpdateRoomReservationResponse updateRoomReservationResponse = roomBookingAccess.previewRoomReservation(request.getPreviewRoomReservationRequest());
            previewReservationResponse.setRoomReservationResponse(updateRoomReservationResponse.getRoomReservation());
            final EnableSessionResponse updateSessionResponse = paymentSessionRoomHandler
                    .managePaymentSessionForRoomReservation(updateRoomReservationResponse.getRoomReservation(), request.getPaymentSessionId(), CallType.UPDATE);
            previewReservationResponse.setPaymentSessionId(updateSessionResponse.getSessionId());
        } catch (SourceAppException e) {
            previewReservationResponse.setRoomReservationResponse(null);
            final com.mgmresorts.rbs.model.ErrorResponse rbsErrorResponse = mapper.readValue(e.getRaw(), com.mgmresorts.rbs.model.ErrorResponse.class);
            if (rbsErrorResponse != null && rbsErrorResponse.getError() != null) {
                logger.error("Error message from RBS Room reservation preview call: {}", e.getRaw());
                previewReservationResponse.setErrorRoomReservationResponse(rbsErrorResponse);
            } else {
                final SessionError psmErrorResponse = mapper.readValue(e.getRaw(), SessionError.class);
                if (!Utils.anyNull(psmErrorResponse, psmErrorResponse.getErrorCode(), psmErrorResponse.getErrorMessage())) {
                    logger.error("Error message from PSM Update payment session call: {}", e.getRaw());
                    previewReservationResponse.setErrorPaymentSessionResponse(psmErrorResponse);
                }
            }
        } catch (AppException e) {
            logger.error("Unexpected exception occurred during room reservation preview/payment session call: {}", e.getDescription());
            previewReservationResponse.setRoomReservationResponse(null);
            throw new AppException(ApplicationError.UNEXPECTED_EXCEPTION_DURING_RESERVATION_PREVIEW, e, "Unexpected exception occurred during room reservation preview.");
        }
        return previewReservationResponse;
    }

    public UpdateReservationResponse updateReservation(final UpdateReservationRequest request,
                                                       final PaymentSessionBaseFields paymentSessionBaseFields) throws AppException {
        if (request.getModifyRoomReservationRequest() == null || Utils.isEmpty(request.getModifyRoomReservationRequest().getConfirmationNumber())) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Invalid input. Please check update reservation request for all mandatory arguments.");
        }

        final ModifyCommitPutRequest roomUpdateRequest = request.getModifyRoomReservationRequest();
        roomUpdateRequest.setSkipPaymentProcess(true);

        final PaymentAuthFields paymentAuthFields = paymentSessionBaseFields.getPaymentAuthFieldsMap().get(roomUpdateRequest.getConfirmationNumber());
        
        final boolean skipTCOLVPaymentProcessing = TCOLV_SKIP_PAYMENT_PROCESSING && (StringUtils.isNotBlank(paymentAuthFields.getPropertyId())
                ? paymentAuthFields.getPropertyId().equalsIgnoreCase(TCOLV_PROPERTY_ID) : false);

        // 3) Verify necessary fields exist
        if (roomUpdateRequest.getPreviewReservationChangeInDeposit() != null && roomUpdateRequest.getPreviewReservationChangeInDeposit().doubleValue() > 0) {
            if ((Utils.isEmpty(paymentSessionBaseFields.getPaymentAuthFieldsMap())) || Utils.isEmpty(paymentSessionBaseFields.getBillings())) {
                throw new AppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "Payment billing details are not found in payment session.");
            }

            if (paymentAuthFields == null || Utils.isEmpty(paymentAuthFields.getAuthorizationCode())) {
                throw new AppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "Payment authorization details are not found in payment session.");
            }

            roomUpdateRequest.setAuthId(paymentAuthFields.getAuthorizationCode());
        }

        final List<Billing> billings = paymentSessionBaseFields.getBillings();
        final String orderReferenceNumber = paymentSessionBaseFields.getOrderReferenceNumber();
        final String sessionId = request.getPaymentSessionId();

        final UpdateReservationResponse response = new UpdateReservationResponse();
        response.setHeader(HeaderBuilder.buildHeader());
        response.setPaymentSessionId(sessionId);

        ModifyCommitErrorResponse commitResponse = null;
        boolean updateOrder = false;
        final Order order = getOrderByConfirmationNumber(request.getModifyRoomReservationRequest().getConfirmationNumber());
        if (order != null) {
            response.setOrderId(order.getId());
        }

        try {
            // 4) Update room reservation via RBS
            commitResponse = roomBookingAccess.commitRoomReservation(roomUpdateRequest);
        } catch (SourceAppException e) {
            // void any failures if payment id is valid & amount > 0 (payment id should only exist in capture case)
            if (paymentAuthFields != null && !Utils.isEmpty(paymentAuthFields.getPaymentId()) && paymentAuthFields.getAmount() > 0 && !skipTCOLVPaymentProcessing) {
                paymentProcessingHandler.voidTransaction(null, orderReferenceNumber, paymentAuthFields);
            }
            final ModifyCommitErrorResponse rbsErrorResponse = mapper.readValue(e.getRaw(), ModifyCommitErrorResponse.class);
            if (rbsErrorResponse != null && rbsErrorResponse.getError() != null) {
                logger.error("Error message from RBS Commit room reservation call: {}", e.getRaw());
                response.setErrorRoomReservationResponse(rbsErrorResponse.getError());
            }
        } catch (AppException e) {
            if (paymentAuthFields != null && !Utils.isEmpty(paymentAuthFields.getPaymentId()) && paymentAuthFields.getAmount() > 0 && !skipTCOLVPaymentProcessing) {
                paymentProcessingHandler.voidTransaction(null, orderReferenceNumber, paymentAuthFields);
            }
            logger.error("Unexpected exception occurred during room reservation commit call: {}", e.getDescription());
        }

        if (commitResponse != null && commitResponse.getRoomReservation() != null) {
            response.setRoomReservationResponse(commitResponse.getRoomReservation());
            if (commitResponse.getError() == null) {
                // 5.A) On success, update order history & capture if amount > 0, refund if amount < 0
                if (commitResponse.getRoomReservation().getRatesSummary() != null
                        && commitResponse.getRoomReservation().getRatesSummary().getChangeInDeposit() != null) {
                    final double changeInDepositAmount = commitResponse.getRoomReservation().getRatesSummary().getChangeInDeposit();
                    if (changeInDepositAmount > 0) {
                        updateOrder = true;
                        if (!skipTCOLVPaymentProcessing) {
                            try {
                                paymentProcessingHandler.captureTransaction(order != null ? order.getId() : null,
                                        order != null ? order.getCartId() : orderReferenceNumber, paymentAuthFields);
                            } catch (SourceAppException e) {
                                final PaymentExceptionResponse ppsErrorResponse = mapper.readValue(e.getRaw(), PaymentExceptionResponse.class);
                                if (!Utils.anyNull(ppsErrorResponse, ppsErrorResponse.getErrorCode(), ppsErrorResponse.getErrorMessage())) {
                                    logger.error("Error message from PPS capture call: {}", e.getRaw());
                                    response.setErrorPaymentProcessingResponse(ppsErrorResponse);
                                }
                            } catch (AppException e) {
                                logger.error("Unexpected exception occurred during room reservation payment capture call: {}", e.getDescription());
                            }
                        }
                    } else if (changeInDepositAmount < 0) {
                        if (!skipTCOLVPaymentProcessing) {
                            boolean releaseReservation = false;
                            try {
                                final PaymentAuthFields refundResponse = paymentProcessingHandler.refundTransaction(order != null ? order.getId() : null,
                                        order != null ? order.getCartId() : orderReferenceNumber, roomUpdateRequest.getConfirmationNumber(),
                                        Math.abs(changeInDepositAmount), billings, paymentAuthFields != null ? paymentAuthFields.getSessionId() : null,
                                        paymentAuthFields != null ? paymentAuthFields.getItemId() : null);
                                if (refundResponse != null && refundResponse.isSuccess()) {
                                    final UpdateRoomReservationResponse refundCommittResponse = roomBookingAccess.refundCommitRoomReservation(createRefundCommitRequest(
                                            request.getModifyRoomReservationRequest().getFirstName(), request.getModifyRoomReservationRequest().getLastName(),
                                            request.getModifyRoomReservationRequest().getConfirmationNumber(), refundResponse.getAuthorizationCode(),
                                            commitResponse.getRoomReservation().getBilling(), commitResponse.getRoomReservation().getDepositDetails().getRefundAmount()));
                                    response.setRoomReservationResponse(refundCommittResponse.getRoomReservation());
                                    updateOrder = true;
                                } else {
                                    releaseReservation = true;
                                    final PaymentExceptionResponse ppsErrorResponse = new PaymentExceptionResponse();
                                    ppsErrorResponse.setErrorCode(refundResponse.getErrorCode());
                                    ppsErrorResponse.setErrorMessage(refundResponse.getErrorMessage());
                                    logger.error("Error message from PPS refund call: {}", refundResponse.getErrorCode() + "|" + refundResponse.getErrorMessage());
                                    response.setErrorPaymentProcessingResponse(ppsErrorResponse);
                                }
                            } catch (SourceAppException e) {
                                final ErrorResponse rbsErrorResponse = mapper.readValue(e.getRaw(), ErrorResponse.class);
                                if (rbsErrorResponse != null && rbsErrorResponse.getError() != null) {
                                    logger.error("Error message from RBS commit refund call: {}", e.getRaw());
                                    response.setErrorRoomReservationResponse(rbsErrorResponse.getError());
                                } else {
                                    releaseReservation = true;
                                    final PaymentExceptionResponse ppsErrorResponse = mapper.readValue(e.getRaw(), PaymentExceptionResponse.class);
                                    if (!Utils.anyNull(ppsErrorResponse, ppsErrorResponse.getErrorCode(), ppsErrorResponse.getErrorMessage())) {
                                        logger.error("Error message from PPS refund call: {}", e.getRaw());
                                        response.setErrorPaymentProcessingResponse(ppsErrorResponse);
                                    }
                                }
                            } catch (AppException e) {
                                logger.error("Unexpected exception occurred during room reservation payment refund call: {}", e.getDescription());
                            }
                            if (releaseReservation) {
                                try {
                                    final RoomReservationResponse roomReservationResponse = commitResponse.getRoomReservation();
                                    roomBookingAccess.releaseRoomReservation(roomReservationResponse.getPropertyId(),
                                            roomReservationResponse.getConfirmationNumber(), roomReservationResponse.getHoldId(),
                                            roomReservationResponse.isF1Package() != null ? roomReservationResponse.isF1Package() : false);
                                } catch (Exception e) {
                                    logger.error("Unexpected exception occurred during room reservation release call. Message: {}", e.getMessage());
                                }
                            }
                        } else {
                            updateOrder = true;
                        }
                    }
                }
            } else {
                // 5.B) On failure, void if amount > 0 and update payment session for price change error
                try {
                    response.setErrorRoomReservationResponse(commitResponse.getError());
                    if (paymentAuthFields != null && !Utils.isEmpty(paymentAuthFields.getPaymentId()) && paymentAuthFields.getAmount() > 0 && !skipTCOLVPaymentProcessing) {
                        paymentProcessingHandler.voidTransaction(null, orderReferenceNumber, paymentAuthFields);
                    }
                    // update new price on payment session (price change error)
                    final EnableSessionResponse enableSessionResponse = paymentSessionRoomHandler
                            .managePaymentSessionForRoomReservation(commitResponse.getRoomReservation(), sessionId, CallType.UPDATE);
                    response.setPaymentSessionId(enableSessionResponse != null ? enableSessionResponse.getSessionId() : null);
                } catch (SourceAppException e) {
                    final SessionError psmErrorResponse = mapper.readValue(e.getRaw(), SessionError.class);
                    if (psmErrorResponse != null) {
                        logger.error("Error message from PSM Update payment session call: {}", e.getRaw());
                        response.setErrorPaymentSessionResponse(psmErrorResponse);
                    }
                } catch (AppException e) {
                    logger.error("Unexpected exception occurred during payment session update call: {}", e.getDescription());
                }
            }
        }
        if (updateOrder) {
            updateReservationOrderHistory(commitResponse.getRoomReservation(), order);
        }
        return response;
    }

    @Override
    public CancelReservationResponse cancelReservation(CancelReservationRequest request, PaymentSessionBaseFields paymentSessionBaseFields) throws AppException {
        if (request.getCancelRoomReservationRequest() == null || Utils.isEmpty(request.getCancelRoomReservationRequest().getConfirmationNumber())) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Invalid input. Please check cancel reservation request for all mandatory arguments.");
        }
        
        final CancelReservationResponse cancelReservationResponse = new CancelReservationResponse();
        cancelReservationResponse.setHeader(HeaderBuilder.buildHeader());
        try {
            final List<Billing> billings = paymentSessionBaseFields.getBillings();
            final String orderReferenceNumber = paymentSessionBaseFields.getOrderReferenceNumber();
            request.getCancelRoomReservationRequest().setSkipPaymentProcess(true);
            request.getCancelRoomReservationRequest().setCancelPending(true);

            final PaymentAuthFields paymentAuthFields =
                    paymentSessionBaseFields.getPaymentAuthFieldsMap().get(request.getCancelRoomReservationRequest().getConfirmationNumber());

            final boolean skipTCOLVPaymentProcessing = TCOLV_SKIP_PAYMENT_PROCESSING
                    && ((paymentAuthFields != null && StringUtils.isNotBlank(paymentAuthFields.getPropertyId()))
                    ? paymentAuthFields.getPropertyId().equalsIgnoreCase(TCOLV_PROPERTY_ID) : false);

            final OrderConfirmationMapping orderConfirmationMapping = orderConfirmationAccess
                    .getOrderByConfirmationNumber(request.getCancelRoomReservationRequest().getConfirmationNumber());

            if (skipTCOLVPaymentProcessing) {
                request.getCancelRoomReservationRequest().setSkipPaymentProcess(true);
                request.getCancelRoomReservationRequest().setCancelPending(false);
                final CancelRoomReservationResponse cancelRoomReservationResponse = roomBookingAccess.cancelRoomReservation(request.getCancelRoomReservationRequest());
                cancelReservationResponse.setRoomReservationResponse(cancelRoomReservationResponse.getRoomReservation());
                if (orderConfirmationMapping != null) {
                    updateOrderLineItemContentAndStatus(orderConfirmationMapping, cancelRoomReservationResponse);
                    cancelReservationResponse.setOrderId(orderConfirmationMapping.getId());
                }
            } else {
                final CancelRoomReservationResponse cancelPendingRoomReservationResponse = roomBookingAccess.cancelRoomReservation(request.getCancelRoomReservationRequest());
                cancelReservationResponse.setRoomReservationResponse(cancelPendingRoomReservationResponse.getRoomReservation());

                if ((cancelPendingRoomReservationResponse.getRoomReservation().isDepositForfeit() != null
                        && !cancelPendingRoomReservationResponse.getRoomReservation().isDepositForfeit())
                        && cancelPendingRoomReservationResponse.getRoomReservation().getDepositDetails().getRefundAmount().doubleValue() > 0) {
                    final PaymentAuthFields refundResponse = paymentProcessingHandler.refundTransaction(
                            orderConfirmationMapping != null ? orderConfirmationMapping.getId() : null,
                            orderReferenceNumber,
                            request.getCancelRoomReservationRequest().getConfirmationNumber(),
                            cancelPendingRoomReservationResponse.getRoomReservation().getDepositDetails().getRefundAmount().doubleValue(),
                            billings,
                            request.getPaymentSessionId(),
                            paymentAuthFields != null ? paymentAuthFields.getItemId() : null);

                    if (refundResponse != null && refundResponse.isSuccess() && !Utils.isEmpty(refundResponse.getAuthorizationCode())) {
                        request.getCancelRoomReservationRequest().setCancelPending(false);
                        final List<RoomBillingDetails> roomBillingDetailsRequestList =
                                transformRoomBillingDetailsResponse(cancelPendingRoomReservationResponse.getRoomReservation().getBilling());
                        roomBillingDetailsRequestList.forEach(billing -> billing.getPayment().setAuthId(refundResponse.getAuthorizationCode()));
                        roomBillingDetailsRequestList
                                .forEach(billing -> billing.getPayment().setAmount(cancelPendingRoomReservationResponse
                                        .getRoomReservation().getDepositDetails().getRefundAmount().negate()));
                        request.getCancelRoomReservationRequest().setBilling(roomBillingDetailsRequestList);

                        final CancelRoomReservationResponse cancelCommitRoomReservationResponse =
                                roomBookingAccess.cancelRoomReservation(request.getCancelRoomReservationRequest());

                        cancelReservationResponse.setRoomReservationResponse(cancelCommitRoomReservationResponse.getRoomReservation());

                        if (orderConfirmationMapping != null) {
                            updateOrderLineItemContentAndStatus(orderConfirmationMapping, cancelCommitRoomReservationResponse);
                            cancelReservationResponse.setOrderId(orderConfirmationMapping.getId());
                        }
                    } else {
                        final PaymentExceptionResponse ppsErrorResponse = new PaymentExceptionResponse();
                        ppsErrorResponse.setErrorCode("401");
                        ppsErrorResponse.setErrorMessage("Refund was not authorized by payment service");
                        cancelReservationResponse.setErrorPaymentRefundResponse(ppsErrorResponse);
                        try {
                            roomBookingAccess.releaseRoomReservation(cancelPendingRoomReservationResponse.getRoomReservation().getPropertyId(),
                                    cancelPendingRoomReservationResponse.getRoomReservation().getConfirmationNumber(),
                                    cancelPendingRoomReservationResponse.getRoomReservation().getHoldId(),
                                    cancelPendingRoomReservationResponse.getRoomReservation().isF1Package() != null
                                            ? cancelReservationResponse.getRoomReservationResponse().isF1Package() : false);
                        } catch (Exception exc) {
                            logger.error("Release reservation for pending cancel failed. Message: {}", exc.getMessage());
                        }
                        cancelReservationResponse.setRoomReservationResponse(null);
                    }
                }
            }
        } catch (SourceAppException e) {
            final ErrorResponse rbsErrorResponse = mapper.readValue(e.getRaw(), ErrorResponse.class);
            if (rbsErrorResponse != null && rbsErrorResponse.getError() != null) {
                logger.error("Error message from RBS Room cancel reservation call: {}", e.getRaw());
                cancelReservationResponse.setErrorRoomReservationResponse(rbsErrorResponse);
            } else {
                final PaymentExceptionResponse ppsErrorResponse = mapper.readValue(e.getRaw(), PaymentExceptionResponse.class);
                if (!Utils.anyNull(ppsErrorResponse, ppsErrorResponse.getErrorCode(), ppsErrorResponse.getErrorMessage())) {
                    logger.error("Error message from PPS refund call: {}", e.getRaw());
                    cancelReservationResponse.setErrorPaymentRefundResponse(ppsErrorResponse);
                }
                try {
                    roomBookingAccess.releaseRoomReservation(cancelReservationResponse.getRoomReservationResponse().getPropertyId(),
                            cancelReservationResponse.getRoomReservationResponse().getConfirmationNumber(),
                            cancelReservationResponse.getRoomReservationResponse().getHoldId(),
                            cancelReservationResponse.getRoomReservationResponse().isF1Package() != null
                                    ? cancelReservationResponse.getRoomReservationResponse().isF1Package() : false);
                } catch (Exception exc) {
                    logger.error("Release reservation for pending cancel failed. Message: {}", exc.getMessage());
                }
            }
            cancelReservationResponse.setRoomReservationResponse(null);
        } catch (AppException e) {
            logger.error("Unexpected exception occurred during room reservation cancel/payment session/refund call: {}", e.getDescription());
            cancelReservationResponse.setRoomReservationResponse(null);
            throw new AppException(ApplicationError.UNEXPECTED_EXCEPTION_DURING_RESERVATION_CANCEL, e, "Unexpected exception occurred during room reservation cancel.");
        }
        return cancelReservationResponse;
    }

    private void updateOrderLineItemContentAndStatus(OrderConfirmationMapping orderConfirmationMapping,
                                                     CancelRoomReservationResponse cancelCommitRoomReservationResponse) throws AppException {
        final Order order = orderAccess.read(orderConfirmationMapping.getId());
        order.setOrderUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
        final String roomReservationMaskedGroup = "room-reservation-content";
        final String maskedRoomReservationContent = logger.getJsonLogger().doMask(mapper.writeValueAsString(cancelCommitRoomReservationResponse),
                MaskLogger.MASKABLE_FIELDS.getOrDefault(roomReservationMaskedGroup, new ArrayList<>()));
        final List<LineItem> orderLineItems = order.getLineItems();
        if (!Utils.isEmpty(orderLineItems)) {
            final Optional<LineItem> reservationLineItem = orderLineItems.stream()
                    .filter(item -> item.getConfirmationNumber() != null)
                    .filter(item -> item.getConfirmationNumber().equalsIgnoreCase(orderConfirmationMapping.getConfirmationNumber()))
                    .findFirst();
            if (reservationLineItem.isPresent()) {
                final LineItem lineItem = reservationLineItem.get();
                lineItem.setStatus("CANCELLED");
                lineItem.setContent(maskedRoomReservationContent);
            }
        }
        orderAccess.update(order);
    }

    private Order getOrderByConfirmationNumber(final String confirmationNumber) {
        try {
            final OrderConfirmationMapping orderConfirmationMapping = orderConfirmationAccess.getOrderByConfirmationNumber(confirmationNumber);
            if (orderConfirmationMapping != null && !Utils.isEmpty(orderConfirmationMapping.getId())) {
                final Order order = orderAccess.read(orderConfirmationMapping.getId());
                return order;
            } else {
                return null;
            }
        } catch (AppException e) {
            logger.error("Failed to retrieve order for confirmation number {}", confirmationNumber);
            return null;
        }
    }

    private void updateReservationOrderHistory(final RoomReservationResponse roomResponse, Order order) {
        try {
            if (order != null && !Utils.isEmpty(order.getLineItems())) {
                final Optional<LineItem> lineItemOptional = order.getLineItems().stream()
                        .filter(item -> item.getConfirmationNumber() != null)
                        .filter(item -> item.getConfirmationNumber().equalsIgnoreCase(roomResponse.getConfirmationNumber()))
                        .findFirst();
                if (lineItemOptional.isPresent()) {
                    final LineItem lineItem = lineItemOptional.get();
                    final String roomRespStr = mapper.asJsonString(roomResponse);
                    final String roomReservationMaskedGroup = "room-reservation-commit";
                    final String maskedRoomReservationContent = logger.getJsonLogger().doMask(roomRespStr,
                            MaskLogger.MASKABLE_FIELDS.getOrDefault(roomReservationMaskedGroup, new ArrayList<String>()));
                    lineItem.setContent(maskedRoomReservationContent);
                    lineItem.setConfirmationNumber(roomResponse.getConfirmationNumber());
                    lineItem.setOperaHotelCode(roomResponse.getOperaHotelCode());
                    lineItem.setOperaConfirmationNumber(roomResponse.getOperaConfirmationNumber());

                    final RatesSummary ratesSummary = roomResponse.getRatesSummary();
                    lineItem.setLineItemCharge(ratesSummary.getRoomSubtotal());
                    lineItem.setLineItemPrice(ratesSummary.getReservationTotal());
                    lineItem.setLineItemDeposit(ratesSummary.getDepositDue());
                    lineItem.setLineItemDiscount(ratesSummary.getProgramDiscount());
                    lineItem.setLineItemBalance(ratesSummary.getBalanceUponCheckIn());
                    lineItem.setLineItemTourismFeeAndTax(ratesSummary.getTourismFeeAndTax());
                    lineItem.setLineItemResortFeePerNight(ratesSummary.getResortFeePerNight());
                    lineItem.setLineItemOccupancyFee(ratesSummary.getOccupancyFee());
                    lineItem.setLineItemResortFeeAndTax(ratesSummary.getResortFeeAndTax());

                    final List<RoomPrice> bookings = roomResponse.getBookings();
                    final double adjustedRoomSubtotal = Optional.ofNullable(ratesSummary.getAdjustedRoomSubtotal()).orElse(0d);
                    final double averagePricePerNight = Utils.isEmpty(bookings) ? 0d : adjustedRoomSubtotal / bookings.size();
                    lineItem.setAveragePricePerNight(averagePricePerNight);
                    lineItem.setLineItemAdjustedItemSubtotal(ratesSummary.getAdjustedRoomSubtotal());
                    lineItem.setLineItemTripSubtotal(ratesSummary.getTripSubtotal());
                    lineItem.setLineItemCasinoSurcharge(ratesSummary.getCasinoSurcharge());
                    lineItem.setLineItemCasinoSurchargeAndTax(ratesSummary.getCasinoSurchargeAndTax());

                    lineItem.setF1Package(roomResponse.isF1Package() != null ? roomResponse.isF1Package() : false);

                    final double roomChargeTax = Optional.ofNullable(ratesSummary.getRoomChargeTax()).orElse(0d);
                    lineItem.setLineItemTax(Utils.roundTwoDecimalPlaces(roomChargeTax));

                    lineItem.setAddOnComponents(toAddOnComponents(roomResponse.getPurchasedComponents()));
                    
                    double selectedAddOnPrice = 0d;
                    double selectedAddOnTax = 0d;
                    
                    if (!Utils.isEmpty(lineItem.getAddOnComponents())) {
                        for (com.mgmresorts.order.dto.AddOnComponent addOn : lineItem.getAddOnComponents()) {
                            if (addOn.getSelected() != null && addOn.getSelected()) {
                                selectedAddOnPrice += addOn.getTripPrice();
                                selectedAddOnTax += addOn.getTripTax();
                            }
                        }
                    }
                    
                    lineItem.setAddOnsPrice(selectedAddOnPrice);
                    lineItem.setAddOnsTax(selectedAddOnTax);

                    final com.mgmresorts.order.dto.services.Order orderDto = orderTransformer.toLeft(order);
                    orders.calculateOrderPrice(orderDto);
                    order = orderTransformer.toRight(orderDto);
                    order.setOrderUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));

                    orderAccess.update(order);
                }
            }
        } catch (AppException e) {
            logger.error("Failed to update order history for orderId: {} & confirmationNumber: {}", roomResponse.getOrderId(), roomResponse.getConfirmationNumber());
        }
    }

    private RefundCommitPutRequest createRefundCommitRequest(String firstName, String lastName, String confirmationNumber,
            String authCode, List<RoomBillingDetailsResponse> roomBillingResponse, BigDecimal refundAmount) throws AppException {
        final RefundCommitPutRequest request = new RefundCommitPutRequest();
        request.setFirstName(firstName);
        request.setLastName(lastName);
        request.setConfirmationNumber(confirmationNumber);
        final List<RoomBillingDetails> roomBillingDetailsRequestList = transformRoomBillingDetailsResponse(roomBillingResponse);
        roomBillingDetailsRequestList.forEach(biling -> biling.getPayment().setAuthId(authCode));
        roomBillingDetailsRequestList.forEach(billing -> billing.getPayment().setAmount(refundAmount.negate()));
        request.setBilling(roomBillingDetailsRequestList);
        return request;
    }
    
    private List<RoomBillingDetails> transformRoomBillingDetailsResponse(List<RoomBillingDetailsResponse> roomBillingDetailsResponseList) {
        final List<RoomBillingDetails> roomBillingDetailsRequestList = new ArrayList<>();
        for (RoomBillingDetailsResponse roomBillingDetailsResponse : roomBillingDetailsResponseList) {
            final com.mgmresorts.rbs.model.RoomBillingDetails roomBillingDetailsRequest = new com.mgmresorts.rbs.model.RoomBillingDetails();
            roomBillingDetailsRequest.setPayment(Utils.cloneByJson(mapper, RoomBillingDetailsPayment.class, roomBillingDetailsResponse.getPayment()));
            roomBillingDetailsRequest.setAddress(roomBillingDetailsResponse.getAddress());
            roomBillingDetailsRequestList.add(roomBillingDetailsRequest);
        }
        return roomBillingDetailsRequestList;
    }

    private List<com.mgmresorts.order.dto.AddOnComponent> toAddOnComponents(List<PurchasedComponents> purchasedComponentsList) {
        final List<com.mgmresorts.order.dto.AddOnComponent> calculateAddonComponents = new ArrayList<>();
        if (!Utils.isEmpty(purchasedComponentsList)) {
            for (PurchasedComponents purchasedComponent : purchasedComponentsList) {
                final com.mgmresorts.order.dto.AddOnComponent calculateAddOn = new com.mgmresorts.order.dto.AddOnComponent();

                calculateAddOn.setActive(purchasedComponent.isActive());
                calculateAddOn.setCode(purchasedComponent.getCode());
                calculateAddOn.setId(purchasedComponent.getId());
                calculateAddOn.setPricingApplied(com.mgmresorts.order.dto.AddOnComponent.PricingApplied.fromValue(
                        purchasedComponent.getPricingApplied().getValue()
                ));
                calculateAddOn.setNonEditable(purchasedComponent.isNonEditable());
                calculateAddOn.setShortDescription(purchasedComponent.getShortDescription());
                calculateAddOn.setLongDescription(purchasedComponent.getLongDescription());
                calculateAddOn.setTripPrice(purchasedComponent.getTripPrice().doubleValue());
                calculateAddOn.setTripTax(purchasedComponent.getTripTax().doubleValue());
                calculateAddOn.setPrice(purchasedComponent.getPrice().doubleValue());
                calculateAddOn.setPrices(toAddOnComponentPrices(purchasedComponent.getPrices()));
                calculateAddOn.setSelected(true);

                calculateAddonComponents.add(calculateAddOn);
            }
        }
        return calculateAddonComponents;
    }

    private List<com.mgmresorts.order.dto.AddOnComponentPrice> toAddOnComponentPrices(List<PurchasedComponentsPrices> purchasedComponentsPriceList) {
        final List<com.mgmresorts.order.dto.AddOnComponentPrice> addOnPrices = new ArrayList<>();
        if (!Utils.isEmpty(purchasedComponentsPriceList)) {
            for (PurchasedComponentsPrices componentPrice : purchasedComponentsPriceList) {
                final com.mgmresorts.order.dto.AddOnComponentPrice addOnPrice = new com.mgmresorts.order.dto.AddOnComponentPrice();
                if (componentPrice != null) {
                    if (componentPrice.getAmount() != null) {
                        addOnPrice.setAmount(componentPrice.getAmount().doubleValue());
                    }
                    if (componentPrice.getTax() != null) {
                        addOnPrice.setTax(componentPrice.getTax().doubleValue());
                    }
                    if (componentPrice.getDate() != null) {
                        addOnPrice.setDate(componentPrice.getDate());
                    }
                }
                addOnPrices.add(addOnPrice);
            }
        }
        return addOnPrices;
    }
}
