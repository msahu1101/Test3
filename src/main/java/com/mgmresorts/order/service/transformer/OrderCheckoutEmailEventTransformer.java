package com.mgmresorts.order.service.transformer;

import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.dto.OfferComponent;
import com.mgmresorts.order.dto.PriceDetails;
import com.mgmresorts.order.dto.PropertyComponent;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.Order;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.dto.services.Version;
import com.mgmresorts.rbs.model.CalculateRoomChargesResponse;
import com.mgmresorts.rbs.model.CreateRoomReservationResponse;
import com.mgmresorts.rbs.model.RatesSummary;
import com.mgmresorts.rtc.AddressElement;
import com.mgmresorts.rtc.AssetElement;
import com.mgmresorts.rtc.ChargeDetailsElement;
import com.mgmresorts.rtc.DiningReservation;
import com.mgmresorts.rtc.GuestDetailsElement;
import com.mgmresorts.rtc.ItemizedElement;
import com.mgmresorts.rtc.Metadata;
import com.mgmresorts.rtc.OfferComponentElement;
import com.mgmresorts.rtc.PackageInclusionsElement;
import com.mgmresorts.rtc.PackagePricingDetails;
import com.mgmresorts.rtc.PropertyComponentElement;
import com.mgmresorts.rtc.PropertyDetailsElement;
import com.mgmresorts.rtc.ReservationsElement;
import com.mgmresorts.rtc.RoomDetailsElement;
import com.mgmresorts.rtc.RoomRequestElement;
import com.mgmresorts.rtc.RoomReservation;
import com.mgmresorts.rtc.RoomReservation.OperationTypeElement;
import com.mgmresorts.rtc.RtcReservationEvent;
import com.mgmresorts.rtc.ReservationEventBody;
import com.mgmresorts.rtc.ReservationEventBody.ReservationStatusTypeElement;
import com.mgmresorts.rtc.PackageConfigDetails;
import com.mgmresorts.rtc.SeatDetailsElement;
import com.mgmresorts.rtc.ServiceChargeElement;
import com.mgmresorts.rtc.ShowReservation;
import com.mgmresorts.rtc.PackageComponentDetailsElement;
import com.mgmresorts.order.dto.AddOnComponent;
import com.mgmresorts.rtc.TransactionFeeElement;
import com.mgmresorts.rtc.PackageConfigDetails.SeatMapOptions;
import com.mgmresorts.sbs.model.Rates;
import com.mgmresorts.sbs.model.ShowChargesResponse;
import com.mgmresorts.sbs.model.ShowReservationResponse;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.ShowTicket;
import com.mgmresorts.order.entity.OrderEvent;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


public class OrderCheckoutEmailEventTransformer implements ITransformer<OrderEvent, RtcReservationEvent> {
    private final JSonMapper mapper = new JSonMapper();

    @Override
    public RtcReservationEvent toRight(OrderEvent left) {
        RtcReservationEvent rtcReservationEvent = null;

        if (left.getOrder() != null && left.getOrder().getType() != null && left.getOrder().getVersion() != null) {
            rtcReservationEvent = new RtcReservationEvent();
            rtcReservationEvent.setReservationType(left.getOrder().getType().equals(Type.GLOBAL) ? RtcReservationEvent.ReservationType.OMNI
                            : RtcReservationEvent.ReservationType.PACKAGE);
            rtcReservationEvent.setReservationVersion(RtcReservationEvent.ReservationVersion.fromValue(left.getOrder().getVersion().value()));
            rtcReservationEvent.setReservationEvent(toReservationEventBody(left));
        }

        return rtcReservationEvent;
    }

