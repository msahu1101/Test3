package com.mgmresorts.order.backend.handler.impl;

import java.math.BigDecimal;
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
import com.mgmresorts.order.backend.handler.IPaymentSessionRoomHandler;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.order.utils.PaymentSessionUtil;
import com.mgmresorts.psm.model.AdditionalAttributes;
import com.mgmresorts.psm.model.Address;
import com.mgmresorts.psm.model.Amount;
import com.mgmresorts.psm.model.BillingAddress;
import com.mgmresorts.psm.model.CardDetails;
import com.mgmresorts.psm.model.Duration;
import com.mgmresorts.psm.model.EnableSessionRequest;
import com.mgmresorts.psm.model.EnableSessionResponse;
import com.mgmresorts.psm.model.GuestDetails;
import com.mgmresorts.psm.model.Item;
import com.mgmresorts.psm.model.ItemAuthGroup;
import com.mgmresorts.psm.model.ItemizedCharges;
import com.mgmresorts.psm.model.ItemsGroupTotal;
import com.mgmresorts.psm.model.OrderItems;
import com.mgmresorts.psm.model.SessionError;
import com.mgmresorts.psm.model.TFCosts;
import com.mgmresorts.psm.model.TotalAmount;
import com.mgmresorts.psm.model.Transaction;
import com.mgmresorts.rbs.model.ReservationProfile;
import com.mgmresorts.rbs.model.ReservationProfileAddresses;
import com.mgmresorts.rbs.model.ReservationProfilePhoneNumbers;
import com.mgmresorts.rbs.model.RoomBillingDetailsAddress;
import com.mgmresorts.rbs.model.RoomBillingDetailsResponsePayment;
import com.mgmresorts.rbs.model.RoomReservationResponse;

public class PaymentSessionRoomHandler implements IPaymentSessionRoomHandler {

    public static final String DEFAULT_PHONE_EMAIL_TYPE = "Other";
    public static final String GATEWAY_ID = "FPY7";
    public static final String CREDIT_CARD = "Credit Card";
    public static final String ROOM = "Room";
    public static final String TAX = "TAX";
    public static final String FEE = "FEE";
    public static final String ROOM_CHECKIN_TIME = "3:00 PM";
    public static final String ROOM_CHECKOUT_TIME = "11:00 AM";
    private final Logger logger = Logger.get(PaymentSessionRoomHandler.class);
    private final JSonMapper mapper = new JSonMapper();

    @Inject
    private IPaymentSessionAccess paymentSessionAccess;

