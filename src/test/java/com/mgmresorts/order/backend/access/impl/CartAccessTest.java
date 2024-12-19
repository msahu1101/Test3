package com.mgmresorts.order.backend.access.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.HttpService;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartType;
import com.mgmresorts.shopping.cart.dto.CartVersion;
import com.mgmresorts.shopping.cart.dto.services.CartResponse;
import com.mgmresorts.shopping.cart.dto.services.ManageCartPaymentSessionRequest;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;

public class CartAccessTest {
    @Tested
    private CartAccess cartAccess;
    @Injectable
    private IHttpService service;
    @Injectable
    private OAuthTokenRegistry registry;

    @BeforeAll
    public static void init() {
        System.setProperty("security.disabled.global", "true");
        ErrorManager.load(Errors.class);
    }
    
    @Test
    public void manageCartPaymentSessionSuccessTest() throws HttpFailureException, AppException {
        Cart cart = new Cart();
        cart.setCartId("cartId1");
        cart.setPaymentSessionId("paymentSessionId1");
        CartResponse cartResponse = new CartResponse();
        cartResponse.setCart(cart);      
        
        new Expectations() {
            {
                service.put(anyString, (ManageCartPaymentSessionRequest) any, CartResponse.class, "cart-manage-payment-session", "cart-manage-payment-session",
                        (HttpService.HttpHeaders.HttpHeader[]) any);
                result = cartResponse;
            }
        };
        
        ManageCartPaymentSessionRequest manageCartPaymentSessionRequest = new ManageCartPaymentSessionRequest();

        CartResponse response = cartAccess.manageCartPaymentSession("cartId1", CartType.GLOBAL, CartVersion.V1, manageCartPaymentSessionRequest);
        assertNotNull(response);
        assertNotNull(response.getCart());
        assertNotNull(response.getCart().getPaymentSessionId());
        assertEquals("paymentSessionId1", response.getCart().getPaymentSessionId());
    }
    
    @Test
    public void manageCartPaymentSessionFailureTest() throws HttpFailureException, AppException {
        new Expectations() {
            {
                service.put(anyString, (ManageCartPaymentSessionRequest) any, CartResponse.class, "cart-manage-payment-session", "cart-manage-payment-session",
                        (HttpService.HttpHeaders.HttpHeader[]) any);
                result = null;
            }
        };
        
        ManageCartPaymentSessionRequest manageCartPaymentSessionRequest = new ManageCartPaymentSessionRequest();
        
        CartResponse response = cartAccess.manageCartPaymentSession("cartId1", CartType.GLOBAL, CartVersion.V1, manageCartPaymentSessionRequest);
        assertNull(response);
    }
}
