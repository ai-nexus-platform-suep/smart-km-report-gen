package com.km.controller.internal;

import com.km.common.dto.ApiResponse;
import com.km.service.ConfigService;
import com.km.vo.EmbeddingConfigVO;
import com.km.vo.ParserConfigVO;
import com.km.vo.RerankConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供 km-ai-service 读取运行时配置（含完整 API Key）。
 * 仅在内网调用，勿暴露到公网网关。
 */
@RestController
@RequestMapping("/internal/config")
@RequiredArgsConstructor
public class InternalConfigController {

    private final ConfigService configService;

    @GetMapping("/embedding")
    public ApiResponse<EmbeddingConfigVO> embedding() {
        return ApiResponse.ok(configService.getEmbeddingConfigInternal());
    }

    @GetMapping("/rerank")
    public ApiResponse<RerankConfigVO> rerank() {
        return ApiResponse.ok(configService.getRerankConfigInternal());
    }

    @GetMapping("/parser")
    public ApiResponse<ParserConfigVO> parser() {
        return ApiResponse.ok(configService.getParserConfig());
    }
}
