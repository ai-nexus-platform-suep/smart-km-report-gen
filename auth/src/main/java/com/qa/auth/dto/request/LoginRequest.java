package com.qa.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 - 支持用户名/邮箱 + 密码 + 验证码
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名/邮箱不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "图形验证码不能为空")
    private String captchaCode;

    @NotBlank(message = "验证码标识不能为空")
    private String captchaKey;

    /** 登录方式：USERNAME（默认）/ EMAIL */
    private String loginType = "USERNAME";
}
