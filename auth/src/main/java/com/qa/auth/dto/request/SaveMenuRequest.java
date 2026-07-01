package com.qa.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveMenuRequest {

    private Long parentId;
    private String menuName;

    @NotBlank
    private String menuCode;

    private String routePath;
    private String routeName;
    private String component;
    private String redirect;
    private String permCode;
    private String menuType;
    private String icon;
    private Integer sortOrder;
    private Boolean visible;
    private Boolean hidden;
    private Boolean keepAlive;
    private Boolean alwaysShow;
    private Boolean enabled;
}
