package com.mgmresorts.order.service.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.dto.DeliveryMethod;

import uk.co.jemos.podam.api.PodamFactoryImpl;

class DeliveryMethodTransformerTest {

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();
    private final DeliveryMethodTransformer transformer = new DeliveryMethodTransformer();

    @Test
    final void testToRight() throws AppException {
        final com.mgmresorts.shopping.cart.dto.DeliveryMethod deliveryMethod = podamFactoryImpl.manufacturePojo(com.mgmresorts.shopping.cart.dto.DeliveryMethod.class); 
        final DeliveryMethod dm = transformer.toRight(deliveryMethod);
        assertEquals(dm.getActive(), deliveryMethod.getActive());
        assertEquals(dm.getSelected(), deliveryMethod.getSelected());
        assertEquals(dm.getCode(), deliveryMethod.getCode());
        assertEquals(dm.getName(), deliveryMethod.getName());
        assertEquals(dm.getId(), deliveryMethod.getId());
        assertEquals(dm.getAmount(), deliveryMethod.getAmount());
        assertEquals(dm.getDefaultDeliveryMethod(), deliveryMethod.getDefaultDeliveryMethod());
        assertEquals(dm.getDescription(), deliveryMethod.getDescription());
        assertEquals(dm.getePrinting(), deliveryMethod.getePrinting());
    }

}
