package com.powerreport.admin.service;

import com.powerreport.admin.dto.LlmConfigRequest;
import com.powerreport.admin.dto.LlmConfigResponse;

public interface LlmConfigService {

    LlmConfigResponse getConfig();

    LlmConfigResponse updateConfig(LlmConfigRequest request);
}