    public ReservationEventBody toReservationEventBody(OrderEvent in) {
        final ReservationEventBody reservationEvent = new ReservationEventBody();
        reservationEvent.setId(in.getOrder().getId());
        if (in.getOrder().getStatus().equals(Order.Status.SUCCESS)
                || in.getOrder().getStatus().equals(Order.Status.PARTIAL)) {
            reservationEvent.setStatus(ReservationStatusTypeElement.fromValue(in.getOrder().getStatus().value()));
        }
        reservationEvent.setMetadata(new Metadata());
        reservationEvent.getMetadata().setDefaultChannel(Metadata.ChannelEnum.EMAIL);
        reservationEvent.getMetadata().setFallbackChannel(Metadata.ChannelEnum.EMAIL);
        reservationEvent.setReservations(toReservations(in));
        if (in.getOrder().getType().equals(Type.PACKAGE) && in.getOrder().getVersion().equals(Version.V2)) {
            reservationEvent.setPackageConfigDetails(toPackageConfigDetails(in.getOrder(), in.getCheckoutRequest()));
        }
        
        return reservationEvent;
    }

    public PackageConfigDetails toPackageConfigDetails(Order order, CheckoutRequest checkoutRequest) {
        final com.mgmresorts.order.dto.PackageConfigDetails configDetails = order.getPackageConfigDetails();
        if (configDetails == null) {
            return null;
        }
        final PackageConfigDetails packageConfigDetails = new PackageConfigDetails();
        packageConfigDetails.setPackageCategoryId(configDetails.getPackageCategoryId());
        packageConfigDetails.setPackagePriceBreakdownType(configDetails.getPackagePriceBreakdown());
        packageConfigDetails.setPackageName(configDetails.getPackageName());
        packageConfigDetails.setSeatMapOptions(!Utils.isEmpty(configDetails.getSeatMapOptions()) ? SeatMapOptions.fromValue(configDetails.getSeatMapOptions()) : null);
        packageConfigDetails.setPackageInclusions(toPackageInclusions(configDetails.getPackagePricingDetails().getPackageInclusions()));
        packageConfigDetails.setPackagePricingDetails(toPackagePricingDetails(configDetails.getPackagePricingDetails(), order.getPriceDetails(),
                !Utils.isEmpty(checkoutRequest.getBillings())
                        ? checkoutRequest.getBillings().get(0).getPayment().getType().toString()
                        : null,
                !Utils.isEmpty(checkoutRequest.getBillings())
                        ? checkoutRequest.getBillings().get(0).getPayment().getCcToken()
                                .substring(checkoutRequest.getBillings().get(0).getPayment().getCcToken().length() - 4)
                        : null));
        final List<PackageComponentDetailsElement> packageComponentDetails = toPackageComponentDetailsElements(configDetails);
        packageConfigDetails.setPackageComponentDetails(packageComponentDetails);
        
        return packageConfigDetails;
    }

    private static List<PackageComponentDetailsElement> toPackageComponentDetailsElements(com.mgmresorts.order.dto.PackageConfigDetails configDetails) {
        if (Utils.isEmpty(configDetails.getPackageComponentDetails())) {
            return null;
        }

        final List<PackageComponentDetailsElement> packageComponentDetails = new ArrayList<>();
        for (com.mgmresorts.order.dto.PackageComponentDetail component : configDetails.getPackageComponentDetails()) {
            final PackageComponentDetailsElement packageComponentDetailsElement = new PackageComponentDetailsElement();
            packageComponentDetailsElement.setId(component.getId());
            packageComponentDetailsElement.setDescription(component.getDescription());
            packageComponentDetailsElement.setLongDescription(component.getLongDescription());
            packageComponentDetailsElement.setShortDescription(component.getShortDescription());
            packageComponentDetailsElement.setTaxRate(component.getTaxRate());
            packageComponentDetailsElement.setNightlyCharge(component.getNightlyCharge());
            packageComponentDetailsElement.setPricingApplied(component.getPricingApplied());
            packageComponentDetailsElement.setRatePlanCode(component.getRatePlanCode());
            packageComponentDetailsElement.setRatePlanName(component.getRatePlanName());
            packageComponentDetailsElement.setAmtAfterTax(component.getAmtAftTax());
            packageComponentDetailsElement.setCode(component.getCode());
            packageComponentDetailsElement.setStart(component.getStart());
            packageComponentDetailsElement.setEnd(component.getEnd());
            packageComponentDetails.add(packageComponentDetailsElement);
        }

        return packageComponentDetails;
    }

