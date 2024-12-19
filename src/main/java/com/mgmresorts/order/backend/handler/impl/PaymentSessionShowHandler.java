package com.mgmresorts.order.backend.handler.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.security.Jwts.Claim;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.IPaymentSessionAccess;
import com.mgmresorts.order.backend.handler.IPaymentSessionShowHandler;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.order.utils.PaymentSessionUtil;
import com.mgmresorts.psm.model.AdditionalAttributes;
import com.mgmresorts.psm.model.Address;
import com.mgmresorts.psm.model.Amount;
import com.mgmresorts.psm.model.BillingAddress;
import com.mgmresorts.psm.model.CardDetails;
import com.mgmresorts.psm.model.Delivery;
import com.mgmresorts.psm.model.Duration;
import com.mgmresorts.psm.model.EnableSessionRequest;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.psm.model.GuestDetails;
import com.mgmresorts.psm.model.Item;
import com.mgmresorts.psm.model.ItemAuthGroup;
import com.mgmresorts.psm.model.ItemizedCharges;
import com.mgmresorts.psm.model.ItemsGroupTotal;
import com.mgmresorts.psm.model.OrderItems;
import com.mgmresorts.psm.model.Seat;
import com.mgmresorts.psm.model.SessionError;
import com.mgmresorts.psm.model.TFCosts;
import com.mgmresorts.psm.model.TotalAmount;
import com.mgmresorts.psm.model.Transaction;
import com.mgmresorts.sbs.model.BillingInfoAddress;
import com.mgmresorts.sbs.model.BillingInfoPayment;
import com.mgmresorts.sbs.model.ReservationProfile;
import com.mgmresorts.sbs.model.ReservationProfilePhoneNumbers;
import com.mgmresorts.sbs.model.ReservationShowTicketDetail;
import com.mgmresorts.sbs.model.ShowReservationResponse;

public class PaymentSessionShowHandler implements IPaymentSessionShowHandler {


    public static final String DEFAULT_PHONE_EMAIL_TYPE = "Other";
    public static final String SHOW = "Show";
    public static final String TAX = "TAX";
    public static final String FEE = "FEE";
    public static final String SHOW_END_TIME = "11:59 PM";
    public static final String GATEWAY_ID = "FPY7";
    public static final String CREDIT_CARD = "Credit Card";
    public static final String TICKET_DELIVERY_TYPE = "DIGITAL";
    public static final String TICKET_DELIVERY_METHOD = "email";
    private final Logger logger = Logger.get(PaymentSessionShowHandler.class);
    private final JSonMapper mapper = new JSonMapper();

    @Inject
    private IPaymentSessionAccess paymentSessionAccess;

    @Override
    public EnableSessionResponse managePaymentSessionForShowReservation(ShowReservationResponse showReservationResponse,
                                                                        String sessionId, CallType callType) throws AppException {
        final EnableSessionRequest enableSessionRequest = createPaymentSessionRequest(showReservationResponse, sessionId, callType);
        EnableSessionResponse enableSessionResponse = null;
        try {
            enableSessionResponse = paymentSessionAccess.managePaymentSession(enableSessionRequest, callType);
        } catch (SourceAppException e) {
            final SessionError psmErrorResponse = mapper.readValue(e.getRaw(), SessionError.class);
            if (!Utils.anyNull(psmErrorResponse, psmErrorResponse.getErrorCode(), psmErrorResponse.getErrorMessage())) {
                logger.error("Error message from PSM Create/Update payment session call: {}", e.getMessage());
                if (psmErrorResponse.getErrorCode().equalsIgnoreCase("00041-00001-0-02011") &&  psmErrorResponse.getErrorMessage().equalsIgnoreCase("Invalid session")) {
                    enableSessionRequest.getTransaction().setSessionId(null);
                    enableSessionResponse = paymentSessionAccess.managePaymentSession(enableSessionRequest, callType);
                } else if (psmErrorResponse.getErrorCode().equalsIgnoreCase("00041-00001-0-01010") &&  psmErrorResponse.getErrorMessage().equalsIgnoreCase("Session Inactive")) {
                    enableSessionRequest.getTransaction().setSessionId(null);
                    enableSessionResponse = paymentSessionAccess.managePaymentSession(enableSessionRequest, callType);
                }
            }
        }
        return enableSessionResponse;
    }

