package com.qa.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
<<<<<<< Updated upstream
 * 登录请求
=======
 * 登录请求 - 支持用户名/邮箱 + 密码 + 验证码
>>>>>>> Stashed changes
 */
@Data
public class LoginRequest {

<<<<<<< Updated upstream
    @NotBlank(message = "用户名不能为空")
=======
    @NotBlank(message = "用户名/邮箱不能为空")
>>>>>>> Stashed changes
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
<<<<<<< Updated upstream
=======

    @NotBlank(message = "图形验证码不能为空")
    private String captchaCode;

    @NotBlank(message = "验证码标识不能为空")
    private String captchaKey;

    /**
     * 登录方式：USERNAME（默认）/ EMAIL
     */
    private String loginType = "USERNAME";
>>>>>>> Stashed changes
}
