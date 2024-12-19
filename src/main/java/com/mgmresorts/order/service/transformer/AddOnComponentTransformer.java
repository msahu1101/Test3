package com.mgmresorts.order.service.transformer;

import java.util.ArrayList;
import java.util.List;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.dto.AddOnComponent;
import com.mgmresorts.order.dto.AddOnComponentPrice;

public class AddOnComponentTransformer implements ITransformer<com.mgmresorts.shopping.cart.dto.AddOnComponent, com.mgmresorts.order.dto.AddOnComponent> {
    
    @Override
    public AddOnComponent toRight(com.mgmresorts.shopping.cart.dto.AddOnComponent left) throws AppException {
        if (left == null) {
            return null;
        }
        final AddOnComponent addOnComponent = new AddOnComponent();
        addOnComponent.setActive(left.getActive());
        addOnComponent.setCode(left.getCode());
        addOnComponent.setSelected(left.getSelected());
        addOnComponent.setNonEditable(left.getNonEditable());
        addOnComponent.setId(left.getId());
        addOnComponent.setLongDescription(left.getLongDescription());
        addOnComponent.setShortDescription(left.getShortDescription());
        addOnComponent.setPricingApplied(toPricingApplied(left.getPricingApplied()));
        addOnComponent.setTripPrice(left.getTripPrice());
        addOnComponent.setTripTax(left.getTripTax());
        addOnComponent.setPrice(left.getPrice());
        addOnComponent.setDepositAmount(left.getDepositAmount());
        addOnComponent.setPrices(toAddOnComponentPrice(left.getPrices()));

        return addOnComponent;
    }

    private AddOnComponent.PricingApplied toPricingApplied(com.mgmresorts.shopping.cart.dto.AddOnComponent.PricingApplied left) {
       switch (left) {
           case CHECKIN:
               return AddOnComponent.PricingApplied.CHECKIN;
           case CHECKOUT:
               return AddOnComponent.PricingApplied.CHECKOUT;
           case NIGHTLY:
               return AddOnComponent.PricingApplied.NIGHTLY;
           case PERSTAY:
               return AddOnComponent.PricingApplied.PERSTAY;
               default:
                   return null;
       }
    }

    private List<AddOnComponentPrice> toAddOnComponentPrice(List<com.mgmresorts.shopping.cart.dto.AddOnComponentPrice> addOnsPriceLst) {
        if (Utils.isEmpty(addOnsPriceLst)) {
            return null;
        }

        final List<AddOnComponentPrice> addOnPrices = new ArrayList<AddOnComponentPrice>();

        for (com.mgmresorts.shopping.cart.dto.AddOnComponentPrice addOnComponentPrice : addOnsPriceLst) {
            final AddOnComponentPrice price = new AddOnComponentPrice();
            price.setAmount(addOnComponentPrice.getAmount());
            price.setDate(addOnComponentPrice.getDate());
            price.setTax(addOnComponentPrice.getTax());
            addOnPrices.add(price);
        }

        return addOnPrices;

    }
}
