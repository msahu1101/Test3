package com.mgmresorts.order.service.transformer;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.transform.ITransformer;

public class ShowBillingTransformer implements ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo> {

    @Override
    public com.mgmresorts.sbs.model.BillingInfo toRight(com.mgmresorts.order.dto.Billing billing) throws AppException {

        final com.mgmresorts.sbs.model.BillingInfo billingInfo = new com.mgmresorts.sbs.model.BillingInfo();
        billingInfo.setPayment(toPayment(billing.getPayment()));
        billingInfo.setAddress(toBillingAddress(billing.getAddress()));
        return billingInfo;
    }

    private com.mgmresorts.sbs.model.BillingInfoPayment toPayment(com.mgmresorts.order.dto.Payment payment) {
        final com.mgmresorts.sbs.model.BillingInfoPayment dto = new com.mgmresorts.sbs.model.BillingInfoPayment();
        dto.setFirstName(payment.getFirstName());
        dto.setLastName(payment.getLastName());
        dto.setPaymentToken(payment.getCcToken());
        dto.setCvv(payment.getCvv());
        dto.setType(payment.getType().toString());
        dto.setExpiry(payment.getExpiry());
        return dto;
    }

    private com.mgmresorts.sbs.model.BillingInfoAddress toBillingAddress(com.mgmresorts.order.dto.BillingAddress address) {

        final com.mgmresorts.sbs.model.BillingInfoAddress dto = new com.mgmresorts.sbs.model.BillingInfoAddress();
        dto.setStreet1(address.getStreet1());
        dto.setStreet2(address.getStreet2());
        dto.setCity(address.getCity());
        dto.setState(address.getState());
        dto.setPostalCode(address.getPostalCode());
        dto.setCountry(address.getCountry());
        return dto;
    }
}
