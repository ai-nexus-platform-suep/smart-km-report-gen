package com.qa.auth.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AssignRolesRequest {

    @NotEmpty
    private List<String> roleCodes;
}
