package com.mgmresorts.order.service.task;

import com.mgmresorts.common.concurrent.Task;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.ThreadContext.TransactionContext;
import com.mgmresorts.order.dto.services.RetrieveReservationResponse;

public class ReservationTask extends Task<RetrieveReservationResponse> {
    private final String confirmationNumber;
    private final String firstName;
    private final String lastName;
    private final TransactionContext transactionContext;
    private final IProductHandler productHandler;

    protected ReservationTask() {
        this.confirmationNumber = null;
        this.firstName = null;
        this.lastName = null;
        this.transactionContext = null;
        this.productHandler = null;
    }

    public ReservationTask(String confirmationNumber, String firstName, String lastName, TransactionContext transactionContext, IProductHandler productHandler) {
        this.confirmationNumber = confirmationNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.transactionContext = transactionContext;
        this.productHandler = productHandler;
    }

    @Override
    protected RetrieveReservationResponse execute() throws Exception {
        ThreadContext.getContext().set(transactionContext);
        return productHandler.getReservation(confirmationNumber, firstName, lastName, false,null);
    }
}
