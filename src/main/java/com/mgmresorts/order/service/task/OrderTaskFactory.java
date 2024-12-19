package com.mgmresorts.order.service.task;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.AppRuntimeException;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.ThreadContext.TransactionContext;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.ItemType;

@Named
public class OrderTaskFactory {
    @Inject
    private Map<ItemType, IProductHandler> resolvers;

    public IProductHandler get(ItemType type) throws AppException {
        if (!resolvers.containsKey(type)) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Product type");
        }
        return resolvers.get(type);
    }

    public OrderTask create(CheckoutRequest request, String orderId, Cart cart, CartLineItem cartLineItem, OrderLineItem orderLineItem, AppliedBillings billable,
                            boolean skipAFS, boolean skipPaymentCapture, Map<String, PaymentAuthFields> paymentAuthFieldsMap,
                            String orderReferenceNumber) throws AppRuntimeException {
        try {
            final TransactionContext transactionContext = ThreadContext.getContext().get().copy();
            return new OrderTask(request, cartLineItem, orderLineItem, billable, orderId, cart.getAgentInfo(), skipAFS, skipPaymentCapture,
                    transactionContext, this.get(cartLineItem.getType()), paymentAuthFieldsMap, orderReferenceNumber);
        } catch (AppException e) {
            throw new AppRuntimeException(e.getCode(), e);
        }
    }
}
