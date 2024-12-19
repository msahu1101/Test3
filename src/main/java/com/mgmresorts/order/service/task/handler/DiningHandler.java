package com.mgmresorts.order.service.task.handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;

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
import com.mgmresorts.dbs.model.CreateReservationRequest;
import com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservation;
import com.mgmresorts.dbs.model.CreateReservationResponse;
import com.mgmresorts.dbs.model.HoldReservationResponse;
import com.mgmresorts.dbs.model.Response;
import com.mgmresorts.dbs.model.SearchReservationResponse;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.IDiningBookingAccess;
import com.mgmresorts.order.database.access.IOrderAccess;
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
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.consumer.IMergeConsumer;
import com.mgmresorts.order.service.task.IProductHandler;
import com.mgmresorts.psm.model.SessionError;
import com.mgmresorts.shopping.cart.dto.AgentInfo;
import com.mgmresorts.shopping.cart.dto.CartLineItem;

public class DiningHandler implements IProductHandler {

    private final Logger logger = Logger.get(DiningHandler.class);
    private final JSonMapper mapper = new JSonMapper();
    
    @Inject
    private IOrderAccess orderAccess;
    @Inject
    private IMergeConsumer mergeConsumer;
    @Inject
    private ITransformer<AgentInfo, com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo> agentTransformer;
    @Inject
    private IDiningBookingAccess diningBookingAccess;

    @Override
    public OrderLineItem checkout(CheckoutRequest request, CartLineItem cartLineItem, OrderLineItem orderLineItem,
            AppliedBillings billable, String orderId, AgentInfo agentInfo, boolean skipAFS, boolean skipPaymentCapture,
            Map<String, PaymentAuthFields> paymentAuthFieldsMap, String orderReferenceNumber) throws AppException {
        final boolean isPackage = request.getCartType() == com.mgmresorts.order.dto.services.Type.PACKAGE;

        try {
            final String itineraryId = request.getItineraryId();

            orderLineItem.setItineraryId(itineraryId);

            final CreateReservationRequest diningReservationRequest = generateDiningReservationRequest(agentInfo,
                    itineraryId, request.getGuestProfile().getId(), cartLineItem, skipAFS);

            final String diningReservationResponse = diningBookingAccess.createDiningReservation(diningReservationRequest);
            addSuccess(cartLineItem, orderLineItem, diningReservationResponse);

        } catch (HttpFailureException e) {
            logger.error("[Error from DBS] Create Reservation call failed : ", e.getMessage());
            final String errorPayload = e.getPayload();
            addFailure(cartLineItem, orderLineItem, errorPayload, Errors.UNABLE_TO_BOOK_DINING, e);
        } catch (AppException e) {
            logger.error("[Error from DBS] Create Reservation call failed : ", e.getMessage());
            addFailure(cartLineItem, orderLineItem, e.getDescription(), e.getCode(), null);
        } catch (Exception e) {
            logger.error("[Error from DBS] Create Reservation call failed : ", e.getMessage());
            addFailure(cartLineItem, orderLineItem, e.getMessage(), 500, null);
        }

        if (!isPackage) {
            final Consumer<Order> merger = mergeConsumer.create(orderLineItem);
            orderAccess.mergeAndUpdate(orderId, Order.class, merger);
        }
        return orderLineItem;
    }

