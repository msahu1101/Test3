package com.mgmresorts.order.entity;

import java.util.HashMap;
import java.util.Map;

public enum ProductType {

    ROOM("ROOM"), //
    SHOW("SHOW"), //
    DINING("DINING"), //
    AIR("AIR"), //
    LIMO("LIMO"), //
    SPA("SPA"),
    OTHER("OTHER");

    private final String value;
    private static final Map<String, ProductType> CONSTANTS = new HashMap<String, ProductType>();

    static {
        for (ProductType c : values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    private ProductType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    public String value() {
        return this.value;
    }

    public static ProductType fromValue(String value) {
        ProductType constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }
}