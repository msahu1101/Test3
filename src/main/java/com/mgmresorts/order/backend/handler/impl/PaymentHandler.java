package com.mgmresorts.order.backend.handler.impl;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.IPaymentAccess;
import com.mgmresorts.order.backend.handler.IPaymentHandler;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.dto.services.CheckoutRequest;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.payments.model.AFSAuthPaymentMethods;
import com.mgmresorts.payments.model.AFSAuthTransactionDetails;
import com.mgmresorts.payments.model.AFSAuthTransactionDetails.BookingType;
import com.mgmresorts.payments.model.AFSAuthTransactionDetails.TransactionType;
import com.mgmresorts.payments.model.BillTo;
import com.mgmresorts.payments.model.BillingDetails;
import com.mgmresorts.payments.model.Card;
import com.mgmresorts.payments.model.GuestDetails;
import com.mgmresorts.payments.model.Headers;
import com.mgmresorts.payments.model.PPSAuthPaymentMethods;
import com.mgmresorts.payments.model.PaymentsOrchestrationWorkFlowResponse;
import com.mgmresorts.payments.model.PaymentsOrchestrationWorkFlowResquest;
import com.mgmresorts.payments.model.QueryParams;
import com.mgmresorts.payments.model.WorkflowArgs;
import com.mgmresorts.payments.model.WorkflowDefs;
import com.mgmresorts.payments.model.WorkflowRequestBody;
import com.mgmresorts.payments.model.WorkflowResponse;

public class PaymentHandler implements IPaymentHandler {

    private final Logger logger = Logger.get(PaymentHandler.class);
    final JSonMapper mapper = new JSonMapper();
    
    @Inject
    private IPaymentAccess paymentAccess;

    public static final String FUNCTION_NAME_AFS = "AFSAuthorize";
    public static final String FUNCTION_NAME_PPS = "PPSAuthorize";
    public static final String CONFIRMATION_PENDING = "PENDING";
    public static final String PAYMENT_TYPE = "Credit Card";
    public static final String CURRENCY_CODE = "USD";
    public static final double ZERO_$_AUTH = 0.00;
    public static final String MERCHANT_ID = Runtime.get().getConfiguration("payment.authorization.merchantid");
    public static final String ACCEPT = "ACCEPT";
    public static final String APPROVED = "APPROVED";
    public static final String X_API_VERSION = "1.0";

    public boolean validatePaymentMethod(final CheckoutRequest request) throws AppException {

        try {
            final PaymentsOrchestrationWorkFlowResquest paymentsOrchestrationWorkFlowResquest = getPaymentsOrchestrationWorkFlowResquest(request);
            final String responseString = paymentAccess.validatePaymentMethod(paymentsOrchestrationWorkFlowResquest);
            final PaymentsOrchestrationWorkFlowResponse paymentsOrchestrationWorkFlowResponse = mapper
                    .readValue(responseString, PaymentsOrchestrationWorkFlowResponse.class);
            return processPaymentsOrchestrationWorkFlowResponse(paymentsOrchestrationWorkFlowResponse);
        } catch (HttpFailureException e) {
            logger.error("[Error from Payments Service] Something unexpected happened in payment validation : "
                    + e.getMessage(), e);
            throw new AppException(ApplicationError.UNABLE_TO_VALIDATE_PAYMENT_METHOD, e, e.getMessage());
        }
    }

    private PaymentsOrchestrationWorkFlowResquest getPaymentsOrchestrationWorkFlowResquest(
            final CheckoutRequest request) {
        final PaymentsOrchestrationWorkFlowResquest paymentsOrchestrationWorkFlowResquest = new PaymentsOrchestrationWorkFlowResquest();
        paymentsOrchestrationWorkFlowResquest.setArguments(createWorkFlowArguments());
        paymentsOrchestrationWorkFlowResquest.setDefinitions(createWorkFlowDefinitions(request));
        return paymentsOrchestrationWorkFlowResquest;
    }

