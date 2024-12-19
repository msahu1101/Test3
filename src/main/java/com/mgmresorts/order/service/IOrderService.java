package com.mgmresorts.order.service;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.CheckoutResponse;

public interface IOrderService {
    CheckoutResponse checkout(CheckoutRequest request) throws AppException;

    CheckoutResponse read(String orderId, com.mgmresorts.order.dto.services.Type cartType,
                          com.mgmresorts.order.dto.services.Version cartVersion, String encryptedEmailAddress) throws AppException;
}
