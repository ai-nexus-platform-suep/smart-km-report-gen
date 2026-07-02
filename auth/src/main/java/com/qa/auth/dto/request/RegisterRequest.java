package com.qa.auth.dto.request;

import lombok.Data;

/**
 * 注册请求 - 支持两种方式：
 * <ul>
 *   <li>USERNAME: username + password + confirmPassword + captcha</li>
 *   <li>EMAIL: email + emailCode + captcha（系统生成默认密码并邮件通知）</li>
 * </ul>
 */
@Data
public class RegisterRequest {

    /** 注册方式：USERNAME（默认）/ EMAIL */
    private String registerType = "USERNAME";

    // ===== 用户名+密码注册 =====
    private String username;
    private String password;
    private String confirmPassword;

    // ===== 邮箱注册 =====
    private String email;
    private String emailCode;

    // ===== 公共 =====
    private String captchaCode;
    private String captchaKey;
}
