package com.mgmresorts.order.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.mgmresorts.common.concurrent.Executor;
import com.mgmresorts.common.concurrent.Executors;
import com.mgmresorts.common.concurrent.Pool;
import com.mgmresorts.common.concurrent.Result;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.event.enterprise.publish.EnableEnterpriseFailureEvent;
import com.mgmresorts.common.event.enterprise.publish.EnableEnterpriseSuccessEvent;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.function.HeaderBuilder;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.dbs.model.CreateReservationResponse;
import com.mgmresorts.dbs.model.CreateReservationResponseRestaurantReservation;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.handler.IPaymentSessionCommonHandler;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.database.access.IOrderConfirmationAccess;
import com.mgmresorts.order.dto.services.CancelReservationRequest;
import com.mgmresorts.order.dto.services.CancelReservationResponse;
import com.mgmresorts.order.dto.services.Message;
import com.mgmresorts.order.dto.services.PreviewReservationRequest;
import com.mgmresorts.order.dto.services.PreviewReservationResponse;
import com.mgmresorts.order.dto.services.ReservationType;
import com.mgmresorts.order.dto.services.RetrieveReservationResponse;
import com.mgmresorts.order.dto.services.SourceSystemError;
import com.mgmresorts.order.dto.services.UpdateReservationRequest;
import com.mgmresorts.order.dto.services.UpdateReservationResponse;
import com.mgmresorts.order.entity.LineItem;
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.entity.OrderConfirmationMapping;
import com.mgmresorts.order.entity.OrderStatus;
import com.mgmresorts.order.entity.Type;
import com.mgmresorts.order.entity.Version;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.IEventType;
import com.mgmresorts.order.service.IReservationService;
import com.mgmresorts.order.service.task.ReservationHandlerFactory;
import com.mgmresorts.order.service.task.ReservationTask;
import com.mgmresorts.order.service.transformer.CancelReservationEventTransformer;
import com.mgmresorts.order.service.transformer.UpdateReservationEventTransformer;
import com.mgmresorts.psm.model.SessionError;
import com.mgmresorts.rbs.model.CreateRoomReservationResponse;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.ItemType;

public class ReservationService implements IReservationService {
    private static final String RESERVATION_POOL = "reservation.pool";
    private final Logger logger = Logger.get(ReservationService.class);

    private final JSonMapper mapper = new JSonMapper();

    @Inject
    private IPaymentSessionCommonHandler paymentSessionCommonHandler;
    @Inject
    private ReservationHandlerFactory factory;
    @Inject
    private IOrderConfirmationAccess orderConfirmationAccess;
    @Inject
    private IOrderAccess orderAccess;
    @Inject
    private Executors executors;
    @Inject
    private ITransformer<com.mgmresorts.order.dto.services.Order, Order> orderTransformer;

    @Override
    public RetrieveReservationResponse getReservation(String confirmationNumber, String firstName, String lastName,
            ReservationType reservationType, boolean createPaymentSession, String paymentSessionId)
            throws AppException {
        return getBookingReservation(confirmationNumber, firstName, lastName, reservationType, createPaymentSession, paymentSessionId);
    }

