package com.mgmresorts.order;

import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.GuestProfile;

import java.util.List;
import java.util.Map;

public class PaymentSessionBaseFields {
    private List<Billing> billings;
    private GuestProfile guestProfile;
    private Map<String, PaymentAuthFields> paymentAuthFieldsMap;
    private String orderReferenceNumber;
    private Map<String, PaymentAuthFields> sessionPreAuthAmountMap; 

    public List<Billing> getBillings() {
        return billings;
    }

    public void setBillings(List<Billing> billings) {
        this.billings = billings;
    }

    public GuestProfile getGuestProfile() {
        return guestProfile;
    }

    public void setGuestProfile(GuestProfile guestProfile) {
        this.guestProfile = guestProfile;
    }

    public Map<String, PaymentAuthFields> getPaymentAuthFieldsMap() {
        return paymentAuthFieldsMap;
    }

    public void setPaymentAuthFieldsMap(Map<String, PaymentAuthFields> paymentAuthFieldsMap) {
        this.paymentAuthFieldsMap = paymentAuthFieldsMap;
    }

    public Map<String, PaymentAuthFields> getSessionPreAuthAmountMap() {
        return sessionPreAuthAmountMap;
    }

    public void setSessionPreAuthAmountMap(Map<String, PaymentAuthFields> sessionPreAuthAmountMap) {
        this.sessionPreAuthAmountMap = sessionPreAuthAmountMap;
    }

    public String getOrderReferenceNumber() {
        return orderReferenceNumber;
    }

    public void setOrderReferenceNumber(String orderReferenceNumber) {
        this.orderReferenceNumber = orderReferenceNumber;
    }
}
