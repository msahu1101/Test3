package com.mgmresorts.order.backend.access;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.pim.model.PackagesEvent;

import java.util.Optional;

public interface IPIMAccess extends CommonConfig {
    Optional<PackagesEvent> getPackageConfigDetails(String packageId) throws AppException;
}