    private WorkflowArgs createWorkFlowArguments() {
        final WorkflowArgs workflowArgs = new WorkflowArgs();
        workflowArgs.setOrder(Arrays.asList(FUNCTION_NAME_AFS, FUNCTION_NAME_PPS));
        return workflowArgs;
    }

    private List<WorkflowDefs> createWorkFlowDefinitions(final CheckoutRequest request) {
        final List<WorkflowDefs> listWorkflowDefs = new ArrayList<>();

        // Set AFS Authorize work flow definition
        final WorkflowDefs afsWorkFlowDefns = new WorkflowDefs();
        afsWorkFlowDefns.setFunctionName(FUNCTION_NAME_AFS);
        afsWorkFlowDefns.setBody(buildAFSAuthBody(request));
        afsWorkFlowDefns.setHeaders(buildHeaders());
        afsWorkFlowDefns.setQueryParams(buildQueryParams());
        listWorkflowDefs.add(afsWorkFlowDefns);

        // Set PPS Authorize work flow definition
        final WorkflowDefs ppsWorkFlowDefns = new WorkflowDefs();
        ppsWorkFlowDefns.setFunctionName(FUNCTION_NAME_PPS);
        ppsWorkFlowDefns.setBody(buildPPSAuthBody(request));
        ppsWorkFlowDefns.setHeaders(buildHeaders());
        ppsWorkFlowDefns.setQueryParams(buildQueryParams());
        listWorkflowDefs.add(ppsWorkFlowDefns);

        return listWorkflowDefs;
    }

    private WorkflowRequestBody buildAFSAuthBody(final CheckoutRequest request) {
        final WorkflowRequestBody afsWorkflowRequestBody = new WorkflowRequestBody();

        final AFSAuthTransactionDetails afsAuthTransactionDetails = new AFSAuthTransactionDetails();
        afsAuthTransactionDetails.setTransactionId(UUID.randomUUID().toString());
        afsAuthTransactionDetails.setInauthTransactionId(request.getInAuthTransactionId());
        afsAuthTransactionDetails.setConfirmationNumbers(CONFIRMATION_PENDING);
        afsAuthTransactionDetails.setBookingType(BookingType.ROOM);
        afsAuthTransactionDetails.setTransactionType(TransactionType.ONLINE);
        afsAuthTransactionDetails.setTransactionDateTime(String.valueOf(ZonedDateTime.now(ZoneOffset.UTC)));
        afsAuthTransactionDetails.setSalesChannel(ThreadContext.getContext().get().getChannel());
        afsAuthTransactionDetails.setGuest(buildGuestDetails(request.getGuestProfile()));
        afsAuthTransactionDetails.setBilling(buildAFSBilling(request.getBillings()));

        afsWorkflowRequestBody.setTransaction(afsAuthTransactionDetails);
        return afsWorkflowRequestBody;
    }

    private WorkflowRequestBody buildPPSAuthBody(final CheckoutRequest request) {
        final WorkflowRequestBody ppsWorkflowRequestBody = new WorkflowRequestBody();
        ppsWorkflowRequestBody.setMerchantID(MERCHANT_ID);
        ppsWorkflowRequestBody.setTransactionRefCode(UUID.randomUUID().toString());
        ppsWorkflowRequestBody.setBillTo(buildPPSBillingTo(request.getBillings()));
        ppsWorkflowRequestBody.setPaymentMethods(buildPPSPaymentMethods(request.getBillings()));
        ppsWorkflowRequestBody.setAmount(ZERO_$_AUTH);
        return ppsWorkflowRequestBody;
    }

