package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.profile.dto.customer.Customer;
import com.mgmresorts.profile.dto.services.CreateRequest;

public interface IProfileAccess extends CommonConfig {
    String createProfile(CreateRequest request) throws AppException, HttpFailureException;

    Customer getProfile(final String mlifeNumber) throws AppException;
}