    public EnableSessionRequest createPaymentSessionRequest(ShowReservationResponse showReservationResponse, String sessionId, CallType callType) throws AppException {
        basicShowReservationValidation(showReservationResponse, callType);

        final EnableSessionRequest enableSessionRequest = new EnableSessionRequest();

        final Transaction transaction = getExtractedTransaction(sessionId, callType);
        enableSessionRequest.setTransaction(transaction);

        final GuestDetails guestDetails = extractGuestProfile(showReservationResponse.getProfile());
        enableSessionRequest.setGuestDetails(guestDetails);

        final OrderItems orderItems = extractOrderItems(showReservationResponse);
        enableSessionRequest.setOrderItems(orderItems);

        final CardDetails cardDetails = extractCardDetails(showReservationResponse);
        enableSessionRequest.setCardDetails(cardDetails);

        final List<AdditionalAttributes> additionalAttributes = PaymentSessionUtil.extractAdditionalAttributes();
        enableSessionRequest.setAdditionalAttributes(additionalAttributes);

        return enableSessionRequest;
    }

    private void basicShowReservationValidation(ShowReservationResponse showReservationResponse, CallType callType) throws AppException {
        final String isRequired = "is required for " + callType + " enablePaymentSession.";
        if (showReservationResponse == null) {
            logger.error("Mandatory field showReservationResponse " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "The showReservationResponse " + isRequired);
        } else if (Utils.isEmpty(showReservationResponse.getTickets())) {
            logger.error("Mandatory field showReservationResponse.Tickets " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "The showReservationResponse.Tickets " + isRequired);
        } else if (showReservationResponse.getProfile() == null) {
            logger.error("Mandatory field showReservationResponse.Profile " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "The showReservationResponse.Profile " + isRequired);
        } else if (showReservationResponse.getCharges() == null) {
            logger.error("Mandatory field showReservationResponse.Charges " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "The showReservationResponse.Charges " + isRequired);
        } else if (showReservationResponse.getBilling() == null) {
            logger.error("Mandatory field showReservationResponse.Billing " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "Mandatory field showReservationResponse.Billing " + isRequired);
        }
    }

    private Transaction getExtractedTransaction(String sessionId, CallType callType) throws AppException {
        return PaymentSessionUtil.extractTransaction(sessionId, callType);
    }

    public GuestDetails extractGuestProfile(ReservationProfile reservationProfile) {
        final GuestDetails guestProfile = new GuestDetails();
        guestProfile.setMgmId(ThreadContext.getContext().get().getJwtClaim(Claim.MGM_ID));
        guestProfile.setFirstName(reservationProfile.getFirstName());
        guestProfile.setLastName(reservationProfile.getLastName());
        guestProfile.setPhoneNumber(getPhoneNumber(reservationProfile));
        guestProfile.setLoggedIn(!Utils.isEmpty(reservationProfile.getMlifeNo()) && !reservationProfile.getMlifeNo().equals("0"));
        guestProfile.setEmail(reservationProfile.getEmailAddress1());
        guestProfile.setCreated(ThreadContext.getContext().get().getJwtClaim(Claim.MLIFE_ENROLLMENT_DATE));
        if (!Utils.isEmpty(reservationProfile.getAddresses())) {
            final com.mgmresorts.sbs.model.ReservationProfileAddresses sourceAddress = reservationProfile.getAddresses().stream().findFirst().get();
            final Address address = new Address();
            address.setAddress(sourceAddress.getStreet1());
            address.setAddress2(sourceAddress.getStreet2());
            address.setCity(sourceAddress.getCity());
            address.setState(sourceAddress.getState());
            address.setCountry(sourceAddress.getCountry());
            address.setZip(sourceAddress.getPostalCode());
            address.setType(sourceAddress.getType() != null ? sourceAddress.getType().getValue() : null);
            guestProfile.setAddress(address);
        }
        return guestProfile;
    }

