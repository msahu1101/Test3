package com.mgmresorts.order.service.task;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.AppRuntimeException;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.order.entity.LineItem;
import com.mgmresorts.shopping.cart.dto.ItemType;

@Named
public class ReservationHandlerFactory {
    @Inject
    private Map<ItemType, IProductHandler> resolvers;

    public IProductHandler get(ItemType type) throws AppException {
        if (!resolvers.containsKey(type)) {
            throw new AppException(SystemError.INVALID_REQUEST_INFORMATION, "Product type");
        }
        return resolvers.get(type);
    }

    public ReservationTask create(LineItem lineItem, String firstName, String lastName) throws AppRuntimeException {
        try {
            final ThreadContext.TransactionContext transactionContext = ThreadContext.getContext().get().copy();
            return new ReservationTask(lineItem.getConfirmationNumber(), firstName, lastName, transactionContext, this.get(ItemType.fromValue(lineItem.getProductType().value())));
        } catch (AppException e) {
            throw new AppRuntimeException(e.getCode(), e);
        }
    }
}
