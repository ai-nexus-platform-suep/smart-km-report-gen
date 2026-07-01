package com.qa.auth.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuVO {

    private Long id;
    private Long parentId;
    private String menuName;
    private String menuCode;
    private String routePath;
    private String routeName;
    private String component;
    private String redirect;
    private String permCode;
    private String menuType;
    private String icon;
    private Integer sortOrder;
    private Boolean hidden;
    private Boolean keepAlive;
    private Boolean alwaysShow;
    private List<MenuVO> children = new ArrayList<>();
}
