package com.mgmresorts.order.backend.access.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.content.model.PackageConfig;
import com.mgmresorts.content.model.ShowEvent;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IContentAccess;

public class ContentAccess implements IContentAccess {

    @Inject
    @Named("simulation.enabled")
    private IHttpService service;

    public Optional<PackageConfig[]> getPackageConfigDetails(String packageId) throws AppException {
        try {
            final String callName = "content-package-config";
            final PackageConfig[] packageConfigResponse = service.get(
                    CONTENT_API_ENDPOINT + CONTENT_API_GET_PACKAGE_CONFIG,
                    PackageConfig[].class, callName, callName, Arrays.asList(CommonConfig.getContentHeaders()), createPackageConfigQueryParams(packageId));

            return Optional.ofNullable(packageConfigResponse);
        } catch (HttpFailureException e) {
            throw new AppException(SystemError.UNABLE_TO_CALL_BACKEND, e);
        } catch (AppException e) {
            throw new AppException(SystemError.REQUESTED_RESOURCE_NOT_FOUND, e);
        }
    }
    
    @Override
    public ShowEvent getShowEventDetailsByEventId(String showEventId) throws AppException {
        try {
            final String callName = "content-show-event-details";
            final ShowEvent response = service.get(
                    CONTENT_API_ENDPOINT + CONTENT_API_GET_SHOW_EVENT + PATH_SEPARATOR + showEventId, ShowEvent.class,
                    callName, callName, Arrays.asList(CommonConfig.getContentHeaders()));
            if (response == null) {
                throw new AppException(SystemError.INVALID_RESPONSE_BODY, "Response is null");
            }
            return response;
        } catch (HttpFailureException e) {
            throw new AppException(SystemError.UNABLE_TO_CALL_BACKEND, e);
        } catch (AppException e) {
            throw new AppException(SystemError.REQUESTED_RESOURCE_NOT_FOUND, e);
        }
    }
    
    private String[][] createPackageConfigQueryParams(final String packageId) {
        final List<String[]> paramList = new ArrayList<>();
        paramList.add(new String[] { "packageId", packageId });
        return paramList.toArray(new String[][]{});
    }
}
