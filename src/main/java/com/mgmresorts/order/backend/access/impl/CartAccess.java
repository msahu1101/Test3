package com.mgmresorts.order.backend.access.impl;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exec.Retry;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.ICartAccess;
import com.mgmresorts.shopping.cart.dto.services.CartResponse;
import com.mgmresorts.shopping.cart.dto.CartType;
import com.mgmresorts.shopping.cart.dto.CartVersion;
import com.mgmresorts.shopping.cart.dto.services.HandleCheckoutRequest;
import com.mgmresorts.shopping.cart.dto.services.ManageCartPaymentSessionRequest;

public class CartAccess implements ICartAccess {
    /* Cart API specific access class */

    @Inject
    @Named("simulation.enabled")
    private IHttpService service;
    
    @Override
    public CartResponse getCart(final String id, final List<String[]> paramList) throws AppException, HttpFailureException {
        final String guestToken = CommonConfig.getGuestToken();
        final String callName = "cart-read";
        return Retry.of(3, CartResponse.class, HttpFailureException.class).exeute(() -> {
            return service.get(SHOPPING_CART_API_ENDPOINT + SHOPPING_CART_READ + PATH_SEPARATOR + id, CartResponse.class, callName, callName,
                    Arrays.asList(CommonConfig.getStandardHeaders(guestToken)), paramList.toArray(new String[][]{}));
        }, condition -> {
            return condition.getHttpCode() == 500 || condition.getHttpCode() == 502 || condition.getHttpCode() == 503 || condition.getHttpCode() == 504;
        });
    }
    
    @Override
    public CartResponse handleCheckOut(final HandleCheckoutRequest request) throws AppException, HttpFailureException {
        final String guestToken = CommonConfig.getGuestToken();
        final String callName = "cart-handle-checkout";
        return Retry.of(3, CartResponse.class, HttpFailureException.class).exeute(() -> {
            return service.post(SHOPPING_CART_API_ENDPOINT + SHOPPING_CART_UPDATE + PATH_SEPARATOR + "product/move", request, CartResponse.class, callName, callName,
                    CommonConfig.getStandardHeaders(guestToken));
        }, condition -> {
            return condition.getHttpCode() == 500 || condition.getHttpCode() == 502 || condition.getHttpCode() == 503 || condition.getHttpCode() == 504;
        });
    }
    
    @Override
    public CartResponse manageCartPaymentSession(final String id, final CartType cartType,
                                                 final CartVersion cartVersion, final ManageCartPaymentSessionRequest request) throws AppException, HttpFailureException {
        final String guestToken = CommonConfig.getGuestToken();
        final String callName = "cart-manage-payment-session";
        final String path = "session?cartType=" + cartType + "&cartVersion=" + cartVersion;
        return Retry.of(3, CartResponse.class, HttpFailureException.class).exeute(() -> {
            return service.put(SHOPPING_CART_API_ENDPOINT + SHOPPING_CART_PAYMENT_SESSION + PATH_SEPARATOR + id + PATH_SEPARATOR + path,
                    request, CartResponse.class, callName, callName, CommonConfig.getStandardHeaders(guestToken));
        }, condition -> {
            return condition.getHttpCode() == 500 || condition.getHttpCode() == 502 || condition.getHttpCode() == 503 || condition.getHttpCode() == 504;
        });
    }
}
