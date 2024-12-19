package com.mgmresorts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mgmresorts.common.dto.Status.Code;

import uk.co.jemos.podam.api.PodamFactoryImpl;

public class ObjectReferenceTest {
    @Test
    void testName() throws Exception {
        final ObjectReference pojo = new PodamFactoryImpl().manufacturePojo(ObjectReference.class);
        final Code code = pojo.getOutHeaderSupport().getHeader().getStatus().getCode();
        assertTrue(code == Code.FAILURE || code == Code.SUCCESS || code == Code.WARNING);
    }
}
