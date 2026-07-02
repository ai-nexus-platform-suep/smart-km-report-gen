package com.qa.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_log")
public class SysLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String method;
    private String requestUri;
    private String requestMethod;
    private String requestParams;
    private Integer responseCode;
    private Boolean status;
    private String errorMsg;
    private String requestIp;
    private String userAgent;
    private Integer costMs;
    private LocalDateTime createdAt;
}
