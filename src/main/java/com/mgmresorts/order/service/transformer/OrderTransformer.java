package com.mgmresorts.order.service.transformer;

import java.util.ArrayList;
import java.util.List;

import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.dto.services.Order;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.entity.LineItem;
import com.mgmresorts.order.entity.OrderStatus;
import com.mgmresorts.order.entity.ProductType;
import com.mgmresorts.order.entity.Type;
import com.mgmresorts.order.entity.Version;

public class OrderTransformer implements ITransformer<Order, com.mgmresorts.order.entity.Order> {

    @Override
    public com.mgmresorts.order.entity.Order toRight(Order left) {
        final com.mgmresorts.order.entity.Order right = new com.mgmresorts.order.entity.Order();
        right.setId(left.getId());
        right.setType(Type.fromValue(left.getType().value()));
        right.setVersion(Version.fromValue(left.getVersion().value()));
        right.setCartId(left.getCartId());
        right.setCustomerId(left.getCustomerId());
        right.setMlifeId(left.getMlifeId());
        right.setPaymentSessionId(left.getPaymentSessionId());
        right.setEncryptedEmailAddress(left.getEncryptedEmailAddress());
        if (left.getStatus() == Order.Status.SUCCESS) {
            right.setStatus(OrderStatus.SUCCESS);
        } else if (left.getStatus() == Order.Status.PENDING) {
            right.setStatus(OrderStatus.PENDING);
        } else {
            if (left.getStatus() == Order.Status.FAILURE) {
                right.setStatus(OrderStatus.FAILURE);
            } else if (left.getStatus() == Order.Status.PAYMENT_FAILURE) {
                right.setStatus(OrderStatus.PAYMENT_FAILURE);
            } else {
                right.setStatus(OrderStatus.PARTIAL);
            }
        }
        final List<OrderLineItem> orderLineItems = left.getOrderLineItems();
        if (!Utils.isEmpty(orderLineItems)) {
            final List<LineItem> lineItems = new ArrayList<>();
            for (OrderLineItem oli : orderLineItems) {
                final LineItem li = new LineItem();
                li.setConfirmationNumber(oli.getConfirmationNumber());
                li.setId(oli.getOrderLineItemId());
                li.setCartLineItemId(oli.getCartLineItemId());
                li.setProductType(ProductType.fromValue(oli.getProductType().value()));
                li.setProductId(oli.getProductId());
                li.setItineraryId(oli.getItineraryId());
                li.setProgramId(oli.getProgramId());
                li.setPropertyId(oli.getPropertyId());
                li.setPackageId(oli.getPackageId());
                li.setPackageLineItemId(oli.getPackageLineItemId());
                li.setStatus(oli.getStatus() != null ? oli.getStatus().name() : null);

                li.setMessages(oli.getMessages());

                li.setLineItemCharge(oli.getLineItemCharge());
                li.setLineItemTax(oli.getLineItemTax());
                li.setLineItemPrice(oli.getLineItemPrice());
                li.setLineItemStrikethroughPrice(oli.getLineItemStrikethroughPrice());
                li.setLineItemDeposit(oli.getLineItemDeposit());
                li.setLineItemDiscount(oli.getLineItemDiscount());
                li.setLineItemBalance(oli.getLineItemBalance());
                li.setSpecialRequests(oli.getSpecialRequests());
                li.setAddOnComponents(oli.getAddOnComponents());

                li.setLineItemTourismFeeAndTax(oli.getLineItemTourismFeeAndTax());
                li.setLineItemResortFeePerNight(oli.getLineItemResortFeePerNight());
                li.setLineItemOccupancyFee(oli.getLineItemOccupancyFee());
                li.setLineItemResortFeeAndTax(oli.getLineItemResortFeeAndTax());
                li.setLineItemAdjustedItemSubtotal(oli.getLineItemAdjustedItemSubtotal());
                li.setLineItemServiceChargeFeeAndTax(oli.getLineItemServiceChargeFeeAndTax());
                li.setLineItemTripSubtotal(oli.getLineItemTripSubtotal());
                li.setEnableJwb(oli.getEnableJwb() != null ? oli.getEnableJwb() : false);
                li.setF1Package(oli.getF1Package() != null ? oli.getF1Package() : false);
                li.setNumberOfNights(oli.getNumberOfNights());

                li.setLineItemDeliveryMethodFee(oli.getLineItemDeliveryMethodFee());
                li.setLineItemGratuity(oli.getLineItemGratuity());
                li.setLineItemLet(oli.getLineItemLet());
                li.setLineItemServiceChargeFee(oli.getLineItemServiceChargeFee());
                li.setLineItemServiceChargeTax(oli.getLineItemServiceChargeTax());
                li.setLineItemTransactionFee(oli.getLineItemTransactionFee());
                li.setLineItemTransactionFeeTax(oli.getLineItemTransactionFeeTax());
                li.setLineItemCasinoSurcharge(oli.getLineItemCasinoSurcharge());
                li.setLineItemCasinoSurchargeAndTax(oli.getLineItemCasinoSurchargeAndTax());
                li.setNumberOfTickets(oli.getNumberOfTickets());
                li.setSelectedDeliveryMethod(oli.getSelectedDeliveryMethod());
                li.setAddOnsPrice(oli.getAddOnsPrice());
                li.setAddOnsTax(oli.getAddOnsTax());
                li.setAveragePricePerNight(oli.getAveragePricePerNight());
                li.setContent(oli.getContent());
                li.setContentVersion(oli.getContentVersion());
                li.setOperaConfirmationNumber(oli.getOperaConfirmationNumber());
                li.setOperaHotelCode(oli.getOperaHotelCode());

                li.setReservationTime(oli.getReservationTime());
                li.setReservationDate(oli.getReservationDate());
                li.setUpgraded(oli.getUpgraded());

                lineItems.add(li);
            }
            right.setLineItems(lineItems);
        }
        right.setEnableJwb(left.getEnableJwb() != null ? left.getEnableJwb() : false);
        right.setJwbFlow(left.getJwbFlow() != null ? left.getJwbFlow() : false);
        right.setPriceDetails(left.getPriceDetails());
        right.setRoomTotals(left.getRoomTotals());
        right.setShowTotals(left.getShowTotals());
        right.setPackageConfigDetails(left.getPackageConfigDetails());
        right.setMgmId(left.getMgmId());
        right.setNewCartId(left.getNewCartId());
        right.setCanRetryCheckout(left.getCanRetryCheckout() != null ? left.getCanRetryCheckout() : false);
        right.setF1Package(!Utils.isEmpty(orderLineItems) ? orderLineItems.stream().anyMatch(p -> (p.getF1Package() != null && p.getF1Package())) ? true : false : false);
        return right;
    }

