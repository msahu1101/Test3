package com.mgmresorts.order.utils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.dto.Asset;
import com.mgmresorts.order.dto.OfferComponent;
import com.mgmresorts.order.dto.PackageCategoryInclusion;
import com.mgmresorts.order.dto.PriceDetails;
import com.mgmresorts.order.dto.PropertyComponent;
import com.mgmresorts.order.dto.RoomTotals;
import com.mgmresorts.order.dto.ShowTotals;
import com.mgmresorts.order.dto.PackageConfigDetails;
import com.mgmresorts.order.dto.PackagePricingDetails;
import com.mgmresorts.order.dto.PackageComponentDetail;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.Order;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.DeliveryMethod;
import com.mgmresorts.shopping.cart.dto.ShowSelectionDetails;
import org.apache.commons.lang3.StringUtils;
import com.mgmresorts.common.crypto.SecurityFactory;

import javax.inject.Inject;

public class Orders {
    @Inject
    private ITransformer<DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> deliveryMethodTransformer;

    private static final int ORDER_ID_LENGTH = Runtime.get().getInt("order.id.length", 12);

    public void calculateOrderPrice(final Order order) {
        final List<OrderLineItem> successLineItems = order.getOrderLineItems().stream().filter(o -> isSuccessOrPendingStatus(o.getStatus())).collect(Collectors.toList());
        if (!Utils.isEmpty(successLineItems)) {
            final PriceDetails priceDetails = new PriceDetails();
            final RoomTotals roomTotals = new RoomTotals();
            final ShowTotals showTotals = new ShowTotals();
            double totalCharge = 0;
            double totalTax = 0;
            double totalPrice = 0;
            double totalStrikethroughPrice = 0;
            double totalShowPrice = 0;
            double totalRoomPrice = 0;
            double totalDeposit = 0;
            double totalDiscount = 0;
            double totalBalanceDue = 0;
            double totalAdjustedItemSubtotal = 0;
            double roomTotalAdjustedItemSubtotal = 0;
            double showTotalAdjustedItemSubtotal = 0;
            double totalServiceChargeFeeAndTax = 0;
            double totalTripSubtotal = 0;
            double totalTourismFeeAndTax = 0;
            double totalResortFeePerNight = 0;
            double totalOccupancyFee = 0;
            double totalResortFeeAndTax = 0;
            double totalDeliveryFee = 0;
            double totalGratuity = 0;
            double totalLet = 0;
            double totalServiceChargeFee = 0;
            double totalServiceChargeTax = 0;
            double totalTransactionFee = 0;
            double totalTransactionTax = 0;
            double totalCasinoSurchargeAndTax = 0;
            double totalAddOnComponentChargeAndTax = 0;

            for (OrderLineItem orderLineItem : successLineItems) {
                totalCharge = Double.sum(totalCharge, Optional.ofNullable(orderLineItem.getLineItemCharge()).orElse(0d));
                totalTax = Double.sum(totalTax, Optional.ofNullable(orderLineItem.getLineItemTax()).orElse(0d));
                totalPrice = Double.sum(totalPrice, Optional.ofNullable(orderLineItem.getLineItemPrice()).orElse(0d));
                totalStrikethroughPrice = Double.sum(totalStrikethroughPrice, Optional.ofNullable(orderLineItem.getLineItemStrikethroughPrice()).orElse(0d));
                switch (orderLineItem.getProductType()) {
                    case ROOM:
                        totalRoomPrice = Double.sum(totalRoomPrice, Optional.ofNullable(orderLineItem.getLineItemPrice()).orElse(0d));
                        roomTotalAdjustedItemSubtotal = Double.sum(roomTotalAdjustedItemSubtotal, Optional.ofNullable(orderLineItem.getLineItemAdjustedItemSubtotal()).orElse(0d));
                        totalAddOnComponentChargeAndTax += (orderLineItem.getAddOnsPrice() != null ? orderLineItem.getAddOnsPrice() : 0)
                                + (orderLineItem.getAddOnsTax() != null ? orderLineItem.getAddOnsTax() : 0);
                        break;
                    case SHOW:
                        totalShowPrice = Double.sum(totalShowPrice, Optional.ofNullable(orderLineItem.getLineItemPrice()).orElse(0d));
                        showTotalAdjustedItemSubtotal = Double.sum(showTotalAdjustedItemSubtotal, Optional.ofNullable(orderLineItem.getLineItemAdjustedItemSubtotal()).orElse(0d));
                        break;
                    default:
                        break;
                }
                totalDeposit = Double.sum(totalDeposit, Optional.ofNullable(orderLineItem.getLineItemDeposit()).orElse(0d));
                totalDiscount = Double.sum(totalDiscount, Optional.ofNullable(orderLineItem.getLineItemDiscount()).orElse(0d));
                totalBalanceDue = Double.sum(totalBalanceDue, Optional.ofNullable(orderLineItem.getLineItemBalance()).orElse(0d));
                totalTourismFeeAndTax = Double.sum(totalTourismFeeAndTax, Optional.ofNullable(orderLineItem.getLineItemTourismFeeAndTax()).orElse(0d));
                totalResortFeePerNight = Double.sum(totalResortFeePerNight, Optional.ofNullable(orderLineItem.getLineItemResortFeePerNight()).orElse(0d));
                totalOccupancyFee = Double.sum(totalOccupancyFee, Optional.ofNullable(orderLineItem.getLineItemOccupancyFee()).orElse(0d));
                totalResortFeeAndTax = Double.sum(totalResortFeeAndTax, Optional.ofNullable(orderLineItem.getLineItemResortFeeAndTax()).orElse(0d));
                totalAdjustedItemSubtotal = Double.sum(totalAdjustedItemSubtotal, Optional.ofNullable(orderLineItem.getLineItemAdjustedItemSubtotal()).orElse(0d));
                totalServiceChargeFeeAndTax = Double.sum(totalServiceChargeFeeAndTax, Optional.ofNullable(orderLineItem.getLineItemServiceChargeFeeAndTax()).orElse(0d));
                totalTripSubtotal = Double.sum(totalTripSubtotal, Optional.ofNullable(orderLineItem.getLineItemTripSubtotal()).orElse(0d));

                totalDeliveryFee = Double.sum(totalDeliveryFee, Optional.ofNullable(orderLineItem.getLineItemDeliveryMethodFee()).orElse(0d));
                totalGratuity = Double.sum(totalGratuity, Optional.ofNullable(orderLineItem.getLineItemGratuity()).orElse(0d));
                totalLet = Double.sum(totalLet, Optional.ofNullable(orderLineItem.getLineItemLet()).orElse(0d));
                totalServiceChargeFee = Double.sum(totalServiceChargeFee, Optional.ofNullable(orderLineItem.getLineItemServiceChargeFee()).orElse(0d));
                totalServiceChargeTax = Double.sum(totalServiceChargeTax, Optional.ofNullable(orderLineItem.getLineItemServiceChargeTax()).orElse(0d));
                totalTransactionFee = Double.sum(totalTransactionFee, Optional.ofNullable(orderLineItem.getLineItemTransactionFee()).orElse(0d));
                totalTransactionTax = Double.sum(totalTransactionTax, Optional.ofNullable(orderLineItem.getLineItemTransactionFeeTax()).orElse(0d));
                totalCasinoSurchargeAndTax = Double.sum(totalCasinoSurchargeAndTax, Optional.ofNullable(orderLineItem.getLineItemCasinoSurchargeAndTax()).orElse(0d));
            }
            priceDetails.setTotalCharge(Utils.roundTwoDecimalPlaces(totalCharge));
            priceDetails.setTotalTax(Utils.roundTwoDecimalPlaces(totalTax));
            priceDetails.setTotalPrice(Utils.roundTwoDecimalPlaces(totalPrice));
            priceDetails.setTotalStrikethroughPrice(totalStrikethroughPrice);
            priceDetails.setTotalDeposit(Utils.roundTwoDecimalPlaces(totalDeposit));
            priceDetails.setTotalDiscount(Utils.roundTwoDecimalPlaces(totalDiscount));
            priceDetails.setTotalBalanceDue(Utils.roundTwoDecimalPlaces(totalBalanceDue));
            priceDetails.setTotalAdjustedItemSubtotal(totalAdjustedItemSubtotal);
            order.setPriceDetails(priceDetails);

            roomTotals.setTotalAdjustedItemSubtotal(roomTotalAdjustedItemSubtotal);
            roomTotals.setTotalTourismFeeAndTax(Utils.roundTwoDecimalPlaces(totalTourismFeeAndTax));
            roomTotals.setTotalResortFeePerNight(Utils.roundTwoDecimalPlaces(totalResortFeePerNight));
            roomTotals.setTotalOccupancyFee(Utils.roundTwoDecimalPlaces(totalOccupancyFee));
            roomTotals.setTotalResortFeeAndTax(Utils.roundTwoDecimalPlaces(totalResortFeeAndTax));
            roomTotals.setTotalCasinoSurchargeAndTax(Utils.roundTwoDecimalPlaces(totalCasinoSurchargeAndTax));
            roomTotals.setTotalPrice(Utils.roundTwoDecimalPlaces(totalRoomPrice));
            roomTotals.setTotalTripSubtotal(Utils.roundTwoDecimalPlaces(totalTripSubtotal));
            order.setRoomTotals(roomTotals);

            showTotals.setTotalAdjustedItemSubtotal(showTotalAdjustedItemSubtotal);
            showTotals.setTotalServiceChargeFeeAndTax(totalServiceChargeFeeAndTax);
            showTotals.setTotalDeliveryFee(totalDeliveryFee);
            showTotals.setTotalGratuity(totalGratuity);
            showTotals.setTotalLet(totalLet);
            showTotals.setTotalServiceChargeFee(totalServiceChargeFee);
            showTotals.setTotalServiceChargeTax(totalServiceChargeTax);
            showTotals.setTotalTransactionFee(totalTransactionFee);
            showTotals.setTotalTransactionTax(totalTransactionTax);
            showTotals.setTotalPrice(totalShowPrice);
            order.setShowTotals(showTotals);
            
            if (order.getPackageConfigDetails() != null && order.getPackageConfigDetails().getPackagePricingDetails() != null) {
                order.getPackageConfigDetails().getPackagePricingDetails()
                        .setPackageTotal(order.getPackageConfigDetails().getPackagePricingDetails().getPackageBaseTotal()
                                + totalAddOnComponentChargeAndTax);
            }
        }
    }

