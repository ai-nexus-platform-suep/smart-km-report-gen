package com.km.controller.config;

import com.km.common.dto.ApiResponse;
import com.km.dto.request.EmbeddingConfigRequest;
import com.km.dto.request.ParserConfigRequest;
import com.km.dto.request.RerankConfigRequest;
import com.km.service.ConfigService;
import com.km.vo.ConfigTestResultVO;
import com.km.vo.EmbeddingConfigVO;
import com.km.vo.ParserConfigVO;
import com.km.vo.RerankConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@Validated
public class ConfigController {

    private final ConfigService configService;

    @GetMapping("/embedding")
    public ApiResponse<EmbeddingConfigVO> getEmbeddingConfig() {
        return ApiResponse.ok(configService.getEmbeddingConfig());
    }

    @PutMapping("/embedding")
    public ApiResponse<EmbeddingConfigVO> updateEmbeddingConfig(@RequestBody EmbeddingConfigRequest request) {
        return ApiResponse.ok(configService.updateEmbeddingConfig(request));
    }

    @GetMapping("/rerank")
    public ApiResponse<RerankConfigVO> getRerankConfig() {
        return ApiResponse.ok(configService.getRerankConfig());
    }

    @PutMapping("/rerank")
    public ApiResponse<RerankConfigVO> updateRerankConfig(@RequestBody RerankConfigRequest request) {
        return ApiResponse.ok(configService.updateRerankConfig(request));
    }

    @GetMapping("/parser")
    public ApiResponse<ParserConfigVO> getParserConfig() {
        return ApiResponse.ok(configService.getParserConfig());
    }

    @PutMapping("/parser")
    public ApiResponse<ParserConfigVO> updateParserConfig(@Valid @RequestBody ParserConfigRequest request) {
        return ApiResponse.ok(configService.updateParserConfig(request));
    }

    @PostMapping("/embedding/test")
    public ApiResponse<ConfigTestResultVO> testEmbeddingConfig() {
        return ApiResponse.ok(configService.testEmbeddingConfig());
    }

    @PostMapping("/rerank/test")
    public ApiResponse<ConfigTestResultVO> testRerankConfig() {
        return ApiResponse.ok(configService.testRerankConfig());
    }
}
