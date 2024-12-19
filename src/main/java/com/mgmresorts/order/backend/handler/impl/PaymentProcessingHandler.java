package com.mgmresorts.order.backend.handler.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.notification.Email;
import com.mgmresorts.common.notification.Emailer;
import com.mgmresorts.common.notification.SmtpEmailer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.backend.access.IPaymentProcessingAccess;
import com.mgmresorts.order.backend.handler.IPaymentProcessingHandler;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.pps.model.Amount;
import com.mgmresorts.pps.model.Payment;
import com.mgmresorts.pps.model.Payment.CardPresentEnum;
import com.mgmresorts.pps.model.PaymentRequest;
import com.mgmresorts.pps.model.PaymentResponse;
import com.mgmresorts.pps.model.Result;
import com.mgmresorts.pps.model.TenderDetails;
import com.mgmresorts.pps.model.Transaction;

public class PaymentProcessingHandler implements IPaymentProcessingHandler {

    private final Logger logger = Logger.get(PaymentProcessingHandler.class);
    final JSonMapper mapper = new JSonMapper();

    @Inject
    private IPaymentProcessingAccess paymentProcessingAccess;

    @Inject
    private SmtpEmailer smtpEmailer;

    @Override
    public void captureTransaction(final String orderId, final String orderReferenceNumber, final PaymentAuthFields paymentAuthFields) throws AppException {
        final PaymentRequest request = createCapturePaymentRequest(orderId, orderReferenceNumber, paymentAuthFields);
        try {
            final PaymentResponse response = paymentProcessingAccess.capturePayment(request);
            checkGatewayResponse(response, request, "Capture", orderId, orderReferenceNumber);
        } catch (SourceAppException e) {
            logger.error("Exception occurred while capturing transaction via PPS: {}", e.getMessage());
            sendEmail(request, e.getRaw(), "Capture", orderId, orderReferenceNumber);
            throw e;
        } catch (AppException e) {
            logger.error("Exception occurred while capturing transaction via PPS: {}", e.getMessage());
            sendEmail(request, e.getMessage(), "Capture", orderId, orderReferenceNumber);
            throw e;
        }
    }

    @Override
    public void voidTransaction(final String orderId, final String orderReferenceNumber, final PaymentAuthFields paymentAuthFields) {
        final PaymentRequest request = createVoidPaymentRequest(orderId, orderReferenceNumber, paymentAuthFields);
        try {
            final PaymentResponse response = paymentProcessingAccess.voidPayment(request);
            checkGatewayResponse(response, request, "Void", orderId, orderReferenceNumber);
        } catch (SourceAppException e) {
            logger.error("Exception occurred while voiding transaction via PPS: {}", e.getMessage());
            sendEmail(request, e.getRaw(), "Void", orderId, orderReferenceNumber);
        } catch (AppException e) {
            logger.error("Exception occurred while voiding transaction via PPS: {}", e.getMessage());
            sendEmail(request, e.getMessage(), "Void", orderId, orderReferenceNumber);
        }
    }

    @Override
    public PaymentAuthFields refundTransaction(final String orderId, final String orderReferenceNumber, final String confirmationNumber, final double amount,
                                  final List<Billing> billings, final String sessionId, final String itemId) throws AppException {
        final PaymentRequest request = createRefundPaymentRequest(orderId, orderReferenceNumber, confirmationNumber, amount, billings, sessionId, itemId);
        try {
            final PaymentResponse response = paymentProcessingAccess.refundPayment(request);
            return checkGatewayResponse(response, request, "Refund", orderId, orderReferenceNumber);
        } catch (SourceAppException e) {
            logger.error("Exception occurred while refunding transaction via PPS: {}", e.getMessage());
            sendEmail(request, e.getRaw(), "Refund", orderId, orderReferenceNumber);
            throw e;
        } catch (AppException e) {
            logger.error("Exception occurred while refunding transaction via PPS: {}", e.getMessage());
            sendEmail(request, e.getMessage(), "Refund", orderId, orderReferenceNumber);
            throw e;
        }
    }

