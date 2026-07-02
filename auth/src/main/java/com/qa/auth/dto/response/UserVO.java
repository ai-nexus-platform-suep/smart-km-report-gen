package com.qa.auth.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private Integer gender;
    private Boolean enabled;
    private List<String> roles;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
