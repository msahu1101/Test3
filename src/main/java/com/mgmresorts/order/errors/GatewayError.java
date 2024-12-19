package com.mgmresorts.order.errors;

public final class GatewayError {
    public Fault fault;
    
    public class Detail {
        public String errorcode;
    }

    public class Fault {
        public String faultstring;
        public Detail detail;
    }
}
