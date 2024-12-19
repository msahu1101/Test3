package com.mgmresorts.order.service.transformer;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo;
import com.mgmresorts.shopping.cart.dto.AgentInfo;

public class DiningAgentInfoTransformer implements ITransformer<AgentInfo, CreateReservationRequestRestaurantReservationAgentInfo> {

    @Override
    public com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo toRight(com.mgmresorts.shopping.cart.dto.AgentInfo agent) throws AppException {

        final com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo agentInfo = new CreateReservationRequestRestaurantReservationAgentInfo();

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
