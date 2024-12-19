package com.mgmresorts.order.utils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.dto.services.Order;
import org.junit.jupiter.api.Test;

public class OrdersTest {
    final JSonMapper mapper = new JSonMapper();

    @Test
    public void testPriceCalculationsGlobalOrderPending() throws Exception {
        final Order order =  mapper.readValue(getPendingOrder(), Order.class);
        new Orders().calculateOrderPrice(order);

        // room line items
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemCharge());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemTax());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemPrice());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemDeposit());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemDiscount());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemBalance());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemTourismFeeAndTax());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemResortFeePerNight());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemOccupancyFee());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemResortFeeAndTax());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemAdjustedItemSubtotal());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemTripSubtotal());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemCasinoSurcharge());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemCasinoSurchargeAndTax());

        // room totals
        assertEquals(0.0, order.getRoomTotals().getTotalTourismFeeAndTax());
        assertEquals(0.0, order.getRoomTotals().getTotalResortFeePerNight());
        assertEquals(0.0, order.getRoomTotals().getTotalOccupancyFee());
        assertEquals(0.0, order.getRoomTotals().getTotalResortFeeAndTax());
        assertEquals(0.0, order.getRoomTotals().getTotalTripSubtotal());
        assertEquals(0.0, order.getRoomTotals().getTotalPrice());

        // show totals
        assertEquals(0.0, order.getShowTotals().getTotalDeliveryFee());
        assertEquals(0.0, order.getShowTotals().getTotalGratuity());
        assertEquals(0.0, order.getShowTotals().getTotalLet());
        assertEquals(0.0, order.getShowTotals().getTotalServiceChargeFee());
        assertEquals(0.0, order.getShowTotals().getTotalServiceChargeTax());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionFee());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionTax());
        assertEquals(0.0, order.getShowTotals().getTotalPrice());

        // price details
        assertEquals(0.0, order.getPriceDetails().getTotalPrice());
        assertEquals(0.0, order.getPriceDetails().getTotalCharge());
        assertEquals(0.0, order.getPriceDetails().getTotalTax());
        assertEquals(0.0, order.getPriceDetails().getTotalDeposit());
        assertEquals(0.0, order.getPriceDetails().getTotalDiscount());
        assertEquals(0.0, order.getPriceDetails().getTotalBalanceDue());
        assertEquals(0.0, order.getPriceDetails().getTotalAdjustedItemSubtotal());
    }

    @Test
    public void testPriceCalculationsGlobalOrderSuccess() throws Exception {
        final Order order =  mapper.readValue(getGlobalOrderRoomShowSuccess(), Order.class);
        new Orders().calculateOrderPrice(order);

        // room line items
        assertEquals(299.98, order.getOrderLineItems().get(0).getLineItemCharge());
        assertEquals(120.42, order.getOrderLineItems().get(0).getLineItemTax());
        assertEquals(1108.8, order.getOrderLineItems().get(0).getLineItemPrice());
        assertEquals(1020.35, order.getOrderLineItems().get(0).getLineItemDeposit());
        assertEquals(-599.96, order.getOrderLineItems().get(0).getLineItemDiscount());
        assertEquals(88.45, order.getOrderLineItems().get(0).getLineItemBalance());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemTourismFeeAndTax());
        assertEquals(39.0, order.getOrderLineItems().get(0).getLineItemResortFeePerNight());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemOccupancyFee());
        assertEquals(88.44, order.getOrderLineItems().get(0).getLineItemResortFeeAndTax());
        assertEquals(899.94, order.getOrderLineItems().get(0).getLineItemAdjustedItemSubtotal());
        assertEquals(977.94, order.getOrderLineItems().get(0).getLineItemTripSubtotal());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemCasinoSurcharge());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemCasinoSurchargeAndTax());

        // show line items
        assertEquals(105.55, order.getOrderLineItems().get(1).getLineItemCharge());
        assertEquals(10.73, order.getOrderLineItems().get(1).getLineItemTax());
        assertEquals(130.0, order.getOrderLineItems().get(1).getLineItemPrice());
        assertEquals(130.0, order.getOrderLineItems().get(1).getLineItemDeposit());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemDiscount());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemBalance());
        assertEquals(9.5, order.getOrderLineItems().get(1).getLineItemLet());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemDeliveryMethodFee());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemGratuity());
        assertEquals(13.72, order.getOrderLineItems().get(1).getLineItemServiceChargeFee());
        assertEquals(1.23, order.getOrderLineItems().get(1).getLineItemServiceChargeTax());
        assertEquals(0.0,order.getOrderLineItems().get(1).getLineItemTransactionFee());
        assertEquals(0.0,order.getOrderLineItems().get(1).getLineItemTransactionFeeTax());

        // room totals
        assertEquals(0.0, order.getRoomTotals().getTotalTourismFeeAndTax());
        assertEquals(39.0, order.getRoomTotals().getTotalResortFeePerNight());
        assertEquals(0.0, order.getRoomTotals().getTotalOccupancyFee());
        assertEquals(88.44, order.getRoomTotals().getTotalResortFeeAndTax());
        assertEquals(977.94, order.getRoomTotals().getTotalTripSubtotal());
        assertEquals(1108.8, order.getRoomTotals().getTotalPrice());

        // show totals
        assertEquals(0.0, order.getShowTotals().getTotalDeliveryFee());
        assertEquals(0.0, order.getShowTotals().getTotalGratuity());
        assertEquals(9.5, order.getShowTotals().getTotalLet());
        assertEquals(13.72, order.getShowTotals().getTotalServiceChargeFee());
        assertEquals(1.23, order.getShowTotals().getTotalServiceChargeTax());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionFee());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionTax());
        assertEquals(130.0, order.getShowTotals().getTotalPrice());

        // price details
        assertEquals(1238.8, order.getPriceDetails().getTotalPrice());
        assertEquals(405.53, order.getPriceDetails().getTotalCharge());
        assertEquals(131.15, order.getPriceDetails().getTotalTax());
        assertEquals(1150.35, order.getPriceDetails().getTotalDeposit());
        assertEquals(-599.96, order.getPriceDetails().getTotalDiscount());
        assertEquals(88.45, order.getPriceDetails().getTotalBalanceDue());
        assertEquals(899.94, order.getPriceDetails().getTotalAdjustedItemSubtotal());
    }

    @Test
    public void testPriceCalculationsGlobalOrderFailure() throws Exception {
        final Order order =  mapper.readValue(getGlobalOrderRoomShowFailure(), Order.class);
        new Orders().calculateOrderPrice(order);

        // room line items
        assertEquals(299.98, order.getOrderLineItems().get(1).getLineItemCharge());
        assertEquals(120.42, order.getOrderLineItems().get(1).getLineItemTax());
        assertEquals(1108.8, order.getOrderLineItems().get(1).getLineItemPrice());
        assertEquals(1020.35, order.getOrderLineItems().get(1).getLineItemDeposit());
        assertEquals(-599.96, order.getOrderLineItems().get(1).getLineItemDiscount());
        assertEquals(88.45, order.getOrderLineItems().get(1).getLineItemBalance());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemTourismFeeAndTax());
        assertEquals(39.0, order.getOrderLineItems().get(1).getLineItemResortFeePerNight());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemOccupancyFee());
        assertEquals(88.44, order.getOrderLineItems().get(1).getLineItemResortFeeAndTax());
        assertEquals(899.94, order.getOrderLineItems().get(1).getLineItemAdjustedItemSubtotal());
        assertEquals(null, order.getOrderLineItems().get(1).getLineItemTripSubtotal());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemCasinoSurcharge());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemCasinoSurchargeAndTax());

        // show line items
        assertEquals(105.55, order.getOrderLineItems().get(0).getLineItemCharge());
        assertEquals(10.73, order.getOrderLineItems().get(0).getLineItemTax());
        assertEquals(130.0, order.getOrderLineItems().get(0).getLineItemPrice());
        assertEquals(130.0, order.getOrderLineItems().get(0).getLineItemDeposit());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemDiscount());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemBalance());
        assertEquals(9.5, order.getOrderLineItems().get(0).getLineItemLet());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemDeliveryMethodFee());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemGratuity());
        assertEquals(13.72, order.getOrderLineItems().get(0).getLineItemServiceChargeFee());
        assertEquals(1.23, order.getOrderLineItems().get(0).getLineItemServiceChargeTax());
        assertEquals(0.0,order.getOrderLineItems().get(0).getLineItemTransactionFee());
        assertEquals(0.0,order.getOrderLineItems().get(0).getLineItemTransactionFeeTax());

        // room totals
        assertEquals(0.0, order.getRoomTotals().getTotalTourismFeeAndTax());
        assertEquals(0.0, order.getRoomTotals().getTotalResortFeePerNight());
        assertEquals(0.0, order.getRoomTotals().getTotalOccupancyFee());
        assertEquals(0.0, order.getRoomTotals().getTotalResortFeeAndTax());
        assertEquals(0.0, order.getRoomTotals().getTotalTripSubtotal());
        assertEquals(0.0, order.getRoomTotals().getTotalPrice());

        // show totals
        assertEquals(0.0, order.getShowTotals().getTotalDeliveryFee());
        assertEquals(0.0, order.getShowTotals().getTotalGratuity());
        assertEquals(0.0, order.getShowTotals().getTotalLet());
        assertEquals(0.0, order.getShowTotals().getTotalServiceChargeFee());
        assertEquals(0.0, order.getShowTotals().getTotalServiceChargeTax());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionFee());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionTax());
        assertEquals(0.0, order.getShowTotals().getTotalPrice());

        // price details
        assertEquals(0.0, order.getPriceDetails().getTotalPrice());
        assertEquals(0.0, order.getPriceDetails().getTotalCharge());
        assertEquals(0.0, order.getPriceDetails().getTotalTax());
        assertEquals(0.0, order.getPriceDetails().getTotalDeposit());
        assertEquals(0.0, order.getPriceDetails().getTotalDiscount());
        assertEquals(0.0, order.getPriceDetails().getTotalBalanceDue());
        assertEquals(0.0, order.getPriceDetails().getTotalAdjustedItemSubtotal());
    }

    @Test
    public void testPriceCalculationsGlobalOrderPartialFailure() throws Exception {
        final Order order =  mapper.readValue(getGlobalOrderRoomSuccessShowFailure(), Order.class);
        new Orders().calculateOrderPrice(order);

        // room line items
        assertEquals(299.98, order.getOrderLineItems().get(1).getLineItemCharge());
        assertEquals(120.42, order.getOrderLineItems().get(1).getLineItemTax());
        assertEquals(1108.8, order.getOrderLineItems().get(1).getLineItemPrice());
        assertEquals(1020.35, order.getOrderLineItems().get(1).getLineItemDeposit());
        assertEquals(-599.96, order.getOrderLineItems().get(1).getLineItemDiscount());
        assertEquals(88.45, order.getOrderLineItems().get(1).getLineItemBalance());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemTourismFeeAndTax());
        assertEquals(39.0, order.getOrderLineItems().get(1).getLineItemResortFeePerNight());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemOccupancyFee());
        assertEquals(88.44, order.getOrderLineItems().get(1).getLineItemResortFeeAndTax());
        assertEquals(899.94, order.getOrderLineItems().get(1).getLineItemAdjustedItemSubtotal());
        assertEquals(977.94, order.getOrderLineItems().get(1).getLineItemTripSubtotal());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemCasinoSurcharge());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemCasinoSurchargeAndTax());

        // show line items
        assertEquals(105.55, order.getOrderLineItems().get(0).getLineItemCharge());
        assertEquals(10.73, order.getOrderLineItems().get(0).getLineItemTax());
        assertEquals(130.0, order.getOrderLineItems().get(0).getLineItemPrice());
        assertEquals(130.0, order.getOrderLineItems().get(0).getLineItemDeposit());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemDiscount());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemBalance());
        assertEquals(9.5, order.getOrderLineItems().get(0).getLineItemLet());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemDeliveryMethodFee());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemGratuity());
        assertEquals(13.72, order.getOrderLineItems().get(0).getLineItemServiceChargeFee());
        assertEquals(1.23, order.getOrderLineItems().get(0).getLineItemServiceChargeTax());
        assertEquals(0.0,order.getOrderLineItems().get(0).getLineItemTransactionFee());
        assertEquals(0.0,order.getOrderLineItems().get(0).getLineItemTransactionFeeTax());

        // room totals
        assertEquals(0.0, order.getRoomTotals().getTotalTourismFeeAndTax());
        assertEquals(39.0, order.getRoomTotals().getTotalResortFeePerNight());
        assertEquals(0.0, order.getRoomTotals().getTotalOccupancyFee());
        assertEquals(88.44, order.getRoomTotals().getTotalResortFeeAndTax());
        assertEquals(0.0, order.getRoomTotals().getTotalCasinoSurchargeAndTax());
        assertEquals(977.94, order.getRoomTotals().getTotalTripSubtotal());
        assertEquals(1108.8, order.getRoomTotals().getTotalPrice());

        // show totals
        assertEquals(0.0, order.getShowTotals().getTotalDeliveryFee());
        assertEquals(0.0, order.getShowTotals().getTotalGratuity());
        assertEquals(0.0, order.getShowTotals().getTotalLet());
        assertEquals(0.0, order.getShowTotals().getTotalServiceChargeFee());
        assertEquals(0.0, order.getShowTotals().getTotalServiceChargeTax());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionFee());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionTax());
        assertEquals(0.0, order.getShowTotals().getTotalPrice());

        // price details
        assertEquals(1108.8, order.getPriceDetails().getTotalPrice());
        assertEquals(299.98, order.getPriceDetails().getTotalCharge());
        assertEquals(120.42, order.getPriceDetails().getTotalTax());
        assertEquals(1020.35, order.getPriceDetails().getTotalDeposit());
        assertEquals(-599.96, order.getPriceDetails().getTotalDiscount());
        assertEquals(88.45, order.getPriceDetails().getTotalBalanceDue());
        assertEquals(899.94, order.getPriceDetails().getTotalAdjustedItemSubtotal());
    }

    @Test
    public void testPriceCalculationsPackageOrderSuccess() throws Exception {
        final Order order =  mapper.readValue(getPackageOrderSuccess(), Order.class);
        new Orders().calculateOrderPrice(order);

        // show line items
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemCharge());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemTax());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemPrice());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemDeposit());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemDiscount());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemBalance());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemLet());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemDeliveryMethodFee());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemGratuity());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemServiceChargeFee());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemServiceChargeTax());
        assertEquals(0.0,order.getOrderLineItems().get(0).getLineItemTransactionFee());
        assertEquals(0.0,order.getOrderLineItems().get(0).getLineItemTransactionFeeTax());

        // room line items
        assertEquals(299997.0, order.getOrderLineItems().get(1).getLineItemCharge());
        assertEquals(267.6, order.getOrderLineItems().get(1).getLineItemTax());
        assertEquals(2420.67, order.getOrderLineItems().get(1).getLineItemPrice());
        assertEquals(2267.61, order.getOrderLineItems().get(1).getLineItemDeposit());
        assertEquals(297996.99, order.getOrderLineItems().get(1).getLineItemDiscount());
        assertEquals(153.06, order.getOrderLineItems().get(1).getLineItemBalance());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemTourismFeeAndTax());
        assertEquals(45.0, order.getOrderLineItems().get(1).getLineItemResortFeePerNight());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemOccupancyFee());
        assertEquals(153.06, order.getOrderLineItems().get(1).getLineItemResortFeeAndTax());
        assertEquals(2000.01, order.getOrderLineItems().get(1).getLineItemAdjustedItemSubtotal());
        assertEquals(2135.01, order.getOrderLineItems().get(1).getLineItemTripSubtotal());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemCasinoSurcharge());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemCasinoSurchargeAndTax());

        // room totals
        assertEquals(0.0, order.getRoomTotals().getTotalTourismFeeAndTax());
        assertEquals(45.0, order.getRoomTotals().getTotalResortFeePerNight());
        assertEquals(0.0, order.getRoomTotals().getTotalOccupancyFee());
        assertEquals(153.06, order.getRoomTotals().getTotalResortFeeAndTax());
        assertEquals(0.0, order.getRoomTotals().getTotalCasinoSurchargeAndTax());
        assertEquals(2135.01, order.getRoomTotals().getTotalTripSubtotal());
        assertEquals(2420.67, order.getRoomTotals().getTotalPrice());

        // show totals
        assertEquals(0.0, order.getShowTotals().getTotalDeliveryFee());
        assertEquals(0.0, order.getShowTotals().getTotalGratuity());
        assertEquals(0.0, order.getShowTotals().getTotalLet());
        assertEquals(0.0, order.getShowTotals().getTotalServiceChargeFee());
        assertEquals(0.0, order.getShowTotals().getTotalServiceChargeTax());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionFee());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionTax());
        assertEquals(0.0, order.getShowTotals().getTotalPrice());

        // price details
        assertEquals(2420.67, order.getPriceDetails().getTotalPrice());
        assertEquals(299997.0, order.getPriceDetails().getTotalCharge());
        assertEquals(267.6, order.getPriceDetails().getTotalTax());
        assertEquals(2267.61, order.getPriceDetails().getTotalDeposit());
        assertEquals(297996.99, order.getPriceDetails().getTotalDiscount());
        assertEquals(153.06, order.getPriceDetails().getTotalBalanceDue());
        assertEquals(2000.01, order.getPriceDetails().getTotalAdjustedItemSubtotal());
    }

    @Test
    public void testPriceCalculationsPackageOrderFailure() throws Exception {
        final Order order =  mapper.readValue(getPackageOrderRoomFailure(), Order.class);
        new Orders().calculateOrderPrice(order);

        // show line items
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemCharge());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemTax());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemPrice());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemDeposit());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemDiscount());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemBalance());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemLet());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemDeliveryMethodFee());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemGratuity());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemServiceChargeFee());
        assertEquals(null, order.getOrderLineItems().get(0).getLineItemServiceChargeTax());
        assertEquals(null,order.getOrderLineItems().get(0).getLineItemTransactionFee());
        assertEquals(null,order.getOrderLineItems().get(0).getLineItemTransactionFeeTax());

        // room line items
        assertEquals(299997.0, order.getOrderLineItems().get(1).getLineItemCharge());
        assertEquals(267.6, order.getOrderLineItems().get(1).getLineItemTax());
        assertEquals(2420.67, order.getOrderLineItems().get(1).getLineItemPrice());
        assertEquals(2267.61, order.getOrderLineItems().get(1).getLineItemDeposit());
        assertEquals(297996.99, order.getOrderLineItems().get(1).getLineItemDiscount());
        assertEquals(153.06, order.getOrderLineItems().get(1).getLineItemBalance());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemTourismFeeAndTax());
        assertEquals(45.0, order.getOrderLineItems().get(1).getLineItemResortFeePerNight());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemOccupancyFee());
        assertEquals(153.06, order.getOrderLineItems().get(1).getLineItemResortFeeAndTax());
        assertEquals(2000.01, order.getOrderLineItems().get(1).getLineItemAdjustedItemSubtotal());
        assertEquals(null, order.getOrderLineItems().get(1).getLineItemTripSubtotal());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemCasinoSurcharge());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemCasinoSurchargeAndTax());

        // room totals
        assertEquals(null, order.getRoomTotals());

        // show totals
        assertEquals(null, order.getShowTotals());

        // price details
        assertEquals(null, order.getPriceDetails());
    }

    @Test
    public void testPriceCalculationsPackageOrderPartialFailure() throws Exception {
        final Order order =  mapper.readValue(getPackageOrderShowFailure(), Order.class);
        new Orders().calculateOrderPrice(order);

        // show line items
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemCharge());
        assertEquals(98.26, order.getOrderLineItems().get(0).getLineItemTax());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemPrice());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemDeposit());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemDiscount());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemBalance());
        assertEquals(98.26, order.getOrderLineItems().get(0).getLineItemLet());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemDeliveryMethodFee());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemGratuity());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemServiceChargeFee());
        assertEquals(0.0, order.getOrderLineItems().get(0).getLineItemServiceChargeTax());
        assertEquals(0.0,order.getOrderLineItems().get(0).getLineItemTransactionFee());
        assertEquals(0.0,order.getOrderLineItems().get(0).getLineItemTransactionFeeTax());

        // room line items
        assertEquals(299997.0, order.getOrderLineItems().get(1).getLineItemCharge());
        assertEquals(267.6, order.getOrderLineItems().get(1).getLineItemTax());
        assertEquals(2420.67, order.getOrderLineItems().get(1).getLineItemPrice());
        assertEquals(2267.61, order.getOrderLineItems().get(1).getLineItemDeposit());
        assertEquals(297996.99, order.getOrderLineItems().get(1).getLineItemDiscount());
        assertEquals(153.06, order.getOrderLineItems().get(1).getLineItemBalance());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemTourismFeeAndTax());
        assertEquals(45.0, order.getOrderLineItems().get(1).getLineItemResortFeePerNight());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemOccupancyFee());
        assertEquals(153.06, order.getOrderLineItems().get(1).getLineItemResortFeeAndTax());
        assertEquals(2000.01, order.getOrderLineItems().get(1).getLineItemAdjustedItemSubtotal());
        assertEquals(2135.01, order.getOrderLineItems().get(1).getLineItemTripSubtotal());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemCasinoSurcharge());
        assertEquals(0.0, order.getOrderLineItems().get(1).getLineItemCasinoSurchargeAndTax());

        // room totals
        assertEquals(0.0, order.getRoomTotals().getTotalTourismFeeAndTax());
        assertEquals(45.0, order.getRoomTotals().getTotalResortFeePerNight());
        assertEquals(0.0, order.getRoomTotals().getTotalOccupancyFee());
        assertEquals(153.06, order.getRoomTotals().getTotalResortFeeAndTax());
        assertEquals(0.0, order.getRoomTotals().getTotalCasinoSurchargeAndTax());
        assertEquals(2135.01, order.getRoomTotals().getTotalTripSubtotal());
        assertEquals(2420.67, order.getRoomTotals().getTotalPrice());

        // show totals
        assertEquals(0.0, order.getShowTotals().getTotalDeliveryFee());
        assertEquals(0.0, order.getShowTotals().getTotalGratuity());
        assertEquals(0.0, order.getShowTotals().getTotalLet());
        assertEquals(0.0, order.getShowTotals().getTotalServiceChargeFee());
        assertEquals(0.0, order.getShowTotals().getTotalServiceChargeTax());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionFee());
        assertEquals(0.0, order.getShowTotals().getTotalTransactionTax());
        assertEquals(0.0, order.getShowTotals().getTotalPrice());

        // price details
        assertEquals(2420.67, order.getPriceDetails().getTotalPrice());
        assertEquals(299997.0, order.getPriceDetails().getTotalCharge());
        assertEquals(267.6, order.getPriceDetails().getTotalTax());
        assertEquals(2267.61, order.getPriceDetails().getTotalDeposit());
        assertEquals(297996.99, order.getPriceDetails().getTotalDiscount());
        assertEquals(153.06, order.getPriceDetails().getTotalBalanceDue());
        assertEquals(2000.01, order.getPriceDetails().getTotalAdjustedItemSubtotal());
    }
    
    @Test
    public void testPackageTotalForPackageV2Order() throws Exception {
        final Order order =  mapper.readValue(getPackagev2OrderSuccess(), Order.class);
        new Orders().calculateOrderPrice(order);

        // package pricing details
        assertEquals(3200.00, order.getPackageConfigDetails().getPackagePricingDetails().getPackageStartingPrice());
        assertEquals(3500.0, order.getPackageConfigDetails().getPackagePricingDetails().getPackageBaseTotal());
        assertEquals(3545.35, order.getPackageConfigDetails().getPackagePricingDetails().getPackageTotal());
        assertEquals(100.00, order.getPackageConfigDetails().getPackagePricingDetails().getRoomModification());
        assertEquals(200.00, order.getPackageConfigDetails().getPackagePricingDetails().getShowModification());
        assertEquals(1500.00, order.getPackageConfigDetails().getPackagePricingDetails().getRoomTotal());
        assertEquals(2000.00, order.getPackageConfigDetails().getPackagePricingDetails().getShowTotal());
    }

    private String getPendingOrder() throws Exception {
        return Utils.readFileFromClassPath("data/pendingOrder.json");
    }

    private String getPackageOrderSuccess() throws Exception {
        return Utils.readFileFromClassPath("data/packageOrderSuccess.json");
    }

    private String getPackageOrderRoomFailure() throws Exception {
        return Utils.readFileFromClassPath("data/packageOrderRoomFailure.json");
    }

    private String getPackageOrderShowFailure() throws Exception {
        return Utils.readFileFromClassPath("data/packageOrderShowFailure.json");
    }

    private String getGlobalOrderRoomShowSuccess() throws Exception {
        return Utils.readFileFromClassPath("data/globalOrderRoomShowSuccess.json");
    }

    private String getGlobalOrderRoomShowFailure() throws Exception {
        return Utils.readFileFromClassPath("data/globalOrderRoomShowFailure.json");
    }
    private String getGlobalOrderRoomSuccessShowFailure() throws Exception {
        return Utils.readFileFromClassPath("data/globalOrderRoomSuccessShowFailure.json");
    }
    private String getPackagev2OrderSuccess() throws Exception {
        return Utils.readFileFromClassPath("data/packagev2OrderSuccess.json");
    }
}

