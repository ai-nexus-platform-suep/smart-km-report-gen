package com.powerreport.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TemplateConfigRequest {

    @NotNull(message = "configJson is required")
    private String configJson;
}
