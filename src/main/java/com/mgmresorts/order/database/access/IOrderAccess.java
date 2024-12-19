package com.mgmresorts.order.database.access;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;

import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.mgmresorts.common.data.IDataAccess;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.database.access.impl.OrderAccess.Queries;
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.entity.Type;
import com.mgmresorts.order.entity.Version;
import com.mgmresorts.order.entity.OrderStatus;

public interface IOrderAccess extends IDataAccess<String, Order> {
    
    Order update(String orderId, Class<Order> clazz, Consumer<Order> merger) throws AppException;
    
    default Order mergeAndUpdate(String orderId, Class<Order> clazz, Consumer<Order> merger) throws AppException {
        return this.update(orderId, clazz, merger);
    }
    
    default Order getCheckedOutOrderByCartId(String cartId, final Type type, final Version version) throws AppException {
        final SqlQuerySpec querySpec = new SqlQuerySpec(Queries.GET_CHECKEDOUT_ORDER_BY_CART_ID, new SqlParameter[] {new SqlParameter(Queries.CART_ID, cartId)});
        final Collection<Order> orders = search(querySpec);
        if (CollectionUtils.isNotEmpty(orders)) {
            //since the cart was not found in cart data store, assumption is it was successfully checked out. so filter only for the successful or partial orders.
            final List<Order> successfulPartialOrders = orders.stream()
                    .filter(o -> o.getType().equals(type))
                    .filter(o -> o.getVersion().equals(version))
                    .filter(o -> o.getStatus().equals(OrderStatus.SUCCESS) ||  o.getStatus().equals(OrderStatus.PARTIAL))
                    .collect(Collectors.toList());
            
            if (CollectionUtils.isNotEmpty(successfulPartialOrders)) {
                return successfulPartialOrders.stream().findFirst().get();
            }
        }
        return null;
    }
    
    default Order getPendingOrderByCartId(String cartId, final Type type, final Version version) throws AppException {
        final SqlQuerySpec querySpec = new SqlQuerySpec(Queries.GET_PENDING_ORDER_BY_CART_ID, new SqlParameter[] {new SqlParameter(Queries.CART_ID, cartId)});
        final Collection<Order> orders = search(querySpec);
        if (CollectionUtils.isNotEmpty(orders)) {
            //since the cart checkout is in progress, it could have any of the pending or non-pending order status.
            final List<Order> filteredOrders = orders.stream()
                    .filter(o -> o.getType().equals(type))
                    .filter(o -> o.getVersion().equals(version))
                    .collect(Collectors.toList());
            
            //there could be a mix of pending, successful, failure or partial orders in db, so first try to filter for the pending ones to see if any exists.
            final List<Order> pendingOrders = filteredOrders.stream()
                    .filter(o -> o.getStatus().equals(OrderStatus.PENDING))
                    .collect(Collectors.toList());
            
            if (CollectionUtils.isNotEmpty(pendingOrders)) {
                return pendingOrders.stream().sorted(Comparator.comparing(Order::getOrderInitiatedAt).reversed()).findFirst().get();
            }
            
            //if there is no pending order. then there could be a mix of successful,
            //failure or partial orders in db, so first try to filter for the successful/partial ones to see if any exists.
            final List<Order> successfulPartialOrders = filteredOrders.stream()
                    .filter(o -> o.getStatus().equals(OrderStatus.SUCCESS) ||  o.getStatus().equals(OrderStatus.PARTIAL))
                    .collect(Collectors.toList());
            
            if (CollectionUtils.isNotEmpty(successfulPartialOrders)) {
                return successfulPartialOrders.stream().findFirst().get();
            }
            
            //if there are only failure/payment failure orders, take the latest one.
            final List<Order> failurePaymentFailureOrders = filteredOrders.stream()
                    .filter(o -> o.getStatus().equals(OrderStatus.FAILURE) ||  o.getStatus().equals(OrderStatus.PAYMENT_FAILURE))
                    .collect(Collectors.toList());
            
            if (CollectionUtils.isNotEmpty(failurePaymentFailureOrders)) {
                return failurePaymentFailureOrders.stream().sorted(Comparator.comparing(Order::getOrderInitiatedAt).reversed()).findFirst().get();
            }
        }
        return null;
    }
}
