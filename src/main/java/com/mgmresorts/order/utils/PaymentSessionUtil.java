package com.mgmresorts.order.utils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.mgmresorts.common.function.HeaderBuilder;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.entity.CallType;
import com.mgmresorts.psm.model.AdditionalAttributes;
import com.mgmresorts.psm.model.ItemsGroupTotal;
import com.mgmresorts.psm.model.TFCosts;
import com.mgmresorts.psm.model.TotalAmount;
import com.mgmresorts.psm.model.Transaction;

public class PaymentSessionUtil {

    public static final int TIME_TO_LIVE = 30;
    public static final String GLOBAL = "GLOBAL";
    public static final String ONLINE = "Online";
    public static final String PENDING = "PENDING";

    public static List<AdditionalAttributes> extractAdditionalAttributes() {
        final AdditionalAttributes additionalAttributes = new AdditionalAttributes();
        //Future use
        additionalAttributes.setName(null);
        additionalAttributes.setValue(null);
        final List<AdditionalAttributes> additionalAttributesList = new ArrayList<>();
        additionalAttributesList.add(additionalAttributes);
        return additionalAttributesList;
    }

    public static String getSessionType(CallType callType) {
        if (callType == CallType.CREATE) {
            return  "Retrieve";
        } else {
            return "Modify";
        }
    }

    public static Transaction extractTransaction(String sessionId, CallType callType) {
        final Transaction transaction = new Transaction();
        final String transactionId = CommonConfig.getTransactionId();
        transaction.setTransactionId(transactionId);
        transaction.setCheckoutTime(ZonedDateTime.now(ZoneOffset.UTC).toString());
        transaction.setCartType(GLOBAL);
        transaction.setTransactionType(ONLINE);
        final String salesChannel = getSalesChannel();
        transaction.setSalesChannel(salesChannel);
        transaction.setOrderStatus(PENDING);
        final String sessionType = PaymentSessionUtil.getSessionType(callType);
        transaction.setSessionType(sessionType);
        transaction.setTimeToLive(TIME_TO_LIVE);
        transaction.setSessionId(StringUtils.isNotBlank(sessionId) ? sessionId : null);
        return transaction;
    }

    public static String getSalesChannel() {
        String salesChannel = null;
        if (!Utils.isEmpty(CommonConfig.getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_ORIGIN))) {
            salesChannel = CommonConfig.getHeaderValue(HeaderBuilder.HEADER_EXTERNAL_ORIGIN);
        }
        return salesChannel;
    }

    public static TFCosts createTFCost(String type, String code, Double resortFeeTax) {
        final TFCosts tfCosts = new TFCosts();
        tfCosts.setAmount(resortFeeTax);
        tfCosts.setCode(code);
        tfCosts.setType(type);
        return tfCosts;
    }

    public static ItemsGroupTotal createItemsGroupTotal(String name, Double value) {
        final ItemsGroupTotal itemsGroupTotal = new ItemsGroupTotal();
        itemsGroupTotal.setValue(value);
        itemsGroupTotal.setName(name);
        return itemsGroupTotal;
    }

    public static TotalAmount createTotalAmount(String name, Double value) {
        final TotalAmount totalAmount = new TotalAmount();
        totalAmount.setValue(value);
        totalAmount.setName(name);
        return totalAmount;
    }
    
    public static String getExpiryMonth(String expiry) {
        if (expiry == null) {
            return null;
        }
        //Example of expiry: "3/2025"
        final int indexOfForwardSlashChar = expiry.indexOf('/');
        if (indexOfForwardSlashChar != -1) {
            expiry = expiry.substring(0, indexOfForwardSlashChar);
            if (expiry.length() == 1) {
                return "0" + expiry;
            } else {
                return expiry;
            }
        }
        return null;
    }
    
    public static String getExpiryYear(String expiry) {
        if (expiry == null) {
            return null;
        }
        //Example of expiry: "3/2025"
        return expiry.substring(expiry.length() - 2);
    }
}
