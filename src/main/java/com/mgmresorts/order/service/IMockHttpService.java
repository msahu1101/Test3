package com.mgmresorts.order.service;

import com.mgmresorts.common.dto.services.OutHeaderSupport;

public interface IMockHttpService {
    OutHeaderSupport mock(String timeout);
}