    public PackagePricingDetails toPackagePricingDetails(com.mgmresorts.order.dto.PackagePricingDetails pricingDetails,
                                                         PriceDetails orderPriceDetails, String paymentMethod, String lastFourCC) {
        if (pricingDetails == null || orderPriceDetails == null) {
            return null;
        }
        final PackagePricingDetails packagePricingDetails = new PackagePricingDetails();
        packagePricingDetails.setPackageStartingPrice(pricingDetails.getPackageStartingPrice());
        packagePricingDetails.setPackageTotal(pricingDetails.getPackageTotal());
        packagePricingDetails.setPackageSubTotal(pricingDetails.getPackageBaseTotal());
        packagePricingDetails.setRoomModification(pricingDetails.getRoomModification());
        packagePricingDetails.setShowModification(pricingDetails.getShowModification());
        packagePricingDetails.setRoomTotal(pricingDetails.getRoomTotal());
        packagePricingDetails.setShowTotal(pricingDetails.getShowTotal());
        packagePricingDetails.setAmountPaid(orderPriceDetails.getTotalDeposit());
        packagePricingDetails.setAmountDue(orderPriceDetails.getTotalBalanceDue());
        packagePricingDetails.setPaymentMethod(paymentMethod);
        packagePricingDetails.setLastFourCC(lastFourCC);
        
        return packagePricingDetails;
    }

    public List<ReservationsElement> toReservations(OrderEvent in) {
        final List<ReservationsElement> reservations = new ArrayList<>();

        for (OrderLineItem orderLineItem : in.getOrder().getOrderLineItems()) {
            if (orderLineItem.getStatus().equals(OrderLineItem.Status.SUCCESS)
                    || (in.getOrder().getType().equals(Type.PACKAGE) && in.getOrder().getStatus().equals(Order.Status.PARTIAL)
                            && (orderLineItem.getStatus().equals(OrderLineItem.Status.FAILURE)
                                    || orderLineItem.getStatus().equals(OrderLineItem.Status.PAYMENT_FAILURE)))) {
                final CartLineItem cartLineItem = in.getCart().getCartLineItems().stream().filter(cli ->
                        cli.getCartLineItemId().equalsIgnoreCase(orderLineItem.getCartLineItemId())).findFirst().get();
                final ReservationsElement reservationsElement = new ReservationsElement();
                switch (orderLineItem.getProductType()) {
                    case ROOM:
                        reservationsElement.setRoomReservation(toRoomReservation(in.getCheckoutRequest(), orderLineItem, cartLineItem));
                        break;
                    case SHOW:
                        reservationsElement.setShowReservation(toShowReservation(in.getCheckoutRequest(), orderLineItem, cartLineItem, in.getOrder()));
                        break;
                    case DINING:
                        reservationsElement.setDiningReservation(toDiningReservation(in.getCheckoutRequest(), orderLineItem, cartLineItem));
                        break;
                    default:
                        break;
                }
                reservations.add(reservationsElement);
            }
        }
        
        return reservations;
    }

