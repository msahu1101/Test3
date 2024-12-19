package com.mgmresorts.order.service.transformer;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mgmresorts.common.event.enterprise.publish.IEnterpriseEventTransformer;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.event.dto.Asset;
import com.mgmresorts.order.event.dto.OfferComponent;
import com.mgmresorts.order.event.dto.PackageCategoryInclusion;
import com.mgmresorts.order.event.dto.PropertyComponent;
import com.mgmresorts.order.event.dto.Address;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.event.dto.DeliveryMethod;
import com.mgmresorts.order.event.dto.ItemSelectionDetails;
import com.mgmresorts.order.event.dto.OrderEvent;
import com.mgmresorts.order.event.dto.Order;
import com.mgmresorts.order.event.dto.PhoneNumber;
import com.mgmresorts.order.event.dto.RatesSummary;
import com.mgmresorts.order.event.dto.ShowRatesSummary;
import com.mgmresorts.order.event.dto.ShowTicket;
import com.mgmresorts.order.event.dto.PackageConfigDetails;
import com.mgmresorts.order.event.dto.PackagePricingDetails;
import com.mgmresorts.rbs.model.CalculateRoomChargesResponse;
import com.mgmresorts.rbs.model.CreateRoomReservationResponse;
import com.mgmresorts.rbs.model.TripDetails;
import com.mgmresorts.shopping.cart.dto.AddOnComponent;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.DiningSelectionDetails;
import com.mgmresorts.shopping.cart.dto.RoomSelectionDetails;
import com.mgmresorts.shopping.cart.dto.ShowSelectionDetails;
import com.mgmresorts.shopping.cart.dto.UpsellCartLineItem;
import org.apache.commons.lang3.StringUtils;

public class OrderCheckoutEventTransformer implements IEnterpriseEventTransformer<com.mgmresorts.order.entity.OrderEvent, OrderEvent> {
    private final JSonMapper mapper = new JSonMapper();

    @Override
    public float version() {
        return 1.0f;
    }

    @Override
    public String referenceNumber(Object data) {
        return data instanceof OrderEvent ? ((OrderEvent) data).getOrderEventId() : null;
    }

    @Override
    public OrderEvent transform(com.mgmresorts.order.entity.OrderEvent in) throws AppException {
        final OrderEvent entity = new OrderEvent();
        entity.setEventOccurrenceTime(ZonedDateTime.now());
        entity.setEventTriggerTime(ZonedDateTime.now());
        entity.setOrderEventId(in.getOrder().getId());
        entity.setEventName("Order Created");
        entity.setTriggeringOrderLineItemIds(in.getOrder().getOrderLineItems().stream().map(i -> i.getCartLineItemId()).collect(Collectors.toList()));
        entity.setOrder(toRight(in));
        entity.setOrderInitiatedAt(in.getOrderInitiatedAt());
        entity.setOrderUpdatedAt(in.getOrderInitiatedAt());
        return entity;
    }

