package com.mgmresorts.order.backend.handler.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.mgmresorts.common.dto.services.OutHeaderSupport;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.IProfileAccess;
import com.mgmresorts.order.backend.handler.IProfileHandler;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.dto.PhoneNumber;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.profile.dto.common.Address;
import com.mgmresorts.profile.dto.common.Email;
import com.mgmresorts.profile.dto.common.Phone;
import com.mgmresorts.profile.dto.customer.Customer;
import com.mgmresorts.profile.dto.customer.InCustomer;
import com.mgmresorts.profile.dto.customer.Profile;
import com.mgmresorts.profile.dto.services.CreateRequest;
import com.mgmresorts.profile.dto.services.LookupResponse;

public class ProfileHandler implements IProfileHandler {
    
    private final Logger logger = Logger.get(ProfileHandler.class);
    final JSonMapper mapper = new JSonMapper();
    
    @Inject
    private IProfileAccess profileAccess;

    public String createGuestProfile(CheckoutRequest request) throws AppException {
        try {
            final CreateRequest createRequest = getCustomerCreateRequest(request.getGuestProfile());
            final String responseString = profileAccess.createProfile(createRequest);
            final LookupResponse customerResponse = mapper.readValue(responseString, LookupResponse.class);
            return customerResponse.getCustomer().getId();
        } catch (HttpFailureException e) {
            if (e.getHttpCode() <= 500) {
                String message = null;
                if (!Utils.isEmpty(e.getPayload()) && Utils.isValidJson(e.getPayload())) {
                    logger.error("[Error from Profile] could not create profile :  {}", e.getPayload());
                    final OutHeaderSupport errorResponse = mapper.readValue(e.getPayload(), OutHeaderSupport.class);
                    if (errorResponse.getHeader() != null && errorResponse.getHeader().getStatus() != null && !Utils.isEmpty(errorResponse.getHeader().getStatus().getMessages())) {
                        message = errorResponse.getHeader().getStatus().getMessages().get(0).getText();
                    } else {
                        message = e.getMessage();
                    }
                }
                logger.error("[Error from Profile Service] Create Customer Request call failed :  ", e.getMessage());
                throw new AppException(ApplicationError.UNABLE_TO_CREATE_CUSTOMER_PROFILE, "Failure message from [Profile Services] : " + message, e);
            } else {
                logger.error("[Error from Profile Service] Something unexpected happened in create profile : " + e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM, e, e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from Profile Service] Create Customer Request call failed :  ", e.getMessage());
            throw new AppException(ApplicationError.UNABLE_TO_CREATE_CUSTOMER_PROFILE, e.getMessage(), e);
        }
    }

    public Customer getGuestProfile(final String mlifeNumber) throws AppException {
        return profileAccess.getProfile(mlifeNumber);
    }

    private CreateRequest getCustomerCreateRequest(GuestProfile guestProfile) {
        final CreateRequest createRequest = new CreateRequest();
        final InCustomer inCustomer = new InCustomer();
        inCustomer.setProfile(getProfile(guestProfile));
        createRequest.setCustomer(inCustomer);
        return createRequest;
    }

    private Profile getProfile(GuestProfile guestProfile) {
        final Profile profile = new Profile();
        if (!Utils.isEmpty(guestProfile.getTitle())) {
            profile.setTitle(Profile.Title.fromValue(guestProfile.getTitle()));
        }
        profile.setFirstName(guestProfile.getFirstName());
        profile.setLastName(guestProfile.getLastName());
        if (!Utils.isEmpty(guestProfile.getDateOfBirth())) {
            profile.setDateOfBirth(LocalDate.parse(guestProfile.getDateOfBirth()));
        }
        if (!Utils.isEmpty(guestProfile.getEmailAddress1())) {
            profile.getEmailAddresses().add(buildEmail(guestProfile.getEmailAddress1()));
        }
        if (!Utils.isEmpty(guestProfile.getEmailAddress2())) {
            profile.getEmailAddresses().add(buildEmail(guestProfile.getEmailAddress2()));
        }
        if (!Utils.isEmpty(guestProfile.getPhoneNumbers())) {
            profile.setPhoneNumbers(buildPhones(guestProfile.getPhoneNumbers()));
        }
        if (!Utils.isEmpty(guestProfile.getAddresses())) {
            profile.setAddresses(buildAddresses(guestProfile.getAddresses()));
        }
        profile.setHgpTier(guestProfile.getHgpNo());
        return profile;
    }

    private List<Address> buildAddresses(List<com.mgmresorts.order.dto.Address> addresses) {
        return addresses.stream().map(address -> toAddress(address)).collect(Collectors.toList());
    }

    private List<Phone> buildPhones(List<PhoneNumber> phoneNumbers) {
        return phoneNumbers.stream().map(phoneNumber -> toPhone(phoneNumber)).collect(Collectors.toList());
    }

    private Phone toPhone(PhoneNumber phoneNumber) {
        final Phone phone = new Phone();
        phone.setNumber(phoneNumber.getNumber());
        phone.setType(phoneNumber.getType() != null ? Phone.Type.fromValue(phoneNumber.getType().name()) : Phone.Type.OTHER);
        return phone;
    }

    private Address toAddress(com.mgmresorts.order.dto.Address address) {
        final Address address2 = new Address();
        address2.setCity(address.getCity());
        address2.setCountry(address.getCountry());
        address2.setPreferred(address.getPreferred());
        address2.setState(address.getState());
        address2.setStreet1(address.getStreet1());
        address2.setStreet2(address.getStreet2());
        address2.setType(address.getType() != null ? Address.Type.fromValue(address.getType().name()) : Address.Type.OTHER);
        address2.setZipCode(address.getPostalCode());
        return address2;
    }

    private Email buildEmail(String emailAddress) {
        final Email email = new Email();
        email.setEmail(emailAddress);
        return email;
    }
}