    private boolean isSuccessOrPendingStatus(OrderLineItem.Status status) {
        return OrderLineItem.Status.SUCCESS == status || OrderLineItem.Status.PENDING == status;
    }

    private String generateOrderId(CheckoutRequest request) {
        final SecureRandom random = new SecureRandom();
        String orderId = "";
        char ch;
        for (int i = 0; i < ORDER_ID_LENGTH; i++) {
            if (random.nextInt(2) != 0) {
                ch = (char) ('A' + random.nextInt(26));
                orderId = orderId.concat(String.valueOf(ch));
            } else {
                orderId = orderId.concat(String.valueOf(random.nextInt(10)));
            }
        }
        return orderId;
    }

    public Order createOrderWithPendingStatus(List<CartLineItem> cartLineItems, Cart cart, CheckoutRequest request) throws AppException {
        final Order order = new Order();
        order.setId(generateOrderId(request));
        if (request.getGuestProfile() != null && StringUtils.isNotEmpty(request.getGuestProfile().getEmailAddress1())) {
            order.setEncryptedEmailAddress(SecurityFactory.get().encrypt(request.getGuestProfile().getEmailAddress1()));
        }


        for (CartLineItem cartLineItem : cartLineItems) {
            final OrderLineItem oli = new OrderLineItem();
            oli.setOrderLineItemId(UUID.randomUUID().toString());
            oli.setCartLineItemId(cartLineItem.getCartLineItemId());
            oli.setProductId(cartLineItem.getProductId());
            oli.setPropertyId(cartLineItem.getPropertyId());
            oli.setPackageId(cartLineItem.getPackageId());
            oli.setPackageLineItemId(cartLineItem.getPackageLineItemId());
            oli.setContent(cartLineItem.getContent());
            oli.setProductType(OrderLineItem.ProductType.fromValue(cartLineItem.getType().value()));
            oli.setLineItemCharge(cartLineItem.getLineItemTotalCharges());
            oli.setLineItemPrice(cartLineItem.getLineItemPrice());
            oli.setLineItemStrikethroughPrice(cartLineItem.getLineItemStrikethroughPrice());
            oli.setLineItemDeposit(cartLineItem.getLineItemDeposit());
            oli.setLineItemDiscount(cartLineItem.getLineItemDiscount());
            oli.setLineItemBalance(cartLineItem.getLineItemBalance());
            oli.setUpgraded(cartLineItem.getUpgraded() != null ? cartLineItem.getUpgraded() : false);

            oli.setStatus(OrderLineItem.Status.PENDING);
            oli.setEnableJwb(cartLineItem.getEnableJwb() != null ? cartLineItem.getEnableJwb() : false);
            
            oli.setAddOnsPrice(cartLineItem.getAddOnsPrice());
            oli.setAddOnsTax(cartLineItem.getAddOnsTax());
            
            if (cartLineItem.getItemSelectionDetails() != null && cartLineItem.getItemSelectionDetails().getRoomSelectionDetails() != null) {
                oli.setF1Package(cartLineItem.getItemSelectionDetails().getRoomSelectionDetails().getF1Package() != null
                        ? cartLineItem.getItemSelectionDetails().getRoomSelectionDetails().getF1Package()
                        : false);
            }

            if (cartLineItem.getItemSelectionDetails() != null && cartLineItem.getItemSelectionDetails().getShowSelectionDetails() != null) {
                try {
                    copySelectedDeliveryMethod(cartLineItem, oli);
                } catch (AppException e) {
                    // ignore the exception
                }
            }
            order.getOrderLineItems().add(oli);
        }

        order.setCartId(cart.getCartId());
        order.setCustomerId(request.getGuestProfile().getId());
        order.setMgmId(cart.getMgmId());
        order.setMlifeId(cart.getMlife());
        order.setEnableJwb(request.getEnableJwb());
        order.setType(com.mgmresorts.order.dto.services.Type.fromValue(cart.getType().value()));
        order.setVersion(com.mgmresorts.order.dto.services.Version.fromValue(cart.getVersion().value()));
        order.setStatus(Order.Status.PENDING);
        order.setCanRetryCheckout(false);
        order.setF1Package(false);
        order.setJwbFlow(cart.getJwbFlow());
        order.setPackageConfigDetails(toPackageConfigDetails(cart.getPackageConfigDetails()));
        order.setPaymentSessionId(cart.getPaymentSessionId());
        return order;
    }