    public com.mgmresorts.order.event.dto.Order toRight(com.mgmresorts.order.entity.OrderEvent in) throws AppException {
        final com.mgmresorts.order.event.dto.Order order = new com.mgmresorts.order.event.dto.Order();
        order.setId(in.getOrder().getId());
        order.setCartId(in.getOrder().getCartId());
        order.setType(in.getOrder().getType());
        order.setVersion(in.getOrder().getVersion());
        order.setBookingChannel(ThreadContext.getContext().get().getChannel());
        order.setBookingSource(ThreadContext.getContext().get().getSource());
        order.setTotalItemCount(in.getOrder().getOrderLineItems().size());
        order.setPriceExpiresAt(in.getCart().getPriceExpiresAt());
        final List<OrderLineItem> successStatuses = in.getOrder().getOrderLineItems().stream().filter(i -> i.getStatus().equals(OrderLineItem.Status.SUCCESS))
                .collect(Collectors.toList());
        order.setTotalSuccessfulItemCount(successStatuses.size());
        if (in.getOrder().getPriceDetails() != null) {
            order.setTotalCharge(in.getOrder().getPriceDetails().getTotalCharge());
            order.setTotalTax(in.getOrder().getPriceDetails().getTotalTax());
            order.setTotalPrice(in.getOrder().getPriceDetails().getTotalPrice());
            order.setTotalStrikethroughPrice(in.getOrder().getPriceDetails().getTotalStrikethroughPrice());
            order.setTotalDeposit(in.getOrder().getPriceDetails().getTotalDeposit());
            order.setTotalDiscount(in.getOrder().getPriceDetails().getTotalDiscount());
            order.setTotalBalance(in.getOrder().getPriceDetails().getTotalBalanceDue());
        }
        order.setPaymentTransaction(toPaymentTransactions(in));
        order.setPaymentMethods(toPaymentMethods(in));
        order.setCustomerProfile(toCustomerProfile(in));
        if (!Utils.isEmpty(in.getOrder().getNewCartId())) {
            order.setFailOverCartId(in.getOrder().getNewCartId());
        }
        order.setOrderStatus(Order.OrderStatus.fromValue(in.getOrder().getStatus().value()));
        order.setOrderLineItems(toItems(in));
        order.setCartCreatedAt(in.getCart().getMetaData().getCreatedAt());
        order.setIsTimerExtended(in.getCart().getIsTimerExtended());
        order.setF1Package(in.getOrder().getF1Package() != null ? in.getOrder().getF1Package() : false);
        order.setJwbFlow(in.getOrder().getJwbFlow() != null ? in.getOrder().getJwbFlow() : false);
        order.setPackageConfigDetails(toPackageConfigDetails(in.getOrder().getPackageConfigDetails()));
        return order;
    }

    public com.mgmresorts.order.event.dto.CustomerProfile toCustomerProfile(com.mgmresorts.order.entity.OrderEvent in) {
        final com.mgmresorts.order.event.dto.CustomerProfile customerProfile = new com.mgmresorts.order.event.dto.CustomerProfile();

        if (!Utils.isEmpty(in.getOrder().getMgmId())) {
            customerProfile.setMgmId(in.getOrder().getMgmId());
        }

        customerProfile.setFirstName(in.getCheckoutRequest().getGuestProfile().getFirstName());
        customerProfile.setLastName(in.getCheckoutRequest().getGuestProfile().getLastName());
        customerProfile.setmLifeId(in.getCheckoutRequest().getGuestProfile().getMlifeNo());
        customerProfile.setEmail(in.getCheckoutRequest().getGuestProfile().getEmailAddress1());
        customerProfile.setCustomerId(in.getCheckoutRequest().getGuestProfile().getId());
        customerProfile.setPerpetualOfferEligible(in.getCheckoutRequest().getGuestProfile().getPerpetualOfferEligible());
        if (!Utils.isEmpty(in.getCheckoutRequest().getGuestProfile().getAddresses())) {
            customerProfile.setAddress(new Address());
            customerProfile.getAddress().setCity(in.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getCity());
            customerProfile.getAddress().setCountry(in.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getCountry());
            customerProfile.getAddress().setPostalCode(in.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getPostalCode());
            customerProfile.getAddress().setState(in.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getState());
            customerProfile.getAddress().setStreet1(in.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getStreet1());
            customerProfile.getAddress().setStreet2(in.getCheckoutRequest().getGuestProfile().getAddresses().get(0).getStreet2());
        }
        if (!Utils.isEmpty(in.getCheckoutRequest().getGuestProfile().getPhoneNumbers())) {
            customerProfile.setPhoneNumber(new PhoneNumber());
            customerProfile.getPhoneNumber().setNumber(in.getCheckoutRequest().getGuestProfile().getPhoneNumbers().get(0).getNumber());
            customerProfile.getPhoneNumber().setType(PhoneNumber.Type.fromValue(in.getCheckoutRequest().getGuestProfile().getPhoneNumbers().get(0).getType().value()));
        }

        return customerProfile;
    }

