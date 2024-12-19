package com.mgmresorts.order.backend.handler.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.order.errors.Errors;
import com.microsoft.azure.functions.HttpRequestMessage;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Tested;

public class LogHandlerTest {

    @Tested
    private LogHandler logHandler;

    @Mocked
    private HttpRequestMessage<Optional<String>> httpRequestMessage;

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
        ErrorManager.load(Errors.class);
    }

    @Test
    public void testGetRequestParamsLogDetails_WhenCartIdPassedAsInput() throws Exception {
        this.mockHttpRequestMessage("{\"mgmId\":\"66964e2b-2550-4476-84c3-1a4c0c5c067f\",\"cartLineItemId\":\"76964e2b-2550-4476-84c3-1a4c0c5c067f\"}");
        String result = logHandler.getRequestParamsLogDetails(httpRequestMessage);
        assertEquals("mgmId: 66964e2b-2550-4476-84c3-1a4c0c5c067f", result);
    }

    @Test
    public void testGetRequestParamsLogDetails_WhenMgmIdIdPassedAsInput() throws Exception {
        this.mockHttpRequestMessage("{\"cartId\":\"66964e2b-2550-4476-84c3-1a4c0c5c067f\",\"cartLineItemId\":\"76964e2b-2550-4476-84c3-1a4c0c5c067f\"}");
        String result = logHandler.getRequestParamsLogDetails(httpRequestMessage);
        assertEquals("cartId: 66964e2b-2550-4476-84c3-1a4c0c5c067f", result);
    }

    private void mockHttpRequestMessage(String payload) {
        new Expectations() {
            {
                httpRequestMessage.getBody();
                result = Optional.of(payload);
            }
        };
    }
}
