package com.powerreport.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerreport.gateway.config.JwtProperties;
import com.powerreport.gateway.dto.LoginResult;
import com.powerreport.gateway.entity.SysUserEntity;
import com.powerreport.gateway.service.RefreshTokenService;
import com.powerreport.gateway.service.UserService;
import com.powerreport.gateway.util.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        webTestClient = WebTestClient
                .bindToRouterFunction(new AuthController(
                        jwtTokenProvider,
                        jwtProperties,
                        userService,
                        refreshTokenService
                ).authRoutes())
                .build();
    }

    @Test
    void registerCreatesUserThroughRoute() {
        when(userService.register("alice", "secret123", "ROLE_USER")).thenReturn(true);

        webTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\" alice \",\"password\":\" secret123 \"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200);
    }

    @Test
    void registerReturnsConflictWhenUsernameExists() {
        when(userService.register("alice", "secret123", "ROLE_USER")).thenReturn(false);

        webTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo(1002);
    }

    @Test
    void loginCreatesAccessAndRefreshTokens() {
        SysUserEntity user = new SysUserEntity();
        user.setUsername("alice");
        user.setRoles("ROLE_USER, ROLE_ADMIN");
        when(userService.login("alice", "secret123")).thenReturn(LoginResult.success(user));
        when(jwtTokenProvider.generateAccessToken(eq("alice"), any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("alice")).thenReturn("refresh-token");

        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.accessToken").isEqualTo("access-token")
                .jsonPath("$.data.refreshToken").isEqualTo("refresh-token")
                .jsonPath("$.data.roles[1]").isEqualTo("ROLE_ADMIN");

        verify(refreshTokenService).saveToken(eq("alice"), eq("refresh-token"), any(LocalDateTime.class));
    }

    @Test
    void getCurrentUserReadsBearerToken() {
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("access-token")).thenReturn("alice");
        when(jwtTokenProvider.getRoles("access-token")).thenReturn(List.of("ROLE_USER"));
        when(userService.findByUsername("alice")).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/api/auth/me")
                .header("Authorization", "Bearer access-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.username").isEqualTo("alice")
                .jsonPath("$.data.roles[0]").isEqualTo("ROLE_USER");
    }
}
