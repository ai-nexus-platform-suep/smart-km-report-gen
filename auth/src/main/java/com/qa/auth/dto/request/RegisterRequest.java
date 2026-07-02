package com.qa.auth.dto.request;

<<<<<<< Updated upstream
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求
=======
import lombok.Data;

/**
 * 注册请求 - 支持两种方式：
 * <ul>
 *   <li>USERNAME: username + password + confirmPassword + captcha</li>
 *   <li>EMAIL: email + emailCode + captcha（系统生成默认密码并邮件通知）</li>
 * </ul>
 * 字段校验在 Controller 层按 registerType 条件执行
>>>>>>> Stashed changes
 */
@Data
public class RegisterRequest {

<<<<<<< Updated upstream
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度需在 6-100 个字符之间")
    private String password;
=======
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
>>>>>>> Stashed changes
}