    public RoomReservation toRoomReservation(CheckoutRequest checkoutRequest, OrderLineItem orderLineItem, CartLineItem cartLineItem) {
        final RoomReservation roomReservation = new RoomReservation();
        final CalculateRoomChargesResponse roomCharge = mapper.readValue(cartLineItem.getContent(), CalculateRoomChargesResponse.class);
        roomReservation.setId(orderLineItem.getOrderLineItemId());
        roomReservation.setOperation(OperationTypeElement.CREATE);
        if (orderLineItem.getStatus().equals(OrderLineItem.Status.SUCCESS)
                || orderLineItem.getStatus().equals(OrderLineItem.Status.FAILURE)
                || orderLineItem.getStatus().equals(OrderLineItem.Status.PAYMENT_FAILURE)) {
            roomReservation.setStatus(ReservationStatusTypeElement.fromValue(orderLineItem.getStatus().value()));
        }
        roomReservation.setConfirmationNumber(orderLineItem.getConfirmationNumber());
        roomReservation.setOfferDescription(cartLineItem.getItemSelectionDetails().getRoomSelectionDetails().getProgramDescription());
        roomReservation.setOfferName(cartLineItem.getItemSelectionDetails().getRoomSelectionDetails().getProgramName());
        roomReservation.setPrePromotionalCopy("yes");
        roomReservation.setArrivalDate(roomCharge.getTripDetails().getCheckInDate());
        roomReservation.setDepartureDate(roomCharge.getTripDetails().getCheckOutDate());
        roomReservation.setStayDuration(cartLineItem.getItemSelectionDetails().getRoomSelectionDetails().getNumberOfNights());
        roomReservation.setPropertyDetails(toPropertyDetails(cartLineItem));
        roomReservation.setNumberOfGuests(roomCharge.getTripDetails().getNumAdults() + roomCharge.getTripDetails().getNumChildren());
        roomReservation.setGuestDetails(toGuestDetails(checkoutRequest.getGuestProfile()));
        roomReservation.setRoomDetails(toRoomDetails(orderLineItem,cartLineItem));
        roomReservation.setReservationPhone(cartLineItem.getItemSelectionDetails().getRoomSelectionDetails().getReservationPhoneNumber());
        roomReservation.setChargeDetails(toRoomChargeDetails(orderLineItem));
        roomReservation.setReservationConciergeEmail(cartLineItem.getItemSelectionDetails().getRoomSelectionDetails().getReservationConciergeEmail());

        return roomReservation;
    }

    public ShowReservation toShowReservation(CheckoutRequest checkoutRequest, OrderLineItem orderLineItem, CartLineItem cartLineItem, Order order) {
        final ShowReservation showReservation = new ShowReservation();
        showReservation.setId(orderLineItem.getOrderLineItemId());
        showReservation.setOperation(OperationTypeElement.CREATE);
        if (orderLineItem.getStatus().equals(OrderLineItem.Status.SUCCESS)
                || orderLineItem.getStatus().equals(OrderLineItem.Status.FAILURE)
                || orderLineItem.getStatus().equals(OrderLineItem.Status.PAYMENT_FAILURE)) {
            showReservation.setStatus(ReservationStatusTypeElement.fromValue(orderLineItem.getStatus().value()));
        }
        showReservation.setConfirmationNumber(orderLineItem.getConfirmationNumber());
        showReservation.setPropertyDetails(toPropertyDetails(cartLineItem));
        showReservation.setShowDate(cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getEventDate().toString());
        if (cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getAllDayEventShowTimeDetails() != null
                && cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getAllDayEventShowTimeDetails().getAllDayEvent()) {
            showReservation.setAllDayEventFlag(true);
            showReservation.setShowEventOpeningTime(cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getAllDayEventShowTimeDetails().getOpeningTime());
            showReservation.setShowEventClosingTime(cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getAllDayEventShowTimeDetails().getClosingTime());
        } else {
            showReservation.setAllDayEventFlag(false);
        }
        if (order.getVersion().equals(Version.V2) && order.getType().equals(Type.PACKAGE) && order.getPackageConfigDetails() != null
            && order.getPackageConfigDetails().getPackagePricingDetails() != null && order.getPackageConfigDetails().getPackagePricingDetails().getIsMultiDayEvent() != null) {
            showReservation.setMultiDayEvent(order.getPackageConfigDetails().getPackagePricingDetails().getIsMultiDayEvent());
            if (showReservation.getMultiDayEvent()) {
                showReservation.setShowStartDate(StringUtils.isNotBlank(order.getPackageConfigDetails().getPackagePricingDetails().getEventStartDate())
                        ? order.getPackageConfigDetails().getPackagePricingDetails().getEventStartDate() : null);
                showReservation.setShowEndDate(StringUtils.isNotBlank(order.getPackageConfigDetails().getPackagePricingDetails().getEventEndDate())
                        ? order.getPackageConfigDetails().getPackagePricingDetails().getEventEndDate() : null);
            }
        }
        showReservation.setShowName(cartLineItem.getProductName());
        showReservation.setShowImage(!Utils.isEmpty(cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getShowImage())
                        ? URI.create(cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getShowImage())
                        : null);
        showReservation.setShowVenue(cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getShowVenueName());
        showReservation.setAdditionalText(cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getAdditionalText());
        showReservation.setGuestDetails(toGuestDetails(checkoutRequest.getGuestProfile()));
        showReservation.setDeliveryMethod(orderLineItem.getSelectedDeliveryMethod() != null ? orderLineItem.getSelectedDeliveryMethod().getDescription() : null);
        showReservation.setPhoneNumber(cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getReservationPhoneNumber());
        showReservation.setTicketCount(orderLineItem.getNumberOfTickets());
        showReservation.setChargeDetails(toShowChargeDetails(orderLineItem));
        showReservation.setShowSeats(toShowSeats(cartLineItem));
        showReservation.setShowTime(cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getEventTime().toString());
        
        return showReservation;
    }

