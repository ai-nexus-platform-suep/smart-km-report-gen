package com.km.controller.auth;

import com.km.common.dto.ApiResponse;
import com.km.dto.request.LoginRequest;
import com.km.dto.request.RegisterRequest;
import com.km.dto.response.LoginResponse;
import com.km.dto.response.UserInfoResponse;
import com.km.security.CurrentUserHolder;
import com.km.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册
     * POST /api/v1/auth/register
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success();
    }

    /**
     * 用户登录
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    /**
     * 获取当前登录用户信息
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> getCurrentUser() {
        Long userId = CurrentUserHolder.get().getUserId();
        UserInfoResponse response = authService.getCurrentUser(userId);
        return ApiResponse.success(response);
    }

    /**
     * 用户登出
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        Long userId = CurrentUserHolder.get().getUserId();
        authService.logout(userId);
        return ApiResponse.success();
    }
}
