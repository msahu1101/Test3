package com.mgmresorts.order.backend.access;

import java.util.Optional;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.content.model.PackageConfig;
import com.mgmresorts.content.model.ShowEvent;

public interface IContentAccess extends CommonConfig {
    Optional<PackageConfig[]> getPackageConfigDetails(String packageId) throws AppException;
    
    ShowEvent getShowEventDetailsByEventId(String showEventId) throws AppException;
}
