package com.myenglish.qachat.controller;

import com.myenglish.qachat.dto.resp.ModelConfigInternalVO;
import com.myenglish.qachat.service.ModelConfigService;
import com.myenglish.qacommon.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部模型配置接口（供 Python qa-agent 微服务调用）
 *
 * <p>不走网关 JWT 鉴权，仅用于微服务间内部通信。
 * 返回解密后的完整模型配置信息（含明文 API Key），不对外暴露。
 *
 */
@Slf4j
@RestController
@RequestMapping("/internal/model-configs")
@RequiredArgsConstructor
public class InternalModelConfigController {

    private final ModelConfigService modelConfigService;

    /**
     * 获取解密后的默认模型配置
     *
     * 返回包含明文 API Key 的完整配置，供 Python 端直接使用。
     * Python 通过 query 参数传入 userId。
     *
     * @param userId   用户 ID（Python qa-agent 直连本接口，不走 Gateway，不带 X-User-Id Header）
     * @param scenario 使用场景（如 chat、summary），默认 "chat"
     * @return 解密后的模型配置（provider、baseUrl、modelName、apiKey 等）
     */
    @GetMapping("/default")
    public ApiResponse<ModelConfigInternalVO> getDefault(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "chat") String scenario) {
        log.info("内部查询默认配置 userId={} scenario={}", userId, scenario);
        return ApiResponse.success(modelConfigService.getDefaultDecrypted(userId, scenario));
    }
}
