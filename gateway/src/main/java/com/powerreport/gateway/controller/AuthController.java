package com.powerreport.gateway.controller;

import com.powerreport.gateway.config.JwtProperties;
import com.powerreport.gateway.dto.AuthResponse;
import com.powerreport.gateway.dto.LoginRequest;
import com.powerreport.gateway.dto.RefreshTokenRequest;
import com.powerreport.gateway.dto.RegisterRequest;
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
import java.util.concurrent.ConcurrentHashMap;


/**
 * 认证控制器（WebFlux 函数式端点）
 *
 * 提供用户注册、登录、Token 刷新接口。
 * 当前使用内存存储用户信息（ConcurrentHashMap），仅用于开发和演示。
 * 生产环境应替换为数据库用户服务。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuthController {

    private static final Map<String, String> USER_STORE = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> ROLE_STORE = new ConcurrentHashMap<>();

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    static {
        USER_STORE.put("admin", "admin123");
        ROLE_STORE.put("admin", Arrays.asList("ROLE_ADMIN", "ROLE_USER"));

        USER_STORE.put("user", "user123");
        ROLE_STORE.put("user", Arrays.asList("ROLE_USER"));
    }

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

                    if (USER_STORE.containsKey(username)) {
                        return status(HttpStatus.CONFLICT,
                                buildResponse(1002, "用户已存在", null));
                    }

                    USER_STORE.put(username, password);
                    ROLE_STORE.put(username, Arrays.asList("ROLE_USER"));

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

                    String storedPassword = USER_STORE.get(username);
                    if (storedPassword == null) {
                        return status(HttpStatus.UNAUTHORIZED,
                                buildResponse(1001, "用户不存在", null));
                    }

                    if (!storedPassword.equals(password)) {
                        return status(HttpStatus.UNAUTHORIZED,
                                buildResponse(1003, "密码错误", null));
                    }

                    List<String> roles = ROLE_STORE.getOrDefault(username, Arrays.asList("ROLE_USER"));

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
                        List<String> roles = ROLE_STORE.getOrDefault(username, Arrays.asList("ROLE_USER"));

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
        String username = request.headers().firstHeader("X-Username");
        if (username == null) {
            return status(HttpStatus.UNAUTHORIZED,
                    buildResponse(401, "未认证", null));
        }

        List<String> roles = ROLE_STORE.getOrDefault(username, Arrays.asList("ROLE_USER"));

        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("roles", roles);

        return ok(buildResponse(200, "操作成功", data));
    }
}
