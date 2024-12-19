package com.mgmresorts.order.service;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.event.dto.OrderServiceEvent;

public interface IOrderEventingService {
    void orderEventListener(OrderServiceEvent input) throws AppException;
}