    public DiningReservation toDiningReservation(CheckoutRequest checkoutRequest, OrderLineItem orderLineItem, CartLineItem cartLineItem) {
        final DiningReservation diningReservation = new DiningReservation();

        diningReservation.setId(orderLineItem.getOrderLineItemId());
        if (orderLineItem.getStatus().equals(OrderLineItem.Status.SUCCESS)
                || orderLineItem.getStatus().equals(OrderLineItem.Status.FAILURE)
                || orderLineItem.getStatus().equals(OrderLineItem.Status.PAYMENT_FAILURE)) {
            diningReservation.setStatus(ReservationStatusTypeElement.fromValue(orderLineItem.getStatus().value()));
        }
        diningReservation.setReservationDate(cartLineItem.getItemSelectionDetails().getDiningSelectionDetails().getReservationDate().toString());
        diningReservation.setReservationTime(cartLineItem.getItemSelectionDetails().getDiningSelectionDetails().getReservationTime().toString());
        diningReservation.setRestaurantName(cartLineItem.getProductName());
        diningReservation.setRestaurantImage(!Utils.isEmpty(cartLineItem.getItemSelectionDetails().getDiningSelectionDetails().getRestaurantImage())
                        ? URI.create(cartLineItem.getItemSelectionDetails().getDiningSelectionDetails().getRestaurantImage())
                        : null);
        diningReservation.setPropertyDetails(toPropertyDetails(cartLineItem));
        diningReservation.setGuestDetails(toGuestDetails(checkoutRequest.getGuestProfile()));
        diningReservation.setAdditionalText(cartLineItem.getItemSelectionDetails().getDiningSelectionDetails().getAdditionalText());

        return diningReservation;
    }

    public PropertyDetailsElement toPropertyDetails(CartLineItem cartLineItem) {
        final PropertyDetailsElement propertyDetailsElement = new PropertyDetailsElement();

        propertyDetailsElement.setOperaPropertyCode(cartLineItem.getOperaHotelCode());
        propertyDetailsElement.setPropertyId(cartLineItem.getPropertyId());
        propertyDetailsElement.setPropertyName(cartLineItem.getPropertyName());
        propertyDetailsElement.setPropertyTimeZone(cartLineItem.getTimeZone());
        
        if (cartLineItem.getPropertyAddress() != null) {
            propertyDetailsElement.setStreet1(cartLineItem.getPropertyAddress().getAddress1());
            propertyDetailsElement.setStreet2(cartLineItem.getPropertyAddress().getAddress2());
            propertyDetailsElement.setCity(cartLineItem.getPropertyAddress().getCity());
            propertyDetailsElement.setState(cartLineItem.getPropertyAddress().getState());
            propertyDetailsElement.setPostalCode(cartLineItem.getPropertyAddress().getZip());
            propertyDetailsElement.setCountry(cartLineItem.getPropertyAddress().getCountry());
        }

        return propertyDetailsElement;
    }

