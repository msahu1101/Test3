package com.mgmresorts.order.database.access.impl;

import java.time.ZonedDateTime;
import java.util.Collection;

import javax.inject.Named;

import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.SqlQuerySpec;
import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.cosmos.CosmosSupport;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.database.access.IOrderProgressAccess;
import com.mgmresorts.order.database.access.impl.OrderProgressAccess.RowLock;

@Named("order-progress-access")
public class OrderProgressAccess extends CosmosSupport<RowLock> implements IOrderProgressAccess {

    protected String getName() {
        return "Order Database";
    }

    protected String getDatabase() {
        return Runtime.get().getConfiguration("order.lock.database.name", "order-management");
    }

    protected String getCollection() {
        return Runtime.get().getConfiguration("order.lock.database.collection", "order-lock");
    }

    @Override
    public RowLock create(RowLock entity) throws AppException {
        super.createItem(entity);
        return entity;
    }

    @Override
    public RowLock read(String id) throws AppException {
        final CosmosItemResponse<RowLock> readItem = super.readItem(id, RowLock.class);
        if (readItem != null) {
            return readItem.getItem();
        }
        return null;
    }

    @Override
    public RowLock update(RowLock entity) throws AppException {
        throw new AppException(SystemError.UNSUPPORTED_OPERATION);
    }

    @Override
    public void delete(String id) throws AppException {
        super.deleteItem(id);
    }

    @Override
    public Collection<RowLock> search(SqlQuerySpec querySpec) throws AppException {
        throw new AppException(SystemError.UNSUPPORTED_OPERATION);
    }

    public static class RowLock {
        private String id;
        private int ttl;
        private ZonedDateTime lockedAt = ZonedDateTime.now();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getTtl() {
            return ttl;
        }

        public void setTtl(int ttl) {
            this.ttl = ttl;
        }

        public ZonedDateTime getLockedAt() {
            return lockedAt;
        }

        public void setLockedAt(ZonedDateTime lockedAt) {
            this.lockedAt = lockedAt;
        }
    }
}
