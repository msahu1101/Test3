package com.mgmresorts.order.backend.access.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.HttpService;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.pim.model.Duration;
import com.mgmresorts.pim.model.PackagesEvent;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;

public class PIMAccessTest {
    @Tested
    private PIMAccess pimAccess;
    @Injectable
    private IHttpService service;
    @Injectable
    private OAuthTokenRegistry registry;

    @BeforeAll
    public static void init() {
        System.setProperty("security.disabled.global", "true");
        ErrorManager.load(Errors.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getPackageConfigDetailsSuccessTest() throws HttpFailureException, AppException {
        PackagesEvent packagesEvent = new PackagesEvent();
        packagesEvent.setBookBy("book by");
        Duration duration = new Duration();
        duration.setEndDate("end date");
        duration.setStartDate("start date");
        packagesEvent.setDuration(duration);
        new Expectations() {
            {
                service.get(anyString, (Class<PackagesEvent>) any, anyString, anyString, (List<HttpService.HttpHeaders.HttpHeader>) any);
                result = packagesEvent;
            }
        };

        Optional<PackagesEvent> response = pimAccess.getPackageConfigDetails("package_test");
        assertNotNull(response);
        assertNotNull(response.get());
        assertNotNull(response.get().getBookBy());
        assertNotNull(response.get().getDuration());
        assertNotNull(response.get().getDuration().getStartDate());
        assertNotNull(response.get().getDuration().getEndDate());
        assertEquals("book by", response.get().getBookBy());
        assertEquals("start date", response.get().getDuration().getStartDate());
        assertEquals("end date", response.get().getDuration().getEndDate());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getPackageConfigDetailsFailureTest() throws HttpFailureException, AppException {
        new Expectations() {
            {
                service.get(anyString, (Class<PackagesEvent>) any, anyString, anyString, (List<HttpService.HttpHeaders.HttpHeader>) any);
                result = null;
            }
        };
        Optional<PackagesEvent> response = pimAccess.getPackageConfigDetails("package_test");
        assertFalse(response.isPresent());
    }
}
