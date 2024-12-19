package com.mgmresorts.order.service.transformer;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.order.dto.DeliveryMethod;

public class DeliveryMethodTransformer implements ITransformer<com.mgmresorts.shopping.cart.dto.DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod> {
    
    @Override
    public DeliveryMethod toRight(com.mgmresorts.shopping.cart.dto.DeliveryMethod left) throws AppException {
        if (left == null) {
            return null;
        }
        final DeliveryMethod deliveryMethod = new DeliveryMethod();
        deliveryMethod.setActive(left.getActive());
        deliveryMethod.setCode(left.getCode());
        deliveryMethod.setDescription(left.getDescription());
        deliveryMethod.setSelected(left.getSelected());
        deliveryMethod.setId(left.getId());
        deliveryMethod.setName(left.getName());
        deliveryMethod.setDefaultDeliveryMethod(left.getDefaultDeliveryMethod());
        deliveryMethod.setePrinting(left.getePrinting());
        deliveryMethod.setAmount(left.getAmount());

        return deliveryMethod;
    }
}
