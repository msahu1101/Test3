package com.mgmresorts.order.backend.access.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exec.Retry;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IProfileAccess;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.profile.dto.customer.Customer;
import com.mgmresorts.profile.dto.services.CreateRequest;
import com.mgmresorts.profile.dto.services.LookupResponse;

public class ProfileAccess implements IProfileAccess {
    /* Profile API specific access class */

    @Inject
    @Named("simulation.enabled")
    private IHttpService service;
    @Inject
    private OAuthTokenRegistry registry;

    @Override
    public String createProfile(CreateRequest request) throws AppException, HttpFailureException {
        final String serviceToken = CommonConfig.getServiceToken(registry);
        final String callName = "profile-create";
        return Retry.of(3, String.class, HttpFailureException.class).exeute(() -> {
            return service.post(PROFILE_CORE_API_ENDPOINT + PROFILE_CORE_CREATE, request, callName, callName, CommonConfig.getStandardHeaders(serviceToken));
        }, condition -> {
            return condition.getHttpCode() == 500 || condition.getHttpCode() == 502 || condition.getHttpCode() == 503 || condition.getHttpCode() == 504;
        });
    }

    @Override
    public Customer getProfile(final String mlifeNumber) throws AppException {
        try {
            final String serviceToken = CommonConfig.getServiceToken(registry);
            final String callName = "profile-get";
            
            final LookupResponse lookupResponse =  Retry.of(3, LookupResponse.class, HttpFailureException.class).exeute(() -> {
                return service.get(PROFILE_CORE_API_ENDPOINT + PROFILE_CORE_GET + PATH_SEPARATOR + mlifeNumber, LookupResponse.class, callName, callName,
                        Arrays.asList(CommonConfig.getStandardHeaders(serviceToken)), createGetProfileQueryParams());
            }, condition -> {
                return condition.getHttpCode() == 500 || condition.getHttpCode() == 502 || condition.getHttpCode() == 503 || condition.getHttpCode() == 504;
            });

            if (lookupResponse == null || lookupResponse.getCustomer() == null) {
                throw new AppException(Errors.UNABLE_TO_GET_CUSTOMER_PROFILE, "Could not get customer profile. No response from backend.");
            }
            return lookupResponse.getCustomer();
        } catch (HttpFailureException e) {
            logger.error("[Error from Profile Core] Get profile call failed. :  {}", e.getPayload());
            throw new AppException(SystemError.UNABLE_TO_CALL_BACKEND, e);
        } catch (AppException e) {
            logger.error("[Error from Profile Core] Get profile call failed with app exception. Message: {} ", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("[Error from Profile Core] Get profile call failed with unknown exception. Message: {} ", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e);
        }
    }

    private String[][] createGetProfileQueryParams() {
        final List<String[]> paramList = new ArrayList<>();
        paramList.add(new String[] { "type", "mlife" });
        return paramList.toArray(new String[][] {});
    }
}
