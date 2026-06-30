package com.qa.auth.controller;

import com.qa.auth.config.JwtProperties;
import com.qa.auth.dto.response.AuthResponse;
import com.qa.auth.dto.request.LoginRequest;
import com.qa.auth.dto.request.RefreshTokenRequest;
import com.qa.auth.dto.request.RegisterRequest;
import com.qa.auth.dto.LoginResult;
import com.qa.auth.entity.SysUserEntity;
import com.qa.auth.service.RefreshTokenService;
import com.qa.auth.service.UserService;
import com.qa.auth.util.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        String username = req.getUsername().trim();
        String password = req.getPassword().trim();

        boolean success = userService.register(username, password, "ROLE_USER");
        if (!success) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(buildResponse(1002, "用户已存在", null));
        }
        log.info("User registered: {}", username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildResponse(200, "注册成功", null));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        String username = req.getUsername().trim();
        String password = req.getPassword().trim();

        LoginResult loginResult = userService.login(username, password);

        if (!loginResult.isSuccess()) {
            return switch (loginResult.getCode()) {
                case LoginResult.USER_NOT_FOUND -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(buildResponse(1006, "用户不存在", null));
                case LoginResult.USER_DISABLED -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(buildResponse(1007, "账号已被禁用", null));
                default -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(buildResponse(1003, "密码错误", null));
            };
        }

        SysUserEntity user = loginResult.getUser().get();
        List<String> roles = parseRoles(user.getRoles());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), username, roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(username);

        long refreshExpiration = jwtProperties.getRefreshTokenExpiration();
        LocalDateTime refreshExpiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(Instant.now().getEpochSecond() + refreshExpiration),
                ZoneId.systemDefault()
        );
        refreshTokenService.saveToken(user.getId(), refreshToken, refreshExpiresAt);

        log.info("User login: {}, roles: {}", username, roles);

        AuthResponse response = new AuthResponse(
                accessToken, refreshToken, "Bearer",
                jwtProperties.getAccessTokenExpiration(), username, roles
        );
        return ResponseEntity.ok(buildResponse(200, "登录成功", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        String refreshToken = req.getRefreshToken();

        try {
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(buildResponse(1005, "Refresh Token 无效或已过期", null));
            }

            var claims = jwtTokenProvider.parseToken(refreshToken);
            String tokenType = claims.get("type", String.class);
            if (!"refresh".equals(tokenType)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(buildResponse(1004, "Token 类型错误", null));
            }

            String username = claims.getSubject();
            if (!refreshTokenService.isValid(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(buildResponse(1005, "Refresh Token 已失效，请重新登录", null));
            }

            refreshTokenService.deleteToken(refreshToken);

            SysUserEntity user = userService.findByUsername(username).orElse(null);
            List<String> roles = user != null
                    ? parseRoles(user.getRoles())
                    : List.of("ROLE_USER");
            Long userId = user != null ? user.getId() : null;

            String newAccessToken = jwtTokenProvider.generateAccessToken(userId, username, roles);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

            long refreshExpiration = jwtProperties.getRefreshTokenExpiration();
            LocalDateTime refreshExpiresAt = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(Instant.now().getEpochSecond() + refreshExpiration),
                    ZoneId.systemDefault()
            );
            refreshTokenService.saveToken(userId, newRefreshToken, refreshExpiresAt);

            AuthResponse response = new AuthResponse(
                    newAccessToken, newRefreshToken, "Bearer",
                    jwtProperties.getAccessTokenExpiration(), username, roles
            );
            return ResponseEntity.ok(buildResponse(200, "Token 刷新成功", response));

        } catch (Exception e) {
            log.error("Token refresh failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(buildResponse(1004, "Token 刷新失败", null));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(buildResponse(401, "未认证，请先登录", null));
        }

        String token = authHeader.substring(7).trim();

        try {
            if (!jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(buildResponse(401, "Token 无效或已过期", null));
            }

            String username = jwtTokenProvider.getUsername(token);
            List<String> roles = jwtTokenProvider.getRoles(token);

            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            data.put("roles", roles);

            return ResponseEntity.ok(buildResponse(200, "操作成功", data));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(buildResponse(401, "Token 无效", null));
        }
    }

    private Map<String, Object> buildResponse(int code, String message, Object data) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("message", message);
        map.put("data", data);
        return map;
    }

    private List<String> parseRoles(String rolesStr) {
        if (rolesStr == null || rolesStr.trim().isEmpty()) return List.of("ROLE_USER");
        return Arrays.stream(rolesStr.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