    @Override
    public EnableSessionResponse managePaymentSessionForRoomReservation(RoomReservationResponse roomReservationResponse,
                                                                        String sessionId, CallType callType) throws AppException {
        final EnableSessionRequest enableSessionRequest = createPaymentSessionRequest(roomReservationResponse, sessionId, callType);
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

    public EnableSessionRequest createPaymentSessionRequest(RoomReservationResponse roomReservationResponse, final String sessionId, final CallType callType) throws AppException {
        basicGetRoomReservationValidation(roomReservationResponse, callType);

        final EnableSessionRequest enableSessionRequest = new EnableSessionRequest();

        final Transaction transaction = getExtractedTransaction(sessionId, callType);
        enableSessionRequest.setTransaction(transaction);

        final GuestDetails guestDetails = extractGuestProfile(roomReservationResponse.getProfile());
        enableSessionRequest.setGuestDetails(guestDetails);

        final OrderItems orderItems = extractOrderItems(roomReservationResponse);
        enableSessionRequest.setOrderItems(orderItems);

        final CardDetails cardDetails = extractCardDetails(roomReservationResponse);
        enableSessionRequest.setCardDetails(cardDetails);

        final List<AdditionalAttributes> additionalAttributes = PaymentSessionUtil.extractAdditionalAttributes();
        enableSessionRequest.setAdditionalAttributes(additionalAttributes);

        return enableSessionRequest;
    }

    private Transaction getExtractedTransaction(String sessionId, CallType callType) throws AppException {
        try {
            return PaymentSessionUtil.extractTransaction(sessionId, callType);
        } catch (Exception e) {
            logger.error("Unable to resolve URI from origin header");
            throw new AppException(ApplicationError.INVALID_REQUEST, "Origin header is not a valid URI");
        }
    }

    private void basicGetRoomReservationValidation(RoomReservationResponse roomReservationResponse, CallType callType) throws AppException {
        final String isRequired = "is required for " + callType + " enablePaymentSession.";
        if (roomReservationResponse == null) {
            logger.error("RoomReservationResponse " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "The RoomReservationResponse " + isRequired);
        } else if (roomReservationResponse.getTripDetails() == null) {
            logger.error("Mandatory field roomReservationResponse.tripDetails " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "The roomReservationResponse.tripDetails " + isRequired);
        } else if (roomReservationResponse.getProfile() == null) {
            logger.error("Mandatory field roomReservationResponse.Profile " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "The roomReservationResponse.Profile " + isRequired);
        } else if (roomReservationResponse.getRatesSummary() == null) {
            logger.error("Mandatory field roomReservationResponse.RatesSummary " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "The roomReservationResponse.RatesSummary " + isRequired);
        } else if (Utils.isEmpty(roomReservationResponse.getBilling())) {
            logger.error("Mandatory field roomReservationResponse.Billing " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "Mandatory field roomReservationResponse.Billing " + isRequired);
        } else if (roomReservationResponse.getBilling().get(0).getPayment() == null) {
            logger.error("Mandatory field roomReservationResponse.Billing.Payment " + isRequired);
            throw new AppException(ApplicationError.INVALID_REQUEST, "Mandatory field roomReservationResponse.Billing.Payment " + isRequired);
        }
    }


    public GuestDetails extractGuestProfile(ReservationProfile reservationProfile) {
        final GuestDetails guestDetails = new GuestDetails();
        guestDetails.setMgmId(ThreadContext.getContext().get().getJwtClaim(Claim.MGM_ID));
        guestDetails.setFirstName(reservationProfile.getFirstName());
        guestDetails.setLastName(reservationProfile.getLastName());
        guestDetails.setPhoneNumber(getPhoneNumber(reservationProfile));
        guestDetails.setLoggedIn(reservationProfile.getMlifeNo() != null && !reservationProfile.getMlifeNo().equals(BigDecimal.ZERO));
        guestDetails.setEmail(reservationProfile.getEmailAddress1());
        guestDetails.setCreated(ThreadContext.getContext().get().getJwtClaim(Claim.MLIFE_ENROLLMENT_DATE));
        if (!Utils.isEmpty(reservationProfile.getAddresses())) {
            final ReservationProfileAddresses sourceAddress = reservationProfile.getAddresses().stream().findFirst().get();
            final Address address = new Address();
            address.setAddress(sourceAddress.getStreet1());
            address.setAddress2(sourceAddress.getStreet2());
            address.setCity(sourceAddress.getCity());
            address.setState(sourceAddress.getState());
            address.setCountry(sourceAddress.getCountry());
            address.setZip(sourceAddress.getPostalCode());
            address.setType(sourceAddress.getType() != null ? sourceAddress.getType().getValue() : null);
            guestDetails.setAddress(address);
        }
        return guestDetails;
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
    
    public OrderItems extractOrderItems(RoomReservationResponse roomReservationResponse) {
        final OrderItems orderItems = new OrderItems();

        orderItems.setOrderReferenceNumber(roomReservationResponse.getConfirmationNumber());

        final List<ItemAuthGroup> itemAuthGroups = extractItemAuthGroups(roomReservationResponse);
        orderItems.setItemAuthGroups(itemAuthGroups);

        return orderItems;
    }


    private List<ItemAuthGroup> extractItemAuthGroups(RoomReservationResponse roomReservationResponse) {
        final List<ItemAuthGroup> itemAuthGroups = new ArrayList<>();
        final ItemAuthGroup itemAuthGroup = extractItemAuthGroup(roomReservationResponse);
        itemAuthGroups.add(itemAuthGroup);
        return itemAuthGroups;
    }


    private ItemAuthGroup extractItemAuthGroup(RoomReservationResponse roomReservationResponse) {
        final ItemAuthGroup itemAuthGroup = new ItemAuthGroup();

        itemAuthGroup.setGroupId(UUID.randomUUID().toString());

        final List<ItemsGroupTotal> itemsGroupTotals = extractItemsGroupTotals(roomReservationResponse);
        itemAuthGroup.setItemsGroupTotal(itemsGroupTotals);

        itemAuthGroup.setClientId(PSM_CLIENT_ID);

        final List<Item> items = new ArrayList<>();
        final Item item = extractItem(roomReservationResponse);
        items.add(item);
        itemAuthGroup.setItems(items);

        return itemAuthGroup;
    }

    private List<ItemsGroupTotal> extractItemsGroupTotals(RoomReservationResponse roomReservationResponse) {
        final List<ItemsGroupTotal> itemsGroupTotalList = new ArrayList<>();
        itemsGroupTotalList.add(PaymentSessionUtil.createItemsGroupTotal("authAmount", roomReservationResponse.getRatesSummary().getChangeInDeposit()));
        itemsGroupTotalList.add(PaymentSessionUtil.createItemsGroupTotal("taxTotal", roomReservationResponse.getRatesSummary().getRoomChargeTax()));
        return itemsGroupTotalList;
    }


    private Item extractItem(RoomReservationResponse roomReservationResponse) {
        final Item item = new Item();
        item.setId(roomReservationResponse.getConfirmationNumber());
        item.setConfirmationNumber(roomReservationResponse.getConfirmationNumber());
        item.setItemId(roomReservationResponse.getRoomTypeId());
        item.setItemType(ROOM);
        item.setItemName("");
        item.setSeasonId(null);
        item.setPropertyId(roomReservationResponse.getPropertyId());
        item.setPropertyName("");
        item.setDescription(null);
        final Integer numberOfGuests = roomReservationResponse.getTripDetails().getNumAdults() + roomReservationResponse.getTripDetails().getNumChildren();
        item.setNumberOfGuests(numberOfGuests);
        item.setQuantity(numberOfGuests);
        item.setSeat(null);
        item.delivery(null);
        final Duration duration = extractItemDuration(roomReservationResponse);
        item.setDuration(duration);
        item.setLocationAddress(null);
        final Amount amount = extractItemAmount(roomReservationResponse);
        item.setAmount(amount);
        item.setAdditionalFraudParams(null);
        return item;
    }


    void addItemizedChargeToList(List<ItemizedCharges> itemizedChargesList, String name, Double value) {
        final ItemizedCharges itemizedCharges = new ItemizedCharges();
        itemizedCharges.setName(name);
        itemizedCharges.setValue(value);
        itemizedChargesList.add(itemizedCharges);
    }

    private Amount extractItemAmount(RoomReservationResponse roomReservationResponse) {
        final Amount amount = new Amount();

        final List<TotalAmount> totalAmounts = extractTotalAmounts(roomReservationResponse);
        amount.setTotalAmount(totalAmounts);

        final List<ItemizedCharges> itemizedChargesList = extractItemizedCharges(roomReservationResponse);
        amount.setItemizedCharges(itemizedChargesList);

        final List<TFCosts> taxesAndFees = extractTaxesAndFees(roomReservationResponse);
        amount.setTaxesAndFees(taxesAndFees);

        final List<TFCosts> discounts = extractDiscounts(roomReservationResponse);
        amount.setTaxesAndFees(discounts);

        return amount;
    }

    private List<TotalAmount> extractTotalAmounts(RoomReservationResponse roomReservationResponse) {
        final List<TotalAmount> totalAmounts = new ArrayList<>();
        totalAmounts.add(PaymentSessionUtil.createTotalAmount("authAmount", roomReservationResponse.getRatesSummary().getChangeInDeposit()));
        totalAmounts.add(PaymentSessionUtil.createTotalAmount("taxTotal", roomReservationResponse.getRatesSummary().getRoomChargeTax()));
        totalAmounts.add(PaymentSessionUtil.createTotalAmount("total", roomReservationResponse.getRatesSummary().getReservationTotal()));
        return totalAmounts;
    }


    private List<ItemizedCharges> extractItemizedCharges(RoomReservationResponse roomReservationResponse) {
        final List<ItemizedCharges> itemizedChargesList = new ArrayList<>();
        final double roomTotal = roomReservationResponse.getRatesSummary().getRoomChargeTax() + roomReservationResponse.getRatesSummary().getDiscountedSubtotal();
        addItemizedChargeToList(itemizedChargesList, "roomTotal", roomTotal);
        addItemizedChargeToList(itemizedChargesList, "depositDue", roomReservationResponse.getRatesSummary().getDepositDue());
        addItemizedChargeToList(itemizedChargesList, "totalRoomCharges", roomReservationResponse.getRatesSummary().getDiscountedSubtotal());
        return itemizedChargesList;
    }


    private List<TFCosts> extractTaxesAndFees(RoomReservationResponse roomReservationResponse) {
        final List<TFCosts> taxesAndFees = new ArrayList<>();

        taxesAndFees.add(PaymentSessionUtil.createTFCost(FEE, "casinoSurcharge", roomReservationResponse.getRatesSummary().getCasinoSurcharge()));
        taxesAndFees.add(PaymentSessionUtil.createTFCost(FEE, "occupancyFee", roomReservationResponse.getRatesSummary().getOccupancyFee()));
        taxesAndFees.add(PaymentSessionUtil.createTFCost(FEE, "resortFee", roomReservationResponse.getRatesSummary().getResortFee()));
        taxesAndFees.add(PaymentSessionUtil.createTFCost(FEE, "resortFeePerNight", roomReservationResponse.getRatesSummary().getResortFeePerNight()));
        taxesAndFees.add(PaymentSessionUtil.createTFCost(FEE, "tourismFee", roomReservationResponse.getRatesSummary().getTourismFee()));

        taxesAndFees.add(PaymentSessionUtil.createTFCost(TAX, "casinoSurchargeAndTax", roomReservationResponse.getRatesSummary().getCasinoSurchargeAndTax()));
        taxesAndFees.add(PaymentSessionUtil.createTFCost(TAX, "resortFeeAndTax", roomReservationResponse.getRatesSummary().getResortFeeAndTax()));
        taxesAndFees.add(PaymentSessionUtil.createTFCost(TAX, "roomChargeTax", roomReservationResponse.getRatesSummary().getRoomChargeTax()));
        taxesAndFees.add(PaymentSessionUtil.createTFCost(TAX, "tourismFeeAndTax", roomReservationResponse.getRatesSummary().getTourismFeeAndTax()));
        return taxesAndFees;
    }

    private List<TFCosts> extractDiscounts(RoomReservationResponse roomReservationResponse) {
        final List<TFCosts> discounts = new ArrayList<>();
        final Double programDiscount = roomReservationResponse.getRatesSummary().getProgramDiscount();
        discounts.add(PaymentSessionUtil.createTFCost(FEE, roomReservationResponse.getProgramId(), programDiscount));
        return discounts;
    }


    private CardDetails extractCardDetails(RoomReservationResponse roomReservationResponse) {
        final RoomBillingDetailsResponsePayment payment = roomReservationResponse.getBilling().get(0).getPayment();
        
        if (payment == null) {
            return null;
        }
        
        final CardDetails cardDetails = new CardDetails();
        cardDetails.setGatewayId(GATEWAY_ID);
        cardDetails.setTenderType(CREDIT_CARD);
        cardDetails.setCreditCardHolderName(payment.getCardHolder());
        cardDetails.setIssuerType(payment.getType() != null ? payment.getType().getValue() : null);
        cardDetails.setTenderDisplay(payment.getMaskedNumber());
        final String expiryMonth = PaymentSessionUtil.getExpiryMonth(payment.getExpiry());
        cardDetails.setExpiryMonth(expiryMonth);
        final String expiryYear = PaymentSessionUtil.getExpiryYear(payment.getExpiry());
        cardDetails.setExpiryYear(expiryYear);
        final BillingAddress billingAddress = extractBillingAddress(roomReservationResponse);
        cardDetails.setBillingAddress(billingAddress);
        cardDetails.setMgmToken(payment.getCcToken());
        return cardDetails;
    }

    private static BillingAddress extractBillingAddress(RoomReservationResponse roomReservationResponse) {
        final RoomBillingDetailsAddress address = roomReservationResponse.getBilling().get(0).getAddress();
        
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

    private static Duration extractItemDuration(RoomReservationResponse roomReservationResponse) {
        final Duration duration = new Duration();
        duration.setStartDate(roomReservationResponse.getTripDetails().getCheckInDate());
        duration.setStartTime(ROOM_CHECKIN_TIME);
        duration.setEndDate(roomReservationResponse.getTripDetails().getCheckOutDate());
        duration.setEndTime(ROOM_CHECKOUT_TIME);
        return duration;
    }
}
