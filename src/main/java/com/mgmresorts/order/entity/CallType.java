package com.mgmresorts.order.entity;

import java.util.HashMap;
import java.util.Map;

public enum CallType {
    CREATE("Create"),
    UPDATE("Update");

    private final String value;
    private static final Map<String, CallType> CONSTANTS = new HashMap<>();

    static {
        for (CallType c : values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    CallType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    public String value() {
        return this.value;
    }

    public static CallType fromValue(String value) {
        CallType constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }
}
