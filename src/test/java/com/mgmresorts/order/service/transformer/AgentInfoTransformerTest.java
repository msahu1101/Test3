package com.mgmresorts.order.service.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.exception.AppException;

import uk.co.jemos.podam.api.PodamFactoryImpl;

class AgentInfoTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final AgentInfoTransformer transformer = new AgentInfoTransformer();

    @Test
    final void testToRight() throws AppException {
        final com.mgmresorts.shopping.cart.dto.AgentInfo agent = podamFactoryImpl.manufacturePojo(com.mgmresorts.shopping.cart.dto.AgentInfo.class);
        final com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo agentInfo = transformer.toRight(agent);
        assertEquals(agentInfo.getAgentId(), agent.getAgentId());
        assertEquals(agentInfo.getAgentType(), agent.getAgentType());
    }
}