    public com.mgmresorts.order.event.dto.PaymentTransactions toPaymentTransactions(com.mgmresorts.order.entity.OrderEvent in) {
        final com.mgmresorts.order.event.dto.PaymentTransactions paymentTransactions = new com.mgmresorts.order.event.dto.PaymentTransactions();

        final Optional<OrderLineItem> orderLineItem = in.getOrder().getOrderLineItems().stream().filter(i -> i.getStatus() == OrderLineItem.Status.SUCCESS).findFirst();

        if (orderLineItem.isPresent()) {
            final CreateRoomReservationResponse resvResp = mapper.readValue(orderLineItem.get().getContent(), CreateRoomReservationResponse.class);
            paymentTransactions.setTransactionDate(
                    resvResp != null && resvResp.getRoomReservation() != null ? resvResp.getRoomReservation().getPayments().get(0).getFxTransDate() : LocalDate.now().toString());
            paymentTransactions.setTransactionAmount(in.getOrder().getPriceDetails().getTotalPrice());
        }
        
        return paymentTransactions;
    }

    public com.mgmresorts.order.event.dto.PaymentMethods toPaymentMethods(com.mgmresorts.order.entity.OrderEvent in) {
        final com.mgmresorts.order.event.dto.PaymentMethods paymentMethods = new com.mgmresorts.order.event.dto.PaymentMethods();
        if (in.getCart().getPaymentRequired() != null && in.getCart().getPaymentRequired()) {
            final Optional<Billing> billing = Optional.ofNullable(in.getCheckoutRequest().getBillings().get(0));
           if (billing.isPresent()) {
                paymentMethods.setFirstName(billing.get().getPayment().getFirstName());
                paymentMethods.setLastName(billing.get().getPayment().getLastName());
                paymentMethods.setCardHolder(billing.get().getPayment().getCardHolder());
                paymentMethods.setExpiry(billing.get().getPayment().getExpiry());
                paymentMethods.setCcToken(billing.get().getPayment().getCcToken());
                paymentMethods.setMaskNumber(billing.get().getPayment().getMaskedNumber());
                final Address address = new Address();
                address.setState(billing.get().getAddress().getState());
                address.setCountry(billing.get().getAddress().getCountry());
                address.setCity(billing.get().getAddress().getCity());
                address.setStreet1(billing.get().getAddress().getStreet1());
                address.setStreet2(billing.get().getAddress().getStreet2());
                address.setPostalCode(billing.get().getAddress().getPostalCode());
                paymentMethods.setBillingAddress(address);
            }
        }
        
        return paymentMethods;
    }

