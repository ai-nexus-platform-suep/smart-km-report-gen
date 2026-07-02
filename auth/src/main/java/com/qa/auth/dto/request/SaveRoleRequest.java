package com.qa.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveRoleRequest {

    @NotBlank
    private String roleCode;

    @NotBlank
    private String roleName;

    private String description;
    private Boolean enabled;
    private Integer sortOrder;
}
