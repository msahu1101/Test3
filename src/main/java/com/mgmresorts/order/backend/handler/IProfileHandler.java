package com.mgmresorts.order.backend.handler;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.profile.dto.customer.Customer;

public interface IProfileHandler extends CommonConfig {

    String createGuestProfile(CheckoutRequest request) throws AppException;

    Customer getGuestProfile(final String mlifeNumber) throws AppException;
}
