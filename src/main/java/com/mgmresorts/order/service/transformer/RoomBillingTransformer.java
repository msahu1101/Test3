package com.mgmresorts.order.service.transformer;

import java.math.BigDecimal;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.transform.ITransformer;

@SuppressWarnings("deprecation")
public class RoomBillingTransformer implements ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails> {

    @Override
    public com.mgmresorts.rbs.model.RoomBillingDetails toRight(com.mgmresorts.order.dto.Billing billing) throws AppException {

        final com.mgmresorts.rbs.model.RoomBillingDetails bill = new com.mgmresorts.rbs.model.RoomBillingDetails();
        bill.setPayment(toPayment(billing.getPayment()));
        bill.setAddress(toBillingAddress(billing.getAddress()));
        return bill;
    }

    private com.mgmresorts.rbs.model.RoomBillingDetailsPayment toPayment(com.mgmresorts.order.dto.Payment payment) {
        final com.mgmresorts.rbs.model.RoomBillingDetailsPayment dto = new com.mgmresorts.rbs.model.RoomBillingDetailsPayment();
        dto.setCardHolder(payment.getCardHolder());
        dto.setFirstName(payment.getFirstName());
        dto.setLastName(payment.getLastName());
        dto.setCcToken(payment.getCcToken());
        dto.setEncryptedccToken(payment.getEncryptedccToken());
        dto.setMaskedNumber(payment.getMaskedNumber());
        dto.setCvv(payment.getCvv());
        dto.setType(com.mgmresorts.rbs.model.RoomBillingDetailsPayment.TypeEnum.fromValue(payment.getType().toString()));
        dto.setExpiry(payment.getExpiry());
        dto.setAmount((payment.getAmount() != null) ? BigDecimal.valueOf(payment.getAmount().doubleValue()).setScale(2, BigDecimal.ROUND_HALF_UP) : null);
        dto.setFxAmount((payment.getFxAmount() != null) ? BigDecimal.valueOf(payment.getFxAmount().doubleValue()).setScale(2, BigDecimal.ROUND_HALF_UP) : null);
        dto.setFxCurrencyISOCode(payment.getFxCurrencyISOCode());
        dto.setFxCurrencyCode(payment.getFxCurrencyCode());
        dto.setFxExchangeRate((payment.getFxExchangeRate() != null) ? BigDecimal.valueOf(payment.getFxExchangeRate().doubleValue()).setScale(2, BigDecimal.ROUND_HALF_UP) : null);
        dto.setFxFlag(payment.getFxFlag());
        return dto;
    }

    private com.mgmresorts.rbs.model.RoomBillingDetailsAddress toBillingAddress(com.mgmresorts.order.dto.BillingAddress address) {

        final com.mgmresorts.rbs.model.RoomBillingDetailsAddress dto = new com.mgmresorts.rbs.model.RoomBillingDetailsAddress();
        dto.setStreet1(address.getStreet1());
        dto.setStreet2(address.getStreet2());
        dto.setCity(address.getCity());
        dto.setState(address.getState());
        dto.setPostalCode(address.getPostalCode());
        dto.setCountry(address.getCountry());
        return dto;
    }
}
