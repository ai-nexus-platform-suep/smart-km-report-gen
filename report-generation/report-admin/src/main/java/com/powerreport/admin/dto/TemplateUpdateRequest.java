package com.powerreport.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TemplateUpdateRequest {

    @Size(max = 200, message = "name is too long")
    private String name;

    private String reportType;

    @Size(max = 50, message = "version is too long")
    private String version;

    private String configJson;

    private Boolean enabled;
}
