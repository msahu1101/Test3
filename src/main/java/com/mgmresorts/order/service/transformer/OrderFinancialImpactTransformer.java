package com.mgmresorts.order.service.transformer;

import java.util.ArrayList;
import java.util.List;

import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.dto.PriceDetails;
import com.mgmresorts.order.dto.services.Order;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.logging.OrderFinancialImpact;
import com.mgmresorts.order.logging.OrderLineItem.ProductType;

public class OrderFinancialImpactTransformer implements ITransformer<Order, OrderFinancialImpact> {

    @Override
    public OrderFinancialImpact toRight(Order left) {
        final OrderFinancialImpact right = new OrderFinancialImpact();
        right.setOrderId(left.getId());
        right.setCartId(left.getCartId());
        right.setStatus(left.getStatus() != null ? OrderFinancialImpact.Status.fromValue(left.getStatus().name()) : null);

        final List<OrderLineItem> orderLineItems = left.getOrderLineItems();
        if (!Utils.isEmpty(orderLineItems)) {
            final List<com.mgmresorts.order.logging.OrderLineItem> lineItems = new ArrayList<>();
            for (OrderLineItem oli : orderLineItems) {
                final com.mgmresorts.order.logging.OrderLineItem li = new com.mgmresorts.order.logging.OrderLineItem();
                li.setOrderLineItemId(oli.getOrderLineItemId());
                li.setProductType(ProductType.fromValue(oli.getProductType().value()));
                li.setStatus(oli.getStatus() != null ? com.mgmresorts.order.logging.OrderLineItem.Status.fromValue(oli.getStatus().name()) : null);
                li.setLineItemPrice(oli.getLineItemPrice());

                lineItems.add(li);
            }
            right.setOrderLineItems(lineItems);
        }

        final PriceDetails priceDetails = left.getPriceDetails();
        if (priceDetails != null) {
            final com.mgmresorts.order.logging.PriceDetails pd = new com.mgmresorts.order.logging.PriceDetails();
            pd.setTotalPrice(priceDetails.getTotalPrice());
            right.setPriceDetails(pd);
        }
        return right;
    }
}
