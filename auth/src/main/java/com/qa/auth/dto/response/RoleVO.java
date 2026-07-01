package com.qa.auth.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Boolean enabled;
    private Integer sortOrder;
    private List<Long> permissionIds;
    private List<Long> menuIds;
    private LocalDateTime createdAt;
}