    private List<com.mgmresorts.order.event.dto.OrderLineItem> toItems(com.mgmresorts.order.entity.OrderEvent in) {
        if (Utils.isEmpty(in.getOrder().getOrderLineItems())) {
            return new ArrayList<>();
        }

        final List<com.mgmresorts.order.event.dto.OrderLineItem> eventOrderLineItems = new ArrayList<>();

        final List<OrderLineItem> orderLineItems = in.getOrder().getOrderLineItems();
        if (!Utils.isEmpty(orderLineItems)) {
            for (com.mgmresorts.order.dto.services.OrderLineItem oli : orderLineItems) {
                final com.mgmresorts.order.event.dto.OrderLineItem eventOli = new com.mgmresorts.order.event.dto.OrderLineItem();

                eventOli.setStatus(oli.getStatus().toString());
                eventOli.setProductType(com.mgmresorts.order.event.dto.OrderLineItem.ProductType.fromValue(oli.getProductType().value()));
                eventOli.setOperaConfirmationNumber(oli.getOperaConfirmationNumber());
                eventOli.setContent(oli.getContent());
                eventOli.setConfirmationNumber(oli.getConfirmationNumber());
                eventOli.setItineraryId(oli.getItineraryId());
                eventOli.setContentVersion(ThreadContext.getContext().get().getVersion());
                eventOli.setOrderLineItemId(oli.getOrderLineItemId());
                eventOli.setLineItemCharge(oli.getLineItemCharge());
                eventOli.setLineItemTax(oli.getLineItemTax());
                eventOli.setLineItemPrice(oli.getLineItemPrice());
                eventOli.setLineItemStrikethroughPrice(oli.getLineItemStrikethroughPrice());
                eventOli.setLineItemDeposit(oli.getLineItemDeposit());
                eventOli.setLineItemDiscount(oli.getLineItemDiscount());
                eventOli.setLineItemBalance(oli.getLineItemBalance());
                eventOli.setPackageId(oli.getPackageId());

                eventOli.setCartLineItemId(oli.getCartLineItemId());
                final Optional<CartLineItem> cartLineItem = in.getCart().getCartLineItems().stream().filter(e -> e.getCartLineItemId().contentEquals(oli.getCartLineItemId()))
                        .findFirst();
                eventOli.setPropertyId(oli.getPropertyId());
                eventOli.setProductId(oli.getProductId());
                eventOli.setCreatedAt(cartLineItem.get().getAddedAt());
                eventOli.setPropertyName(cartLineItem.get().getPropertyName());
                eventOli.setOperaHotelCode(cartLineItem.get().getOperaHotelCode());
                eventOli.setStartsAt(cartLineItem.get().getStartsAt() != null ? cartLineItem.get().getStartsAt().toLocalDate() : null);
                eventOli.setEndsAt(cartLineItem.get().getEndsAt() != null ? cartLineItem.get().getEndsAt().toLocalDate() : null);
                eventOli.setProductName(cartLineItem.get().getProductName());

                final ItemSelectionDetails itemSelectionDetails = new ItemSelectionDetails();
                eventOli.setItemSelectionDetails(itemSelectionDetails);

                if (cartLineItem.get().getItemSelectionDetails() != null && cartLineItem.get().getItemSelectionDetails().getRoomSelectionDetails() != null) {
                    itemSelectionDetails.setRoomSelectionDetails(toRoomSelectionDetails(cartLineItem.get().getItemSelectionDetails().getRoomSelectionDetails()));

                    final CalculateRoomChargesResponse roomCharge = mapper.readValue(cartLineItem.get().getContent(), CalculateRoomChargesResponse.class);
                    final TripDetails tripDetails = roomCharge != null ? roomCharge.getTripDetails() : null;
                    if (tripDetails != null) {
                        eventOli.setNumberOfGuests(Double.valueOf(tripDetails.getNumAdults() + tripDetails.getNumChildren()));
                        if (tripDetails.getNumRooms() == null || tripDetails.getNumRooms() == 0) {
                            tripDetails.setNumRooms(1);
                        }
                        itemSelectionDetails.getRoomSelectionDetails().setNumberOfAdults(Double.valueOf(tripDetails.getNumAdults()));
                        itemSelectionDetails.getRoomSelectionDetails().setNumberOfChildren(Double.valueOf(tripDetails.getNumChildren()));
                        itemSelectionDetails.getRoomSelectionDetails().setNumberOfRooms(Double.valueOf(tripDetails.getNumRooms()));
                        itemSelectionDetails.getRoomSelectionDetails().setCheckInDate(tripDetails.getCheckInDate());
                        itemSelectionDetails.getRoomSelectionDetails().setCheckOutDate(tripDetails.getCheckOutDate());
                    }
                }

                if (cartLineItem.get().getItemSelectionDetails() != null && cartLineItem.get().getItemSelectionDetails().getDiningSelectionDetails() != null) {
                    itemSelectionDetails.setDiningSelectionDetails(toDiningSelectionDetails(cartLineItem.get().getItemSelectionDetails().getDiningSelectionDetails()));
                }

                if (cartLineItem.get().getItemSelectionDetails() != null && cartLineItem.get().getItemSelectionDetails().getShowSelectionDetails() != null) {
                    itemSelectionDetails.setShowSelectionDetails(toShowSelectionDetails(cartLineItem.get().getItemSelectionDetails().getShowSelectionDetails()));
                }

                eventOli.setAddOnsPrice(cartLineItem.get().getAddOnsPrice());
                eventOli.setAddOnsTax(cartLineItem.get().getAddOnsTax());
                
                eventOli.setNumberOfNights(oli.getNumberOfNights());
                eventOli.setUpgraded(oli.getUpgraded());
                eventOli.setUpsellAvailable(cartLineItem.get().getUpsellAvailable());
                

                if (cartLineItem.get().getUpgraded()) {
                    eventOli.setUpsellPriceDifference(cartLineItem.get().getUpsellRoomRateDifference());
                    eventOli.setUpsellGrossRevenueDifference(cartLineItem.get().getUpsellGrossRevenueDifference());
                }

                
                if (cartLineItem.get().getUpsellAvailable() != null && cartLineItem.get().getUpsellAvailable() && cartLineItem.get().getUpsellLineItem() != null) {
                    eventOli.setUpsellLineItem(toUpsellOrderEventOrderLineItem(cartLineItem.get()));
                }
                eventOrderLineItems.add(eventOli);
            }
        }
        
        return eventOrderLineItems;
    }
    
