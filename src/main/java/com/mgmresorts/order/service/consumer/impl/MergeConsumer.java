package com.mgmresorts.order.service.consumer.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.logging.masker.MaskLogger;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.event.dto.show.ErrorResponse;
import com.mgmresorts.event.dto.show.ReservationResponse;
import com.mgmresorts.event.dto.show.ServiceCharge;
import com.mgmresorts.event.dto.show.ShowReservationFromBody;
import com.mgmresorts.event.dto.show.TransactionFee;
import com.mgmresorts.order.dto.services.Message;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.SourceSystemError;
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.service.consumer.IMergeConsumer;
import com.mgmresorts.order.utils.Orders;

public class MergeConsumer implements IMergeConsumer {
    private final Logger logger = Logger.get(MergeConsumer.class);
    private final JSonMapper mapper = new JSonMapper();
    @Inject
    private ITransformer<com.mgmresorts.order.dto.services.Order, Order> orderTransformer;
    @Inject
    private Orders orders;

    @Override
    public Consumer<Order> create(OrderLineItem newOrderLineItem) throws AppException {
        
        return (existingOrderEntity) -> {
            try {
                final com.mgmresorts.order.dto.services.Order orderDto = orderTransformer.toLeft(existingOrderEntity);
                final Optional<OrderLineItem> orderLineItem =
                        orderDto.getOrderLineItems().stream().filter(li -> newOrderLineItem.getOrderLineItemId().equalsIgnoreCase(li.getOrderLineItemId())).findFirst();
                if (orderLineItem.isPresent()) {
                    orderDto.getOrderLineItems().remove(orderLineItem.get());
                }
                orderDto.getOrderLineItems().add(newOrderLineItem);

                orders.calculateOrderPrice(orderDto);
                final Order updatedOrderEntity = orderTransformer.toRight(orderDto);
                existingOrderEntity.setLineItems(updatedOrderEntity.getLineItems());
                existingOrderEntity.setPriceDetails(updatedOrderEntity.getPriceDetails());
                existingOrderEntity.setRoomTotals(updatedOrderEntity.getRoomTotals());
                existingOrderEntity.setShowTotals(updatedOrderEntity.getShowTotals());
            } catch (AppException ape) {
                throw new RuntimeException("Failed to merge order entity");
            }
        };
    }

    @Override
    public Consumer<Order> updateShowSuccess(ShowReservationFromBody payload) throws AppException {
        return (existingOrderEntity) -> {
            try {
                final com.mgmresorts.order.dto.services.Order orderDto = orderTransformer.toLeft(existingOrderEntity);
                final Optional<OrderLineItem> orderLineItem =
                        orderDto.getOrderLineItems().stream().filter(
                                li -> payload.getShowReservation().getReservationResponse().getOrderLineItemId().equalsIgnoreCase(li.getOrderLineItemId())).findFirst();
                orderLineItem.get().setStatus(OrderLineItem.Status.SUCCESS);
                final String reservation = mapper.writeValueAsString(payload.getShowReservation().getReservationResponse());
                final String showReservationMaskedGroup = "show-reservation-content";
                final String maskedShowReservationContent = logger.getJsonLogger().doMask(reservation,
                        MaskLogger.MASKABLE_FIELDS.getOrDefault(showReservationMaskedGroup, new ArrayList<String>()));
                orderLineItem.get().setContent(maskedShowReservationContent);
                orders.calculateOrderPrice(orderDto);
                final Order updatedOrderEntity = orderTransformer.toRight(orderDto);
                existingOrderEntity.setLineItems(updatedOrderEntity.getLineItems());
                existingOrderEntity.setPriceDetails(updatedOrderEntity.getPriceDetails());
                existingOrderEntity.setRoomTotals(updatedOrderEntity.getRoomTotals());
                existingOrderEntity.setShowTotals(updatedOrderEntity.getShowTotals());
                final List<String> pendingProducts = updatedOrderEntity.getLineItems().stream()
                        .filter(o -> o.getStatus().equalsIgnoreCase("PENDING")).map(item -> item.getCartLineItemId())
                        .collect(Collectors.toList());
                if (Utils.isEmpty(pendingProducts)) {
                    updatedOrderEntity.setComplete(true);
                    existingOrderEntity.setComplete(updatedOrderEntity.isComplete());
                }
                logger.info("-----------------At the end of updateShowSuccess------------------------------------");
            } catch (AppException ape) {
                throw new RuntimeException("Failed to merge order entity");
            }
        };
    }