    private void copySelectedDeliveryMethod(final CartLineItem input, final OrderLineItem output) throws AppException {
        final ShowSelectionDetails showDetails = getShowSelectionDetails(input);
        if (showDetails != null && !Utils.isEmpty(showDetails.getPermissibleDeliveryMethods())) {
            Optional<DeliveryMethod> selectedDeliveryMethod = Optional.empty();
            selectedDeliveryMethod = showDetails.getPermissibleDeliveryMethods().stream()
                    .filter(dm -> dm.getSelected() == true).findFirst();
            if (selectedDeliveryMethod.isPresent()) {
                output.setSelectedDeliveryMethod(deliveryMethodTransformer.toRight(selectedDeliveryMethod.get()));
                output.setLineItemDeliveryMethodFee(Optional.ofNullable(selectedDeliveryMethod.get().getAmount()).orElse(0d));
            }
        }
    }

    private ShowSelectionDetails getShowSelectionDetails(final CartLineItem input) {
        if (input.getItemSelectionDetails() != null && input.getItemSelectionDetails().getShowSelectionDetails() != null) {
            return input.getItemSelectionDetails().getShowSelectionDetails();
        }
        return null;
    }

    private PackageConfigDetails toPackageConfigDetails(final com.mgmresorts.shopping.cart.dto.PackageConfigDetails cartPackageConfigDetails) {
        if (cartPackageConfigDetails == null) {
            return null;
        }
        final PackageConfigDetails packageConfigDetails = new PackageConfigDetails();
        if (StringUtils.isNotBlank(cartPackageConfigDetails.getPackageCategoryId())) {
            packageConfigDetails.setPackageCategoryId(cartPackageConfigDetails.getPackageCategoryId());
        }
        if (StringUtils.isNotBlank(cartPackageConfigDetails.getPackagePriceBreakdown())) {
            packageConfigDetails.setPackagePriceBreakdown(cartPackageConfigDetails.getPackagePriceBreakdown());
        }
        if (StringUtils.isNotBlank(cartPackageConfigDetails.getPackageName())) {
            packageConfigDetails.setPackageName(cartPackageConfigDetails.getPackageName());
        }
        if (StringUtils.isNotBlank(cartPackageConfigDetails.getSeatMapOptions())) {
            packageConfigDetails.setSeatMapOptions(cartPackageConfigDetails.getSeatMapOptions());
        }
        if (cartPackageConfigDetails.getPackagePricingDetails() != null) {
            final PackagePricingDetails packagePricingDetails = new PackagePricingDetails();
            packagePricingDetails.setPackageBaseTotal(cartPackageConfigDetails.getPackagePricingDetails().getPackageBaseTotal());
            packagePricingDetails.setPackageStartingPrice(cartPackageConfigDetails.getPackagePricingDetails().getPackageStartingPrice());
            packagePricingDetails.setPackageTotal(cartPackageConfigDetails.getPackagePricingDetails().getPackageTotal());
            packagePricingDetails.setRoomModification(cartPackageConfigDetails.getPackagePricingDetails().getRoomModification());
            packagePricingDetails.setShowModification(cartPackageConfigDetails.getPackagePricingDetails().getShowModification());
            packagePricingDetails.setRoomTotal(cartPackageConfigDetails.getPackagePricingDetails().getRoomTotal());
            packagePricingDetails.setShowTotal(cartPackageConfigDetails.getPackagePricingDetails().getShowTotal());
            packagePricingDetails.setPackageInclusions(toPackageInclusions(cartPackageConfigDetails.getPackagePricingDetails().getPackageInclusions()));
            packagePricingDetails.setIsMultiDayEvent(cartPackageConfigDetails.getPackagePricingDetails().getIsMultiDayEvent());
            packagePricingDetails.setEventStartDate(cartPackageConfigDetails.getPackagePricingDetails().getEventStartDate());
            packagePricingDetails.setEventEndDate(cartPackageConfigDetails.getPackagePricingDetails().getEventEndDate());
            packageConfigDetails.setPackagePricingDetails(packagePricingDetails);
        }
        if (!Utils.isEmpty(cartPackageConfigDetails.getPackageComponentDetails())) {
            final List<PackageComponentDetail> packageComponentDetails = toPackageComponentDetailsOrderDTO(cartPackageConfigDetails.getPackageComponentDetails());
            packageConfigDetails.setPackageComponentDetails(packageComponentDetails);
        }
        return packageConfigDetails;
    }