    private RetrieveReservationResponse getBookingReservation(String confirmationNumber, String firstName,
            String lastName, ReservationType reservationType, boolean createPaymentSession, String paymentSessionId)
            throws AppException {

        validateGetReservationRequest(confirmationNumber, firstName, lastName, reservationType);

        final OrderConfirmationMapping orderConfirmationMapping = orderConfirmationAccess.getOrderByConfirmationNumber(confirmationNumber);

        if (orderConfirmationMapping != null && orderConfirmationMapping.getType() == Type.PACKAGE
                && orderConfirmationMapping.getVersion() == Version.V2) {

            final Order order = orderAccess.read(orderConfirmationMapping.getId());

            if (!createPaymentSession) {

                final List<ReservationTask> reservationTasks = createReservationTasks(firstName, lastName, order);
                final Executor executor = executors.get(Pool.getOrCreate(RESERVATION_POOL));
                final List<Result<RetrieveReservationResponse>> reservationResponses = executor.invoke(reservationTasks);

                final RetrieveReservationResponse retrieveReservationResponse = combineReservations(reservationResponses);
                retrieveReservationResponse.setIsPackage(true);

                if (retrieveReservationResponse.getRoomReservationResponse() != null) {
                    retrieveReservationResponse.getRoomReservationResponse().setIsStayDateModifiable(false);
                }

                updateContentFieldOfOrderLineItems(order, retrieveReservationResponse, null);

                final com.mgmresorts.order.dto.services.Order orderDto = orderTransformer.toLeft(order);
                retrieveReservationResponse.setOrder(orderDto);

                return  retrieveReservationResponse;
            } else {
                final RetrieveReservationResponse retrieveReservationResponse = factory.get(ItemType.fromValue(reservationType.value()))
                        .getReservation(confirmationNumber, firstName, lastName, true, paymentSessionId);
                retrieveReservationResponse.setIsPackage(true);
                if (retrieveReservationResponse.getRoomReservationResponse() != null) {
                    retrieveReservationResponse.getRoomReservationResponse().setIsStayDateModifiable(false);
                }

                updateContentFieldOfOrderLineItems(order, retrieveReservationResponse, confirmationNumber);

                final com.mgmresorts.order.dto.services.Order orderDto = orderTransformer.toLeft(order);
                retrieveReservationResponse.setOrder(orderDto);

                return retrieveReservationResponse;
            }
        } else {
            final RetrieveReservationResponse retrieveReservationResponse = factory.get(ItemType.fromValue(reservationType.value()))
                    .getReservation(confirmationNumber, firstName, lastName, createPaymentSession, paymentSessionId);
            retrieveReservationResponse.setIsPackage(false);
            return retrieveReservationResponse;
        }
    }

