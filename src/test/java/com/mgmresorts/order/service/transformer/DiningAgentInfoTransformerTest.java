package com.mgmresorts.order.service.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.exception.AppException;

import uk.co.jemos.podam.api.PodamFactoryImpl;

class DiningAgentInfoTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final DiningAgentInfoTransformer transformer = new DiningAgentInfoTransformer();

    @Test
    final void testToRight() throws AppException {
        final com.mgmresorts.shopping.cart.dto.AgentInfo agent = podamFactoryImpl.manufacturePojo(com.mgmresorts.shopping.cart.dto.AgentInfo.class);
        final com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo agentInfo = transformer.toRight(agent);
        assertEquals(agentInfo.getAgentId(), agent.getAgentId());
        assertEquals(agentInfo.getAgentType(), agent.getAgentType());
    }
}

