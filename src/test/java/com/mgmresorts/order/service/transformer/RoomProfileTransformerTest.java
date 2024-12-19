package com.mgmresorts.order.service.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.rbs.model.ReservationProfile;

import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.math.BigDecimal;

class RoomProfileTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final RoomProfileTransformer transformer = new RoomProfileTransformer();

    @Test
    final void testToRight() throws AppException {
        final GuestProfile guestProfile = podamFactoryImpl.manufacturePojo(GuestProfile.class);
        guestProfile.setId("1234");
        guestProfile.setMlifeNo("5678");
        final ReservationProfile profile = transformer.toRight(guestProfile);

        assertEquals(profile.getId(), new BigDecimal(guestProfile.getId()));
        assertEquals(profile.getTitle(), guestProfile.getTitle());
        assertEquals(profile.getOperaId(), guestProfile.getOperaId());
        assertEquals(profile.getFirstName(), guestProfile.getFirstName());
        assertEquals(profile.getLastName(), guestProfile.getLastName());
        assertEquals(profile.getEmailAddress1(), guestProfile.getEmailAddress1());
        assertEquals(profile.getEmailAddress2(), guestProfile.getEmailAddress2());
        assertEquals(profile.getDateOfBirth(), guestProfile.getDateOfBirth());
        assertEquals(profile.getMlifeNo(), new BigDecimal(guestProfile.getMlifeNo()));
        assertEquals(profile.getTier(), guestProfile.getTier());
        assertEquals(profile.getDateOfEnrollment(), guestProfile.getDateOfEnrollment());
        assertEquals(profile.getHgpNo(), guestProfile.getHgpNo());
        assertEquals(profile.getSwrrNo(), guestProfile.getSwrrNo());

        for (int i = 0; i < profile.getPhoneNumbers().size(); i++) {
            assertEquals(profile.getPhoneNumbers().get(i).getNumber(), guestProfile.getPhoneNumbers().get(i).getNumber());
            assertEquals(profile.getPhoneNumbers().get(i).getType(), com.mgmresorts.rbs.model.ReservationProfilePhoneNumbers.TypeEnum.fromValue(guestProfile.getPhoneNumbers().get(i).getType().toString()));
        }

        for (int i = 0; i < profile.getAddresses().size(); i++) {
            assertEquals(profile.getAddresses().get(i).getType(), com.mgmresorts.rbs.model.ReservationProfileAddresses.TypeEnum.fromValue(guestProfile.getAddresses().get(i).getType().toString()));
            assertEquals(profile.getAddresses().get(i).isPreferred(), guestProfile.getAddresses().get(i).getPreferred());
            assertEquals(profile.getAddresses().get(i).getStreet1(), guestProfile.getAddresses().get(i).getStreet1());
            assertEquals(profile.getAddresses().get(i).getStreet2(), guestProfile.getAddresses().get(i).getStreet2());
            assertEquals(profile.getAddresses().get(i).getCity(), guestProfile.getAddresses().get(i).getCity());
            assertEquals(profile.getAddresses().get(i).getState(), guestProfile.getAddresses().get(i).getState());
            assertEquals(profile.getAddresses().get(i).getCountry(), guestProfile.getAddresses().get(i).getCountry());
            assertEquals(profile.getAddresses().get(i).getPostalCode(), guestProfile.getAddresses().get(i).getPostalCode());
        }
    }
}