    public GuestDetailsElement toGuestDetails(com.mgmresorts.order.dto.GuestProfile guestProfile) {
        if (guestProfile == null) {
            return null;
        }
        
        final GuestDetailsElement guestDetailsElement = new GuestDetailsElement();

        guestDetailsElement.setFirstName(guestProfile.getFirstName());
        guestDetailsElement.setLastName(guestProfile.getLastName());
        guestDetailsElement.setEmailAddress(guestProfile.getEmailAddress1());
        guestDetailsElement.setPhoneNumber(!Utils.isEmpty(guestProfile.getPhoneNumbers()) ? guestProfile.getPhoneNumbers().get(0).getNumber() : null);
        guestDetailsElement.setAddress(!Utils.isEmpty(guestProfile.getAddresses()) ? toAddress(guestProfile.getAddresses().get(0)) : null);

        return guestDetailsElement;
    }

    public AddressElement toAddress(com.mgmresorts.order.dto.Address address) {
        final AddressElement addressElement = new AddressElement();

        addressElement.setAddressLine1(address.getStreet1());
        addressElement.setAddressLine2(address.getStreet2());
        addressElement.setCity(address.getCity());
        addressElement.setState(address.getState());
        addressElement.setZipCode(address.getPostalCode());

        return addressElement;
    }

    public RoomDetailsElement toRoomDetails(OrderLineItem orderLineItem, CartLineItem cartLineItem) {
        final RoomDetailsElement roomDetailsElement = new RoomDetailsElement();

        roomDetailsElement.setRoomName(cartLineItem.getProductName());
        roomDetailsElement.setRoomImage(!Utils.isEmpty(cartLineItem.getItemSelectionDetails().getRoomSelectionDetails().getRoomImage())
                        ? URI.create(cartLineItem.getItemSelectionDetails().getRoomSelectionDetails().getRoomImage())
                        : null);
        roomDetailsElement.setRoomRequestsCount(!Utils.isEmpty(orderLineItem.getAddOnComponents()) ? orderLineItem.getAddOnComponents().size() : 0);
        roomDetailsElement.setRoomRequests(!Utils.isEmpty(orderLineItem.getAddOnComponents()) ? toRoomRequests(orderLineItem.getAddOnComponents()) : Collections.emptyList());

        return roomDetailsElement;
    }

    public List<RoomRequestElement> toRoomRequests(List<AddOnComponent> addOnComponents) {
        final List<RoomRequestElement> roomRequestElements = new ArrayList<>();

        for (AddOnComponent addOnComponent : addOnComponents) {
            final RoomRequestElement roomRequestElement = new RoomRequestElement();
            roomRequestElement.setRequestCode(addOnComponent.getCode());
            roomRequestElement.setRequestDescription(addOnComponent.getShortDescription());
            roomRequestElement.setRequestPrice(addOnComponent.getPrice());
            roomRequestElements.add(roomRequestElement);
        }

        return roomRequestElements;
    }

    public ChargeDetailsElement toRoomChargeDetails(OrderLineItem orderLineItem) {
        final CreateRoomReservationResponse resvResp = mapper.readValue(orderLineItem.getContent(), CreateRoomReservationResponse.class);
        final RatesSummary ratesSummary = resvResp.getRoomReservation().getRatesSummary();
        final ChargeDetailsElement chargeDetailsElement = new ChargeDetailsElement();

        chargeDetailsElement.setReservationTotal(ratesSummary.getReservationTotal());
        chargeDetailsElement.setResortFee(ratesSummary.getResortFee());
        chargeDetailsElement.setResortFeeTax(Utils.roundTwoDecimalPlaces(ratesSummary.getResortFeeAndTax() - ratesSummary.getResortFee()));
        chargeDetailsElement.setResortFeeAvgPerNight(ratesSummary.getResortFeePerNight());
        chargeDetailsElement.setTourismPromotionFee(ratesSummary.getTourismFee());
        chargeDetailsElement.setTourismPromotionFeeAndTax(ratesSummary.getTourismFeeAndTax());
        chargeDetailsElement.setCasinoOccupancyFee(ratesSummary.getOccupancyFee());
        chargeDetailsElement.setAmountPaid(ratesSummary.getDepositDue());
        chargeDetailsElement.setDueUponCheckin(ratesSummary.getBalanceUponCheckIn());

        chargeDetailsElement.setForfeitDate(resvResp.getRoomReservation().getDepositDetails().getForfeitDate());
        chargeDetailsElement.setDepositForfeit(resvResp.getRoomReservation().getDepositDetails().getForfeitAmount().doubleValue());

        chargeDetailsElement.setDiscountedSubtotal(ratesSummary.getDiscountedSubtotal());
        chargeDetailsElement.setRoomChargeTax(ratesSummary.getRoomChargeTax());

        return chargeDetailsElement;
    }

