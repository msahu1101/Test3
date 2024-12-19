package com.mgmresorts.order.database.access.impl;

import java.util.Collection;

import javax.inject.Named;

import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.SqlQuerySpec;
import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.cosmos.CosmosSupport;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.database.access.IOrderConfirmationAccess;
import com.mgmresorts.order.entity.OrderConfirmationMapping;

@Named("order-confirmation-access")
public class OrderConfirmationAccess extends CosmosSupport<OrderConfirmationMapping> implements IOrderConfirmationAccess {
    final int ttl = Integer.valueOf(Runtime.get().getConfiguration("order.confirmation.mapping.ttl.days")) * 24 * 60 * 60;
    
    public interface Queries {
        // Interface for Queries
        String CONFIRMATION_NUMBER = "@confirmationNumber";
        String GET_ORDER_BY_CONFIRMATION_NUMBER = "Select * FROM c where c.confirmationNumber = " + CONFIRMATION_NUMBER;
    }

    protected String getName() {
        return "Order Database";
    }

    protected String getDatabase() {
        return Runtime.get().getConfiguration("order.confirmation.database.name", "order-management");
    }

    protected String getCollection() {
        return Runtime.get().getConfiguration("order.confirmation.database.collection", "order-conf-mapping");
    }

    @Override
    public OrderConfirmationMapping create(OrderConfirmationMapping entity) throws AppException {
        entity.setTtl(ttl);// keeping ttl same for both types of documents
        super.createItem(entity);
        return entity;
    }

    @Override
    public OrderConfirmationMapping read(String id) throws AppException {
        final CosmosItemResponse<OrderConfirmationMapping> readItem = super.readItem(id, OrderConfirmationMapping.class);
        if (readItem != null) {
            return readItem.getItem();
        }
        return null;
    }

    @Override
    public OrderConfirmationMapping update(OrderConfirmationMapping entity) throws AppException {
        entity.setTtl(ttl);// keeping ttl same for both types of documents
        super.updateItem(entity);
        return entity;
    }

    @Override
    public void delete(String id) throws AppException {
        super.deleteItem(id);
    }

    @Override
    public Collection<OrderConfirmationMapping> search(SqlQuerySpec sqlQuerySpec) throws AppException {
        return super.readItemByQuery(sqlQuerySpec, OrderConfirmationMapping.class);
    }
}
