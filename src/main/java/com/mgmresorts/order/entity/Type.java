package com.mgmresorts.order.entity;

import java.util.HashMap;
import java.util.Map;

import com.mgmresorts.common.utils.Utils;

public enum Type {

        GLOBAL("GLOBAL"), //
        PACKAGE("PACKAGE");

        private final String value;
        private static final Map<String, Type> CONSTANTS = new HashMap<String, Type>();

        static {
            for (Type c : values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private Type(String value) {
            if (Utils.isEmpty(value)) {
                this.value = "GLOBAL";
            } else {
                this.value = value;
            }
        }

        @Override
        public String toString() {
            return this.value;
        }

        public String value() {
            return this.value;
        }

        public static Type fromValue(String value) {
            Type constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }
}
