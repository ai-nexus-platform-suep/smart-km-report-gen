package com.powerreport.content.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.content.dto.LlmConfigRequest;
import com.powerreport.content.dto.LlmConfigResponse;
import com.powerreport.content.service.LlmConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/config/llm")
public class LlmConfigController {

    private final LlmConfigService llmConfigService;

    @GetMapping
    public ApiResponse<LlmConfigResponse> getConfig() {
        return ApiResponse.success(llmConfigService.getConfig());
    }

    @PutMapping
    public ApiResponse<LlmConfigResponse> updateConfig(@Valid @RequestBody LlmConfigRequest request) {
        return ApiResponse.success("配置已更新", llmConfigService.updateConfig(request));
    }
}
