package com.qa.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class SysPermissionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String permCode;
    private String permName;
    private String permType;
    private String httpMethod;
    private String apiPath;
    private String module;
    private String description;
    private Boolean enabled;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