    private static String getPhoneNumber(ReservationProfile guestProfile) {
        if (Utils.isEmpty(guestProfile.getPhoneNumbers())) {
            return null;
        }
        final Map<String, String> phoneNumberMap = new HashMap<>();
        for (ReservationProfilePhoneNumbers number : guestProfile.getPhoneNumbers()) {
            if (number.getType() != null && !Utils.isEmpty(number.getNumber())) {
                phoneNumberMap.put(number.getType().getValue(), number.getNumber());
            } else if (number.getType() == null && !Utils.isEmpty(number.getNumber())) {
                phoneNumberMap.put(DEFAULT_PHONE_EMAIL_TYPE, number.getNumber());
            }
        }
        if (phoneNumberMap.containsKey("Mobile")) {
            return phoneNumberMap.get("Mobile");
        } else if (phoneNumberMap.containsKey("Home")) {
            return phoneNumberMap.get("Home");
        } else if (phoneNumberMap.containsKey("Business")) {
            return phoneNumberMap.get("Business");
        } else if (phoneNumberMap.containsKey("Other")) {
            return phoneNumberMap.get("Other");
        } else if (phoneNumberMap.containsKey("Fax")) {
            return phoneNumberMap.get("Fax");
        } else if (phoneNumberMap.containsKey("Pager")) {
            return phoneNumberMap.get("Pager");
        } else if (phoneNumberMap.containsKey(DEFAULT_PHONE_EMAIL_TYPE)) {
            return phoneNumberMap.get(DEFAULT_PHONE_EMAIL_TYPE);
        }
        return null;
    }

    public OrderItems extractOrderItems(ShowReservationResponse showReservationResponse) {
        final OrderItems orderItems = new OrderItems();

        orderItems.setOrderReferenceNumber(showReservationResponse.getConfirmationNumber());

        final List<ItemAuthGroup> itemAuthGroups = extractItemAuthGroups(showReservationResponse);
        orderItems.setItemAuthGroups(itemAuthGroups);

        return orderItems;
    }

    private List<ItemAuthGroup> extractItemAuthGroups(ShowReservationResponse showReservationResponse) {
        final List<ItemAuthGroup> itemAuthGroups = new ArrayList<>();
        final ItemAuthGroup itemAuthGroup = extractItemAuthGroup(showReservationResponse);
        itemAuthGroups.add(itemAuthGroup);
        return itemAuthGroups;
    }

    private ItemAuthGroup extractItemAuthGroup(ShowReservationResponse showReservationResponse) {
        final ItemAuthGroup itemAuthGroup = new ItemAuthGroup();

        itemAuthGroup.setGroupId(UUID.randomUUID().toString());

        final List<ItemsGroupTotal> itemsGroupTotals = extractItemsGroupTotals(showReservationResponse);
        itemAuthGroup.setItemsGroupTotal(itemsGroupTotals);

        itemAuthGroup.setClientId(PSM_CLIENT_ID);

        final List<Item> items = new ArrayList<>();
        final Item item = extractItem(showReservationResponse);
        items.add(item);
        itemAuthGroup.setItems(items);

        return itemAuthGroup;
    }

    private List<ItemsGroupTotal> extractItemsGroupTotals(ShowReservationResponse showReservationResponse) {
        final List<ItemsGroupTotal> itemsGroupTotalList = new ArrayList<>();
        itemsGroupTotalList.add(PaymentSessionUtil.createItemsGroupTotal("authAmount", (double) 0));
        final Double tax = extractTaxTotal(showReservationResponse);
        itemsGroupTotalList.add(PaymentSessionUtil.createItemsGroupTotal("taxTotal", tax));
        return itemsGroupTotalList;
    }

    private static Double extractTaxTotal(ShowReservationResponse showReservationResponse) {
        Double tax = (double) 0;
        if (showReservationResponse.getCharges().getServiceCharge() != null
                && showReservationResponse.getCharges().getServiceCharge().getItemized() != null
                && showReservationResponse.getCharges().getServiceCharge().getItemized().getTax() != null) {
            tax += showReservationResponse.getCharges().getServiceCharge().getItemized().getTax();
        }
        if (showReservationResponse.getCharges().getTransactionFee() != null
                && showReservationResponse.getCharges().getTransactionFee().getItemized() != null
                && showReservationResponse.getCharges().getTransactionFee().getItemized().getTax() != null) {
            tax += showReservationResponse.getCharges().getTransactionFee().getItemized().getTax();
        }
        return tax;
    }

