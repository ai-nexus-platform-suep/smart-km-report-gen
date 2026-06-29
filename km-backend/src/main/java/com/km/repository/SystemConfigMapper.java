package com.km.repository;

import com.km.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SystemConfigMapper {
    SystemConfig getByKey(@Param("configKey") String configKey);
    int upsert(SystemConfig config);
}