    public ChargeDetailsElement toShowChargeDetails(OrderLineItem orderLineItem) {
        final ChargeDetailsElement chargeDetailsElement = new ChargeDetailsElement();
        
        if (!Utils.isEmpty(orderLineItem.getContent())) {
            ShowReservationResponse resvResp = null;
            ShowChargesResponse chargesResp = null;
            if (orderLineItem.getStatus().equals(OrderLineItem.Status.SUCCESS)) {
                resvResp = mapper.readValue(orderLineItem.getContent(), ShowReservationResponse.class);
            } else {
                chargesResp = mapper.readValue(orderLineItem.getContent(), ShowChargesResponse.class);
            }
            if (resvResp != null || chargesResp != null) {
                final Rates ratesSummary = resvResp != null ? resvResp.getCharges() : chargesResp != null ? chargesResp.getCharges() : null;
                if (ratesSummary != null) {
                    chargeDetailsElement.setReservationTotal(ratesSummary.getReservationTotal());
                    chargeDetailsElement.setAmountPaid(ratesSummary.getReservationTotal());
                    chargeDetailsElement.setShowSubtotal(ratesSummary.getDiscountedSubtotal());
                    chargeDetailsElement.setLet(ratesSummary.getLet());
                    chargeDetailsElement.setDeliveryFee(ratesSummary.getDeliveryFee());
                    chargeDetailsElement.setGratuity(ratesSummary.getGratuity());
                    if (ratesSummary.getServiceCharge() != null && ratesSummary.getServiceCharge().getItemized() != null) {
                        chargeDetailsElement.setServiceCharge(new ServiceChargeElement());
                        chargeDetailsElement.getServiceCharge().setAmount(ratesSummary.getServiceCharge().getAmount());
                        chargeDetailsElement.getServiceCharge().setItemized(new ItemizedElement());
                        chargeDetailsElement.getServiceCharge().getItemized().setCharge(ratesSummary.getServiceCharge().getItemized().getCharge());
                        chargeDetailsElement.getServiceCharge().getItemized().setTax(ratesSummary.getServiceCharge().getItemized().getTax());
                    }
                    if (ratesSummary.getTransactionFee() != null && ratesSummary.getTransactionFee().getItemized() != null) {
                        chargeDetailsElement.setTransactionFee(new TransactionFeeElement());
                        chargeDetailsElement.getTransactionFee().setAmount(ratesSummary.getTransactionFee().getAmount());
                        chargeDetailsElement.getTransactionFee().setItemized(new ItemizedElement());
                        chargeDetailsElement.getTransactionFee().getItemized().setCharge(ratesSummary.getTransactionFee().getItemized().getCharge());
                        chargeDetailsElement.getTransactionFee().getItemized().setTax(ratesSummary.getTransactionFee().getItemized().getTax());
                    }
                }
            }
        }
        
        return chargeDetailsElement;
    }

