package com.mgmresorts.order.backend.handler.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.order.backend.handler.ILogHandler;
import com.microsoft.azure.functions.HttpRequestMessage;

public class LogHandler implements ILogHandler {

    private final Logger logger = Logger.get(LogHandler.class);
    private final JSonMapper mapper = new JSonMapper();
    private final String cartId = "cartId";
    private final String mgmId = "mgmId";
    private static final String sourceId = "sourceId";
    private static final String targetKey = "targetKey";
    private static final String targetType = "targetType";

    @Override
    public String getRequestParamsLogDetails(HttpRequestMessage<Optional<String>> payload) {
        final StringBuilder sb = new StringBuilder();

        try {
            final Map<String, String> map = updateFromPayload(payload);

            map.forEach((k, val) -> {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(k).append(":").append(" ").append(val);
            });
        } catch (Exception ex) {
            logger.error("Exception occurred in getRequestParamsLogDetails :  ", ex.getMessage());
        }
        return sb.toString();
    }

    @Override
    public String getRequestParamsLogDetails(String orderId, String cartLineItemId, HttpRequestMessage<Optional<String>> payload) {
        final StringBuilder sb = new StringBuilder();
        try {
            final Map<String, String> fileds = extractIdentifiers(payload);

            if (orderId != null) {
                fileds.put("orderId", orderId);
            }
            
            fileds.forEach((k, val) -> {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(k).append(":").append(" ").append(val);
            });
        } catch (Exception ex) {
            logger.error("Exception occurred : ", ex.getMessage());
        }
        return sb.toString();
    }
    
    private Map<String, String> updateFromPayload(HttpRequestMessage<Optional<String>> payload) throws JsonMappingException, JsonProcessingException {

        final Map<String, String> fields = new HashMap<>();
        if (payload != null && payload.getBody().isPresent()) {
            final Optional<String> body = payload.getBody();

            final JsonNode node = mapper.readTree(body.get());
            if (node.get(cartId) != null) {
                fields.put(cartId, node.get(cartId).asText());
            }
            if (node.get(mgmId) != null) {
                fields.put(mgmId, node.get(mgmId).asText());
            }
        }
        return fields;

    }

    private Map<String, String> extractIdentifiers(HttpRequestMessage<Optional<String>> payload) throws JsonMappingException, JsonProcessingException {
        final Map<String, String> fields = new HashMap<>();
        if (payload != null && payload.getBody().isPresent()) {
            final Optional<String> body = payload.getBody();

            final JsonNode node = mapper.readTree(body.get());
            if (node.get(cartId) != null) {
                fields.put(cartId, node.get(cartId).asText());
            }
            if (node.get(sourceId) != null) {
                fields.put(sourceId, node.get(sourceId).asText());
            }
            if (node.get(mgmId) != null) {
                fields.put(mgmId, node.get(mgmId).asText());
            }
            if (node.get(targetType) != null && node.get(targetKey) != null) {
                fields.put(node.get(targetType).asText(), node.get(targetKey).asText());
            }
        }
        return fields;
    }
}
