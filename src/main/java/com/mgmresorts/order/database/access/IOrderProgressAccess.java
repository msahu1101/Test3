package com.mgmresorts.order.database.access;

import java.time.ZonedDateTime;

import com.azure.cosmos.CosmosException;
import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.data.IDataAccess;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.database.access.impl.OrderProgressAccess.RowLock;
import com.mgmresorts.order.errors.Errors;

public interface IOrderProgressAccess extends IDataAccess<String, RowLock> {
    final int ttl = Runtime.get().getInt("order.lock.ttl.seconds", 240); // seconds
    
    default boolean tryLock(String id) throws AppException {
        final RowLock entity = new RowLock();
        entity.setTtl(ttl + 1);
        entity.setLockedAt(ZonedDateTime.now());
        entity.setId(id);
        try {
            create(entity);
        } catch (AppException | CosmosException e) {
            throw new AppException(Errors.CHECKOUT_IN_PROGRESS, e);
        }
        return true;
    }

    default void release(String id) throws AppException {
        delete(id);
    }
}
