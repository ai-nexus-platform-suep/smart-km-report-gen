package com.powerreport.gateway.controller;

import com.powerreport.gateway.config.JwtProperties;
import com.powerreport.gateway.dto.AuthResponse;
import com.powerreport.gateway.dto.LoginRequest;
import com.powerreport.gateway.dto.RefreshTokenRequest;
import com.powerreport.gateway.dto.RegisterRequest;
import com.powerreport.gateway.entity.SysUserEntity;
import com.powerreport.gateway.service.UserService;
import com.powerreport.gateway.util.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * 认证控制器（WebFlux 函数式端点）
 *
 * 提供用户注册、登录、Token 刷新接口。
 * 用户数据持久化存储于 MySQL（sys_user 表），密码使用 BCrypt 加密。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UserService userService;

    @Bean
    public RouterFunction<ServerResponse> authRoutes() {
        return RouterFunctions.route()
                .POST("/api/auth/register", this::register)
                .POST("/api/auth/login", this::login)
                .POST("/api/auth/refresh", this::refreshToken)
                .GET("/api/auth/me", this::getCurrentUser)
                .build();
    }

    private Map<String, Object> buildResponse(int code, String message, Object data) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("message", message);
        map.put("data", data);
        return map;
    }

    private Mono<ServerResponse> ok(Object body) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body));
    }

    private Mono<ServerResponse> status(HttpStatus status, Object body) {
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body));
    }

    private Mono<ServerResponse> register(ServerRequest request) {
        return request.bodyToMono(RegisterRequest.class)
                .flatMap(registerReq -> {
                    String username = registerReq.getUsername().trim();
                    String password = registerReq.getPassword().trim();

                    // 调用 UserService 注册（BCrypt 加密 + 写入 DB）
                    boolean success = userService.register(username, password, "ROLE_USER");
                    if (!success) {
                        return status(HttpStatus.CONFLICT,
                                buildResponse(1002, "用户已存在", null));
                    }

                    log.info("User registered: {}", username);

                    return status(HttpStatus.CREATED,
                            buildResponse(200, "注册成功", null));
                });
    }

    private Mono<ServerResponse> login(ServerRequest request) {
        return request.bodyToMono(LoginRequest.class)
                .flatMap(loginReq -> {
                    String username = loginReq.getUsername().trim();
                    String password = loginReq.getPassword().trim();

                    // 调用 UserService 登录校验（BCrypt 密码匹配）
                    Optional<SysUserEntity> userOpt = userService.login(username, password);
                    if (!userOpt.isPresent()) {
                        // 不区分"用户不存在"和"密码错误"，防止用户名枚举攻击
                        return status(HttpStatus.UNAUTHORIZED,
                                buildResponse(1003, "密码错误", null));
                    }

                    SysUserEntity user = userOpt.get();
                    List<String> roles = parseRoles(user.getRoles());

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

                    return ok(buildResponse(200, "登录成功", response));
                });
    }

    private Mono<ServerResponse> refreshToken(ServerRequest request) {
        return request.bodyToMono(RefreshTokenRequest.class)
                .flatMap(refreshReq -> {
                    String refreshToken = refreshReq.getRefreshToken();

                    try {
                        if (!jwtTokenProvider.validateToken(refreshToken)) {
                            return status(HttpStatus.UNAUTHORIZED,
                                    buildResponse(1005, "Refresh Token 无效或已过期", null));
                        }

                        Claims claims = jwtTokenProvider.parseToken(refreshToken);
                        String tokenType = claims.get("type", String.class);

                        if (!"refresh".equals(tokenType)) {
                            return status(HttpStatus.UNAUTHORIZED,
                                    buildResponse(1004, "Token 类型错误", null));
                        }

                        String username = claims.getSubject();

                        // 从数据库获取用户角色
                        List<String> roles = userService.findByUsername(username)
                                .map(user -> parseRoles(user.getRoles()))
                                .orElse(Arrays.asList("ROLE_USER"));

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

                        return ok(buildResponse(200, "Token 刷新成功", response));

                    } catch (Exception e) {
                        log.error("Token refresh failed", e);
                        return status(HttpStatus.UNAUTHORIZED,
                                buildResponse(1004, "Token 刷新失败", null));
                    }
                });
    }

    private Mono<ServerResponse> getCurrentUser(ServerRequest request) {
        // 从 Authorization 请求头中解析 Token
        String authHeader = request.headers().firstHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return status(HttpStatus.UNAUTHORIZED,
                    buildResponse(401, "未认证，请先登录", null));
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return status(HttpStatus.UNAUTHORIZED,
                    buildResponse(401, "Token 不能为空", null));
        }

        try {
            // 校验 Token
            if (!jwtTokenProvider.validateToken(token)) {
                return status(HttpStatus.UNAUTHORIZED,
                        buildResponse(401, "Token 无效或已过期", null));
            }

            String username = jwtTokenProvider.getUsername(token);
            List<String> roles = jwtTokenProvider.getRoles(token);

            // 从数据库获取最新角色信息
            List<String> dbRoles = userService.findByUsername(username)
                    .map(user -> parseRoles(user.getRoles()))
                    .orElse(roles);

            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            data.put("roles", dbRoles);

            return ok(buildResponse(200, "操作成功", data));

        } catch (Exception e) {
            log.error("Get current user failed", e);
            return status(HttpStatus.UNAUTHORIZED,
                    buildResponse(401, "Token 无效", null));
        }
    }

    /**
     * 将数据库中的 roles 字符串（逗号分隔）解析为 List
     */
    private List<String> parseRoles(String rolesStr) {
        if (rolesStr == null || rolesStr.trim().isEmpty()) {
            return Arrays.asList("ROLE_USER");
        }
        String[] parts = rolesStr.split(",");
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
}
