package com.qa.auth.controller;

import com.qa.auth.dto.LoginResult;
import com.qa.auth.dto.request.LoginRequest;
import com.qa.auth.dto.request.RefreshTokenRequest;
import com.qa.auth.dto.request.RegisterRequest;
import com.qa.auth.dto.response.AuthResponse;
<<<<<<< Updated upstream
import com.qa.auth.dto.response.MenuVO;
import com.qa.auth.dto.response.UserVO;
import com.qa.auth.entity.SysUserEntity;
import com.qa.auth.service.AuthTokenService;
import com.qa.auth.service.MenuService;
import com.qa.auth.service.RefreshTokenService;
import com.qa.auth.service.UserService;
=======
import com.qa.auth.dto.response.CurrentUserVO;
import com.qa.auth.dto.response.MenuVO;
import com.qa.auth.dto.response.UserVO;
import com.qa.auth.entity.SysUserEntity;
import com.qa.auth.service.*;
>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
import java.util.HashMap;
import java.util.List;
import java.util.Map;
=======
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
>>>>>>> Stashed changes

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
<<<<<<< Updated upstream

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
=======
    private final CaptchaService captchaService;
    private final EmailService emailService;

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]");
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "password", "12345678", "123456789", "qwerty123", "admin123",
            "abc12345", "11111111", "aaaaaaaa", "Password1", "Pa$$w0rd");
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$");

    // ======================== 注册 ========================

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> register(@RequestBody RegisterRequest req) {
        String type = req.getRegisterType() != null ? req.getRegisterType().toUpperCase() : "USERNAME";

        // 公共：校验图形验证码
        if (isBlank(req.getCaptchaKey()) || isBlank(req.getCaptchaCode())
                || !captchaService.validate(req.getCaptchaKey(), req.getCaptchaCode())) {
            throw new BusinessException(ApiCode.CAPTCHA_ERROR, "图形验证码错误或已过期");
        }

        if ("EMAIL".equals(type)) {
            registerByEmail(req);
        } else {
            registerByUsername(req);
        }

        return ApiResponse.success("注册成功", null);
    }

    /** 用户名+密码注册 */
    private void registerByUsername(RegisterRequest req) {
        if (isBlank(req.getUsername())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "用户名不能为空");
        }
        if (req.getUsername().trim().length() < 3 || req.getUsername().trim().length() > 50) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "用户名长度需在 3-50 个字符之间");
        }
        if (isBlank(req.getPassword())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "密码不能为空");
        }
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "两次密码输入不一致");
        }
        validatePasswordStrength(req.getPassword());

        boolean success = userService.register(
                req.getUsername().trim(), req.getPassword().trim(), null);
        if (!success) {
            throw new BusinessException(ApiCode.USER_ALREADY_EXISTS, "用户已存在");
        }
        log.info("User registered by username: {}", req.getUsername());
    }

    /** 邮箱注册：系统生成默认密码并通过邮件发送 */
    private void registerByEmail(RegisterRequest req) {
        if (isBlank(req.getEmail()) || !EMAIL_PATTERN.matcher(req.getEmail()).matches()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "邮箱格式不正确");
        }
        if (isBlank(req.getEmailCode())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "邮箱验证码不能为空");
        }
        String email = req.getEmail().trim().toLowerCase();
        if (!emailService.validateCode(email, req.getEmailCode())) {
            throw new BusinessException(ApiCode.EMAIL_CODE_ERROR, "邮箱验证码错误或已过期");
        }

        String username = userService.registerByEmail(email);
        emailService.deleteCode(email);
        log.info("User registered by email: username={}, email={}", username, email);
    }

    /** 密码强度校验（复用 StrongPassword 逻辑） */
    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "密码长度不能少于8位");
        }
        if (WEAK_PASSWORDS.contains(password.toLowerCase())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "密码过于简单，请使用更复杂的密码");
        }
        int types = 0;
        if (UPPER.matcher(password).find()) types++;
        if (LOWER.matcher(password).find()) types++;
        if (DIGIT.matcher(password).find()) types++;
        if (SPECIAL.matcher(password).find()) types++;
        if (types < 3) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "密码需包含大写字母、小写字母、数字、特殊字符中的至少3种");
        }
    }

    // ======================== 登录 ========================

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        // 1. 校验图形验证码
        if (!captchaService.validate(req.getCaptchaKey(), req.getCaptchaCode())) {
            throw new BusinessException(ApiCode.CAPTCHA_ERROR, "图形验证码错误或已过期");
        }

        // 2. 登录（内置锁定检查）
        String account = req.getUsername().trim();
        String loginType = req.getLoginType() != null ? req.getLoginType() : "USERNAME";
        LoginResult loginResult = userService.login(
                account, req.getPassword().trim(), resolveClientIp(request), loginType);
>>>>>>> Stashed changes

        if (!loginResult.isSuccess()) {
            throw switch (loginResult.getCode()) {
                case LoginResult.USER_NOT_FOUND -> new BusinessException(ApiCode.USER_NOT_FOUND, "用户不存在");
                case LoginResult.USER_DISABLED -> new BusinessException(ApiCode.ACCOUNT_DISABLED, "账号已被禁用");
<<<<<<< Updated upstream
=======
                case LoginResult.ACCOUNT_LOCKED -> new BusinessException(ApiCode.ACCOUNT_LOCKED,
                        loginResult.getMessage() != null ? loginResult.getMessage() : "账号已被锁定");
>>>>>>> Stashed changes
                default -> new BusinessException(ApiCode.INVALID_PASSWORD, "密码错误");
            };
        }

        SysUserEntity user = loginResult.getUser().orElseThrow();
        AuthResponse response = authTokenService.issueTokens(user);
        log.info("User login: {}, roles={}", user.getUsername(), response.getRoles());
        return ApiResponse.success("登录成功", response);
    }

<<<<<<< Updated upstream
=======
    // ======================== Token 刷新 ========================

>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
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
=======
    // ======================== 当前用户 ========================

    @GetMapping("/me")
    public ApiResponse<CurrentUserVO> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = resolveUserId(authHeader);
        UserVO profile = userService.getProfile(userId);

        List<String> permissions = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (jwtTokenProvider.validateToken(token)) {
                permissions = jwtTokenProvider.getPermissions(token);
            }
        }
        return ApiResponse.success(CurrentUserVO.from(profile, permissions));
>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
=======
    // ======================== 私有辅助 ========================

>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
=======

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
>>>>>>> Stashed changes
}
