package com.qa.auth.controller;

import com.qa.auth.dto.LoginResult;
import com.qa.auth.dto.request.LoginRequest;
import com.qa.auth.dto.request.RefreshTokenRequest;
import com.qa.auth.dto.request.RegisterRequest;
import com.qa.auth.dto.response.AuthResponse;
import com.qa.auth.dto.response.MenuVO;
import com.qa.auth.dto.response.UserVO;
import com.qa.auth.entity.SysUserEntity;
import com.qa.auth.service.AuthTokenService;
import com.qa.auth.service.MenuService;
import com.qa.auth.service.RefreshTokenService;
import com.qa.auth.service.UserService;
import com.qa.auth.support.CurrentUserResolver;
import com.qa.auth.util.JwtTokenProvider;
import com.myenglish.qacommon.dto.ApiCode;
import com.myenglish.qacommon.dto.ApiResponse;
import com.myenglish.qacommon.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final AuthTokenService authTokenService;
    private final RefreshTokenService refreshTokenService;
    private final MenuService menuService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest req) {
        boolean success = userService.register(req.getUsername().trim(), req.getPassword().trim());
        if (!success) {
            throw new BusinessException(ApiCode.USER_ALREADY_EXISTS, "用户已存在");
        }
        log.info("User registered: {}", req.getUsername());
        return ApiResponse.success("注册成功", null);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        LoginResult loginResult = userService.login(
                req.getUsername().trim(), req.getPassword().trim(), resolveClientIp(request));

        if (!loginResult.isSuccess()) {
            throw switch (loginResult.getCode()) {
                case LoginResult.USER_NOT_FOUND -> new BusinessException(ApiCode.USER_NOT_FOUND, "用户不存在");
                case LoginResult.USER_DISABLED -> new BusinessException(ApiCode.ACCOUNT_DISABLED, "账号已被禁用");
                default -> new BusinessException(ApiCode.INVALID_PASSWORD, "密码错误");
            };
        }

        SysUserEntity user = loginResult.getUser().orElseThrow();
        AuthResponse response = authTokenService.issueTokens(user);
        log.info("User login: {}, roles={}", user.getUsername(), response.getRoles());
        return ApiResponse.success("登录成功", response);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        String refreshToken = req.getRefreshToken();
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ApiCode.TOKEN_EXPIRED, "Refresh Token 无效或已过期");
        }
        var claims = jwtTokenProvider.parseToken(refreshToken);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BusinessException(ApiCode.TOKEN_INVALID, "Token 类型错误");
        }
        if (!refreshTokenService.isValid(refreshToken)) {
            throw new BusinessException(ApiCode.TOKEN_EXPIRED, "Refresh Token 已失效，请重新登录");
        }
        refreshTokenService.deleteToken(refreshToken);

        String username = claims.getSubject();
        SysUserEntity user = userService.findActiveByUsername(username)
                .orElseThrow(() -> new BusinessException(ApiCode.USER_NOT_FOUND, "用户不存在"));
        return ApiResponse.success("Token 刷新成功", authTokenService.issueTokens(user));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = resolveUserId(authHeader);
        UserVO profile = userService.getProfile(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("id", profile.getId());
        data.put("username", profile.getUsername());
        data.put("nickname", profile.getNickname());
        data.put("realName", profile.getRealName());
        data.put("email", profile.getEmail());
        data.put("phone", profile.getPhone());
        data.put("avatar", profile.getAvatar());
        data.put("gender", profile.getGender());
        data.put("roles", profile.getRoles());
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (jwtTokenProvider.validateToken(token)) {
                data.put("permissions", jwtTokenProvider.getPermissions(token));
            }
        }
        return ApiResponse.success(data);
    }

    @GetMapping("/me/permissions")
    public ApiResponse<List<String>> myPermissions(@RequestHeader("Authorization") String authHeader) {
        String token = extractBearerToken(authHeader);
        return ApiResponse.success(jwtTokenProvider.getPermissions(token));
    }

    @GetMapping("/me/menus")
    public ApiResponse<List<MenuVO>> myMenus(@RequestHeader("Authorization") String authHeader) {
        String token = extractBearerToken(authHeader);
        Long userId = jwtTokenProvider.getUserId(token);
        return ApiResponse.success(menuService.listUserMenuTree(userId));
    }

    private Long resolveUserId(String authHeader) {
        try {
            return currentUserResolver.requireUserId();
        } catch (BusinessException ignored) {
            return jwtTokenProvider.getUserId(extractBearerToken(authHeader));
        }
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "未认证，请先登录");
        }
        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "Token 无效或已过期");
        }
        return token;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
