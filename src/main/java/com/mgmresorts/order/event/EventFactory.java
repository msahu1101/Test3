package com.mgmresorts.order.event;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.utils.Utils;

@Named
@Singleton
public class EventFactory {
    private final Logger logger = Logger.get(EventFactory.class);
    @Inject
    private Map<InEventType, IEventHandler> handler;

    public IEventHandler handler(String type) throws AppException {

        if (Utils.isEmpty(type)) {
            logger.error("Event type not found... ");
            throw new AppException(Errors.INVALID_REQUEST_INFORMATION, "Type");
        }
        final InEventType byValue = InEventType.byValue(type);
        if (byValue == null) {
            logger.error("Invalid event type. Unable to process {}", type);
            throw new AppException(Errors.INVALID_REQUEST_INFORMATION, "Type");
        }
        final IEventHandler iEventHandler = handler.get(byValue);
        if (iEventHandler == null) {
            logger.error("Invalid event type. Unable to process {}", type);
            throw new AppException(Errors.INVALID_REQUEST_INFORMATION, "Type");
        }
        return iEventHandler;

    }
}
