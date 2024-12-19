package com.mgmresorts.order.service.transformer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.exception.AppException;

import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;

class RoomBillingTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final RoomBillingTransformer transformer = new RoomBillingTransformer();

    @Test
    final void testToRight() throws AppException {
        final com.mgmresorts.order.dto.Billing billing = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.dto.Billing.class); 
        final com.mgmresorts.rbs.model.RoomBillingDetails bill = transformer.toRight(billing);

        assertEquals(bill.getPayment().getCardHolder(), billing.getPayment().getCardHolder());
        assertEquals(bill.getPayment().getFirstName(), billing.getPayment().getFirstName());
        assertEquals(bill.getPayment().getLastName(), billing.getPayment().getLastName());
        assertEquals(bill.getPayment().getCcToken(), billing.getPayment().getCcToken());
        assertEquals(bill.getPayment().getEncryptedccToken(), billing.getPayment().getEncryptedccToken());
        assertEquals(bill.getPayment().getMaskedNumber(), billing.getPayment().getMaskedNumber());
        assertEquals(bill.getPayment().getCvv(), billing.getPayment().getCvv());
        assertEquals(bill.getPayment().getType(), com.mgmresorts.rbs.model.RoomBillingDetailsPayment.TypeEnum.fromValue(billing.getPayment().getType().toString()));
        assertEquals(bill.getPayment().getExpiry(), billing.getPayment().getExpiry());
        assertEquals(bill.getPayment().getAmount(), BigDecimal.valueOf(billing.getPayment().getAmount().doubleValue()).setScale(2, RoundingMode.HALF_UP));
        assertEquals(bill.getPayment().getFxAmount(), BigDecimal.valueOf(billing.getPayment().getFxAmount().doubleValue()).setScale(2, RoundingMode.HALF_UP));
        assertEquals(bill.getPayment().getFxCurrencyISOCode(), billing.getPayment().getFxCurrencyISOCode());
        assertEquals(bill.getPayment().getFxCurrencyCode(), billing.getPayment().getFxCurrencyCode());
        assertEquals(bill.getPayment().getFxExchangeRate(), BigDecimal.valueOf(billing.getPayment().getFxExchangeRate().doubleValue()).setScale(2, RoundingMode.HALF_UP));
        assertEquals(bill.getPayment().getFxFlag(), billing.getPayment().getFxFlag());

        assertEquals(bill.getAddress().getStreet1(), billing.getAddress().getStreet1());
        assertEquals(bill.getAddress().getStreet2(), billing.getAddress().getStreet2());
        assertEquals(bill.getAddress().getCity(), billing.getAddress().getCity());
        assertEquals(bill.getAddress().getState(), billing.getAddress().getState());
        assertEquals(bill.getAddress().getPostalCode(), billing.getAddress().getPostalCode());
        assertEquals(bill.getAddress().getCountry(), billing.getAddress().getCountry());
    }
}
