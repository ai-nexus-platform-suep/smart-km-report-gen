package com.powerreport.gateway.controller;

import com.powerreport.gateway.config.JwtProperties;
import com.powerreport.gateway.dto.AuthResponse;
import com.powerreport.gateway.dto.LoginRequest;
import com.powerreport.gateway.dto.RefreshTokenRequest;
import com.powerreport.gateway.dto.RegisterRequest;
import com.powerreport.gateway.util.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证控制器
 *
 * 提供用户注册、登录、Token 刷新接口。
 * 当前使用内存存储用户信息（ConcurrentHashMap），仅用于开发和演示。
 * 生产环境应替换为数据库用户服务。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Map<String, String> USER_STORE = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> ROLE_STORE = new ConcurrentHashMap<>();

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    static {
        USER_STORE.put("admin", "admin123");
        ROLE_STORE.put("admin", List.of("ROLE_ADMIN", "ROLE_USER"));

        USER_STORE.put("user", "user123");
        ROLE_STORE.put("user", List.of("ROLE_USER"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.getUsername().trim();
        String password = request.getPassword().trim();

        if (USER_STORE.containsKey(username)) {
            Map<String, Object> body = new HashMap<>();
            body.put("code", 1002);
            body.put("message", "用户已存在");
            body.put("data", null);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        USER_STORE.put(username, password);
        ROLE_STORE.put(username, List.of("ROLE_USER"));

        log.info("User registered: {}", username);

        Map<String, Object> successBody = new HashMap<>();
        successBody.put("code", 200);
        successBody.put("message", "注册成功");
        successBody.put("data", null);
        return ResponseEntity.status(HttpStatus.CREATED).body(successBody);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String username = request.getUsername().trim();
        String password = request.getPassword().trim();

        String storedPassword = USER_STORE.get(username);
        if (storedPassword == null) {
            Map<String, Object> body = new HashMap<>();
            body.put("code", 1001);
            body.put("message", "用户不存在");
            body.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        if (!storedPassword.equals(password)) {
            Map<String, Object> body = new HashMap<>();
            body.put("code", 1003);
            body.put("message", "密码错误");
            body.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        List<String> roles = ROLE_STORE.getOrDefault(username, List.of("ROLE_USER"));

        String accessToken = jwtTokenProvider.generateAccessToken(username, roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(username);

        log.info("User login: {}, roles: {}", username, roles);

        AuthResponse response = new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtProperties.getAccessTokenExpiration(),
                username,
                roles
        );

        return ResponseEntity.ok(Map.of("code", 200, "message", "登录成功", "data", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        try {
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                Map<String, Object> body = new HashMap<>();
                body.put("code", 1005);
                body.put("message", "Refresh Token 无效或已过期");
                body.put("data", null);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }

            Claims claims = jwtTokenProvider.parseToken(refreshToken);
            String tokenType = claims.get("type", String.class);

            if (!"refresh".equals(tokenType)) {
                Map<String, Object> body = new HashMap<>();
                body.put("code", 1004);
                body.put("message", "Token 类型错误");
                body.put("data", null);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }

            String username = claims.getSubject();
            List<String> roles = ROLE_STORE.getOrDefault(username, List.of("ROLE_USER"));

            String newAccessToken = jwtTokenProvider.generateAccessToken(username, roles);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

            log.info("Token refreshed for user: {}", username);

            AuthResponse response = new AuthResponse(
                    newAccessToken,
                    newRefreshToken,
                    "Bearer",
                    jwtProperties.getAccessTokenExpiration(),
                    username,
                    roles
            );

            return ResponseEntity.ok(Map.of("code", 200, "message", "Token 刷新成功", "data", response));

        } catch (Exception e) {
            log.error("Token refresh failed", e);
            Map<String, Object> body = new HashMap<>();
            body.put("code", 1004);
            body.put("message", "Token 刷新失败");
            body.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("X-Username") String username) {
        List<String> roles = ROLE_STORE.getOrDefault(username, List.of("ROLE_USER"));

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "操作成功",
                "data", Map.of(
                        "username", username,
                        "roles", roles
                )
        ));
    }
}
