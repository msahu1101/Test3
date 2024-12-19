package com.mgmresorts.order.errors;

import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.errors.SystemError;

public class Errors implements SystemError, ApplicationError {

    public Errors() {
        ErrorManager.load(Errors.class);
    }

}