    private static List<PackageComponentDetail> toPackageComponentDetailsOrderDTO(List<com.mgmresorts.shopping.cart.dto.PackageComponentDetail> packageRoomInclusionsCartDto) {
        final List<PackageComponentDetail> packageComponentDetails = new ArrayList<>();
        for (com.mgmresorts.shopping.cart.dto.PackageComponentDetail component
                : packageRoomInclusionsCartDto) {
            final PackageComponentDetail packageComponentDetailDto = new PackageComponentDetail();
            packageComponentDetailDto.setId(component.getId());
            packageComponentDetailDto.setDescription(component.getDescription());
            packageComponentDetailDto.setLongDescription(component.getLongDescription());
            packageComponentDetailDto.setShortDescription(component.getShortDescription());
            packageComponentDetailDto.setTaxRate(component.getTaxRate());
            packageComponentDetailDto.setNightlyCharge(component.getNightlyCharge());
            packageComponentDetailDto.setPricingApplied(component.getPricingApplied());
            packageComponentDetailDto.setRatePlanCode(component.getRatePlanCode());
            packageComponentDetailDto.setRatePlanName(component.getRatePlanName());
            packageComponentDetailDto.setAmtAftTax(component.getAmtAftTax());
            packageComponentDetailDto.setCode(component.getCode());
            packageComponentDetailDto.setStart(component.getStart());
            packageComponentDetailDto.setEnd(component.getEnd());
            packageComponentDetails.add(packageComponentDetailDto);
        }
        return packageComponentDetails;
    }

    private List<com.mgmresorts.order.dto.PackageCategoryInclusion> toPackageInclusions(
            final List<com.mgmresorts.shopping.cart.dto.PackageCategoryInclusion> cartPackageInclusion) {
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
            packageInclusions.setBookingDestinationDisplayText(cartInclusion.getBookingDestinationDisplayText());
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


    private List<OfferComponent> toRateComponents(final List<com.mgmresorts.shopping.cart.dto.OfferComponent> cartRateComponents) {
        if (Utils.isEmpty(cartRateComponents)) {
            return null;
        }

        return cartRateComponents.stream().map(cartRateComponent -> {
            final OfferComponent rateComponent = new OfferComponent();
            rateComponent.setComponentCode(cartRateComponent.getComponentCode());
            rateComponent.setPropertyComponents(toPropertyComponents(cartRateComponent.getPropertyComponents()));
            return rateComponent;
        }).collect(Collectors.toList());
    }

    private List<PropertyComponent> toPropertyComponents(final List<com.mgmresorts.shopping.cart.dto.PropertyComponent> cartPropertyComponents) {
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

    private Asset toAsset(final com.mgmresorts.shopping.cart.dto.Asset cartAsset) {
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