    private void updateContentFieldOfOrderLineItems(Order order, RetrieveReservationResponse reservation, String confirmationNumber) {
        final Cart rawCart = order.getRawCart() != null ? mapper.readValue(order.getRawCart(), Cart.class) : null;
        for (LineItem lineItem : order.getLineItems()) {
            if (StringUtils.isBlank(confirmationNumber) || confirmationNumber.equalsIgnoreCase(lineItem.getConfirmationNumber())) {
                final Optional<CartLineItem> cartLineItem = rawCart != null ? rawCart.getCartLineItems().stream()
                        .filter(li -> li.getCartLineItemId().equalsIgnoreCase(lineItem.getCartLineItemId())).findFirst()
                        : Optional.empty();
                switch (lineItem.getProductType()) {
                    case ROOM:
                        if (reservation.getRoomReservationResponse() != null) {
                            final CreateRoomReservationResponse createRoomReservationResponse = new CreateRoomReservationResponse();
                            createRoomReservationResponse.setRoomReservation(reservation.getRoomReservationResponse());
                            final String roomReservationContent = mapper.asJsonString(createRoomReservationResponse);
                            lineItem.setContent(roomReservationContent);
                        } else {
                            final Message msg = createOrderLineItemErrorMessage(Errors.UNABLE_TO_GET_ROOM_RESERVATION);
                            if (reservation.getErrorRoomReservationResponse() != null) {
                                final com.mgmresorts.rbs.model.ErrorResponse roomErrorResponse = reservation.getErrorRoomReservationResponse();
                                final String sseCode;
                                final String sseMessage;
                                if (roomErrorResponse.getError() != null) {
                                    sseCode = roomErrorResponse.getError().getCode();
                                    sseMessage = roomErrorResponse.getError().getMessage();
                                    msg.getSourceSystemError().setSourceSystemCode(sseCode);
                                    msg.getSourceSystemError().setSourceSystemMessage(sseMessage);
                                }
                            } else {
                                msg.getSourceSystemError().setSourceSystemMessage(new AppException(SystemError.UNEXPECTED_SYSTEM).getDescription());
                                msg.getSourceSystemError().setSourceSystemCode(new AppException(SystemError.UNEXPECTED_SYSTEM).getDisplayCode());
                            }
                            lineItem.setStatus("FAILURE");
                            lineItem.getMessages().add(msg);
                        }
                        break;
                    case SHOW:
                        if (reservation.getShowReservationResponse() != null) {
                            final String showReservationContent = mapper.asJsonString(reservation.getShowReservationResponse());
                            lineItem.setContent(showReservationContent);
                        } else {
                            final Message msg = createOrderLineItemErrorMessage(Errors.UNABLE_TO_GET_SHOW_RESERVATION);
                            if (reservation.getErrorShowReservationResponse() != null) {
                                final com.mgmresorts.sbs.model.ErrorResponse showErrorResponse = reservation.getErrorShowReservationResponse();
                                final String sseCode;
                                final String sseMessage;
                                if (showErrorResponse.getError() != null) {
                                    sseCode = showErrorResponse.getError().getCode();
                                    sseMessage = showErrorResponse.getError().getMessage();
                                    msg.getSourceSystemError().setSourceSystemCode(sseCode);
                                    msg.getSourceSystemError().setSourceSystemMessage(sseMessage);
                                }
                            } else {
                                msg.getSourceSystemError().setSourceSystemMessage(new AppException(SystemError.UNEXPECTED_SYSTEM).getDescription());
                                msg.getSourceSystemError().setSourceSystemCode(new AppException(SystemError.UNEXPECTED_SYSTEM).getDisplayCode());
                            }
                            lineItem.setStatus("FAILURE");
                            lineItem.getMessages().add(msg);
                        }
                        break;
                    case DINING:
                        if (reservation.getDiningReservationResponse() != null) {
                            final CreateReservationResponse createReservationResponse = new CreateReservationResponse();
                            createReservationResponse.setRestaurantReservation(Utils.cloneByJson(mapper, CreateReservationResponseRestaurantReservation.class,
                                    reservation.getDiningReservationResponse().getRestaurantReservationList().get(0)));
                            final String diningReservationContent = mapper.asJsonString(createReservationResponse);
                            lineItem.setContent(diningReservationContent);
                        } else {
                            final Message msg = createOrderLineItemErrorMessage(Errors.UNABLE_TO_GET_DINING_RESERVATION);
                            if (reservation.getErrorDiningReservationResponse() != null) {
                                final com.mgmresorts.dbs.model.Response diningErrorResponse = reservation.getErrorDiningReservationResponse();
                                final String sseCode;
                                final String sseMessage;
                                if (diningErrorResponse.getError() != null) {
                                    sseCode = diningErrorResponse.getError().getCode();
                                    sseMessage = diningErrorResponse.getError().getMessage();
                                    msg.getSourceSystemError().setSourceSystemCode(sseCode);
                                    msg.getSourceSystemError().setSourceSystemMessage(sseMessage);
                                }
                            } else {
                                msg.getSourceSystemError().setSourceSystemMessage(new AppException(SystemError.UNEXPECTED_SYSTEM).getDescription());
                                msg.getSourceSystemError().setSourceSystemCode(new AppException(SystemError.UNEXPECTED_SYSTEM).getDisplayCode());
                            }
                            lineItem.setStatus("FAILURE");
                            lineItem.getMessages().add(msg);
                        }
                        break;
                    default:
                        logger.error("Product type is currently not supported.");
                }

                if (lineItem.getStatus().equalsIgnoreCase("FAILURE")) {
                    if (cartLineItem.isPresent()) {
                        lineItem.setContent(cartLineItem.get().getContent());
                    }
                }
            }
        }
        
        final List<String> failedProducts = order.getLineItems().stream().filter(o -> o.getStatus().equalsIgnoreCase("FAILURE")).map(item -> item.getCartLineItemId())
                .collect(Collectors.toList());
        if (Utils.isEmpty(failedProducts)) {
            order.setStatus(OrderStatus.SUCCESS);
        } else if (failedProducts.size() == order.getLineItems().size()) {
            order.setStatus(OrderStatus.FAILURE);
        } else {
            order.setStatus(OrderStatus.PARTIAL);
        }
    }
    
    private static Message createOrderLineItemErrorMessage(int code) {
        final Message msg = new Message();
        msg.setType(Message.Type.ERROR);
        msg.setCode(new AppException(code).getDisplayCode());
        final SourceSystemError sse = new SourceSystemError();
        msg.setSourceSystemError(sse);
        return msg;
    }

