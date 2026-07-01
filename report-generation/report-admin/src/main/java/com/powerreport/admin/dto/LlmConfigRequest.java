package com.powerreport.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LlmConfigRequest {

    @NotBlank(message = "apiUrl is required")
    private String apiUrl;

    private String apiKey;

    @NotBlank(message = "modelName is required")
    private String modelName;

    @Min(value = 1, message = "timeoutSeconds must be greater than 0")
    @Max(value = 600, message = "timeoutSeconds must be less than or equal to 600")
    private Integer timeoutSeconds;
}
