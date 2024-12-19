package com.mgmresorts.order.backend.handler.impl;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.AppRuntimeException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.ICartAccess;
import com.mgmresorts.order.backend.handler.ICartHandler;
import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.dto.services.Version;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartType;
import com.mgmresorts.shopping.cart.dto.CartVersion;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.GuestProfile;
import com.mgmresorts.shopping.cart.dto.services.CartResponse;
import com.mgmresorts.shopping.cart.dto.services.HandleCheckoutRequest;
import com.mgmresorts.shopping.cart.dto.services.ManageCartPaymentSessionRequest;

public class CartHandler implements ICartHandler {

    private final Logger logger = Logger.get(CartHandler.class);
    
    @Inject
    private ICartAccess cartAccess;

    public Cart getCart(final String cartId, final String mgmId, final Type cartType, final Version cartVersion) throws AppException {
        CartResponse cartResponse;
        final List<String[]> paramList = new ArrayList<>();
        paramList.add(new String[] { "cartType", cartType != null ? cartType.value() : Type.GLOBAL.value() });
        paramList.add(new String[] { "cartVersion", cartVersion != null ? cartVersion.value() : Version.V1.value() });
        String id;
        if (StringUtils.isNotBlank(mgmId)) {
            paramList.add(new String[] { "keyType", "mgmId" });
            id = mgmId;
        } else {
            paramList.add(new String[] { "keyType", "id" });
            id = cartId;
        }
        try {
            cartResponse = cartAccess.getCart(id, paramList);
        } catch (HttpFailureException e) {
            throw new AppException(Errors.UNABLE_TO_GET_CART, e, "The cart id was: " + cartId + " and the mgmId was: " + mgmId);
        } catch (AppRuntimeException e) {
            throw new AppException(Errors.UNABLE_TO_GET_CART, e, "The cart id was: " + cartId + " and the mgmId was: " + mgmId);
        } catch (AppException e) {
            throw new AppException(Errors.UNABLE_TO_GET_CART, e, "The cart id was: " + cartId + " and the mgmId was: " + mgmId);
        } catch (Exception e) {
            throw new AppException(Errors.UNABLE_TO_GET_CART, e, "The cart id was: " + cartId + " and the mgmId was: " + mgmId);
        }
        if (cartResponse == null) {
            logger.error("The response from cart services was null.");
            throw new AppException(Errors.UNABLE_TO_GET_CART, "The response from cart services was invalid. " + "The cart id was: " + cartId + " and the mgmId was: " + mgmId);
        }
        return cartResponse.getCart();
    }
    
    @Override
    public void validateCartResponse(final Cart cart, final String cartId, final String mgmId) throws AppException {
        if (cart == null) {
            logger.error("No cart was found. The cart id was: " + cartId + " and the mgmId was: " + mgmId);
            throw new AppException(Errors.NO_CART_FOUND, cartId, mgmId);
        }
        if (Utils.isEmpty(cart.getCartLineItems())) {
            logger.error("The cart with cartId: " + cart.getCartId() + " is empty.");
            throw new AppException(Errors.EMPTY_CART, cartId, mgmId);
        }
        if (!Utils.isEmpty(cart.getCartLineItems())) {
            if (cart.getCartLineItems().stream().anyMatch(
                    cartLineItem -> !(cartLineItem.getStatus().equals(CartLineItem.Status.SAVED) || cartLineItem.getStatus().equals(CartLineItem.Status.PRICE_EXPIRED)))) {
                logger.error("Some cart items are in checkout ineligible states that is, other than saved or price expired. CartId: " + cartId + " and the mgmId was: " + mgmId);
                throw new AppException(Errors.NO_CHECKOUT_ELIGIBLE_INELIGIBLE_ITEMS, cartId, mgmId);
            }
        }
    }

    @Override
    public String handleCheckout(final String cartId, final List<String> failedProducts, final Boolean enableJwb) throws AppException {
        final HandleCheckoutRequest handleCheckoutRequest = new HandleCheckoutRequest();
        handleCheckoutRequest.setCartId(cartId);
        handleCheckoutRequest.setFailedCartLineItemIds(failedProducts);
        handleCheckoutRequest.setEnableJwb(enableJwb);
        CartResponse cartResponse = null;
        try {
            cartResponse = cartAccess.handleCheckOut(handleCheckoutRequest);
        } catch (HttpFailureException e) {
            throw new AppException(Errors.UNABLE_TO_MOVE_PRODUCT, "The response from cart services was invalid.");
        }
        if (cartResponse == null) {
            logger.error("The response from cart services was null.");
            throw new AppException(Errors.UNABLE_TO_MOVE_PRODUCT, "The response from cart services was invalid.");
        }
        if (cartResponse.getCart() == null) {
            return null;
        }
        return cartResponse.getCart().getCartId();
    }
    
    @Override
    public String manageCartPaymentSession(final String cartId, final CartType cartType, final CartVersion cartVersion, final GuestProfile guestProfile) throws AppException {
        final ManageCartPaymentSessionRequest manageCartPaymentSessionRequest = new ManageCartPaymentSessionRequest();
        manageCartPaymentSessionRequest.setGuestProfile(guestProfile);
        CartResponse cartResponse = null;
        try {
            cartResponse = cartAccess.manageCartPaymentSession(cartId, cartType, cartVersion, manageCartPaymentSessionRequest);
        } catch (HttpFailureException e) {
            logger.error("Unable to create new payment session for cart. cartId:" + cartId);
        } catch (AppException e) {
            logger.error("Unable to create new payment session for cart. cartId:" + cartId);
        }
        if (cartResponse != null && cartResponse.getCart() != null) {
            return cartResponse.getCart().getPaymentSessionId();
        }
        return null;
    }
}