    private static RetrieveReservationResponse combineReservations(List<Result<RetrieveReservationResponse>> getReservationResults) {
        final RetrieveReservationResponse finalCombinedReservation = new RetrieveReservationResponse();
        finalCombinedReservation.setHeader(HeaderBuilder.buildHeader());

        for (Result<RetrieveReservationResponse> result : getReservationResults) {
            RetrieveReservationResponse fetchedReservation = result.getOutput();
            if (fetchedReservation != null) {
                if (fetchedReservation.getRoomReservationResponse() != null) {
                    finalCombinedReservation.setRoomReservationResponse(fetchedReservation.getRoomReservationResponse());
                } else if (fetchedReservation.getShowReservationResponse() != null) {
                    finalCombinedReservation.setShowReservationResponse(fetchedReservation.getShowReservationResponse());
                } else if (fetchedReservation.getDiningReservationResponse() != null) {
                    finalCombinedReservation.setDiningReservationResponse(fetchedReservation.getDiningReservationResponse());
                } else if (fetchedReservation.getErrorRoomReservationResponse() != null) {
                    finalCombinedReservation.setErrorRoomReservationResponse(fetchedReservation.getErrorRoomReservationResponse());
                } else if (fetchedReservation.getErrorShowReservationResponse() != null) {
                    finalCombinedReservation.setErrorShowReservationResponse(fetchedReservation.getErrorShowReservationResponse());
                } else if (fetchedReservation.getErrorDiningReservationResponse() != null) {
                    finalCombinedReservation.setErrorDiningReservationResponse(fetchedReservation.getErrorDiningReservationResponse());
                }
            }
        }
        return finalCombinedReservation;
    }

    private List<ReservationTask> createReservationTasks(String firstName, String lastName, Order order) throws AppException {
        if (order == null) {
            throw new AppException(SystemError.REQUESTED_RESOURCE_NOT_FOUND, "Order for reservation not found.");
        }
        final List<ReservationTask> reservationTasks = new ArrayList<>();
        if (!Utils.isEmpty(order.getLineItems())) {
            final List<LineItem> lineItems = order.getLineItems();
            Optional.ofNullable(lineItems).ifPresent(items -> items.forEach(lineItem -> {
                if (lineItem.getConfirmationNumber() != null && lineItem.getProductType() != null) {
                    final ReservationTask reservationTask = factory.create(lineItem, firstName, lastName);
                    reservationTasks.add(reservationTask);
                }
            }));
        }
        return reservationTasks;
    }

