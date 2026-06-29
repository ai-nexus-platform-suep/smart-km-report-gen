package com.km.repository;

import com.km.entity.SystemConfig;

import java.util.List;

public interface SystemConfigMapper {
    List<SystemConfig> findAll();
    SystemConfig findByKey(String configKey);
=======
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SystemConfigMapper {

    SystemConfig getByKey(@Param("configKey") String configKey);

    int upsert(SystemConfig config);
}
