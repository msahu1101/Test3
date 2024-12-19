package com.mgmresorts.order;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.Payment;
import com.mgmresorts.order.errors.Errors;

public final class AppliedBillings {
    private final List<Billing> inputBillings;
    private final double totalCharges;

    private List<Billing> unbilled;

    public AppliedBillings(List<Billing> billings) {
        this.inputBillings = billings;
        double totalCharges = 0;
        for (Billing billing : this.inputBillings) {
            if (billing.getPayment() != null && billing.getPayment().getAmount() != null) {
                totalCharges += billing.getPayment().getAmount().doubleValue();
            }
        }
        this.totalCharges = totalCharges;
        this.unbilled = this.inputBillings;
    }

    public double getTotalCharges() {
        return totalCharges;
    }

    public Collection<Billing> bill(double chargable) throws AppException {
        final List<Billing> changed = new ArrayList<Billing>();
        final List<Billing> pending = new ArrayList<Billing>(this.unbilled);
        double due = chargable;
        for (Billing billing : this.unbilled) {
            final Double amount = billing.getPayment().getAmount();
            if (amount == null) {
                throw new AppException(Errors.INVALID_REQUEST_INFORMATION, "Billing details");
            }
            final double currentAllowedAmount = amount.doubleValue();
            if (due == currentAllowedAmount) {
                changed.add(billing);
                final Billing remainingBilling = clone(billing, 0);
                pending.remove(billing);
                pending.add(remainingBilling);
                break;
            } else if (due < currentAllowedAmount) {
                final Billing currentBilling = clone(billing, due);
                final Billing remainingBilling = clone(billing, currentAllowedAmount - due);
                changed.add(currentBilling);
                pending.remove(billing);
                pending.add(remainingBilling);
                break;
            } else if (due > currentAllowedAmount && currentAllowedAmount > 0) {
                due = due - currentAllowedAmount;
                changed.add(billing);
                final Billing remainingBilling = clone(billing, 0);
                pending.remove(billing);
                pending.add(remainingBilling);
            }
        }
        this.unbilled = pending;
        return changed;
    }

    private Billing clone(Billing billing, double amount) {
        final Billing tmp = new Billing();
        tmp.setAddress(billing.getAddress());
        final Payment payment = billing.getPayment();
        final Payment newPayment = new Payment();
        newPayment.setAmount(amount);
        newPayment.setCardHolder(payment.getCardHolder());
        newPayment.setCcToken(payment.getCcToken());
        newPayment.setCvv(payment.getCvv());
        newPayment.setEncryptedccToken(payment.getEncryptedccToken());
        newPayment.setExpiry(payment.getExpiry());
        newPayment.setFirstName(payment.getFirstName());
        newPayment.setFxAmount(payment.getFxAmount());
        newPayment.setFxCurrencyCode(payment.getFxCurrencyCode());
        newPayment.setFxCurrencyISOCode(payment.getFxCurrencyISOCode());
        newPayment.setFxExchangeRate(payment.getFxExchangeRate());
        newPayment.setFxFlag(payment.getFxFlag());
        newPayment.setLastName(payment.getLastName());
        newPayment.setMaskedNumber(payment.getMaskedNumber());
        newPayment.setType(payment.getType());
        tmp.setPayment(newPayment);
        return tmp;
    }

}