    private PaymentRequest createCapturePaymentRequest(final String orderId, final String orderReferenceNumber, final PaymentAuthFields paymentAuthFields) {

        final PaymentRequest paymentRequest = new PaymentRequest();

        paymentRequest.setSessionId(paymentAuthFields.getSessionId());
        paymentRequest.setMgmId(paymentAuthFields.getMgmId());
        paymentRequest.setClientReferenceNumber(paymentAuthFields.getConfirmationNumber());
        paymentRequest.setPaymentId(paymentAuthFields.getPaymentId());
        paymentRequest.setItemId(paymentAuthFields.getItemId());

        final List<Amount> amountList = new ArrayList<>();

        final Amount total = new Amount();
        total.setName("total");
        total.setValue(BigDecimal.valueOf(paymentAuthFields.getAmount()));
        amountList.add(total);

        paymentRequest.setAmount(amountList);
        return paymentRequest;
    }

    private PaymentRequest createVoidPaymentRequest(final String orderId, final String orderReferenceNumber, final PaymentAuthFields paymentAuthFields) {

        final PaymentRequest paymentRequest = new PaymentRequest();

        paymentRequest.setSessionId(paymentAuthFields.getSessionId());
        paymentRequest.setMgmId(paymentAuthFields.getMgmId());
        paymentRequest.setClientReferenceNumber(orderReferenceNumber);
        paymentRequest.setPaymentId(paymentAuthFields.getPaymentId());

        final List<Amount> amountList = new ArrayList<>();

        final Amount total = new Amount();
        total.setName("total");
        total.setValue(BigDecimal.valueOf(paymentAuthFields.getAmount()));
        amountList.add(total);

        paymentRequest.setAmount(amountList);
        return paymentRequest;
    }

    private PaymentRequest createRefundPaymentRequest(final String orderId, final String orderReferenceNumber, final String confirmationNumber,
                                                      final double amount, final List<Billing> billings, final String sessionId, final String itemId) {

        final PaymentRequest paymentRequest = new PaymentRequest();

        paymentRequest.setSessionId(sessionId);
        paymentRequest.setItemId(itemId);
        paymentRequest.setClientReferenceNumber(confirmationNumber);

        final List<Amount> amountList = new ArrayList<>();

        final Amount total = new Amount();

        total.setName("total");
        total.setValue(BigDecimal.valueOf(amount));
        amountList.add(total);

        final Payment payment = new Payment();
        final TenderDetails tenderDetails = new TenderDetails();
        tenderDetails.setMgmToken("");
        tenderDetails.setExpireMonth("");
        tenderDetails.setExpireYear("");

        if (!Utils.isEmpty(billings)) {
            final Billing billing = billings.stream().findFirst().get();
            if (billing.getPayment() != null) {
                tenderDetails.setMgmToken(billing.getPayment().getCcToken());
                final String expiry = billing.getPayment().getExpiry();

                if (!Utils.isEmpty(expiry) && expiry.contains("/")) {
                    final String[] expiryList = expiry.split("/");

                    final String expireMonth = expiryList[0].length() == 1 ? "0" + expiryList[0] : expiryList[0];
                    final String expireYear = expiryList[1].length() > 2 ? expiryList[1].substring(expiryList[1].length() - 2) : expiryList[1];

                    tenderDetails.setExpireMonth(expireMonth);
                    tenderDetails.setExpireYear(expireYear);
                }
            }
        }
        payment.setTenderDetails(tenderDetails);
        payment.setCardPresent(CardPresentEnum.N);

        paymentRequest.setAmount(amountList);
        paymentRequest.setPayment(payment);
        return paymentRequest;
    }

