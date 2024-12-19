package com.mgmresorts.order.event;

import java.util.HashMap;
import java.util.Map;

public enum InEventType {

    SHOW_RESERVATION_CREATE("CREATE"),
    SHOW_RESERVATION_FAILURE("CREATE_FAILURE");

    private static Map<String, InEventType> constraints = new HashMap<String, InEventType>();
    static {
        for (InEventType type : InEventType.values()) {
            constraints.put(type.getValue().toLowerCase(), type);
        }
    }

    public static InEventType byValue(String key) {
        return key != null ? constraints.get(key.toLowerCase()) : null;
    }

    private String value;

    private InEventType(String name) {
        this.value = name;
    }

    public String getValue() {
        return value;
    }
}