    private com.mgmresorts.order.event.dto.OrderLineItem toUpsellOrderEventOrderLineItem(CartLineItem originalCartLineItem) {
        
        final com.mgmresorts.order.event.dto.OrderLineItem upsellOrderEventOrderLineItem = new com.mgmresorts.order.event.dto.OrderLineItem();
        
        final UpsellCartLineItem upsellCartLineItem = originalCartLineItem.getUpsellLineItem();
        
        upsellOrderEventOrderLineItem.setStatus("SUCCESS");
        upsellOrderEventOrderLineItem.setProductType(com.mgmresorts.order.event.dto.OrderLineItem.ProductType.fromValue(upsellCartLineItem.getType().value()));
        upsellOrderEventOrderLineItem.setPropertyName(upsellCartLineItem.getPropertyName());
        upsellOrderEventOrderLineItem.setConfirmationNumber(upsellCartLineItem.getConfirmationNumber());
        
        final ItemSelectionDetails itemSelectionDetails = new ItemSelectionDetails();
        upsellOrderEventOrderLineItem.setItemSelectionDetails(itemSelectionDetails);
        
        if (upsellCartLineItem.getItemSelectionDetails() != null && upsellCartLineItem.getItemSelectionDetails().getRoomSelectionDetails() != null) {
            itemSelectionDetails.setRoomSelectionDetails(toRoomSelectionDetails(upsellCartLineItem.getItemSelectionDetails().getRoomSelectionDetails()));
        }
        
        return upsellOrderEventOrderLineItem;
    }

    private com.mgmresorts.order.event.dto.RoomSelectionDetails toRoomSelectionDetails(RoomSelectionDetails product) {
        final com.mgmresorts.order.event.dto.RoomSelectionDetails details = new com.mgmresorts.order.event.dto.RoomSelectionDetails();

        details.setProgramId(product.getProgramId());
        details.setProgramName(product.getProgramName());
        details.setProgramType(product.getProgramType());
        details.setOperaPromoCode(product.getOperaPromoCode());
        details.setSpecialRequests(product.getSpecialRequests());

        details.setAddOnComponents(toAddOnComponents(product.getAddOnComponents()));
        details.setBookings(toBookings(product.getBookings()));

        details.setF1Package(product.getF1Package() != null ? product.getF1Package() : false);

        final com.mgmresorts.shopping.cart.dto.RoomRatesSummary roomRatesSummary = product.getRatesSummary();
        if (roomRatesSummary != null) {
            details.setRatesSummary(Utils.cloneByJson(mapper, RatesSummary.class, roomRatesSummary));
        }

        details.setRatePlanTags(product.getRatePlanTags());

        return details;
    }

    private com.mgmresorts.order.event.dto.DiningSelectionDetails toDiningSelectionDetails(DiningSelectionDetails product) {
        final com.mgmresorts.order.event.dto.DiningSelectionDetails details = new com.mgmresorts.order.event.dto.DiningSelectionDetails();

        details.setReservationDate(product.getReservationDate());
        details.setReservationTime(product.getReservationTime());
        details.setPartySize(product.getPartySize());

        return details;
    }

