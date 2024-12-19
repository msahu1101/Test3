package com.mgmresorts.order.backend.access;

import java.util.List;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.shopping.cart.dto.CartType;
import com.mgmresorts.shopping.cart.dto.CartVersion;
import com.mgmresorts.shopping.cart.dto.services.CartResponse;
import com.mgmresorts.shopping.cart.dto.services.HandleCheckoutRequest;
import com.mgmresorts.shopping.cart.dto.services.ManageCartPaymentSessionRequest;

public interface ICartAccess extends CommonConfig {
    CartResponse getCart(final String id, final List<String[]> paramList) throws AppException, HttpFailureException;
    
    CartResponse handleCheckOut(final HandleCheckoutRequest request) throws AppException, HttpFailureException;
    
    CartResponse manageCartPaymentSession(final String id, final CartType cartType, final CartVersion cartVersion,
                                          final ManageCartPaymentSessionRequest request) throws AppException, HttpFailureException;
}