    private static void validateGetReservationRequest(String confirmationNumber, String firstName, String lastName, ReservationType reservationType) throws AppException {
        if (StringUtils.isBlank(confirmationNumber)) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "confirmation number is mandatory.");
        }

        if (StringUtils.isBlank(firstName)) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "first name is mandatory.");
        }

        if (StringUtils.isBlank(lastName)) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "last name is mandatory.");
        }

        if (reservationType == null || StringUtils.isBlank(reservationType.value())) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "reservation type is mandatory.");
        }
    }
    
    @Override
    public PreviewReservationResponse previewReservation(PreviewReservationRequest request) throws AppException {
        return previewBookingReservation(request);      
    }

    private PreviewReservationResponse previewBookingReservation(PreviewReservationRequest request) throws AppException {
        if (request == null || request.getPreviewRoomReservationRequest() == null
                || Utils.isEmpty(request.getPaymentSessionId()) || request.getReservationType() == null
                || Utils.isEmpty(request.getPreviewRoomReservationRequest().getConfirmationNumber())) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Invalid input. Please check preview reservation request for all mandatory arguments.");
        }

        final OrderConfirmationMapping orderConfirmationMapping =
                orderConfirmationAccess.getOrderByConfirmationNumber(request.getPreviewRoomReservationRequest().getConfirmationNumber());

        final PreviewReservationResponse previewReservationResponse = factory.get(ItemType.fromValue(request.getReservationType().value())).previewReservation(request);

        if (orderConfirmationMapping != null && orderConfirmationMapping.getType().equals(Type.PACKAGE) && orderConfirmationMapping.getVersion().equals(Version.V2)) {
            if (previewReservationResponse.getRoomReservationResponse() != null) {
                previewReservationResponse.getRoomReservationResponse().setIsStayDateModifiable(false);
            }
        }

        return previewReservationResponse;
    }

    @Override
    @EnableEnterpriseSuccessEvent(eventType = IEventType.EVENT_CART_ORDER_UPDATE, transformer = UpdateReservationEventTransformer.class)
    @EnableEnterpriseFailureEvent(eventType = IEventType.EVENT_CART_ORDER_UPDATE_FAILURE, exceptions = AppException.class)
    public UpdateReservationResponse updateReservation(UpdateReservationRequest request) throws AppException {
        return updateBookingReservation(request);
    }

    private UpdateReservationResponse updateBookingReservation(UpdateReservationRequest request) throws AppException {
        if (request == null || Utils.isEmpty(request.getPaymentSessionId()) || request.getReservationType() == null) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Invalid input. Please check update reservation request for all mandatory arguments.");
        }

        if (request.getReservationType() != ReservationType.ROOM) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Item type is currently not supported.");
        }

        // 1) Fetch payment session and ensure it is valid
        PaymentSessionBaseFields paymentSessionBaseFields;
        try {
            paymentSessionBaseFields = paymentSessionCommonHandler.getPaymentAuthResults(request.getPaymentSessionId());
            if (paymentSessionBaseFields == null) {
                throw new AppException(Errors.UNABLE_TO_GET_PAYMENT_SESSION, "Payment session base details are not found in payment session.");
            }
        } catch (SourceAppException e) {
            logger.error("Failed to read payment session, unable to commit reservation.");
            final SessionError psmErrorResponse = mapper.readValue(e.getRaw(), SessionError.class);
            final UpdateReservationResponse updateReservationResponse = new UpdateReservationResponse();
            updateReservationResponse.setHeader(HeaderBuilder.buildHeader());
            updateReservationResponse.setPaymentSessionId(request.getPaymentSessionId());
            if (!Utils.anyNull(psmErrorResponse, psmErrorResponse.getErrorCode(), psmErrorResponse.getErrorMessage())) {
                logger.error("Error message from PSM get payment session call: {}", e.getRaw());
                updateReservationResponse.setErrorPaymentSessionResponse(psmErrorResponse);
            }
            return updateReservationResponse;
        } catch (AppException e) {
            logger.error("Failed to read payment session, unable to update reservation.");
            throw new AppException(Errors.UNABLE_TO_GET_PAYMENT_SESSION, "Payment auth details could not be retrieved from payment session.");
        }

        // 2) Start update flow per item type (go inside roomHandler.updateRoomReservation for room example)
        return factory.get(ItemType.fromValue(request.getReservationType().value())).updateReservation(request, paymentSessionBaseFields);
    }
    
    @EnableEnterpriseSuccessEvent(eventType = IEventType.EVENT_CART_PRODUCT_CANCEL, transformer = CancelReservationEventTransformer.class)
    @EnableEnterpriseFailureEvent(eventType = IEventType.EVENT_CART_PRODUCT_CANCEL_FAILURE, exceptions = AppException.class)
    public CancelReservationResponse cancelReservation(CancelReservationRequest request) throws AppException {
        return cancelBookingReservation(request);
    }

    private CancelReservationResponse cancelBookingReservation(CancelReservationRequest request) throws AppException {
        if (request == null || Utils.isEmpty(request.getPaymentSessionId()) || request.getReservationType() == null) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Invalid input. Please check cancel reservation request for all mandatory arguments.");
        }
        
        if (request.getReservationType() != ReservationType.ROOM) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Item type is currently not supported.");
        }
        
        PaymentSessionBaseFields paymentSessionBaseFields;
        try {
            paymentSessionBaseFields = paymentSessionCommonHandler.getPaymentAuthResults(request.getPaymentSessionId());
            
            if (paymentSessionBaseFields ==  null) {
                throw new AppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "Payment session base details are not found in the payment session.");
            }

            if (Utils.isEmpty(paymentSessionBaseFields.getBillings())) {
                logger.error("The billings array was null or empty.");
                throw new AppException(ApplicationError.INVALID_REQUEST, "Billing methods are missing in the payment session.");
            }
        } catch (SourceAppException e) {
            logger.error("Failed to read payment session, unable to cancel reservation.");
            final SessionError psmErrorResponse = mapper.readValue(e.getRaw(), SessionError.class);
            final CancelReservationResponse cancelReservationResponse = new CancelReservationResponse();
            cancelReservationResponse.setHeader(HeaderBuilder.buildHeader());
            if (!Utils.anyNull(psmErrorResponse, psmErrorResponse.getErrorCode(), psmErrorResponse.getErrorMessage())) {
                logger.error("Error message from PSM get payment session call: {}", e.getRaw());
                cancelReservationResponse.setErrorPaymentSessionResponse(psmErrorResponse);
            }
            return cancelReservationResponse;
        } catch (AppException e) {
            logger.error("Failed to read payment session, unable to cancel reservation.");
            throw new AppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "Payment auth details could not be retrieved from payment session.");
        }

        return factory.get(ItemType.fromValue(request.getReservationType().value())).cancelReservation(request, paymentSessionBaseFields);
    }
}