    public Collection<OrderLineItem> reserve(CheckoutRequest request, List<CartLineItem> diningCartLineItems,
            List<OrderLineItem> diningOrderLineItems, AppliedBillings billable, String orderId,
            com.mgmresorts.shopping.cart.dto.AgentInfo agentInfo, boolean skipAFS, boolean skipPaymentCapture,
            Map<String, PaymentAuthFields> paymentAuthFieldsMap, String orderReferenceNumber) throws AppException {
        return diningCartLineItems.stream().map(dining -> {
            try {
                final OrderLineItem orderLineItem = diningOrderLineItems.stream().filter(li ->
                        dining.getCartLineItemId().equalsIgnoreCase(li.getCartLineItemId())).findFirst().get();
                return checkout(request, dining, orderLineItem, billable, orderId, agentInfo, skipAFS,
                        skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
            } catch (AppException e) {
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());
    }

    private void addSuccess(CartLineItem cartLineItem, final OrderLineItem output, final String reservation) {
        final String diningReservationMaskedGroup = "dining-reservation-content";
        final CreateReservationResponse resvResp = mapper.readValue(reservation, CreateReservationResponse.class);
        final String maskedReservationContent = logger.getJsonLogger().doMask(reservation,
                MaskLogger.MASKABLE_FIELDS.getOrDefault(diningReservationMaskedGroup, new ArrayList<String>()));

        output.setContent(maskedReservationContent);
        output.setCartLineItemId(cartLineItem.getCartLineItemId());
        output.setConfirmationNumber(resvResp.getRestaurantReservation().getConfirmationNumber());
        output.setReservationDate(cartLineItem.getItemSelectionDetails().getDiningSelectionDetails().getReservationDate().toString());
        output.setReservationTime(cartLineItem.getItemSelectionDetails().getDiningSelectionDetails().getReservationTime());
        output.setStatus(Status.SUCCESS);
    }

    private void addFailure(final CartLineItem input, final OrderLineItem output, final String errorPayload, final int code, final HttpFailureException exception) {
        final Message msg = new Message();
        msg.setType(Message.Type.ERROR);
        msg.setCode(new AppException(code).getDisplayCode());
        final SourceSystemError sse = new SourceSystemError();
        if (!Utils.isEmpty(errorPayload) && Utils.isValidJson(errorPayload) && exception.getHttpCode() <= 500) {
            try {
                final Response errorResponse = mapper.readValue(errorPayload, Response.class);
                final String sseCode;
                final String sseMessage;
                if (errorResponse != null && errorResponse.getError() != null) {
                    sse.setSourceSystemCode(errorResponse.getError().getCode());
                    sse.setSourceSystemMessage(errorResponse.getError().getMessage());
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

        output.setContent(input.getContent());
        output.setCartLineItemId(input.getCartLineItemId());
        
        output.setStatus(Status.FAILURE);

        msg.setSourceSystemError(sse);
        output.getMessages().add(msg);
    }

    private CreateReservationRequest generateDiningReservationRequest(com.mgmresorts.shopping.cart.dto.AgentInfo agentInfo,
                                                                      String itineraryId, String customerId, CartLineItem cartLineItem, boolean skipAFS) throws AppException {
        final CreateReservationRequestRestaurantReservation reservation = new CreateReservationRequestRestaurantReservation();

        final HoldReservationResponse response = mapper.readValue(cartLineItem.getContent(), HoldReservationResponse.class);

        reservation.setCustomerId(customerId);
        reservation.setItineraryId(itineraryId);
        reservation.setRestaurantId(cartLineItem.getProductId());
        reservation.setReservationDateTime(response.getReservationDateTime());
        reservation.setNumAdults(cartLineItem.getItemSelectionDetails().getDiningSelectionDetails().getPartySize());
        reservation.setNumChildren(0);
        reservation.setAgentInfo(agentTransformer.toRight(agentInfo));
        reservation.setReservationHoldId(response.getReservationHoldId());

        final CreateReservationRequest reservationRequest = new CreateReservationRequest();
        reservationRequest.setRestaurantReservation(reservation);
        return reservationRequest;
    }

    public RetrieveReservationResponse getReservation(final String confirmationNumber, final String firstName,
            final String lastName, final boolean createPaymentSession, final String paymentSessionId)
            throws AppException {
        
        final RetrieveReservationResponse retrieveReservationResponse = new RetrieveReservationResponse();
        retrieveReservationResponse.setHeader(HeaderBuilder.buildHeader());
        
        try {
            final SearchReservationResponse searchReservationResponse = diningBookingAccess.searchDiningReservation(confirmationNumber, firstName, lastName);
            retrieveReservationResponse.setDiningReservationResponse(searchReservationResponse);
            retrieveReservationResponse.setPaymentSessionId(null);
        } catch (SourceAppException e) {
            retrieveReservationResponse.setDiningReservationResponse(null);
            final com.mgmresorts.dbs.model.Response dbsErrorResponse = mapper.readValue(e.getRaw(), com.mgmresorts.dbs.model.Response.class);
            if (dbsErrorResponse != null && dbsErrorResponse.getError() != null) {
                logger.error("Error message from DBS Get Dining reservation call: {}", e.getRaw());
                retrieveReservationResponse.setErrorDiningReservationResponse(dbsErrorResponse);
            } else {
                final SessionError psmErrorResponse = mapper.readValue(e.getRaw(), SessionError.class);
                if (!Utils.anyNull(psmErrorResponse, psmErrorResponse.getErrorCode(), psmErrorResponse.getErrorMessage())) {
                    logger.error("Error message from PSM Get payment session call: {}", e.getRaw());
                    retrieveReservationResponse.setErrorPaymentSessionResponse(psmErrorResponse);
                }
            }
        } catch (AppException e) {
            logger.error("Unexpected exception occurred during get dining reservation/payment session call: {}", e.getDescription());
            retrieveReservationResponse.setDiningReservationResponse(null);
            throw new AppException(Errors.UNEXPECTED_EXCEPTION_DURING_GET_RESERVATION, e, "Unexpected exception occurred during get dining reservation.");
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
}
