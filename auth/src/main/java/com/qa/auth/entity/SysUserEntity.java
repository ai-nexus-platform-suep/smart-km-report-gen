package com.qa.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String nickname;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private Integer gender;
    private String remark;
    private Boolean enabled;
    private Boolean deleted;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Long createdBy;
    private Long updatedBy;

    /**
     * Token版本号：角色/权限变更时递增，用于JWT校验，旧Token失效后需重新登录
     */
    private Long tokenVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
