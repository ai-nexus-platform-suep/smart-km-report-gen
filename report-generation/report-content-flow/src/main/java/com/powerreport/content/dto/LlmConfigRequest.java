package com.powerreport.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LlmConfigRequest {

    @NotBlank(message = "API 地址不能为空")
    private String apiUrl;

    private String apiKey;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    @Min(value = 1, message = "超时时间必须大于 0")
    @Max(value = 600, message = "超时时间不能超过 600 秒")
    private Integer timeoutSeconds;
}
