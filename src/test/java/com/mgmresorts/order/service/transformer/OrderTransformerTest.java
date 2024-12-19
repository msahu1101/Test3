package com.mgmresorts.order.service.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.entity.ProductType;
import com.mgmresorts.order.entity.Type;
import com.mgmresorts.order.entity.Version;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.services.Order;

import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

class OrderTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final OrderTransformer transformer = new OrderTransformer();
    private final Random random = new Random();
    @Test
    final void testToRight() throws AppException {
        final Order order = podamFactoryImpl.manufacturePojo(Order.class);
        final com.mgmresorts.order.entity.Order orderEntity = transformer.toRight(order);

        // order
        assertEquals(orderEntity.getId(), order.getId());
        assertEquals(orderEntity.getType(), Type.fromValue(order.getType().value()));
        assertEquals(orderEntity.getVersion(), Version.fromValue(order.getVersion().value()));
        assertEquals(orderEntity.getCartId(), order.getCartId());
        assertEquals(orderEntity.getCustomerId(), order.getCustomerId());
        assertEquals(orderEntity.getMlifeId(), order.getMlifeId());
        assertEquals(orderEntity.getStatus().toString().toUpperCase(), order.getStatus().value().toUpperCase());
        assertEquals(orderEntity.isEnableJwb(), order.getEnableJwb());
        assertEquals(orderEntity.getMgmId(), order.getMgmId());
        assertEquals(orderEntity.getNewCartId(), order.getNewCartId());
        assertEquals(orderEntity.isCanRetryCheckout(), order.getCanRetryCheckout());
        assertEquals(orderEntity.isF1Package(), order.getF1Package());
        assertEquals(orderEntity.isJwbFlow(), order.getJwbFlow());
        assertEquals(orderEntity.getPaymentSessionId(), order.getPaymentSessionId());
        assertEquals(orderEntity.getEncryptedEmailAddress(), order.getEncryptedEmailAddress());

        assertEquals(orderEntity.getPriceDetails().getTotalCharge(), order.getPriceDetails().getTotalCharge());
        assertEquals(orderEntity.getPriceDetails().getTotalPrice(), order.getPriceDetails().getTotalPrice());
        assertEquals(orderEntity.getPriceDetails().getTotalStrikethroughPrice(), order.getPriceDetails().getTotalStrikethroughPrice());
        assertEquals(orderEntity.getPriceDetails().getTotalTax(), order.getPriceDetails().getTotalTax());
        assertEquals(orderEntity.getPriceDetails().getTotalDeposit(), order.getPriceDetails().getTotalDeposit());
        assertEquals(orderEntity.getPriceDetails().getTotalDiscount(), order.getPriceDetails().getTotalDiscount());
        assertEquals(orderEntity.getPriceDetails().getTotalBalanceDue(), order.getPriceDetails().getTotalBalanceDue());
        assertEquals(orderEntity.getPriceDetails().getTotalAdjustedItemSubtotal(), order.getPriceDetails().getTotalAdjustedItemSubtotal());

        assertEquals(orderEntity.getRoomTotals().getTotalAdjustedItemSubtotal(), order.getRoomTotals().getTotalAdjustedItemSubtotal());
        assertEquals(orderEntity.getRoomTotals().getTotalTourismFeeAndTax(), order.getRoomTotals().getTotalTourismFeeAndTax());
        assertEquals(orderEntity.getRoomTotals().getTotalResortFeePerNight(), order.getRoomTotals().getTotalResortFeePerNight());
        assertEquals(orderEntity.getRoomTotals().getTotalOccupancyFee(), order.getRoomTotals().getTotalOccupancyFee());
        assertEquals(orderEntity.getRoomTotals().getTotalResortFeeAndTax(), order.getRoomTotals().getTotalResortFeeAndTax());
        assertEquals(orderEntity.getRoomTotals().getTotalCasinoSurchargeAndTax(), order.getRoomTotals().getTotalCasinoSurchargeAndTax());
        assertEquals(orderEntity.getRoomTotals().getTotalTripSubtotal(), order.getRoomTotals().getTotalTripSubtotal());
        assertEquals(orderEntity.getRoomTotals().getTotalPrice(), order.getRoomTotals().getTotalPrice());

        assertEquals(orderEntity.getShowTotals().getTotalAdjustedItemSubtotal(), order.getShowTotals().getTotalAdjustedItemSubtotal());
        assertEquals(orderEntity.getShowTotals().getTotalServiceChargeFeeAndTax(), order.getShowTotals().getTotalServiceChargeFeeAndTax());
        assertEquals(orderEntity.getShowTotals().getTotalDeliveryFee(), order.getShowTotals().getTotalDeliveryFee());
        assertEquals(orderEntity.getShowTotals().getTotalGratuity(), order.getShowTotals().getTotalGratuity());
        assertEquals(orderEntity.getShowTotals().getTotalLet(), order.getShowTotals().getTotalLet());
        assertEquals(orderEntity.getShowTotals().getTotalServiceChargeFee(), order.getShowTotals().getTotalServiceChargeFee());
        assertEquals(orderEntity.getShowTotals().getTotalServiceChargeTax(), order.getShowTotals().getTotalServiceChargeTax());
        assertEquals(orderEntity.getShowTotals().getTotalTransactionFee(), order.getShowTotals().getTotalTransactionFee());
        assertEquals(orderEntity.getShowTotals().getTotalTransactionTax(), order.getShowTotals().getTotalTransactionTax());
        assertEquals(orderEntity.getShowTotals().getTotalPrice(), order.getShowTotals().getTotalPrice());

        assertEquals(orderEntity.getPackageConfigDetails().getPackageCategoryId(), order.getPackageConfigDetails().getPackageCategoryId());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePriceBreakdown(), order.getPackageConfigDetails().getPackagePriceBreakdown());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getPackageBaseTotal(), order.getPackageConfigDetails().getPackagePricingDetails().getPackageBaseTotal());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getPackageStartingPrice(), order.getPackageConfigDetails().getPackagePricingDetails().getPackageStartingPrice());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getPackageTotal(), order.getPackageConfigDetails().getPackagePricingDetails().getPackageTotal());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getRoomModification(), order.getPackageConfigDetails().getPackagePricingDetails().getRoomModification());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getShowModification(), order.getPackageConfigDetails().getPackagePricingDetails().getShowModification());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getRoomTotal(), order.getPackageConfigDetails().getPackagePricingDetails().getRoomTotal());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getShowTotal(), order.getPackageConfigDetails().getPackagePricingDetails().getShowTotal());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getPackageInclusions(), order.getPackageConfigDetails().getPackagePricingDetails().getPackageInclusions());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getCode(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getCode());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getId(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getId());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getStart(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getStart());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getEnd(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getEnd());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getDescription(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getDescription());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getLongDescription(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getLongDescription());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getShortDescription(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getShortDescription());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getNightlyCharge(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getNightlyCharge());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getAmtAftTax(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getAmtAftTax());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getPricingApplied(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getPricingApplied());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanCode(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanCode());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanName(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanName());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getTaxRate(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getTaxRate());

        // order line items
        for (int i = 0; i < orderEntity.getLineItems().size(); i++) {
            assertEquals(orderEntity.getLineItems().get(i).getConfirmationNumber(), order.getOrderLineItems().get(i).getConfirmationNumber());
            assertEquals(orderEntity.getLineItems().get(i).getId(), order.getOrderLineItems().get(i).getOrderLineItemId());
            assertEquals(orderEntity.getLineItems().get(i).getCartLineItemId(), order.getOrderLineItems().get(i).getCartLineItemId());
            assertEquals(orderEntity.getLineItems().get(i).getProductType(), ProductType.fromValue(order.getOrderLineItems().get(i).getProductType().value()));
            assertEquals(orderEntity.getLineItems().get(i).getProductId(), order.getOrderLineItems().get(i).getProductId());
            assertEquals(orderEntity.getLineItems().get(i).getItineraryId(), order.getOrderLineItems().get(i).getItineraryId());
            assertEquals(orderEntity.getLineItems().get(i).getProgramId(), order.getOrderLineItems().get(i).getProgramId());
            assertEquals(orderEntity.getLineItems().get(i).getPropertyId(), order.getOrderLineItems().get(i).getPropertyId());
            assertEquals(orderEntity.getLineItems().get(i).getPackageId(), order.getOrderLineItems().get(i).getPackageId());
            assertEquals(orderEntity.getLineItems().get(i).getPackageLineItemId(), order.getOrderLineItems().get(i).getPackageLineItemId());
            assertEquals(orderEntity.getLineItems().get(i).getStatus(), order.getOrderLineItems().get(i).getStatus().name());

            assertEquals(orderEntity.getLineItems().get(i).getMessages().get(0).getCode(), order.getOrderLineItems().get(i).getMessages().get(0).getCode());
            assertEquals(orderEntity.getLineItems().get(i).getMessages().get(0).getType(), order.getOrderLineItems().get(i).getMessages().get(0).getType());
            assertEquals(orderEntity.getLineItems().get(i).getMessages().get(0).getSourceSystemError().getSourceSystemMessage(), order.getOrderLineItems().get(i).getMessages().get(0).getSourceSystemError().getSourceSystemMessage());
            assertEquals(orderEntity.getLineItems().get(i).getMessages().get(0).getSourceSystemError().getSourceSystemCode(), order.getOrderLineItems().get(i).getMessages().get(0).getSourceSystemError().getSourceSystemCode());


            assertEquals(orderEntity.getLineItems().get(i).getLineItemCharge(), order.getOrderLineItems().get(i).getLineItemCharge());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemTax(), order.getOrderLineItems().get(i).getLineItemTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemPrice(), order.getOrderLineItems().get(i).getLineItemPrice());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemStrikethroughPrice(), order.getOrderLineItems().get(i).getLineItemStrikethroughPrice());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemDeposit(), order.getOrderLineItems().get(i).getLineItemDeposit());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemDiscount(), order.getOrderLineItems().get(i).getLineItemDiscount());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemBalance(), order.getOrderLineItems().get(i).getLineItemBalance());

            assertEquals(orderEntity.getLineItems().get(i).getLineItemTourismFeeAndTax(), order.getOrderLineItems().get(i).getLineItemTourismFeeAndTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemResortFeePerNight(), order.getOrderLineItems().get(i).getLineItemResortFeePerNight());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemOccupancyFee(), order.getOrderLineItems().get(i).getLineItemOccupancyFee());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemResortFeeAndTax(), order.getOrderLineItems().get(i).getLineItemResortFeeAndTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemAdjustedItemSubtotal(), order.getOrderLineItems().get(i).getLineItemAdjustedItemSubtotal());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemServiceChargeFeeAndTax(), order.getOrderLineItems().get(i).getLineItemServiceChargeFeeAndTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemTripSubtotal(), order.getOrderLineItems().get(i).getLineItemTripSubtotal());
            assertEquals(orderEntity.getLineItems().get(i).getEnableJwb(), order.getOrderLineItems().get(i).getEnableJwb());
            assertEquals(orderEntity.getLineItems().get(i).getF1Package(), order.getOrderLineItems().get(i).getF1Package());
            assertEquals(orderEntity.getLineItems().get(i).getNumberOfNights(), order.getOrderLineItems().get(i).getNumberOfNights());
            assertEquals(orderEntity.getLineItems().get(i).getSpecialRequests(), order.getOrderLineItems().get(i).getSpecialRequests());

            assertEquals(orderEntity.getLineItems().get(i).getLineItemDeliveryMethodFee(), order.getOrderLineItems().get(i).getLineItemDeliveryMethodFee());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemGratuity(), order.getOrderLineItems().get(i).getLineItemGratuity());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemLet(), order.getOrderLineItems().get(i).getLineItemLet());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemServiceChargeFee(), order.getOrderLineItems().get(i).getLineItemServiceChargeFee());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemServiceChargeTax(), order.getOrderLineItems().get(i).getLineItemServiceChargeTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemTransactionFee(), order.getOrderLineItems().get(i).getLineItemTransactionFee());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemTransactionFeeTax(), order.getOrderLineItems().get(i).getLineItemTransactionFeeTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemCasinoSurcharge(), order.getOrderLineItems().get(i).getLineItemCasinoSurcharge());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemCasinoSurchargeAndTax(), order.getOrderLineItems().get(i).getLineItemCasinoSurchargeAndTax());
            assertEquals(orderEntity.getLineItems().get(i).getNumberOfTickets(), order.getOrderLineItems().get(i).getNumberOfTickets());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnsPrice(), order.getOrderLineItems().get(i).getAddOnsPrice());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnsTax(), order.getOrderLineItems().get(i).getAddOnsTax());
            assertEquals(orderEntity.getLineItems().get(i).getAveragePricePerNight(), order.getOrderLineItems().get(i).getAveragePricePerNight());
            assertEquals(orderEntity.getLineItems().get(i).getContent(), order.getOrderLineItems().get(i).getContent());
            assertEquals(orderEntity.getLineItems().get(i).getContentVersion(), order.getOrderLineItems().get(i).getContentVersion());
            assertEquals(orderEntity.getLineItems().get(i).getOperaConfirmationNumber(), order.getOrderLineItems().get(i).getOperaConfirmationNumber());
            assertEquals(orderEntity.getLineItems().get(i).getOperaHotelCode(), order.getOrderLineItems().get(i).getOperaHotelCode());

            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getSelected(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getSelected());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getId(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getId());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getCode(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getCode());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getDescription(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getDescription());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getName(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getName());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getActive(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getActive());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getDefaultDeliveryMethod(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getDefaultDeliveryMethod());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getePrinting(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getePrinting());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getAmount(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getAmount());

            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getNonEditable(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getNonEditable());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getSelected(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getSelected());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getId(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getId());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getCode(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getCode());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getPricingApplied(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getPricingApplied());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getLongDescription(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getLongDescription());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getShortDescription(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getShortDescription());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getActive(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getActive());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getTripPrice(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getTripPrice());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getTripTax(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getTripTax());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getPrice(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getPrice());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getDepositAmount(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getDepositAmount());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getDate(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getDate());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getAmount(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getAmount());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getTax(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getTax());

            assertEquals(orderEntity.getLineItems().get(i).getReservationTime(), order.getOrderLineItems().get(i).getReservationTime());
            assertEquals(orderEntity.getLineItems().get(i).getReservationDate(), order.getOrderLineItems().get(i).getReservationDate());
            assertEquals(orderEntity.getLineItems().get(i).isUpgraded(), order.getOrderLineItems().get(i).getUpgraded());
        }
    }

    @Test
    final void testToLeft() throws AppException {
        final com.mgmresorts.order.entity.Order orderEntity = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.entity.Order.class);
        final List<OrderLineItem.Status> statuses = Arrays.stream(OrderLineItem.Status.values()).collect(Collectors.toList());
        orderEntity.getLineItems().forEach(lineItem -> {
            lineItem.setStatus(statuses.get(random.nextInt(statuses.size())).toString());
        });
        final Order order = transformer.toLeft(orderEntity);
        // order
        assertEquals(orderEntity.getId(), order.getId());
        assertEquals(orderEntity.getType(), Type.fromValue(order.getType().value()));
        assertEquals(orderEntity.getVersion(), Version.fromValue(order.getVersion().value()));
        assertEquals(orderEntity.getCartId(), order.getCartId());
        assertEquals(orderEntity.getCustomerId(), order.getCustomerId());
        assertEquals(orderEntity.getMlifeId(), order.getMlifeId());
        assertEquals(orderEntity.getStatus().toString().toUpperCase(), order.getStatus().value().toUpperCase());
        assertEquals(orderEntity.isEnableJwb(), order.getEnableJwb());
        assertEquals(orderEntity.getMgmId(), order.getMgmId());
        assertEquals(orderEntity.getNewCartId(), order.getNewCartId());
        assertEquals(orderEntity.isCanRetryCheckout(), order.getCanRetryCheckout());
        assertEquals(orderEntity.isF1Package(), order.getF1Package());
        assertEquals(orderEntity.isJwbFlow(), order.getJwbFlow());
        assertEquals(orderEntity.getPaymentSessionId(), order.getPaymentSessionId());
        assertEquals(orderEntity.getEncryptedEmailAddress(), order.getEncryptedEmailAddress());

        assertEquals(orderEntity.getPriceDetails().getTotalCharge(), order.getPriceDetails().getTotalCharge());
        assertEquals(orderEntity.getPriceDetails().getTotalPrice(), order.getPriceDetails().getTotalPrice());
        assertEquals(orderEntity.getPriceDetails().getTotalStrikethroughPrice(), order.getPriceDetails().getTotalStrikethroughPrice());
        assertEquals(orderEntity.getPriceDetails().getTotalTax(), order.getPriceDetails().getTotalTax());
        assertEquals(orderEntity.getPriceDetails().getTotalDeposit(), order.getPriceDetails().getTotalDeposit());
        assertEquals(orderEntity.getPriceDetails().getTotalDiscount(), order.getPriceDetails().getTotalDiscount());
        assertEquals(orderEntity.getPriceDetails().getTotalBalanceDue(), order.getPriceDetails().getTotalBalanceDue());
        assertEquals(orderEntity.getPriceDetails().getTotalAdjustedItemSubtotal(), order.getPriceDetails().getTotalAdjustedItemSubtotal());

        assertEquals(orderEntity.getRoomTotals().getTotalAdjustedItemSubtotal(), order.getRoomTotals().getTotalAdjustedItemSubtotal());
        assertEquals(orderEntity.getRoomTotals().getTotalTourismFeeAndTax(), order.getRoomTotals().getTotalTourismFeeAndTax());
        assertEquals(orderEntity.getRoomTotals().getTotalResortFeePerNight(), order.getRoomTotals().getTotalResortFeePerNight());
        assertEquals(orderEntity.getRoomTotals().getTotalOccupancyFee(), order.getRoomTotals().getTotalOccupancyFee());
        assertEquals(orderEntity.getRoomTotals().getTotalResortFeeAndTax(), order.getRoomTotals().getTotalResortFeeAndTax());
        assertEquals(orderEntity.getRoomTotals().getTotalCasinoSurchargeAndTax(), order.getRoomTotals().getTotalCasinoSurchargeAndTax());
        assertEquals(orderEntity.getRoomTotals().getTotalTripSubtotal(), order.getRoomTotals().getTotalTripSubtotal());
        assertEquals(orderEntity.getRoomTotals().getTotalPrice(), order.getRoomTotals().getTotalPrice());

        assertEquals(orderEntity.getShowTotals().getTotalAdjustedItemSubtotal(), order.getShowTotals().getTotalAdjustedItemSubtotal());
        assertEquals(orderEntity.getShowTotals().getTotalServiceChargeFeeAndTax(), order.getShowTotals().getTotalServiceChargeFeeAndTax());
        assertEquals(orderEntity.getShowTotals().getTotalDeliveryFee(), order.getShowTotals().getTotalDeliveryFee());
        assertEquals(orderEntity.getShowTotals().getTotalGratuity(), order.getShowTotals().getTotalGratuity());
        assertEquals(orderEntity.getShowTotals().getTotalLet(), order.getShowTotals().getTotalLet());
        assertEquals(orderEntity.getShowTotals().getTotalServiceChargeFee(), order.getShowTotals().getTotalServiceChargeFee());
        assertEquals(orderEntity.getShowTotals().getTotalServiceChargeTax(), order.getShowTotals().getTotalServiceChargeTax());
        assertEquals(orderEntity.getShowTotals().getTotalTransactionFee(), order.getShowTotals().getTotalTransactionFee());
        assertEquals(orderEntity.getShowTotals().getTotalTransactionTax(), order.getShowTotals().getTotalTransactionTax());
        assertEquals(orderEntity.getShowTotals().getTotalPrice(), order.getShowTotals().getTotalPrice());

        assertEquals(orderEntity.getPackageConfigDetails().getPackageCategoryId(), order.getPackageConfigDetails().getPackageCategoryId());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePriceBreakdown(), order.getPackageConfigDetails().getPackagePriceBreakdown());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getPackageBaseTotal(), order.getPackageConfigDetails().getPackagePricingDetails().getPackageBaseTotal());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getPackageStartingPrice(), order.getPackageConfigDetails().getPackagePricingDetails().getPackageStartingPrice());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getPackageTotal(), order.getPackageConfigDetails().getPackagePricingDetails().getPackageTotal());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getRoomModification(), order.getPackageConfigDetails().getPackagePricingDetails().getRoomModification());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getShowModification(), order.getPackageConfigDetails().getPackagePricingDetails().getShowModification());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getRoomTotal(), order.getPackageConfigDetails().getPackagePricingDetails().getRoomTotal());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getShowTotal(), order.getPackageConfigDetails().getPackagePricingDetails().getShowTotal());
        assertEquals(orderEntity.getPackageConfigDetails().getPackagePricingDetails().getPackageInclusions(), order.getPackageConfigDetails().getPackagePricingDetails().getPackageInclusions());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getCode(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getCode());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getId(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getId());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getStart(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getStart());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getEnd(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getEnd());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getDescription(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getDescription());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getLongDescription(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getLongDescription());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getShortDescription(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getShortDescription());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getNightlyCharge(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getNightlyCharge());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getAmtAftTax(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getAmtAftTax());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getPricingApplied(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getPricingApplied());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanCode(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanCode());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanName(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getRatePlanName());
        assertEquals(orderEntity.getPackageConfigDetails().getPackageComponentDetails().get(0).getTaxRate(), order.getPackageConfigDetails().getPackageComponentDetails().get(0).getTaxRate());

        // order line items
        for (int i = 0; i < orderEntity.getLineItems().size(); i++) {
            assertEquals(orderEntity.getLineItems().get(i).getConfirmationNumber(), order.getOrderLineItems().get(i).getConfirmationNumber());
            assertEquals(orderEntity.getLineItems().get(i).getId(), order.getOrderLineItems().get(i).getOrderLineItemId());
            assertEquals(orderEntity.getLineItems().get(i).getCartLineItemId(), order.getOrderLineItems().get(i).getCartLineItemId());
            assertEquals(orderEntity.getLineItems().get(i).getProductType(), ProductType.fromValue(order.getOrderLineItems().get(i).getProductType().value()));
            assertEquals(orderEntity.getLineItems().get(i).getProductId(), order.getOrderLineItems().get(i).getProductId());
            assertEquals(orderEntity.getLineItems().get(i).getItineraryId(), order.getOrderLineItems().get(i).getItineraryId());
            assertEquals(orderEntity.getLineItems().get(i).getProgramId(), order.getOrderLineItems().get(i).getProgramId());
            assertEquals(orderEntity.getLineItems().get(i).getPropertyId(), order.getOrderLineItems().get(i).getPropertyId());
            assertEquals(orderEntity.getLineItems().get(i).getPackageId(), order.getOrderLineItems().get(i).getPackageId());
            assertEquals(orderEntity.getLineItems().get(i).getPackageLineItemId(), order.getOrderLineItems().get(i).getPackageLineItemId());
            assertEquals(orderEntity.getLineItems().get(i).getStatus(), order.getOrderLineItems().get(i).getStatus().name());

            assertEquals(orderEntity.getLineItems().get(i).getMessages().get(0).getCode(), order.getOrderLineItems().get(i).getMessages().get(0).getCode());
            assertEquals(orderEntity.getLineItems().get(i).getMessages().get(0).getType(), order.getOrderLineItems().get(i).getMessages().get(0).getType());
            assertEquals(orderEntity.getLineItems().get(i).getMessages().get(0).getSourceSystemError().getSourceSystemMessage(), order.getOrderLineItems().get(i).getMessages().get(0).getSourceSystemError().getSourceSystemMessage());
            assertEquals(orderEntity.getLineItems().get(i).getMessages().get(0).getSourceSystemError().getSourceSystemCode(), order.getOrderLineItems().get(i).getMessages().get(0).getSourceSystemError().getSourceSystemCode());

            assertEquals(orderEntity.getLineItems().get(i).getLineItemCharge(), order.getOrderLineItems().get(i).getLineItemCharge());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemTax(), order.getOrderLineItems().get(i).getLineItemTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemPrice(), order.getOrderLineItems().get(i).getLineItemPrice());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemStrikethroughPrice(), order.getOrderLineItems().get(i).getLineItemStrikethroughPrice());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemDeposit(), order.getOrderLineItems().get(i).getLineItemDeposit());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemDiscount(), order.getOrderLineItems().get(i).getLineItemDiscount());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemBalance(), order.getOrderLineItems().get(i).getLineItemBalance());

            assertEquals(orderEntity.getLineItems().get(i).getLineItemTourismFeeAndTax(), order.getOrderLineItems().get(i).getLineItemTourismFeeAndTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemResortFeePerNight(), order.getOrderLineItems().get(i).getLineItemResortFeePerNight());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemOccupancyFee(), order.getOrderLineItems().get(i).getLineItemOccupancyFee());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemResortFeeAndTax(), order.getOrderLineItems().get(i).getLineItemResortFeeAndTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemAdjustedItemSubtotal(), order.getOrderLineItems().get(i).getLineItemAdjustedItemSubtotal());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemServiceChargeFeeAndTax(), order.getOrderLineItems().get(i).getLineItemServiceChargeFeeAndTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemTripSubtotal(), order.getOrderLineItems().get(i).getLineItemTripSubtotal());
            assertEquals(orderEntity.getLineItems().get(i).getEnableJwb(), order.getOrderLineItems().get(i).getEnableJwb());
            assertEquals(orderEntity.getLineItems().get(i).getF1Package(), order.getOrderLineItems().get(i).getF1Package());
            assertEquals(orderEntity.getLineItems().get(i).getNumberOfNights(), order.getOrderLineItems().get(i).getNumberOfNights());
            assertEquals(orderEntity.getLineItems().get(i).getSpecialRequests(), order.getOrderLineItems().get(i).getSpecialRequests());

            assertEquals(orderEntity.getLineItems().get(i).getLineItemDeliveryMethodFee(), order.getOrderLineItems().get(i).getLineItemDeliveryMethodFee());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemGratuity(), order.getOrderLineItems().get(i).getLineItemGratuity());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemLet(), order.getOrderLineItems().get(i).getLineItemLet());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemServiceChargeFee(), order.getOrderLineItems().get(i).getLineItemServiceChargeFee());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemServiceChargeTax(), order.getOrderLineItems().get(i).getLineItemServiceChargeTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemTransactionFee(), order.getOrderLineItems().get(i).getLineItemTransactionFee());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemTransactionFeeTax(), order.getOrderLineItems().get(i).getLineItemTransactionFeeTax());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemCasinoSurcharge(), order.getOrderLineItems().get(i).getLineItemCasinoSurcharge());
            assertEquals(orderEntity.getLineItems().get(i).getLineItemCasinoSurchargeAndTax(), order.getOrderLineItems().get(i).getLineItemCasinoSurchargeAndTax());
            assertEquals(orderEntity.getLineItems().get(i).getNumberOfTickets(), order.getOrderLineItems().get(i).getNumberOfTickets());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnsPrice(), order.getOrderLineItems().get(i).getAddOnsPrice());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnsTax(), order.getOrderLineItems().get(i).getAddOnsTax());
            assertEquals(orderEntity.getLineItems().get(i).getAveragePricePerNight(), order.getOrderLineItems().get(i).getAveragePricePerNight());
            assertEquals(orderEntity.getLineItems().get(i).getContent(), order.getOrderLineItems().get(i).getContent());
            assertEquals(orderEntity.getLineItems().get(i).getContentVersion(), order.getOrderLineItems().get(i).getContentVersion());
            assertEquals(orderEntity.getLineItems().get(i).getOperaConfirmationNumber(), order.getOrderLineItems().get(i).getOperaConfirmationNumber());
            assertEquals(orderEntity.getLineItems().get(i).getOperaHotelCode(), order.getOrderLineItems().get(i).getOperaHotelCode());

            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getSelected(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getSelected());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getId(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getId());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getCode(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getCode());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getDescription(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getDescription());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getName(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getName());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getActive(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getActive());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getDefaultDeliveryMethod(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getDefaultDeliveryMethod());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getePrinting(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getePrinting());
            assertEquals(orderEntity.getLineItems().get(i).getSelectedDeliveryMethod().getAmount(), order.getOrderLineItems().get(i).getSelectedDeliveryMethod().getAmount());

            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getNonEditable(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getNonEditable());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getSelected(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getSelected());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getId(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getId());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getCode(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getCode());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getPricingApplied(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getPricingApplied());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getLongDescription(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getLongDescription());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getShortDescription(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getShortDescription());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getActive(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getActive());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getTripPrice(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getTripPrice());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getTripTax(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getTripTax());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getPrice(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getPrice());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getDate(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getDate());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getAmount(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getAmount());
            assertEquals(orderEntity.getLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getTax(), order.getOrderLineItems().get(i).getAddOnComponents().get(0).getPrices().get(0).getTax());

            assertEquals(orderEntity.getLineItems().get(i).getReservationTime(), order.getOrderLineItems().get(i).getReservationTime());
            assertEquals(orderEntity.getLineItems().get(i).getReservationDate(), order.getOrderLineItems().get(i).getReservationDate());
            assertEquals(orderEntity.getLineItems().get(i).isUpgraded(), order.getOrderLineItems().get(i).getUpgraded());
        }
    }
}
