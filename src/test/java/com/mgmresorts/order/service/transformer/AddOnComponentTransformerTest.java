package com.mgmresorts.order.service.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.AddOnComponent;

import uk.co.jemos.podam.api.PodamFactoryImpl;

class AddOnComponentTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final AddOnComponentTransformer transformer = new AddOnComponentTransformer();

    @Test
    final void testToRight() throws AppException {
        final com.mgmresorts.shopping.cart.dto.AddOnComponent addOnComponent = podamFactoryImpl.manufacturePojo(com.mgmresorts.shopping.cart.dto.AddOnComponent.class); 
        final AddOnComponent addOn = transformer.toRight(addOnComponent);
        assertEquals(addOn.getActive(), addOnComponent.getActive());
        assertEquals(addOn.getSelected(), addOnComponent.getSelected());
        assertEquals(addOn.getNonEditable(), addOnComponent.getNonEditable());
        assertEquals(addOn.getCode(), addOnComponent.getCode());
        assertEquals(addOn.getShortDescription(), addOnComponent.getShortDescription());
        assertEquals(addOn.getLongDescription(), addOnComponent.getLongDescription());
        assertEquals(addOn.getId(), addOnComponent.getId());
        assertEquals(addOn.getPricingApplied().value(), addOnComponent.getPricingApplied().value());
        assertEquals(addOn.getTripPrice(), addOnComponent.getTripPrice());
        assertEquals(addOn.getTripTax(), addOnComponent.getTripTax());
        assertEquals(addOn.getPrice(), addOnComponent.getPrice());
        assertEquals(addOn.getDepositAmount(), addOnComponent.getDepositAmount());
        assertEquals(addOn.getPrices().size(), addOnComponent.getPrices().size());
        for (int i=0; i < addOn.getPrices().size(); i++) {
            assertEquals(addOn.getPrices().get(i).getAmount(), addOnComponent.getPrices().get(i).getAmount());
            assertEquals(addOn.getPrices().get(i).getDate(), addOnComponent.getPrices().get(i).getDate());
            assertEquals(addOn.getPrices().get(i).getTax(), addOnComponent.getPrices().get(i).getTax());
        }
    }

}
