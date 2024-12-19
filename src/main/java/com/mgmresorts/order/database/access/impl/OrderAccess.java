package com.mgmresorts.order.database.access.impl;

import java.util.Collection;
import java.util.function.Consumer;

import javax.inject.Named;

import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.SqlQuerySpec;
import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.cosmos.CosmosSupport;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.entity.Type;
import com.mgmresorts.order.entity.Version;

@Named("order-access")
public class OrderAccess extends CosmosSupport<Order> implements IOrderAccess {
    final int ttl = Integer.valueOf(Runtime.get().getConfiguration("order.ttl.days")) * 24 * 60 * 60;

    public interface Queries {
        // Interface for Queries
        String CART_ID = "@cartId";
        String GET_CHECKEDOUT_ORDER_BY_CART_ID = "Select * FROM c where c.cartId =" + CART_ID;
        String GET_PENDING_ORDER_BY_CART_ID = "Select * FROM c where c.cartId =" + CART_ID;
    }

    protected String getName() {
        return "Order Database";
    }

    protected String getDatabase() {
        return Runtime.get().getConfiguration("order.database.name", "order-management");
    }

    protected String getCollection() {
        return Runtime.get().getConfiguration("order.database.collection", "order");
    }

    @Override
    public Order create(Order entity) throws AppException {
        entity.setTtl(ttl);// keeping ttl same for both types of documents
        super.createItem(entity);
        return entity;
    }

    @Override
    public Order read(String id) throws AppException {
        final CosmosItemResponse<Order> readItem = super.readItem(id, Order.class);
        if (readItem != null) {
            if (readItem.getItem().getType() == null) {
                readItem.getItem().setType(Type.GLOBAL);
            }
            if (readItem.getItem().getVersion() == null) {
                readItem.getItem().setVersion(Version.V1);
            }
            return readItem.getItem();
        }
        return null;
    }

    @Override
    public Order update(Order entity) throws AppException {
        entity.setTtl(ttl);// keeping ttl same for both types of documents
        super.updateItem(entity);
        return entity;
    }
    
    @Override
    public Order update(String orderId, Class<Order> clazz, Consumer<Order> merger) throws AppException {
        return super.updateItem(orderId, clazz, merger);
    }

    @Override
    public void delete(String id) throws AppException {
        super.deleteItem(id);
    }

    @Override
    public Collection<Order> search(SqlQuerySpec querySpec) throws AppException {
        return super.readItemByQuery(querySpec, Order.class);
    }
}
