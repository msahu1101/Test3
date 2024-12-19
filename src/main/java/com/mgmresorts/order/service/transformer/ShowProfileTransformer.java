package com.mgmresorts.order.service.transformer;

import java.util.ArrayList;
import java.util.List;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.sbs.model.ReservationProfile;

public class ShowProfileTransformer implements ITransformer<GuestProfile, ReservationProfile> {

    @Override
    public ReservationProfile toRight(GuestProfile guestProfile) throws AppException {

        final ReservationProfile profile = new ReservationProfile();
        profile.setFirstName(guestProfile.getFirstName());
        profile.setLastName(guestProfile.getLastName());
        profile.setPhoneNumbers(toPhoneNumbers(guestProfile.getPhoneNumbers()));
        profile.setEmailAddress1(guestProfile.getEmailAddress1());
        profile.setDateOfBirth(guestProfile.getDateOfBirth());
        if (!Utils.isEmpty(guestProfile.getMlifeNo())) {
            profile.setMlifeNo(guestProfile.getMlifeNo());
        }
        profile.setAddresses(toAddresses(guestProfile.getAddresses()));

        return profile;
    }

    private List<com.mgmresorts.sbs.model.ReservationProfilePhoneNumbers> toPhoneNumbers(List<com.mgmresorts.order.dto.PhoneNumber> phoneNumbers) {

        if (Utils.isEmpty(phoneNumbers)) {
            return null;
        }

        final List<com.mgmresorts.sbs.model.ReservationProfilePhoneNumbers> dtos = new ArrayList<>();
        for (com.mgmresorts.order.dto.PhoneNumber phoneNumber : phoneNumbers) {
            final com.mgmresorts.sbs.model.ReservationProfilePhoneNumbers dto = new com.mgmresorts.sbs.model.ReservationProfilePhoneNumbers();
            dto.setNumber(phoneNumber.getNumber());
            dto.setType(phoneNumber.getType() != null
                    ? com.mgmresorts.sbs.model.ReservationProfilePhoneNumbers.TypeEnum.fromValue(phoneNumber.getType().toString())
                    : com.mgmresorts.sbs.model.ReservationProfilePhoneNumbers.TypeEnum.OTHER);
            dtos.add(dto);
        }
        return dtos;
    }

    private List<com.mgmresorts.sbs.model.ReservationProfileAddresses> toAddresses(List<com.mgmresorts.order.dto.Address> addresses) {

        if (Utils.isEmpty(addresses)) {
            return null;
        }

        final List<com.mgmresorts.sbs.model.ReservationProfileAddresses> dtos = new ArrayList<>();

        for (com.mgmresorts.order.dto.Address address : addresses) {
            final com.mgmresorts.sbs.model.ReservationProfileAddresses dto = new com.mgmresorts.sbs.model.ReservationProfileAddresses();
            dto.setType(address.getType() != null
                    ? com.mgmresorts.sbs.model.ReservationProfileAddresses.TypeEnum.fromValue(address.getType().toString())
                    : com.mgmresorts.sbs.model.ReservationProfileAddresses.TypeEnum.OTHER);
            dto.setPreferred(address.getPreferred());
            dto.setStreet1(address.getStreet1());
            dto.setStreet2(address.getStreet2());
            dto.setCity(address.getCity());
            dto.setState(address.getState());
            dto.setCountry(address.getCountry());
            dto.setPostalCode(address.getPostalCode());
            dtos.add(dto);
        }
        return dtos;
    }
}
