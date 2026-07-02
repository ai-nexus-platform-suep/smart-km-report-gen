package com.qa.auth.dto.request;

import lombok.Data;

@Data
public class UpdateUserRequest {

    private String nickname;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private Integer gender;
    private Boolean enabled;
    private String password;
}
