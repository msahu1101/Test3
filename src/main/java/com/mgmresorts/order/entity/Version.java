package com.mgmresorts.order.entity;

import java.util.HashMap;
import java.util.Map;

import com.mgmresorts.common.utils.Utils;

public enum Version {

        V1("V1"), //
        V2("V2");

        private final String value;
        private static final Map<String, Version> CONSTANTS = new HashMap<String, Version>();

        static {
            for (Version c : values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private Version(String value) {
            if (Utils.isEmpty(value)) {
                this.value = "V1";
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

        public static Version fromValue(String value) {
            Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }
}