    private Item extractItem(ShowReservationResponse showReservationResponse) {
        final Item item = new Item();
        item.setId(showReservationResponse.getConfirmationNumber());
        item.setConfirmationNumber(showReservationResponse.getConfirmationNumber());
        item.setItemId(showReservationResponse.getShowEventId());
        item.setItemType(SHOW);
        item.setItemName("");
        item.setSeasonId(showReservationResponse.getSeasonId());
        item.setPropertyId(showReservationResponse.getPropertyId());
        item.setPropertyName("");
        item.setDescription(null);
        final Integer numberOfGuests = showReservationResponse.getTickets().size();
        item.setQuantity(numberOfGuests);
        item.setNumberOfGuests(numberOfGuests);
        final List<Seat> seats = extractSeats(showReservationResponse);
        item.setSeat(seats);
        item.delivery(new Delivery());
        item.getDelivery().setMethod(TICKET_DELIVERY_METHOD);
        item.getDelivery().setAmount(0.0);
        item.getDelivery().setType(TICKET_DELIVERY_TYPE);
        final Duration duration = extractItemDuration(showReservationResponse);
        item.setDuration(duration);
        item.setLocationAddress(null);
        final Amount amount = extractItemAmount(showReservationResponse);
        item.setAmount(amount);
        item.setAdditionalFraudParams(null);
        return item;
    }

    private static List<Seat> extractSeats(ShowReservationResponse showReservationResponse) {
        final List<Seat> seats = new ArrayList<>();
        for (ReservationShowTicketDetail ticket : showReservationResponse.getTickets()) {
            final Seat seat = new Seat();
            seat.setSeatNumber(String.valueOf(ticket.getSeat().getSeatNumber()));
            Double price = null;
            if (ticket.getDiscountedPrice() != null) {
                price = ticket.getDiscountedPrice().doubleValue();
            } else if (ticket.getBasePrice() != null) {
                price = ticket.getBasePrice().doubleValue();
            }
            seat.setPrice(price);
            seat.setRow(String.valueOf(ticket.getSeat().getSeatNumber()));
            seat.setSection(ticket.getSeat().getSectionName());
            seats.add(seat);
        }
        return seats;
    }

    void addItemizedChargeToList(List<ItemizedCharges> itemizedChargesList, String name, Double value) {
        final ItemizedCharges itemizedCharges = new ItemizedCharges();
        itemizedCharges.setName(name);
        itemizedCharges.setValue(value);
        itemizedChargesList.add(itemizedCharges);
    }

    private Amount extractItemAmount(ShowReservationResponse showReservationResponse) {
        final Amount amount = new Amount();

        final List<TotalAmount> totalAmounts = extractTotalAmounts(showReservationResponse);
        amount.setTotalAmount(totalAmounts);

        final List<ItemizedCharges> itemizedChargesList = extractItemizedCharges(showReservationResponse);
        amount.setItemizedCharges(itemizedChargesList);

        final List<TFCosts> taxesAndFees = extractTaxesAndFees(showReservationResponse);
        amount.setTaxesAndFees(taxesAndFees);

        final List<TFCosts> discounts = extractDiscounts(showReservationResponse);
        amount.setTaxesAndFees(discounts);

        return amount;
    }

    private List<TFCosts> extractDiscounts(ShowReservationResponse showReservationResponse) {
        final List<TFCosts> discounts = new ArrayList<>();
        final Double programDiscount = showReservationResponse.getCharges().getShowSubtotal() - showReservationResponse.getCharges().getDiscountedSubtotal();
        discounts.add(PaymentSessionUtil.createTFCost(FEE, showReservationResponse.getProgramId(), programDiscount));
        return discounts;
    }

    private List<TotalAmount> extractTotalAmounts(ShowReservationResponse showReservationResponse) {
        final List<TotalAmount> totalAmounts = new ArrayList<>();
        totalAmounts.add(PaymentSessionUtil.createTotalAmount("authAmount", (double) 0));
        totalAmounts.add(PaymentSessionUtil.createTotalAmount("taxTotal", extractTaxTotal(showReservationResponse)));
        totalAmounts.add(PaymentSessionUtil.createTotalAmount("total", showReservationResponse.getCharges().getReservationTotal()));
        return totalAmounts;
    }