    private com.mgmresorts.order.event.dto.ShowSelectionDetails toShowSelectionDetails(ShowSelectionDetails product) {
        final com.mgmresorts.order.event.dto.ShowSelectionDetails details = new com.mgmresorts.order.event.dto.ShowSelectionDetails();

        details.setProgramId(product.getProgramId());
        details.setProgramName(product.getProgramName());
        details.setOfferType(product.getOfferType() != null ? com.mgmresorts.order.event.dto.ShowSelectionDetails.OfferType.fromValue(product.getOfferType().toString()) : null);
        details.setEventDate(product.getEventDate());
        details.setEventTime(product.getEventTime());
        details.setSeasonId(product.getSeasonId());
        details.setMyVegasComp(product.getMyVegasComp());
        details.setMyVegasCode(product.getMyVegasCode());
        details.setHdePackage(product.getHdePackage());
        details.setNumberOfTickets(product.getNumberOfTickets());
        details.setInventorySource(product.getInventorySource());
        
        if (product.getRatesSummary() != null) {
            details.setRatesSummary(Utils.cloneByJson(mapper, ShowRatesSummary.class, product.getRatesSummary()));
        }
        
        if (product.getPermissibleDeliveryMethods() != null) {
            final Optional<com.mgmresorts.shopping.cart.dto.DeliveryMethod> selectedDeliveryMethod =
                    product.getPermissibleDeliveryMethods().stream().filter(deliveryMethod -> deliveryMethod.getSelected()).findFirst();
            if (selectedDeliveryMethod.isPresent()) {
                final DeliveryMethod deliveryMethod = Utils.cloneByJson(mapper, DeliveryMethod.class, selectedDeliveryMethod.get());
                details.setSelectedDeliveryMethod(deliveryMethod);
            }
            
        }
        
        if (product.getShowTickets() != null) {
            final List<ShowTicket> showTickets = product.getShowTickets().stream()
                    .map(showTicket -> Utils.cloneByJson(mapper, ShowTicket.class, showTicket))
                    .collect(Collectors.toList());
            details.setShowTickets(showTickets);
        }

        return details;
    }

    private List<com.mgmresorts.order.event.dto.AddOnComponent> toAddOnComponents(List<AddOnComponent> list) {
        final List<com.mgmresorts.order.event.dto.AddOnComponent> calculateAddonComponents = !Utils.isEmpty(list) ? list.stream().map(component -> {
            final com.mgmresorts.order.event.dto.AddOnComponent calculateAddOn = new com.mgmresorts.order.event.dto.AddOnComponent();

            if (!Utils.isEmpty(list)) {
                calculateAddOn.setActive(component.getActive());
                calculateAddOn.setCode(component.getCode());
                calculateAddOn.setId(component.getId());
                calculateAddOn.setPricingApplied(toPricingApplied(component.getPricingApplied()));
                calculateAddOn.setSelected(component.getSelected());
                calculateAddOn.setNonEditable(component.getNonEditable());
                calculateAddOn.setShortDescription(component.getShortDescription());
                calculateAddOn.setTripPrice(component.getTripPrice());
                calculateAddOn.setTripTax(component.getTripTax());
                calculateAddOn.setPrice(component.getPrice());
                calculateAddOn.setDepositAmount(component.getDepositAmount());
                calculateAddOn.setPrices(toAddOnComponentPrices(component.getPrices()));
            }

            return calculateAddOn;
        }).collect(Collectors.toList()) : Collections.emptyList();

        return calculateAddonComponents;
    }

    private com.mgmresorts.order.event.dto.AddOnComponent.PricingApplied toPricingApplied(com.mgmresorts.shopping.cart.dto.AddOnComponent.PricingApplied left) {
        switch (left) {
        case CHECKIN:
            return com.mgmresorts.order.event.dto.AddOnComponent.PricingApplied.CHECKIN;
        case CHECKOUT:
            return com.mgmresorts.order.event.dto.AddOnComponent.PricingApplied.CHECKOUT;
        case NIGHTLY:
            return com.mgmresorts.order.event.dto.AddOnComponent.PricingApplied.NIGHTLY;
        case PERSTAY:
            return com.mgmresorts.order.event.dto.AddOnComponent.PricingApplied.PERSTAY;
        default:
            return null;
        }
    }

