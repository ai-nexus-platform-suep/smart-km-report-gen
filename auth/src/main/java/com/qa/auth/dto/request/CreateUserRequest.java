package com.qa.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank
    @Size(max = 50)
    private String username;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    private String nickname;
    private String realName;
    private String email;
    private String phone;
}
