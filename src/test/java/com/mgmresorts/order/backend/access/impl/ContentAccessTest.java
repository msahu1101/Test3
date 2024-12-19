package com.mgmresorts.order.backend.access.impl;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.HttpService;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.content.model.PackageConfig;
import com.mgmresorts.content.model.ShowEvent;
import com.mgmresorts.order.errors.Errors;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import net.minidev.json.parser.ParseException;

public class ContentAccessTest {
    @Tested
    private ContentAccess contentAccess;
    @Injectable
    private IHttpService service;
    
    private JSonMapper jsonMapper = new JSonMapper();
    
    @BeforeAll
    public static void init() {
        System.setProperty("security.disabled.global", "true");
        ErrorManager.load(Errors.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getPackageConfigDetailsSuccessTest() throws HttpFailureException, AppException, ParseException, java.text.ParseException, IOException {
        List<PackageConfig> packageConfigs = new ArrayList<>();
        PackageConfig packageConfig = new PackageConfig();
        packageConfig.setActive(true);
        packageConfig.setPackageCategoryId("Bruno21-1");
        packageConfig.setPackageId("Bruno21-1-2021-10-14");
        packageConfig.setShowEventDate("2021-10-14");
        packageConfig.setShowProgramId("EDDC753A-F5D7-4E2B-915A-4157F19BF5A4");
        packageConfig.setSegmentId("T21O141P");
        packageConfig.setMinLos(2d);
        packageConfig.setPackageTier(1d);
        packageConfigs.add(packageConfig);
        new Expectations() {
            {
                service.get(anyString, (Class<PackageConfig>) any, anyString, anyString, (List<HttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = packageConfigs.toArray(new PackageConfig[0]);
            }
        };

        Optional<PackageConfig[]> response = contentAccess.getPackageConfigDetails("Bruno21-1-2021-10-14");
        assertNotNull(response);
        assertNotNull(response.get());
        assertNotNull(response.get()[0].getPackageCategoryId());
        assertNotNull(response.get()[0].getPackageId());
        assertNotNull(response.get()[0].getShowProgramId());
        assertNotNull(response.get()[0].getSegmentId());
        assertEquals("Bruno21-1", response.get()[0].getPackageCategoryId());
        assertEquals("Bruno21-1-2021-10-14", response.get()[0].getPackageId());
        assertEquals("2021-10-14", response.get()[0].getShowEventDate());
        assertEquals("EDDC753A-F5D7-4E2B-915A-4157F19BF5A4", response.get()[0].getShowProgramId());
        assertEquals("T21O141P", response.get()[0].getSegmentId());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getPackageConfigDetailsFailureTest() throws HttpFailureException, AppException {
        new Expectations() {
            {
                service.get(anyString, (Class<PackageConfig>) any, anyString, anyString, (List<HttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = null;
            }
        };
        Optional<PackageConfig[]> response = contentAccess.getPackageConfigDetails("packageId");
        assertFalse(response.isPresent());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getPackageConfigDetails404ErrorResponse() throws AppException, HttpFailureException {
        new Expectations() {
            {
                service.get(anyString, (Class<PackageConfig>) any, anyString, anyString, (List<HttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(404, jsonMapper.writeValueAsString(content404ErrorResponse()), "header");
            }
        };
        assertThrows(AppException.class, () -> contentAccess.getPackageConfigDetails("packageId"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getPackageConfigDetails500ErrorResponse() throws AppException, HttpFailureException {
        new Expectations() {
            {
                service.get(anyString, (Class<PackageConfig>) any, anyString, anyString, (List<HttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(content500ErrorResponse()), "header");
            }
        };
        assertThrows(AppException.class, () -> contentAccess.getPackageConfigDetails("packageId"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getShowEventDetailsSuccessTest() throws HttpFailureException, AppException, ParseException, java.text.ParseException, IOException {
        ShowEvent showEvent = new ShowEvent();
        showEvent.setActive(true);
        showEvent.setEventTime("8:00 PM");
        showEvent.setPropertyId("66964e2b-2550-4476-84c3-1a4c0c5c067f");
        showEvent.setEventDate("03/15/2022");
        showEvent.setShowId("brad-garretts-comedy-club");
        showEvent.setEventId("ffa65de7-2bb9-46ed-bc99-6edbd298f682");
        showEvent.setSeasonId("6e12bad4-330f-433e-92e4-eb1fa1976674");
        showEvent.setEventCode("BGC0315E");
        showEvent.setId("BGC0315E");
        showEvent.setName("BGC0315E");
        new Expectations() {
            {
                service.get(anyString, (Class<ShowEvent>) any, anyString, anyString, (List<HttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = showEvent;
            }
        };

        ShowEvent response = contentAccess.getShowEventDetailsByEventId("ffa65de7-2bb9-46ed-bc99-6edbd298f682");
        assertNotNull(response);
        assertEquals("ffa65de7-2bb9-46ed-bc99-6edbd298f682", response.getEventId());
        assertEquals("66964e2b-2550-4476-84c3-1a4c0c5c067f", response.getPropertyId());
        assertEquals("8:00 PM", response.getEventTime());
        assertEquals("03/15/2022", response.getEventDate());
        assertEquals("BGC0315E", response.getId());
        assertEquals("brad-garretts-comedy-club", response.getShowId());
        assertEquals("BGC0315E", response.getName());
        assertEquals("6e12bad4-330f-433e-92e4-eb1fa1976674", response.getSeasonId());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getShowEventDetailsFailureTest() throws HttpFailureException, AppException {
        new Expectations() {
            {
                service.get(anyString, (Class<ShowEvent>) any, anyString, anyString, (List<HttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = null;
            }
        };
        assertThrows(AppException.class, () -> contentAccess.getShowEventDetailsByEventId("ffa65de7-2bb9-46ed-bc99-6edbd298f682"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getShowEventDetails404ErrorResponse() throws AppException, HttpFailureException {
        new Expectations() {
            {
                service.get(anyString, (Class<ShowEvent>) any, anyString, anyString, (List<HttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(404, jsonMapper.writeValueAsString(content404ErrorResponse()), "header");
            }
        };
        assertThrows(AppException.class, () -> contentAccess.getPackageConfigDetails("packageId"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getShowEventDetails500ErrorResponse() throws AppException, HttpFailureException {
        new Expectations() {
            {
                service.get(anyString, (Class<ShowEvent>) any, anyString, anyString, (List<HttpService.HttpHeaders.HttpHeader>) any, (String[][]) any);
                result = new HttpFailureException(500, jsonMapper.writeValueAsString(content500ErrorResponse()), "header");
            }
        };
        assertThrows(AppException.class, () -> contentAccess.getPackageConfigDetails("packageId"));
    }
    
    private com.mgmresorts.content.model.Error content404ErrorResponse() {
        com.mgmresorts.content.model.Error responseError = new com.mgmresorts.content.model.Error();
        List<com.mgmresorts.content.model.Message> errorMsgList = new ArrayList<com.mgmresorts.content.model.Message>();
        com.mgmresorts.content.model.Message errorMsg = new com.mgmresorts.content.model.Message();
        errorMsg.setCode("_no_results_found");
        errorMsg.setType("error");
        errorMsg.setMsg("no results available");
        errorMsgList.add(errorMsg);
        responseError.setMessages(errorMsgList);
        return responseError;
    }
    
    private com.mgmresorts.content.model.Error content500ErrorResponse() {
        com.mgmresorts.content.model.Error responseError = new com.mgmresorts.content.model.Error();
        List<com.mgmresorts.content.model.Message> errorMsgList = new ArrayList<com.mgmresorts.content.model.Message>();
        com.mgmresorts.content.model.Message errorMsg = new com.mgmresorts.content.model.Message();
        errorMsg.setCode("_system_error");
        errorMsg.setType("error");
        errorMsg.setMsg("system unavailable");
        errorMsgList.add(errorMsg);
        responseError.setMessages(errorMsgList);
        return responseError;
    }
}
