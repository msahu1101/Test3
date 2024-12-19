package com.mgmresorts.order.function;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.registry.FunctionRegistry;
import com.mgmresorts.common.utils.Utils;
import com.microsoft.azure.functions.HttpResponseMessage;

public class MainOrderFunctions {

    public static void main(String[] args) throws AppException, Exception {
        System.setProperty("runtime.environment", "local");
        String s = Utils.readAllBytes("/data/checkout.json");
        final HttpResponseMessage call = FunctionRegistry.getRegistry().call(IFunctions.NAME_CHECKOUT, "checkout", s);
        System.out.println(call.getBody());
    }
}
