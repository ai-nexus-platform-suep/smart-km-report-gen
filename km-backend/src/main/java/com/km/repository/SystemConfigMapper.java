package com.km.repository;

import com.km.entity.SystemConfig;
import java.util.List;

public interface SystemConfigMapper {
    List<SystemConfig> findAll();
    SystemConfig findByKey(String configKey);
    int upsert(SystemConfig config);
}
