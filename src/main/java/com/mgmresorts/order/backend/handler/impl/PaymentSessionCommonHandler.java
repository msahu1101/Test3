package com.mgmresorts.order.backend.handler.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.security.Jwts.Claim;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.Payment;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.dto.PhoneNumber;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.IPaymentSessionAccess;
import com.mgmresorts.order.backend.handler.IPaymentSessionCommonHandler;
import com.mgmresorts.order.dto.Address;
import com.mgmresorts.order.dto.Address.Type;
import com.mgmresorts.order.dto.BillingAddress;
import com.mgmresorts.psm.model.CardDetails;
import com.mgmresorts.psm.model.GuestDetails;
import com.mgmresorts.psm.model.Item;
import com.mgmresorts.psm.model.ItemAuthGroup;
import com.mgmresorts.psm.model.PaymentAuthResults;
import com.mgmresorts.psm.model.RetrieveSessionResponse;
import com.mgmresorts.psm.model.TotalAmount;

import org.apache.commons.lang3.StringUtils;

public class PaymentSessionCommonHandler implements IPaymentSessionCommonHandler {
    private final Logger logger = Logger.get(PaymentSessionCommonHandler.class);
    
    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_FAILURE = "FAILURE";
    private static final String FRAUD_FAILURE = "DECLINED";
    private static final String TCOLV_PROPERTY_ID = Runtime.get().getConfiguration("tcolv.property.id");
    private static final boolean TCOLV_SKIP_PAYMENT_PROCESSING = Boolean.parseBoolean(Runtime.get().getConfiguration("tcolv.skip.payment.processing"));

    @Inject
    private IPaymentSessionAccess paymentSessionAccess;

    @Override
    public PaymentSessionBaseFields getPaymentAuthResults(String sessionId) throws AppException {
        final PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        RetrieveSessionResponse retrieveSessionResponse = paymentSessionAccess.getPaymentSession(sessionId);
        
        if (retrieveSessionResponse != null && retrieveSessionResponse.getSessionStatus().equalsIgnoreCase("Active")
                && !retrieveSessionResponse.getSessionType().equals("Retrieve")) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            retrieveSessionResponse = paymentSessionAccess.getPaymentSession(sessionId);
        }