    public List<SeatDetailsElement> toShowSeats(CartLineItem cartLineItem) {
        final List<SeatDetailsElement> seatDetailsElements = new ArrayList<>();

        for (ShowTicket showTicket : cartLineItem.getItemSelectionDetails().getShowSelectionDetails().getShowTickets()) {
            final SeatDetailsElement seatDetailsElement = new SeatDetailsElement();
            if (showTicket.getSeat() != null) {
                seatDetailsElement.setSection(showTicket.getSeat().getSectionName());
                seatDetailsElement.setRow(showTicket.getSeat().getRowName());
                seatDetailsElement.setSeatNumber(showTicket.getSeat().getSeatNumber() != null ? showTicket.getSeat().getSeatNumber().toString() : null);
            }
            seatDetailsElement.setPriceCodeDescription(showTicket.getPriceCodeDescription());
            seatDetailsElement.setTicketTypeCodeDescription(showTicket.getTicketTypeCodeDescription());
            seatDetailsElements.add(seatDetailsElement);
        }

        return seatDetailsElements;
    }


    private List<PackageInclusionsElement> toPackageInclusions(final List<com.mgmresorts.order.dto.PackageCategoryInclusion> orderPackageInclusion) {
        if (Utils.isEmpty(orderPackageInclusion)) {
            return null;
        }
        return orderPackageInclusion.stream().map(orderInclusion -> {
            final PackageInclusionsElement packageInclusions = new PackageInclusionsElement();
            packageInclusions.setName(orderInclusion.getName());
            packageInclusions.setDescription(orderInclusion.getDescription());
            packageInclusions.setEnabled(orderInclusion.getEnabled());
            packageInclusions.setDisplayInCarouselGrid(orderInclusion.getDisplayInCarouselGrid());
            packageInclusions.setCarouselGridDisplayText(orderInclusion.getCarouselGridDisplayText());
            packageInclusions.setDetailText(orderInclusion.getDetailText());
            packageInclusions.setBookingDestinationDisplayText(orderInclusion.getBookingDestinationDisplayText());
            packageInclusions.setBookingDestinationUrl(orderInclusion.getBookingDestinationUrl());
            packageInclusions.setInclusionMultiplierType(orderInclusion.getInclusionMultiplierType() != null
                    ? PackageInclusionsElement.InclusionMultiplierTypeEnum.fromValue(orderInclusion.getInclusionMultiplierType().value()) : null);

            if (!Utils.isEmpty(orderInclusion.getRateComponents())) {
                packageInclusions.setRateComponents(toRateComponents(orderInclusion.getRateComponents()));
            }
            if (orderInclusion.getAsset() != null) {
                packageInclusions.setAsset(toAsset(orderInclusion.getAsset()));
            }

            return packageInclusions;
        }).collect(Collectors.toList());
    }

    private List<OfferComponentElement> toRateComponents(final List<OfferComponent> orderRateComponents) {
        if (Utils.isEmpty(orderRateComponents)) {
            return null;
        }
        return orderRateComponents.stream().map(orderRateComponent -> {
            final OfferComponentElement rateComponent = new OfferComponentElement();
            rateComponent.setComponentCode(orderRateComponent.getComponentCode());
            rateComponent.setPropertyComponents(toPropertyComponents(orderRateComponent.getPropertyComponents()));
            return rateComponent;
        }).collect(Collectors.toList());
    }

    private List<PropertyComponentElement> toPropertyComponents(final List<PropertyComponent> orderPropertyComponents) {
        if (Utils.isEmpty(orderPropertyComponents)) {
            return null;
        }
        return orderPropertyComponents.stream().map(orderRateComponent -> {
            final PropertyComponentElement propertyComponentElement = new PropertyComponentElement();
            propertyComponentElement.setComponentId(orderRateComponent.getComponentId());
            propertyComponentElement.setProperty(orderRateComponent.getProperty());
            return propertyComponentElement;
        }).collect(Collectors.toList());
    }

    private AssetElement toAsset(final com.mgmresorts.order.dto.Asset orderAsset) {
        if (orderAsset == null) {
            return null;
        }
        final AssetElement asset = new AssetElement();
        asset.setAssetId(orderAsset.getAssetId());
        asset.setAssetName(orderAsset.getAssetName());
        asset.setAssetType(orderAsset.getAssetType());
        asset.setMimeType(orderAsset.getMimeType());
        asset.setTransformBaseUrl(orderAsset.getTransformBaseUrl());
        asset.setDescription(orderAsset.getDescription());
        return asset;
    }
}
