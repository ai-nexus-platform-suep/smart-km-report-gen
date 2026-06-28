package com.km.service;

import com.km.dto.request.LoginRequest;
import com.km.dto.request.RegisterRequest;
import com.km.dto.response.LoginResponse;
import com.km.dto.response.UserInfoResponse;

public interface AuthService {

    /**
     * 用户注册
     */
    void register(RegisterRequest request);

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 获取当前登录用户信息
     */
    UserInfoResponse getCurrentUser(Long userId);

    /**
     * 用户登出
     */
    void logout(Long userId);
}
