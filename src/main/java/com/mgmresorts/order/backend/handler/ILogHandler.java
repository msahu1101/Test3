package com.mgmresorts.order.backend.handler;

import java.util.Optional;

import com.microsoft.azure.functions.HttpRequestMessage;

public interface ILogHandler {

    String getRequestParamsLogDetails(HttpRequestMessage<Optional<String>> request);

    String getRequestParamsLogDetails(String keyType, String cartLineItemId, HttpRequestMessage<Optional<String>> payload);
}
