package com.mgmresorts.order.backend.handler;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.shopping.cart.dto.Cart;

public interface IItineraryHandler extends CommonConfig {

    public String create(GuestProfile profile, Cart cart) throws AppException;
}
