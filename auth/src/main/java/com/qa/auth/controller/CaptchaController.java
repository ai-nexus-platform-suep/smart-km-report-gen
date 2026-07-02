package com.qa.auth.controller;

import com.myenglish.qacommon.dto.ApiCode;
import com.myenglish.qacommon.dto.ApiResponse;
import com.myenglish.qacommon.exception.BusinessException;
import com.qa.auth.service.CaptchaService;
import com.qa.auth.service.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 验证码 & 邮件验证码 控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;
    private final EmailService emailService;

    /**
     * 获取图形验证码
     */
    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> captcha() {
        return ApiResponse.success(captchaService.generate());
    }

    /**
     * 发送邮箱验证码（用于注册）
     */
    @PostMapping("/register/send-code")
    public ApiResponse<Void> sendRegisterCode(@Valid @RequestBody SendCodeRequest req) {
        emailService.sendVerificationCode(req.getEmail().trim().toLowerCase());
        return ApiResponse.success("验证码已发送", null);
    }

    @Data
    public static class SendCodeRequest {
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;
    }
}
