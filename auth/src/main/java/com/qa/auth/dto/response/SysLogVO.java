package com.qa.auth.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String requestUri;
    private String requestMethod;
    private Integer responseCode;
    private Boolean status;
    private String requestIp;
    private Integer costMs;
    private LocalDateTime createdAt;
}
