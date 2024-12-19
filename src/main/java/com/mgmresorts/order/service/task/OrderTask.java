package com.mgmresorts.order.service.task;

import com.mgmresorts.common.concurrent.Task;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.ThreadContext.TransactionContext;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.shopping.cart.dto.AgentInfo;
import com.mgmresorts.shopping.cart.dto.CartLineItem;

import java.util.Map;

public class OrderTask extends Task<OrderLineItem> {
    private final CheckoutRequest request;
    private final com.mgmresorts.shopping.cart.dto.CartLineItem cartLineItem;
    private final com.mgmresorts.order.dto.services.OrderLineItem orderLineItem;
    private final AppliedBillings billable;

    private final String orderId;
    private final boolean skipAFS;
    private final AgentInfo agentInfo;
    private final TransactionContext transactionContext;
    private final IProductHandler productHandler;
    private final Map<String, PaymentAuthFields> paymentAuthFieldsMap;
    private final String orderReferenceNumber;
    private final boolean skipPaymentCapture;


    protected OrderTask() {
        this.request = null;
        this.cartLineItem = null;
        this.orderLineItem = null;
        this.billable = null;
        this.orderId = null;
        this.skipAFS = false;
        this.agentInfo = null;
        this.transactionContext = null;
        this.productHandler = null;
        this.paymentAuthFieldsMap = null;
        this.orderReferenceNumber = null;
        this.skipPaymentCapture = false;
    }
    
    public OrderTask(CheckoutRequest request, CartLineItem cartLineItem, OrderLineItem orderLineItem, AppliedBillings billable, String orderId, AgentInfo agentInfo,
                     boolean skipAFS, boolean skipPaymentCapture, TransactionContext transactionContext,
                     IProductHandler productHandler, Map<String, PaymentAuthFields> paymentAuthFieldsMap, String orderReferenceNumber) {
        this.request = request;
        this.cartLineItem = cartLineItem;
        this.orderLineItem = orderLineItem;
        this.billable = billable;
        this.orderId = orderId;
        this.skipAFS = skipAFS;
        this.agentInfo = agentInfo;
        this.transactionContext = transactionContext;
        this.productHandler = productHandler;
        this.paymentAuthFieldsMap = paymentAuthFieldsMap;
        this.orderReferenceNumber = orderReferenceNumber;
        this.skipPaymentCapture = skipPaymentCapture;
    }

    @Override
    protected OrderLineItem execute() throws Exception {
        ThreadContext.getContext().set(transactionContext);
        return productHandler.checkout(request, cartLineItem, orderLineItem, billable, orderId, agentInfo, skipAFS,
                skipPaymentCapture, paymentAuthFieldsMap, orderReferenceNumber);
    }
}
