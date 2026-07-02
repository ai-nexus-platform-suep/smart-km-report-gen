package com.qa.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_menu")
public class SysMenuEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String ancestors;
    private Integer level;
    private String menuName;
    private String menuCode;
    private String routePath;
    private String routeName;
    private String component;
    private String redirect;
    private String queryParam;
    private String permCode;
    private String menuType;
    private String icon;
    private Integer sortOrder;
    private Boolean visible;
    private Boolean hidden;
    private Boolean keepAlive;
    private Boolean alwaysShow;
    private Boolean isFrame;
    private String frameUrl;
    private Boolean enabled;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
