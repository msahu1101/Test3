package com.mgmresorts.order.backend.access.impl;

import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.function.HeaderBuilder;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.HttpService;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IPIMAccess;
import com.mgmresorts.pim.model.PackagesEvent;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.Optional;

public class PIMAccess implements IPIMAccess {
    private final Logger logger = Logger.get(PIMAccess.class);
    @Inject
    @Named("simulation.enabled")
    private IHttpService service;
    @Inject
    private OAuthTokenRegistry registry;

    @Override
    public Optional<PackagesEvent> getPackageConfigDetails(String packageId) throws AppException {
        try {
            final String callName = "pim-package-config";
            final String token = CommonConfig.getServiceToken(registry);
            final PackagesEvent packageConfigResponse = service.get(
                    PIM_API_ENDPOINT + PIM_API_GET_PACKAGE_CONFIG + PATH_SEPARATOR + packageId,
                    PackagesEvent.class, callName, callName, Arrays.asList(pimHeaders(token)));

            return Optional.ofNullable(packageConfigResponse);
        } catch (HttpFailureException e) {
            logger.error("[Error from PIM API] Something unexpected happened in get package config details call. --> {}", e.getPayload());
            throw new AppException(SystemError.UNABLE_TO_CALL_BACKEND, e);
        } catch (AppException e) {
            throw new AppException(SystemError.REQUESTED_RESOURCE_NOT_FOUND, e);
        }
    }

    protected HttpService.HttpHeaders.HttpHeader[] pimHeaders(String token) {
        return new HttpService.HttpHeaders.HttpHeader[] { HttpService.HttpHeaders.APPLICATION_JSON,
                new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_AUTHORIZATION, "Bearer " + token),
                new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_CORRELATION_ID,
                        ThreadContext.getContext().get().getCorrelationId()),
                new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_SOURCE, ThreadContext.getContext().get().getSource()),
                new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_CHANNEL, ThreadContext.getContext().get().getChannel()),
                new HttpService.HttpHeaders.HttpHeader(HeaderBuilder.HEADER_EXTERNAL_TRANSACTION_ID,
                        ThreadContext.getContext().get().getCorrelationId()) };
    }
}
