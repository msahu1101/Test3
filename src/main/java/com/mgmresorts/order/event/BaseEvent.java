package com.mgmresorts.order.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mgmresorts.common.event.IEvent;

public abstract class BaseEvent implements IEvent {

    @JsonIgnore
    public String getReferenceNumber() {
        return getId();
    }

    public abstract String getId();
}
