package com.powerreport.content.service;

import com.powerreport.content.dto.LlmConfigRequest;
import com.powerreport.content.dto.LlmConfigResponse;

public interface LlmConfigService {

    LlmConfigResponse getConfig();

    LlmConfigResponse updateConfig(LlmConfigRequest request);
}