        if (retrieveSessionResponse != null) {
            final Map<String, PaymentAuthFields> paymentIdMapping = toPaymentIdMapping(retrieveSessionResponse);
            final GuestProfile guestProfile = toGuestProfile(retrieveSessionResponse.getGuestDetails(), retrieveSessionResponse.getCardDetails());
            final List<Billing> billings = toBillings(retrieveSessionResponse.getCardDetails(), retrieveSessionResponse);

            paymentSessionBaseFields.setPaymentAuthFieldsMap(paymentIdMapping);
            paymentSessionBaseFields.setGuestProfile(guestProfile);
            paymentSessionBaseFields.setBillings(billings);
            paymentSessionBaseFields.setOrderReferenceNumber(toOrderReferenceNumber(retrieveSessionResponse));
            paymentSessionBaseFields.setSessionPreAuthAmountMap(toPaymentSessionPreAuthAmountMapping(retrieveSessionResponse));
            return paymentSessionBaseFields;
        }
        logger.warn("Unable to create paymentId mapping. Investigate get payment session response.");
        return null;
    }

    private GuestProfile toGuestProfile(GuestDetails guestDetails, com.mgmresorts.psm.model.CardDetails cardDetails) {
        if (guestDetails == null) {
            return null;
        }
        final GuestProfile guestProfile = new GuestProfile();
        guestProfile.setFirstName(guestDetails.getFirstName());
        guestProfile.setLastName(guestDetails.getLastName());
        guestProfile.setEmailAddress1(guestDetails.getEmail());
        guestProfile.setPhoneNumbers(toPhoneNumbers(guestDetails.getPhoneNumber()));
        if (guestDetails.getMgmId() != null && !guestDetails.isLoggedIn() && cardDetails != null) {
            guestProfile.setAddresses(toAddressesFromBilling(cardDetails.getBillingAddress()));
        }
        guestProfile.setAddresses(toAddresses(guestDetails.getAddress()));
        return guestProfile;
    }

    private List<PhoneNumber> toPhoneNumbers(String phoneNumber) {
        if (StringUtils.isBlank(phoneNumber)) {
            return null;
        }
        final List<PhoneNumber> phoneNumbers = new ArrayList<>();
        final PhoneNumber number = new PhoneNumber();
        number.setNumber(phoneNumber);
        number.setType(PhoneNumber.Type.OTHER);
        phoneNumbers.add(number);
        return phoneNumbers;
    }

    private List<Address> toAddresses(com.mgmresorts.psm.model.Address address) {
        if (address == null) {
            return null;
        }
        final List<Address> addresses = new ArrayList<>();
        final Address guestAddress = new Address();
        guestAddress.setStreet1(address.getAddress());
        guestAddress.setStreet2(address.getAddress2());
        guestAddress.setCity(address.getCity());
        guestAddress.setState(address.getState());
        guestAddress.setCountry(address.getCountry());
        guestAddress.setPostalCode(address.getZip());
        guestAddress.setType(address.getType() != null ? Type.fromValue(address.getType()) : Type.OTHER);
        guestAddress.setPreferred(false);
        addresses.add(guestAddress);
        return addresses;
    }

    private List<Address> toAddressesFromBilling(com.mgmresorts.psm.model.BillingAddress billingAddress) {
        if (billingAddress == null) {
            return null;
        }
        final List<Address> addresses = new ArrayList<>();
        final Address guestAddress = new Address();
        guestAddress.setStreet1(billingAddress.getAddress());
        guestAddress.setStreet2(billingAddress.getAddress2());
        guestAddress.setCity(billingAddress.getCity());
        guestAddress.setState(billingAddress.getState());
        guestAddress.setCountry(billingAddress.getCountry());
        guestAddress.setPostalCode(billingAddress.getPostalCode());
        guestAddress.setType(Type.OTHER);
        guestAddress.setPreferred(false);
        addresses.add(guestAddress);
        return addresses;
    }

    private List<Billing> toBillings(CardDetails cardDetails, RetrieveSessionResponse retrieveSessionResponse) {
        if (cardDetails == null) {
            return null;
        }
        if (retrieveSessionResponse.getOrderItems() == null
                || Utils.isEmpty(retrieveSessionResponse.getOrderItems().getItemAuthGroups())) {
            return null;
        }
        final List<Billing> billings = new ArrayList<>();
        final Billing billing = new Billing();
        final Double amount = calculateAmount(retrieveSessionResponse.getOrderItems().getItemAuthGroups());
        billing.setPayment(toPayment(cardDetails, amount));
        billing.setAddress(toBillingAddress(cardDetails.getBillingAddress()));
        billings.add(billing);
        return billings;
    }

    private Payment toPayment(CardDetails cardDetails, Double amount) {
        if (cardDetails == null) {
            return null;
        }
        final Payment payment = new Payment();
        payment.setCardHolder(cardDetails.getCreditCardHolderName());
        payment.setCcToken(cardDetails.getMgmToken());
        payment.setMaskedNumber(cardDetails.getTenderDisplay());
        payment.setType(Payment.Type.fromValue(cardDetails.getIssuerType()));
        payment.setExpiry(cardDetails.getExpiryMonth() + "/" + cardDetails.getExpiryYear());
        payment.setAmount(amount);
        return payment;
    }

    private BillingAddress toBillingAddress(com.mgmresorts.psm.model.BillingAddress address) {
        if (address == null) {
            return null;
        }
        final BillingAddress billingAddress = new BillingAddress();
        billingAddress.setStreet1(address.getAddress());
        billingAddress.setStreet2(address.getAddress2());
        billingAddress.setCity(address.getCity());
        billingAddress.setState(address.getState());
        billingAddress.setPostalCode(address.getPostalCode());
        billingAddress.setCountry(address.getCountry());
        return billingAddress;
    }

    private Map<String, PaymentAuthFields> toPaymentIdMapping(RetrieveSessionResponse retrieveSessionResponse) {
        if (retrieveSessionResponse.getOrderItems() == null || Utils.isEmpty(retrieveSessionResponse.getOrderItems().getItemAuthGroups())) {
            return null;
        }
        
        final Map<String, PaymentAuthFields> paymentIdMapping = new HashMap<>();
        final List<ItemAuthGroup> itemAuthGroups = retrieveSessionResponse.getOrderItems().getItemAuthGroups();

        for (ItemAuthGroup itemAuthGroup : itemAuthGroups) {
            if (!Utils.isEmpty(itemAuthGroup.getItems())) {
                for (Item item : itemAuthGroup.getItems()) {
                    Optional<PaymentAuthResults> paymentAuthResults = Optional.empty();
                    
                    if (!Utils.isEmpty(itemAuthGroup.getPaymentAuthResults())) {
                        paymentAuthResults = itemAuthGroup.getPaymentAuthResults().stream()
                                .filter(a -> a.getType().equalsIgnoreCase("Authorize") && a.getStatus().equalsIgnoreCase(PAYMENT_SUCCESS)).findFirst();
                        
                        if (paymentAuthResults.isEmpty()) {
                            paymentAuthResults = itemAuthGroup.getPaymentAuthResults().stream()
                                    .filter(a -> a.getType().equalsIgnoreCase("Authorize")).findFirst();
                        }
                    }
                    
                    final boolean skipTCOLVPaymentProcessing = TCOLV_SKIP_PAYMENT_PROCESSING && (StringUtils.isNotBlank(item.getPropertyId())
                            ? item.getPropertyId().equalsIgnoreCase(TCOLV_PROPERTY_ID) : false);
                    
                    final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
                    paymentAuthFields.setSessionId(retrieveSessionResponse.getSessionId());
                    paymentAuthFields.setMgmId(ThreadContext.getContext().get().getJwtClaim(Claim.MGM_ID));
                    paymentAuthFields.setPropertyId(item.getPropertyId());
                    paymentAuthFields.setItemId(item.getItemId());
                    if (paymentAuthResults.isPresent() && paymentAuthResults.get().getStatus().equalsIgnoreCase(PAYMENT_SUCCESS)) {
                        paymentAuthFields.setAuthorizationCode(paymentAuthResults.get().getAuthorizationCode());
                        paymentAuthFields.setPaymentId(paymentAuthResults.get().getPaymentId());
                        paymentAuthFields.setConfirmationNumber(item.getConfirmationNumber());
                        paymentAuthFields.setAmount(paymentAuthResults.get().getAuthorizedAmount());
                        paymentAuthFields.setSuccess(true);
                    } else if (paymentAuthResults.isPresent() && paymentAuthResults.get().getStatus().equalsIgnoreCase(PAYMENT_FAILURE)) {
                        paymentAuthFields.setErrorCode(paymentAuthResults.get().getErrorCode());
                        paymentAuthFields.setErrorMessage(paymentAuthResults.get().getErrorDescription());
                        paymentAuthFields.setSuccess(false);
                    } else if (skipTCOLVPaymentProcessing && itemAuthGroup.getPaymentVerifyResults() != null
                            && itemAuthGroup.getPaymentVerifyResults().getStatus().equalsIgnoreCase(PAYMENT_SUCCESS)) {
                        paymentAuthFields.setConfirmationNumber(item.getConfirmationNumber());
                        paymentAuthFields.setAuthorizationCode(itemAuthGroup.getPaymentVerifyResults().getAuthorizationCode());
                        paymentAuthFields.setSuccess(true);
                    } else if (itemAuthGroup.getPaymentFraudResults() != null && itemAuthGroup.getPaymentFraudResults().getStatus().equalsIgnoreCase(FRAUD_FAILURE)) {
                        paymentAuthFields.setErrorCode(itemAuthGroup.getPaymentFraudResults().getErrorCode());
                        paymentAuthFields.setErrorMessage(itemAuthGroup.getPaymentFraudResults().getErrorDescription());
                        paymentAuthFields.setSuccess(false);
                    }
                    paymentIdMapping.put(item.getId(), paymentAuthFields);
                }
            }
        }
        return paymentIdMapping;
    }

    private String toOrderReferenceNumber(RetrieveSessionResponse retrieveSessionResponse) {
        if (retrieveSessionResponse.getOrderItems() == null) {
            return null;
        }
        return retrieveSessionResponse.getOrderItems().getOrderReferenceNumber();
    }

    private Double calculateAmount(List<ItemAuthGroup> itemAuthGroups) {
        double totalAmount = 0;
        for (ItemAuthGroup itemAuthGroup: itemAuthGroups) {
            final boolean skipTCOLVPaymentProcessing = TCOLV_SKIP_PAYMENT_PROCESSING && !Utils.isEmpty(itemAuthGroup.getItems())
                    && itemAuthGroup.getItems().get(0) != null && StringUtils.isNotBlank(itemAuthGroup.getItems().get(0).getPropertyId())
                    && itemAuthGroup.getItems().get(0).getPropertyId().equalsIgnoreCase(TCOLV_PROPERTY_ID);
            if (!skipTCOLVPaymentProcessing) {
                if (!Utils.isEmpty(itemAuthGroup.getPaymentAuthResults())) {
                    final Optional<PaymentAuthResults> paymentAuthResults = itemAuthGroup.getPaymentAuthResults().stream()
                            .filter(a -> a.getType().equalsIgnoreCase("Authorize")).findFirst();
                    if (paymentAuthResults.isPresent()) {
                        if (paymentAuthResults.get().getStatus().equalsIgnoreCase(PAYMENT_SUCCESS)) {
                            totalAmount += paymentAuthResults.get().getAuthorizedAmount();
                        } else {
                            totalAmount += paymentAuthResults.get().getRemainingAuthAmount();
                        }
                    }
                }
            } else {
                if (itemAuthGroup.getItems().get(0).getAmount() != null && !Utils.isEmpty(itemAuthGroup.getItems().get(0).getAmount().getTotalAmount())) {
                    final Optional<TotalAmount> amount = itemAuthGroup.getItems().get(0).getAmount().getTotalAmount().stream()
                            .filter(a -> a.getName().equalsIgnoreCase("authAmount")).findFirst();
                    if (amount.isPresent() && amount.get().getValue() != null) {
                        totalAmount += amount.get().getValue();
                    }
                }
            }
        }
        return Utils.roundTwoDecimalPlaces(totalAmount);
    }
    
    private Map<String, PaymentAuthFields> toPaymentSessionPreAuthAmountMapping(RetrieveSessionResponse retrieveSessionResponse) {
        if (retrieveSessionResponse.getOrderItems() == null || Utils.isEmpty(retrieveSessionResponse.getOrderItems().getItemAuthGroups())) {
            return null;
        }
        
        final Map<String, PaymentAuthFields> paymentIdMapping = new HashMap<>();
        final List<ItemAuthGroup> itemAuthGroups = retrieveSessionResponse.getOrderItems().getItemAuthGroups();

        for (ItemAuthGroup itemAuthGroup : itemAuthGroups) {
            if (!Utils.isEmpty(itemAuthGroup.getItems())) {
                for (Item item : itemAuthGroup.getItems()) {
                    if (item.getAmount() != null && !Utils.isEmpty(item.getAmount().getTotalAmount())) {
                        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
                        final Optional<TotalAmount> tm =  item.getAmount().getTotalAmount().stream().filter(a -> a.getName().equalsIgnoreCase("authAmount")).findFirst();
                        paymentAuthFields.setAmount(tm.get().getValue());
                        paymentIdMapping.put(item.getId(), paymentAuthFields);
                    }
                }
            }
        }
        return paymentIdMapping;
    }
}