    private PaymentAuthFields checkGatewayResponse(final PaymentResponse response, final PaymentRequest request, final String callName,
                                                   final String orderId, final String orderReferenceNumber) {
        // Check if there is a direct failure from Shift4 gateway
        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        if (response != null && !Utils.isEmpty(response.getResults())) {
            final Result result = response.getResults().stream().findFirst().get();
            if (result != null && result.getGatewayResult() != null) {
                final Transaction transaction = result.getGatewayResult().getTransaction();
                if (result.getGatewayResult().getTransactionStatus().equalsIgnoreCase("SUCCESS")
                && transaction != null && !Utils.isEmpty(transaction.getAuthorizationCode())) {
                    paymentAuthFields.setSuccess(true);
                    paymentAuthFields.setAuthorizationCode(transaction.getAuthorizationCode());
                    return paymentAuthFields;
                } else {
                    if (transaction != null && transaction.getGatewayResponse() != null) {
                        logger.error("PPS " + callName + " call was not successful: {}", mapper.writeValueAsString(response));
                        final String errorMessage = transaction.getGatewayResponse().getReasonCode()
                                + " | " + transaction.getGatewayResponse().getReasonDescription()
                                + " | " + transaction.getGatewayResponse().getReattemptPermission();
                        sendEmail(request, errorMessage, callName, orderId, orderReferenceNumber);
                        paymentAuthFields.setErrorCode(transaction.getGatewayResponse().getReasonCode());
                        paymentAuthFields.setErrorMessage(transaction.getGatewayResponse().getReasonDescription());
                    }
                }
            }
        }
        paymentAuthFields.setSuccess(false);
        return paymentAuthFields;
    }

    private void sendEmail(final PaymentRequest request, final String errorMessage, final String callName, final String orderId, final String orderReferenceNumber) {
        final String lowerEnvTag = !StringUtils.equalsAnyIgnoreCase(Runtime.get().readableEnvironment(), "PROD") ? "[NON-PROD]" : "";
        try {
            final Email emailReq = Emailer.wrap(lowerEnvTag + "Urgent! Payment Processing Service " + callName + " transaction failed!",
                    Arrays.asList(!Utils.isEmpty(PPS_SEND_EMAIL_TO_LIST) ? PPS_SEND_EMAIL_TO_LIST.split(",") : new String[] { }),
                    Arrays.asList(!Utils.isEmpty(PPS_SEND_EMAIL_CC_LIST) ? PPS_SEND_EMAIL_CC_LIST.split(",") : new String[] { }),
                    Arrays.asList(!Utils.isEmpty(PPS_SEND_EMAIL_BCC_LIST) ? PPS_SEND_EMAIL_BCC_LIST.split(",") : new String[] { }));

            final String emailContentBody = createPPSFailureEmailBody(request, errorMessage, orderId, orderReferenceNumber);
            logger.info("PPS " + callName + " transaction failure email body content generated: {}", emailContentBody);
            emailReq.setHtmlBody(Email.Html.init().body(emailContentBody).build());
            smtpEmailer.send(emailReq);
        } catch (AppException e) {
            logger.error("Exception occurred while sending PPS " + callName + " transaction failure email : {}", e.getMessage());
        }
    }

    private String createPPSFailureEmailBody(final PaymentRequest request, final String errorMessage, final String orderId, final String orderReferenceNumber) {
        final String ppsFailureEmailTemplate = "<b><u>Order Id</u> :</b> %s \n "
                + "<b><u>Order Reference Number</u> :</b> %s \n <b><u>Client Reference Number</u> :</b> %s \n <b><u>Payment Id</u> :</b> %s \n "
                + "<b><u>Amount Total</u> :</b> %s \n <b><u>Error Message</u> :</b> %s \n <b><u>Correlation id</u> :</b> %s";

        final Optional<Amount> totalField = request.getAmount().stream()
                .filter(amount -> amount != null && amount.getName() != null && amount.getName().equalsIgnoreCase("total")).findFirst();
        double totalValue = 0.0;
        if (totalField.isPresent()) {
            totalValue = totalField.get().getValue() != null ? totalField.get().getValue().doubleValue() : 0.0;
        }

        return String
                .format(ppsFailureEmailTemplate, orderId, orderReferenceNumber, request.getClientReferenceNumber(), request.getPaymentId(),
                        totalValue, errorMessage, ThreadContext.getContext().get().getCorrelationId())
                .replace("\n", "<br/><br/>");
    }
}
