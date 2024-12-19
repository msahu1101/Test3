package com.mgmresorts.order.backend.handler.impl;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.IProfileAccess;
import com.mgmresorts.profile.dto.common.Address;
import com.mgmresorts.profile.dto.customer.Customer;
import com.mgmresorts.profile.dto.customer.InCustomer;
import com.mgmresorts.profile.dto.customer.Profile;
import com.mgmresorts.profile.dto.services.CreateRequest;
import com.mgmresorts.profile.dto.services.LookupResponse;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProfileHandlerTest {
    @Tested
    private ProfileHandler profileHandler;
    
    @Injectable
    private IHttpService service;
    @Injectable
    private IProfileAccess profileAccess;

    private final JSonMapper jsonMapper = new JSonMapper();

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @Test
    void testCreateProfile_SuccessResponse() throws Exception {
        new Expectations() {
            {
                profileAccess.createProfile((CreateRequest) any);
                result = Utils.readFileFromClassPath("data/profile_core_create_profile_success_response.json");
            }
        };

        final CreateRequest createRequest = createCreateProfileRequest();
        final String lookupResponseString = profileAccess.createProfile(createRequest);
        final LookupResponse lookupResponse = jsonMapper.readValue(lookupResponseString, LookupResponse.class);

        assertNotNull(lookupResponse);
        assertNotNull(lookupResponse.getCustomer());
        assertNotNull(lookupResponse.getCustomer().getProfile());
        assertEquals("Cart", lookupResponse.getCustomer().getProfile().getFirstName());
        assertEquals("Sanchez", lookupResponse.getCustomer().getProfile().getLastName());
    }

    @Test
    void testCreateProfile_ErrorResponse() throws Exception {
        new Expectations() {
            {
                profileAccess.createProfile((CreateRequest) any);
                result = new HttpFailureException(500, Utils.readFileFromClassPath("data/profile_core_create_profile_error_response.json"), "failed", new String[]{"header"});
            }
        };

        final CreateRequest createRequest = createCreateProfileRequest();
        assertThrows(HttpFailureException.class, () -> profileAccess.createProfile(createRequest));
    }

    @Test
    void testGetProfile_SuccessResponse() throws Exception {
        new Expectations() {
            {
                profileAccess.getProfile(anyString);
                LookupResponse lookupResponse = jsonMapper.readValue(Utils.readFileFromClassPath("data/profile_core_get_profile_success_response.json"), LookupResponse.class);
                result = lookupResponse.getCustomer();
            }
        };
        final Customer customerResponse = profileAccess.getProfile("102780541");
        assertNotNull(customerResponse);
        assertNotNull(customerResponse.getExternalId());
        assertNotNull(customerResponse.getProfile());
        assertNotNull(customerResponse.getMemberships());
        assertNotNull(customerResponse.getMetadata());
        assertEquals("1126170165250", customerResponse.getExternalId().getGseCustomerId());
    }

    @Test
    void testGetProfile_ErrorResponse_NoProfileFound() throws Exception {
        new Expectations() {
            {
                profileAccess.getProfile(anyString);
                result = new AppException(1004, Utils.readFileFromClassPath("data/profile_core_get_profile_profile_not_found.json"), "failed", new String[]{"header"});
            }
        };
        assertThrows(AppException.class, () -> profileAccess.getProfile("112780541"));
    }

    @Test
    void testGetProfile_ErrorResponse_Unauthorized() throws Exception {
        new Expectations() {
            {
                profileAccess.getProfile(anyString);
                result = new AppException(1004, Utils.readFileFromClassPath("data/profile_core_get_profile_client_not_authorized.json"), "failed", new String[]{"header"});
            }
        };
        assertThrows(AppException.class, () -> profileAccess.getProfile("102780541"));
    }

    private CreateRequest createCreateProfileRequest() {
        final CreateRequest createRequest = new CreateRequest();
        final InCustomer customer = new InCustomer();
        final Profile profile = new Profile();
        profile.setFirstName("Cart");
        profile.setLastName("Sanchez");
        Address address = new Address();
        address.setType(Address.Type.HOME);
        address.setPreferred(false);
        address.setState("NV");
        address.setCity("Las Vegas");
        address.setCountry("US");
        address.setStreet1("6700 Edmond Street");
        address.setZipCode("89118");
        customer.setForgetMyInfo(false);
        final List<Address> addresses = new ArrayList<>();
        addresses.add(address);
        profile.setAddresses(addresses);
        customer.setProfile(profile);
        createRequest.setCustomer(customer);
        return createRequest;
    }
}