    private List<com.mgmresorts.order.event.dto.AddOnComponentPrice> toAddOnComponentPrices(List<com.mgmresorts.shopping.cart.dto.AddOnComponentPrice> list) {
        final List<com.mgmresorts.order.event.dto.AddOnComponentPrice> addOnPrices = !Utils.isEmpty(list) ? list.stream().map(componentPrice -> {
            final com.mgmresorts.order.event.dto.AddOnComponentPrice addOnPrice = new com.mgmresorts.order.event.dto.AddOnComponentPrice();
            if (componentPrice != null) {
                if (componentPrice.getAmount() != null) {
                    addOnPrice.setAmount(componentPrice.getAmount().doubleValue());
                }
                if (componentPrice.getTax() != null) {
                    addOnPrice.setTax(componentPrice.getTax().doubleValue());
                }
                if (componentPrice.getDate() != null) {
                    addOnPrice.setDate(componentPrice.getDate());
                }
            }

            return addOnPrice;
        }).collect(Collectors.toList()) : Collections.emptyList();

        return addOnPrices;
    }

    private List<com.mgmresorts.order.event.dto.Booking> toBookings(List<com.mgmresorts.shopping.cart.dto.Booking> list) {
        if (Utils.isEmpty(list)) {
            return Collections.emptyList();
        }
        final List<com.mgmresorts.order.event.dto.Booking> bookings = new ArrayList<com.mgmresorts.order.event.dto.Booking>();
        list.forEach(item -> {
            final com.mgmresorts.order.event.dto.Booking booking = new com.mgmresorts.order.event.dto.Booking();
            booking.setDate(item.getDate());
            booking.setBasePrice(item.getBasePrice());
            booking.setCustomerPrice(item.getCustomerPrice());
            booking.setPrice(item.getPrice());
            booking.setDiscounted(item.getIsDiscounted());
            booking.setProgramIdIsRateTable(item.getProgramIdIsRateTable());
            booking.setOverridePrice(item.getOverridePrice());
            booking.setOverrideProgramIdIsRateTable(item.getOverrideProgramIdIsRateTable());
            booking.setComp(item.getIsComp());
            booking.setResortFeeIsSpecified(item.getResortFeeIsSpecified());
            booking.setResortFee(item.getResortFee());
            booking.setProgramId(item.getProgramId());
            booking.setPricingRuleId(item.getPricingRuleId());
            booking.setOverrideProgramId(item.getOverrideProgramId());
            booking.setOverridePricingRuleId(item.getOverridePricingRuleId());
            bookings.add(booking);
        });
        
        return bookings;
    }

    private com.mgmresorts.order.event.dto.PackageConfigDetails toPackageConfigDetails(final com.mgmresorts.order.dto.PackageConfigDetails orderPackageConfigDetails) {
        if (orderPackageConfigDetails == null) {
            return null;
        }
        final PackageConfigDetails packageConfigDetails = new PackageConfigDetails();
        if (StringUtils.isNotBlank(orderPackageConfigDetails.getPackageCategoryId())) {
            packageConfigDetails.setPackageCategoryId(orderPackageConfigDetails.getPackageCategoryId());
        }
        if (StringUtils.isNotBlank(orderPackageConfigDetails.getPackagePriceBreakdown())) {
            packageConfigDetails.setPackagePriceBreakdown(orderPackageConfigDetails.getPackagePriceBreakdown());
        }
        if (orderPackageConfigDetails.getPackagePricingDetails() != null) {
            final PackagePricingDetails packagePricingDetails = new PackagePricingDetails();
            packagePricingDetails.setPackageBaseTotal(orderPackageConfigDetails.getPackagePricingDetails().getPackageBaseTotal());
            packagePricingDetails.setPackageStartingPrice(orderPackageConfigDetails.getPackagePricingDetails().getPackageStartingPrice());
            packagePricingDetails.setPackageTotal(orderPackageConfigDetails.getPackagePricingDetails().getPackageTotal());
            packagePricingDetails.setRoomModification(orderPackageConfigDetails.getPackagePricingDetails().getRoomModification());
            packagePricingDetails.setShowModification(orderPackageConfigDetails.getPackagePricingDetails().getShowModification());
            packagePricingDetails.setRoomTotal(orderPackageConfigDetails.getPackagePricingDetails().getRoomTotal());
            packagePricingDetails.setShowTotal(orderPackageConfigDetails.getPackagePricingDetails().getShowTotal());
            packagePricingDetails.setPackageInclusions(toPackageInclusions(orderPackageConfigDetails.getPackagePricingDetails().getPackageInclusions()));
            packagePricingDetails.setIsMultiDayEvent(orderPackageConfigDetails.getPackagePricingDetails().getIsMultiDayEvent());
            packagePricingDetails.setEventStartDate(orderPackageConfigDetails.getPackagePricingDetails().getEventStartDate());
            packagePricingDetails.setEventEndDate(orderPackageConfigDetails.getPackagePricingDetails().getEventEndDate());
            packageConfigDetails.setPackagePricingDetails(packagePricingDetails);
        }
        return packageConfigDetails;
    }


