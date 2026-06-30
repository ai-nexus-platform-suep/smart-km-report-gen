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
 * Python 内部接口：获取解密后的模型配置，不做鉴权
 */
@Slf4j
@RestController
@RequestMapping("/internal/model-configs")
@RequiredArgsConstructor
public class InternalModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping("/default")
    public ApiResponse<ModelConfigInternalVO> getDefault(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "chat") String scenario) {
        log.info("内部查询默认配置 userId={} scenario={}", userId, scenario);
        return ApiResponse.success(modelConfigService.getDefaultDecrypted(userId, scenario));
    }
}
