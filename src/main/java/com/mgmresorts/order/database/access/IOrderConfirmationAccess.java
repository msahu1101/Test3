package com.mgmresorts.order.database.access;

import java.util.Collection;

import org.apache.commons.collections4.CollectionUtils;

import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.mgmresorts.common.data.IDataAccess;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.database.access.impl.OrderConfirmationAccess.Queries;
import com.mgmresorts.order.entity.OrderConfirmationMapping;

public interface IOrderConfirmationAccess extends IDataAccess<String, OrderConfirmationMapping> {
    
    default OrderConfirmationMapping getOrderByConfirmationNumber(String confirmationNumber) throws AppException {
        final SqlQuerySpec querySpec = new SqlQuerySpec(Queries.GET_ORDER_BY_CONFIRMATION_NUMBER,
                new SqlParameter[] { new SqlParameter(Queries.CONFIRMATION_NUMBER, confirmationNumber) });
        final Collection<OrderConfirmationMapping> orders = search(querySpec);
        if (CollectionUtils.isNotEmpty(orders)) {
            return orders.stream().findFirst().get();
        }
        return null;
    }
}