    private List<com.mgmresorts.order.event.dto.PackageCategoryInclusion> toPackageInclusions(final List<com.mgmresorts.order.dto.PackageCategoryInclusion> cartPackageInclusion) {
        if (Utils.isEmpty(cartPackageInclusion)) {
            return null;
        }
        return cartPackageInclusion.stream().map(cartInclusion -> {
            final PackageCategoryInclusion packageInclusions = new PackageCategoryInclusion();
            packageInclusions.setName(cartInclusion.getName());
            packageInclusions.setDescription(cartInclusion.getDescription());
            packageInclusions.setEnabled(cartInclusion.getEnabled());
            packageInclusions.setDisplayInCarouselGrid(cartInclusion.getDisplayInCarouselGrid());
            packageInclusions.setCarouselGridDisplayText(cartInclusion.getCarouselGridDisplayText());
            packageInclusions.setDetailText(cartInclusion.getDetailText());
            packageInclusions.setBookingDestinationUrl(cartInclusion.getBookingDestinationUrl());
            packageInclusions.setInclusionMultiplierType(cartInclusion.getInclusionMultiplierType() != null
                    ? PackageCategoryInclusion.InclusionMultiplierType.fromValue(cartInclusion.getInclusionMultiplierType().value()) : null);

            if (!Utils.isEmpty(cartInclusion.getRateComponents())) {
                packageInclusions.setRateComponents(toRateComponents(cartInclusion.getRateComponents()));
            }
            if (cartInclusion.getAsset() != null) {
                packageInclusions.setAsset(toAsset(cartInclusion.getAsset()));
            }

            return packageInclusions;
        }).collect(Collectors.toList());
    }

    private List<OfferComponent> toRateComponents(final List<com.mgmresorts.order.dto.OfferComponent> orderRateComponents) {
        if (Utils.isEmpty(orderRateComponents)) {
            return null;
        }

        return orderRateComponents.stream().map(orderRateComponent -> {
            final OfferComponent rateComponent = new OfferComponent();
            rateComponent.setComponentCode(orderRateComponent.getComponentCode());
            rateComponent.setPropertyComponents(toPropertyComponents(orderRateComponent.getPropertyComponents()));
            return rateComponent;
        }).collect(Collectors.toList());
    }

    private List<PropertyComponent> toPropertyComponents(final List<com.mgmresorts.order.dto.PropertyComponent> cartPropertyComponents) {
        if (Utils.isEmpty(cartPropertyComponents)) {
            return null;
        }
        return cartPropertyComponents.stream().map(cartPropertyComponent -> {
            final PropertyComponent propertyComponent = new PropertyComponent();
            propertyComponent.setComponentId(cartPropertyComponent.getComponentId());
            propertyComponent.setProperty(cartPropertyComponent.getProperty());
            return propertyComponent;
        }).collect(Collectors.toList());
    }

    private Asset toAsset(final com.mgmresorts.order.dto.Asset cartAsset) {
        if (cartAsset == null) {
            return null;
        }
        final Asset asset = new Asset();
        asset.setAssetId(cartAsset.getAssetId());
        asset.setAssetName(cartAsset.getAssetName());
        asset.setAssetType(cartAsset.getAssetType());
        asset.setMimeType(cartAsset.getMimeType());
        asset.setTransformBaseUrl(cartAsset.getTransformBaseUrl());
        asset.setDescription(cartAsset.getDescription());
        return asset;
    }
}
