package com.mgmresorts.order.service.task;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.order.AppliedBillings;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.dto.services.CancelReservationRequest;
import com.mgmresorts.order.dto.services.CancelReservationResponse;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.PreviewReservationRequest;
import com.mgmresorts.order.dto.services.PreviewReservationResponse;
import com.mgmresorts.order.dto.services.RetrieveReservationResponse;
import com.mgmresorts.order.dto.services.UpdateReservationRequest;
import com.mgmresorts.order.dto.services.UpdateReservationResponse;
import com.mgmresorts.shopping.cart.dto.CartLineItem;

import java.util.Map;

public interface IProductHandler extends CommonConfig {
    OrderLineItem checkout(CheckoutRequest request, CartLineItem cartLineItem, OrderLineItem orderLineItem,
            AppliedBillings billable, String orderId, com.mgmresorts.shopping.cart.dto.AgentInfo agentInfo,
            boolean skipAFS, boolean skipPaymentCapture, Map<String, PaymentAuthFields> paymentAuthFieldsMap,
            String orderReferenceNumber) throws AppException;

    RetrieveReservationResponse getReservation(String confirmationNumber, String firstName, String lastName,
            boolean createPaymentSession, String paymentSessionId) throws AppException;

    PreviewReservationResponse previewReservation(PreviewReservationRequest request) throws AppException;

    UpdateReservationResponse updateReservation(UpdateReservationRequest request,
            PaymentSessionBaseFields paymentSessionBaseFields) throws AppException;

    CancelReservationResponse cancelReservation(CancelReservationRequest request,
            PaymentSessionBaseFields paymentSessionBaseFields) throws AppException;
}