    private GuestDetails buildGuestDetails(final GuestProfile guestProfile) {
        if (guestProfile == null) {
            return null;
        }
        final GuestDetails guestDetails = new GuestDetails();
        guestDetails.setLoggedIn(!Utils.isEmpty(guestProfile.getMlifeNo()));
        guestDetails.setMemberId(!Utils.isEmpty(guestProfile.getMlifeNo()) ? guestProfile.getMlifeNo() : null);
        guestDetails.setFirstName(guestProfile.getFirstName());
        guestDetails.setLastName(guestProfile.getLastName());
        guestDetails.setEmailAddress(guestProfile.getEmailAddress1());
        guestDetails.setPhone(
                !Utils.isEmpty(guestProfile.getPhoneNumbers()) ? guestProfile.getPhoneNumbers().get(0).getNumber()
                        : null);
        if (!Utils.isEmpty(guestProfile.getAddresses())) {
            guestDetails.setAddress1(guestProfile.getAddresses().get(0).getStreet1());
            guestDetails.setAddress2(guestProfile.getAddresses().get(0).getStreet2());
            guestDetails.setCity(guestProfile.getAddresses().get(0).getCity());
            guestDetails.setState(guestProfile.getAddresses().get(0).getState());
            guestDetails.setPostalCode(guestProfile.getAddresses().get(0).getPostalCode());
            guestDetails.setCountry(guestProfile.getAddresses().get(0).getCountry());
        }
        return guestDetails;
    }

    private BillingDetails buildAFSBilling(final List<Billing> billings) {
        if (Utils.isEmpty(billings)) {
            return null;
        }
        final BillingDetails billingDetails = new BillingDetails();

        billingDetails.setPaymentMethods(buildAFSPaymentMethods(billings));
        return billingDetails;
    }

    private List<AFSAuthPaymentMethods> buildAFSPaymentMethods(final List<Billing> billings) {
        return billings.stream().map(billing -> toAFSPaymentMethods(billing)).collect(Collectors.toList());
    }

    private AFSAuthPaymentMethods toAFSPaymentMethods(final Billing billing) {
        final AFSAuthPaymentMethods afsAuthPaymentMethods = new AFSAuthPaymentMethods();
        afsAuthPaymentMethods.setPaymentType(PAYMENT_TYPE);
        afsAuthPaymentMethods.setCardHolderName(billing.getPayment().getCardHolder());
        afsAuthPaymentMethods.setBillingAddress1(billing.getAddress().getStreet1());
        afsAuthPaymentMethods.setBillingAddress2(billing.getAddress().getStreet2());
        afsAuthPaymentMethods.setBillingCity(billing.getAddress().getCity());
        afsAuthPaymentMethods.setBillingState(billing.getAddress().getState());
        afsAuthPaymentMethods.setBillingPostalCode(billing.getAddress().getPostalCode());
        afsAuthPaymentMethods.setBillingCountry(billing.getAddress().getCountry());
        afsAuthPaymentMethods.setCreditCardType(billing.getPayment().getType().value());
        afsAuthPaymentMethods.setPaymentToken(billing.getPayment().getCcToken());
        afsAuthPaymentMethods.setCreditCardExpireMonth(billing.getPayment().getExpiry().split("/")[0]);
        afsAuthPaymentMethods.setCreditCardExpireYear(billing.getPayment().getExpiry().split("/")[1]);
        afsAuthPaymentMethods.setCurrencyCode(CURRENCY_CODE);
        afsAuthPaymentMethods.setTransactionChargeAmount(billing.getPayment().getAmount());
        return afsAuthPaymentMethods;
    }

    private Headers buildHeaders() {
        final Headers headers = new Headers();
        headers.setSource(ThreadContext.getContext().get().getChannel());
        headers.setxApiVersion(X_API_VERSION);
        headers.setxMgmCorrelationId(ThreadContext.getContext().get().getCorrelationId());
        return headers;
    }

    private QueryParams buildQueryParams() {
        final QueryParams queryParams = new QueryParams();
        return queryParams;
    }

    private BillTo buildPPSBillingTo(final List<Billing> billings) {
        if (Utils.isEmpty(billings)) {
            return null;
        }
        final BillTo billTo = new BillTo();

        final Optional<Billing> billing = Optional.ofNullable(billings.get(0));

        if (billing.isPresent()) {

            billTo.setFirstName(billing.get().getPayment().getFirstName());
            billTo.setLastName(billing.get().getPayment().getLastName());
            billTo.setStreet1(billing.get().getAddress().getStreet1());
            billTo.setCity(billing.get().getAddress().getCity());
            billTo.setState(billing.get().getAddress().getState());
            billTo.setPostalCode(billing.get().getAddress().getPostalCode());
        }

        return billTo;
    }

