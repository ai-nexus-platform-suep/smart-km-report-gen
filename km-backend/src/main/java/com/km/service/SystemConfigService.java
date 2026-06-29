package com.km.service;

import com.km.dto.response.SystemConfigVO;
import java.util.List;
import java.util.Map;

public interface SystemConfigService {
    List<SystemConfigVO> findAll();
    SystemConfigVO findByKey(String configKey);
    SystemConfigVO update(String configKey, Map<String, Object> value);
}
