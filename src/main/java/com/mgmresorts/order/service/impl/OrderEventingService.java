package com.mgmresorts.order.service.impl;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.event.dto.OrderServiceEvent;
import com.mgmresorts.order.event.EventFactory;
import com.mgmresorts.order.service.IOrderEventingService;

@Named
public class OrderEventingService implements IOrderEventingService {
    private final Logger logger = Logger.get(OrderEventingService.class);
    private final JSonMapper mapper = new JSonMapper();

    @Inject
    private EventFactory factory;

    @Override
    public void orderEventListener(OrderServiceEvent input) throws AppException {
        if (input.getData() != null) {
            final String type = input.getEventType();
            final String data = input.getData().getPayload() != null ? mapper.writeValueAsString(input.getData().getPayload())
                    : input.getData().getBody() != null ? mapper.writeValueAsString(input.getData().getBody()) : "***";
            logger.info("Event Received. Id {} | Type {} | Payload {}", input.getId(), type, data);
            factory.handler(type).process(input);
        } else {
            logger.info("No event payload found. Verification code {}", input.getData().getValidationCode());
        }
        System.out.println("------------------------------------------------------------------");
        System.out.println(new JSonMapper().writeValueAsString(input));
    }
}
