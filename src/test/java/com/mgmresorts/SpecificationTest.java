package com.mgmresorts;
import com.mgmresorts.common.specification.Specification;
import com.mgmresorts.common.specification.BaseSpecification.Output;

public class SpecificationTest {

    public static void main(String[] args) throws Exception {
        System.setProperty("runtime.environment", "local");
        System.setProperty("security.disabled.global", "true");

        System.out.println(new Specification().definition("http://localhost:8080", null, Output.JSON, false, "ritwick", null, null));
    }

}
