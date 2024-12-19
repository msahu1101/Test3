package com.mgmresorts.order.service.transformer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.exception.AppException;

import uk.co.jemos.podam.api.PodamFactoryImpl;

class ShowBillingTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final ShowBillingTransformer transformer = new ShowBillingTransformer();

    @Test
    final void testToRight() throws AppException {
        final com.mgmresorts.order.dto.Billing billing = podamFactoryImpl.manufacturePojo(com.mgmresorts.order.dto.Billing.class);
        final com.mgmresorts.sbs.model.BillingInfo billingInfo = transformer.toRight(billing);

        assertEquals(billingInfo.getPayment().getFirstName(), billing.getPayment().getFirstName());
        assertEquals(billingInfo.getPayment().getLastName(), billing.getPayment().getLastName());
        assertEquals(billingInfo.getPayment().getPaymentToken(), billing.getPayment().getCcToken());
        assertEquals(billingInfo.getPayment().getCvv(), billing.getPayment().getCvv());
        assertEquals(billingInfo.getPayment().getType(), billing.getPayment().getType().toString());
        assertEquals(billingInfo.getPayment().getExpiry(), billing.getPayment().getExpiry());

        assertEquals(billingInfo.getAddress().getStreet1(), billing.getAddress().getStreet1());
        assertEquals(billingInfo.getAddress().getStreet2(), billing.getAddress().getStreet2());
        assertEquals(billingInfo.getAddress().getCity(), billing.getAddress().getCity());
        assertEquals(billingInfo.getAddress().getState(), billing.getAddress().getState());
        assertEquals(billingInfo.getAddress().getPostalCode(), billing.getAddress().getPostalCode());
        assertEquals(billingInfo.getAddress().getCountry(), billing.getAddress().getCountry());
    }
}
