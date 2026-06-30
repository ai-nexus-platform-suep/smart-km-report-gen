package com.powerreport.admin.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.admin.dto.LlmConfigRequest;
import com.powerreport.admin.dto.LlmConfigResponse;
import com.powerreport.admin.service.LlmConfigService;
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
        return ApiResponse.success("LLM config updated", llmConfigService.updateConfig(request));
    }
}