    private PPSAuthPaymentMethods buildPPSPaymentMethods(final List<Billing> billings) {
        if (Utils.isEmpty(billings)) {
            return null;
        }
        final PPSAuthPaymentMethods ppsAuthPaymentMethods = new PPSAuthPaymentMethods();

        final Optional<Billing> billing = Optional.ofNullable(billings.get(0));

        if (billing.isPresent()) {
            final Card card = new Card();
            card.setPaymentToken(billing.get().getPayment().getCcToken());
            card.setCvv(billing.get().getPayment().getCvv());
            card.setExpirationMonth(billing.get().getPayment().getExpiry().split("/")[0]);
            card.setExpirationYear(billing.get().getPayment().getExpiry().split("/")[1]);
            ppsAuthPaymentMethods.setCard(card);
        }

        return ppsAuthPaymentMethods;
    }

    private boolean processPaymentsOrchestrationWorkFlowResponse(
            final PaymentsOrchestrationWorkFlowResponse paymentsOrchestrationWorkFlowResponse) throws AppException {
        boolean isPaymentMethodValidationSuccess = false;
        boolean afsAuthorized = false;
        boolean ppsAuthorized = false;

        // get the AFS workflow response object to inspect the response data
        final Optional<WorkflowResponse> afsWorkFlowResponse = paymentsOrchestrationWorkFlowResponse
                .getWorkflowResponse().stream().filter(e -> e.getFunctionName().equalsIgnoreCase(FUNCTION_NAME_AFS))
                .findAny();

        if (afsWorkFlowResponse.isPresent()) {
            afsAuthorized = afsWorkFlowResponse.get().getBody() != null
                    ? afsWorkFlowResponse.get().getBody().getAuthorized() != null
                            ? afsWorkFlowResponse.get().getBody().getAuthorized()
                            : false
                    : false;
            if (!afsAuthorized && afsWorkFlowResponse.get().getBody() != null && !Utils.isEmpty(afsWorkFlowResponse.get().getBody().getMsg())) {
                throw new AppException(Errors.AFS_AUTHORIZATION_FAILED, afsWorkFlowResponse.get().getBody().getMsg());
            }
        } else {
            throw new AppException(Errors.AFS_AUTHORIZATION_FAILED, "AFS workflow response is not present.");
        }

        // get the PPS workflow response object to inspect the response data
        final Optional<WorkflowResponse> ppsWorkFlowResponse = paymentsOrchestrationWorkFlowResponse
                .getWorkflowResponse().stream().filter(e -> e.getFunctionName().equalsIgnoreCase(FUNCTION_NAME_PPS))
                .findAny();

        if (ppsWorkFlowResponse.isPresent()) {
            ppsAuthorized = ppsWorkFlowResponse.get().getBody() != null
                    ? (!Utils.isEmpty(ppsWorkFlowResponse.get().getBody().getStatusMessage())
                            && ppsWorkFlowResponse.get().getBody().getStatusMessage().equalsIgnoreCase(APPROVED))
                    : false;

            if (!ppsAuthorized && ppsWorkFlowResponse.get().getBody() != null && !Utils.isEmpty(ppsWorkFlowResponse.get().getBody().getStatusMessage())) {
                throw new AppException(Errors.PAYMENT_AUTHORIZATION_FAILED, ppsWorkFlowResponse.get().getBody().getStatusMessage());
            }
        } else {
            throw new AppException(Errors.PAYMENT_AUTHORIZATION_FAILED, "PPS workflow response is not present.");
        }

        isPaymentMethodValidationSuccess = afsAuthorized && ppsAuthorized;

        return isPaymentMethodValidationSuccess;
    }
}
