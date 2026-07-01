package com.qa.auth.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class RoleGrantRequest {

    private List<Long> permissionIds;
    private List<Long> menuIds;
}
