package com.mgmresorts.order.backend.handler;

import java.util.List;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.dto.services.Version;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartType;
import com.mgmresorts.shopping.cart.dto.CartVersion;
import com.mgmresorts.shopping.cart.dto.GuestProfile;

public interface ICartHandler extends CommonConfig {
    public Cart getCart(final String cartId, final String mgmId, final Type cartType, final Version cartVersion) throws AppException;

    public String handleCheckout(final String cartId, final List<String> failedProducts, final Boolean enableJwb) throws AppException;
    
    public String manageCartPaymentSession(final String cartId, final CartType cartType, final CartVersion cartVersion, final GuestProfile guestProfile) throws AppException;
    
    public void validateCartResponse(final Cart cart, final String cartId, final String mgmId) throws AppException;
}
