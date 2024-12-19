package com.mgmresorts.order.service.transformer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.rbs.model.ReservationProfile;

public class RoomProfileTransformer implements ITransformer<GuestProfile, ReservationProfile> {

    @Override
    public ReservationProfile toRight(GuestProfile guestProfile) throws AppException {

        final ReservationProfile profile = new ReservationProfile();
        profile.setId(new BigDecimal(guestProfile.getId()));
        profile.setTitle(guestProfile.getTitle());
        profile.setOperaId(guestProfile.getOperaId());
        profile.setFirstName(guestProfile.getFirstName());
        profile.setLastName(guestProfile.getLastName());
        profile.setPhoneNumbers(toPhoneNumbers(guestProfile.getPhoneNumbers()));
        profile.setEmailAddress1(guestProfile.getEmailAddress1());
        profile.setEmailAddress2(guestProfile.getEmailAddress2());
        profile.setDateOfBirth(guestProfile.getDateOfBirth());
        if (!Utils.isEmpty(guestProfile.getMlifeNo())) {
            profile.setMlifeNo(new BigDecimal(guestProfile.getMlifeNo()));
        }
        profile.setTier(guestProfile.getTier());
        profile.setDateOfEnrollment(guestProfile.getDateOfEnrollment());
        profile.setHgpNo(guestProfile.getHgpNo());
        profile.setSwrrNo(guestProfile.getSwrrNo());
        profile.setAddresses(toAddresses(guestProfile.getAddresses()));

        return profile;
    }

    private List<com.mgmresorts.rbs.model.ReservationProfilePhoneNumbers> toPhoneNumbers(List<com.mgmresorts.order.dto.PhoneNumber> phoneNumbers) {

        if (Utils.isEmpty(phoneNumbers)) {
            return null;
        }

        final List<com.mgmresorts.rbs.model.ReservationProfilePhoneNumbers> dtos = new ArrayList<>();
        for (com.mgmresorts.order.dto.PhoneNumber phoneNumber : phoneNumbers) {
            final com.mgmresorts.rbs.model.ReservationProfilePhoneNumbers dto = new com.mgmresorts.rbs.model.ReservationProfilePhoneNumbers();
            dto.setNumber(phoneNumber.getNumber());
            dto.setType(phoneNumber.getType() != null
                    ? com.mgmresorts.rbs.model.ReservationProfilePhoneNumbers.TypeEnum.fromValue(phoneNumber.getType().toString())
                    : com.mgmresorts.rbs.model.ReservationProfilePhoneNumbers.TypeEnum.OTHER);
            dtos.add(dto);
        }
        return dtos;
    }

    private List<com.mgmresorts.rbs.model.ReservationProfileAddresses> toAddresses(List<com.mgmresorts.order.dto.Address> addresses) {

        if (Utils.isEmpty(addresses)) {
            return null;
        }

        final List<com.mgmresorts.rbs.model.ReservationProfileAddresses> dtos = new ArrayList<>();

        for (com.mgmresorts.order.dto.Address address : addresses) {
            final com.mgmresorts.rbs.model.ReservationProfileAddresses dto = new com.mgmresorts.rbs.model.ReservationProfileAddresses();
            dto.setType(com.mgmresorts.rbs.model.ReservationProfileAddresses.TypeEnum.fromValue(address.getType().toString()));
            dto.setType(address.getType() != null
                    ? com.mgmresorts.rbs.model.ReservationProfileAddresses.TypeEnum.fromValue(address.getType().toString())
                    : com.mgmresorts.rbs.model.ReservationProfileAddresses.TypeEnum.OTHER);
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