    private List<ItemizedCharges> extractItemizedCharges(ShowReservationResponse showReservationResponse) {
        final List<ItemizedCharges> itemizedChargesList = new ArrayList<>();
        final double showTotal = showReservationResponse.getCharges().getReservationTotal();
        addItemizedChargeToList(itemizedChargesList, "showTotal", showTotal);
        addItemizedChargeToList(itemizedChargesList, "let", showReservationResponse.getCharges().getLet());
        addItemizedChargeToList(itemizedChargesList, "discountedSubtotal", showReservationResponse.getCharges().getDiscountedSubtotal());
        return itemizedChargesList;
    }

    private List<TFCosts> extractTaxesAndFees(ShowReservationResponse showReservationResponse) {
        final List<TFCosts> taxesAndFees = new ArrayList<>();
        final Double taxes = showReservationResponse.getCharges().getLet()
                + showReservationResponse.getCharges().getTransactionFee().getItemized().getTax()
                + showReservationResponse.getCharges().getServiceCharge().getItemized().getTax();
        taxesAndFees.add(PaymentSessionUtil.createTFCost(TAX, "taxes", taxes));
        taxesAndFees.add(PaymentSessionUtil.createTFCost(TAX, "serviceChargeAndTaxes", showReservationResponse.getCharges().getServiceCharge().getAmount()));
        taxesAndFees.add(PaymentSessionUtil.createTFCost(TAX, "transactionFeeAndTaxes", showReservationResponse.getCharges().getTransactionFee().getAmount()));

        taxesAndFees.add(PaymentSessionUtil.createTFCost(FEE, "deliveryFee", showReservationResponse.getCharges().getDeliveryFee()));

        return taxesAndFees;
    }

    private CardDetails extractCardDetails(ShowReservationResponse showReservationResponse) {
        final BillingInfoPayment payment = showReservationResponse.getBilling().getPayment();
        
        if (payment == null) {
            return null;
        }
        
        final CardDetails cardDetails = new CardDetails();
        cardDetails.setGatewayId(GATEWAY_ID);
        cardDetails.setTenderType(CREDIT_CARD);
        cardDetails.setIssuerType(payment.getType());
        cardDetails.setTenderDisplay(payment.getPaymentToken());
        cardDetails.setCreditCardHolderName(payment.getFirstName() + " " + payment.getLastName());
        cardDetails.setMgmToken(payment.getPaymentToken());
        final String expiryMonth = PaymentSessionUtil.getExpiryMonth(payment.getExpiry());
        cardDetails.setExpiryMonth(expiryMonth);
        final String expiryYear = PaymentSessionUtil.getExpiryYear(payment.getExpiry());
        cardDetails.setExpiryYear(expiryYear);
        final BillingAddress billingAddress = extractBillingAddress(showReservationResponse);
        cardDetails.setBillingAddress(billingAddress);
        return cardDetails;
    }
    
    private static BillingAddress extractBillingAddress(ShowReservationResponse showReservationResponse) {
        final BillingInfoAddress address = showReservationResponse.getBilling().getAddress();
        
        if (address == null) {
            return null;
        }
        
        final BillingAddress billingAddress = new BillingAddress();
        billingAddress.setAddress(address.getStreet1());
        billingAddress.setAddress2(address.getStreet2());
        billingAddress.setCity(address.getCity());
        billingAddress.setState(address.getState());
        billingAddress.setCountry(address.getCountry());
        billingAddress.setPostalCode(address.getPostalCode());
        return billingAddress;
    }

    private static Duration extractItemDuration(ShowReservationResponse showReservationResponse) {
        final Duration duration = new Duration();
        duration.setStartDate(showReservationResponse.getEventDate());
        duration.setStartTime(showReservationResponse.getEventTime());
        duration.setEndDate(showReservationResponse.getEventDate());
        duration.setEndTime(SHOW_END_TIME);
        return duration;
    }
}