    @Override
    public Consumer<Order> updateShowFailure(ShowReservationFromBody payload) throws AppException {
        return (existingOrderEntity) -> {
            try {
                final com.mgmresorts.order.dto.services.Order orderDto = orderTransformer.toLeft(existingOrderEntity);
                final Optional<OrderLineItem> orderLineItem =
                        orderDto.getOrderLineItems().stream().filter(
                                li -> payload.getShowReservation().getReservationResponse().getOrderLineItemId().equalsIgnoreCase(li.getOrderLineItemId())).findFirst();
                final ErrorResponse errorPayload = payload.getError();
                final Message msg = new Message();
                msg.setType(Message.Type.ERROR);
                msg.setCode(new AppException(Errors.UNABLE_TO_BOOK_SHOW).getDisplayCode());
                final SourceSystemError sse = new SourceSystemError();
                boolean paymentFailure = false;
                if (errorPayload != null) {
                    try {
                        final String sseCode;
                        final String sseMessage;
                        sseCode = errorPayload.getCode();
                        sseMessage = errorPayload.getMessage();
                        if (StringUtils.equals(sseCode, "620-2-240") || StringUtils.equals(sseCode, "620-2-241")
                                || StringUtils.equals(sseCode, "620-2-242")) {
                            paymentFailure = true;
                        }
                        sse.setSourceSystemCode(sseCode);
                        sse.setSourceSystemMessage(sseMessage);
                    } catch (Exception e) {
                        sse.setSourceSystemMessage(null);
                        sse.setSourceSystemCode(null);
                        logger.warn("Something went wrong when mapping source system error from SBS! orderId: "
                                + payload.getShowReservation().getReservationResponse().getOrderId());
                    }
                } else {
                    sse.setSourceSystemMessage(null);
                    sse.setSourceSystemCode(null);
                    logger.warn("No source system error was received from SBS! orderId: " + payload.getShowReservation().getReservationResponse().getOrderId());
                }
                msg.setSourceSystemError(sse);

                final ReservationResponse showCharge = payload.getShowReservation().getReservationResponse();
                final ServiceCharge serviceCharge = showCharge.getCharges().getServiceCharge();
                final TransactionFee transactionFee = showCharge.getCharges().getTransactionFee();

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
                orderLineItem.get().setLineItemServiceChargeFee(serviceChargeFee);
                orderLineItem.get().setLineItemServiceChargeTax(serviceChargeTax);
                orderLineItem.get().setLineItemServiceChargeFeeAndTax(serviceChargeFeeAndTax);

                if (transactionFee != null && transactionFee.getItemized() != null) {
                    transactionFeeCharge += Optional.ofNullable(transactionFee.getItemized().getCharge()).orElse(0d);
                    transactionFeeTax += Optional.ofNullable(transactionFee.getItemized().getTax()).orElse(0d);
                }

                orderLineItem.get().setLineItemTransactionFee(transactionFeeCharge);
                orderLineItem.get().setLineItemTransactionFeeTax(transactionFeeTax);

                orderLineItem.get().setLineItemDeliveryMethodFee(Optional.ofNullable(showCharge.getCharges().getDeliveryFee()).orElse(0d));
                orderLineItem.get().setLineItemGratuity(Optional.ofNullable(showCharge.getCharges().getGratuity()).orElse(0d));
                final double let = Optional.ofNullable(showCharge.getCharges().getLet()).orElse(0d);
                orderLineItem.get().setLineItemLet(let);

                final double discountedSubtotal = Optional.ofNullable(showCharge.getCharges().getDiscountedSubtotal()).orElse(0d);
                final double totalCharge = discountedSubtotal;
                final double adjustedItemSubtotal = totalCharge + let + transactionFeeCharge + transactionFeeTax;
                orderLineItem.get().setLineItemAdjustedItemSubtotal(adjustedItemSubtotal);

                final double totalTax = let + transactionFeeTax + serviceChargeTax;
                orderLineItem.get().setLineItemTax(Utils.roundTwoDecimalPlaces(totalTax));

                if (paymentFailure) {
                    orderLineItem.get().setStatus(OrderLineItem.Status.PAYMENT_FAILURE);
                } else {
                    orderLineItem.get().setStatus(OrderLineItem.Status.FAILURE);
                }
                orderLineItem.get().getMessages().add(msg);
                final Order updatedOrderEntity = orderTransformer.toRight(orderDto);
                existingOrderEntity.setLineItems(updatedOrderEntity.getLineItems());
                existingOrderEntity.setPriceDetails(updatedOrderEntity.getPriceDetails());
                existingOrderEntity.setRoomTotals(updatedOrderEntity.getRoomTotals());
                existingOrderEntity.setShowTotals(updatedOrderEntity.getShowTotals());
                final List<String> pendingProducts = updatedOrderEntity.getLineItems().stream()
                        .filter(o -> o.getStatus().equalsIgnoreCase("PENDING")).map(item -> item.getCartLineItemId())
                        .collect(Collectors.toList());
                if (Utils.isEmpty(pendingProducts)) {
                    updatedOrderEntity.setComplete(true);
                    existingOrderEntity.setComplete(updatedOrderEntity.isComplete());
                }
            } catch (AppException ape) {
                throw new RuntimeException("Failed to merge order entity");
            }
        };
    }
}