    @Override
    public Order toLeft(com.mgmresorts.order.entity.Order right) {
        final Order left = new Order();
        left.setId(right.getId());
        left.setType(com.mgmresorts.order.dto.services.Type.fromValue(right.getType().value()));
        left.setVersion(com.mgmresorts.order.dto.services.Version.fromValue(right.getVersion().value()));
        left.setCartId(right.getCartId());
        left.setCustomerId(right.getCustomerId());
        left.setMlifeId(right.getMlifeId());
        left.setPaymentSessionId(right.getPaymentSessionId());
        left.setEncryptedEmailAddress(right.getEncryptedEmailAddress());
        if (right.getStatus() == OrderStatus.SUCCESS) {
            left.setStatus(Order.Status.SUCCESS);
        } else if (right.getStatus() == OrderStatus.PENDING) {
            left.setStatus(Order.Status.PENDING);
        } else {
            if (right.getStatus() == OrderStatus.FAILURE) {
                left.setStatus(Order.Status.FAILURE);
            } else if (right.getStatus() == OrderStatus.PAYMENT_FAILURE) {
                left.setStatus(Order.Status.PAYMENT_FAILURE);
            } else {
                left.setStatus(Order.Status.PARTIAL);
            }
        }
        final List<LineItem> lineItems = right.getLineItems();
        if (!Utils.isEmpty(lineItems)) {
            final List<OrderLineItem> orderLineItems = new ArrayList<>();
            for (LineItem li : lineItems) {
                final OrderLineItem oli = new OrderLineItem();
                oli.setConfirmationNumber(li.getConfirmationNumber());
                oli.setOrderLineItemId(li.getId());
                oli.setCartLineItemId(li.getCartLineItemId());
                oli.setItineraryId(li.getItineraryId());
                oli.setProductType(OrderLineItem.ProductType.fromValue(li.getProductType().value()));
                oli.setProductId(li.getProductId());
                oli.setProgramId(li.getProgramId());
                oli.setPropertyId(li.getPropertyId());
                oli.setPackageId(li.getPackageId());
                oli.setPackageLineItemId(li.getPackageLineItemId());
                oli.setStatus(OrderLineItem.Status.fromValue(li.getStatus()));
                oli.setMessages(li.getMessages());

                oli.setLineItemCharge(li.getLineItemCharge());
                oli.setLineItemTax(li.getLineItemTax());
                oli.setLineItemPrice(li.getLineItemPrice());
                oli.setLineItemStrikethroughPrice(li.getLineItemStrikethroughPrice());
                oli.setLineItemDeposit(li.getLineItemDeposit());
                oli.setLineItemDiscount(li.getLineItemDiscount());
                oli.setLineItemBalance(li.getLineItemBalance());
                oli.setSpecialRequests(li.getSpecialRequests());
                oli.setAddOnComponents(li.getAddOnComponents());

                oli.setLineItemTourismFeeAndTax(li.getLineItemTourismFeeAndTax());
                oli.setLineItemResortFeePerNight(li.getLineItemResortFeePerNight());
                oli.setLineItemOccupancyFee(li.getLineItemOccupancyFee());
                oli.setLineItemResortFeeAndTax(li.getLineItemResortFeeAndTax());
                oli.setLineItemAdjustedItemSubtotal(li.getLineItemAdjustedItemSubtotal());
                oli.setLineItemServiceChargeFeeAndTax(li.getLineItemServiceChargeFeeAndTax());
                oli.setLineItemTripSubtotal(li.getLineItemTripSubtotal());
                oli.setEnableJwb(li.getEnableJwb());
                oli.setF1Package(li.getF1Package());
                oli.setNumberOfNights(li.getNumberOfNights());

                oli.setLineItemDeliveryMethodFee(li.getLineItemDeliveryMethodFee());
                oli.setLineItemGratuity(li.getLineItemGratuity());
                oli.setLineItemLet(li.getLineItemLet());
                oli.setLineItemServiceChargeFee(li.getLineItemServiceChargeFee());
                oli.setLineItemServiceChargeTax(li.getLineItemServiceChargeTax());
                oli.setLineItemTransactionFee(li.getLineItemTransactionFee());
                oli.setLineItemTransactionFeeTax(li.getLineItemTransactionFeeTax());
                oli.setLineItemCasinoSurcharge(li.getLineItemCasinoSurcharge());
                oli.setLineItemCasinoSurchargeAndTax(li.getLineItemCasinoSurchargeAndTax());
                oli.setNumberOfTickets(li.getNumberOfTickets());
                oli.setSelectedDeliveryMethod(li.getSelectedDeliveryMethod());
                oli.setAddOnsPrice(li.getAddOnsPrice());
                oli.setAddOnsTax(li.getAddOnsTax());
                oli.setAveragePricePerNight(li.getAveragePricePerNight());
                oli.setContent(li.getContent());
                oli.setContentVersion(li.getContentVersion());
                oli.setOperaConfirmationNumber(li.getOperaConfirmationNumber());
                oli.setOperaHotelCode(li.getOperaHotelCode());

                oli.setProductId(li.getProductId());
                oli.setReservationTime(li.getReservationTime());
                oli.setReservationDate(li.getReservationDate());
                oli.setUpgraded(li.isUpgraded());

                orderLineItems.add(oli);
            }
            left.setOrderLineItems(orderLineItems);
        }
        left.setEnableJwb(right.isEnableJwb());
        left.setJwbFlow(right.isJwbFlow());
        left.setPriceDetails(right.getPriceDetails());
        left.setRoomTotals(right.getRoomTotals());
        left.setShowTotals(right.getShowTotals());
        left.setPackageConfigDetails(right.getPackageConfigDetails());
        left.setMgmId(right.getMgmId());
        left.setNewCartId(right.getNewCartId());
        left.setCanRetryCheckout(right.isCanRetryCheckout());
        left.setF1Package(!Utils.isEmpty(lineItems) ? lineItems.stream().anyMatch(p -> (p.getF1Package())) ? true : false : false);
        return left;
    }
}
