package com.mgmresorts.order.service.transformer;

import com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.Utils;

public class AgentInfoTransformer implements ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo> {

    @Override
    public com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo toRight(com.mgmresorts.shopping.cart.dto.AgentInfo agent) throws AppException {

        final com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo agentInfo = new RoomReservationRequestAgentInfo();

        if (agent == null || agent.getAgentId() == null) {

            return null;
        }

        if (!Utils.isEmpty(agent.getAgentId())) {
            agentInfo.setAgentId(agent.getAgentId());
        }

        if (!Utils.isEmpty(agent.getAgentType())) {
            agentInfo.setAgentType(agent.getAgentType());
        }
        
        return agentInfo;
    }
}
