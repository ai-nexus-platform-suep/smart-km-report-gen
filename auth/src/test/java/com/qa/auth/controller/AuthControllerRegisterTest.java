package com.qa.auth.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.qa.auth.dto.response.AuthResponse;
import com.qa.auth.entity.SysUserEntity;
import com.qa.auth.service.AuthTokenService;
import com.qa.auth.service.CaptchaService;
import com.qa.auth.service.EmailService;
import com.qa.auth.service.MenuService;
import com.qa.auth.service.RefreshTokenService;
import com.qa.auth.service.UserService;
import com.qa.auth.support.CurrentUserResolver;
import com.qa.auth.util.JwtTokenProvider;
import com.qa.auth.dto.request.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerRegisterTest {

    private UserService userService;
    private AuthTokenService authTokenService;
    private CaptchaService captchaService;
    private EmailService emailService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        authTokenService = mock(AuthTokenService.class);
        captchaService = mock(CaptchaService.class);
        emailService = mock(EmailService.class);
        controller = new AuthController(
                mock(JwtTokenProvider.class),
                userService,
                authTokenService,
                mock(RefreshTokenService.class),
                mock(MenuService.class),
                mock(CurrentUserResolver.class),
                captchaService,
                emailService
        );
    }

    @Test
    void registerByUsernameReturnsIssuedAuthResponse() {
        RegisterRequest req = new RegisterRequest();
        req.setRegisterType("USERNAME");
        req.setUsername("alice");
        req.setPassword("Alice123!");
        req.setConfirmPassword("Alice123!");
        req.setCaptchaKey("captcha-key");
        req.setCaptchaCode("1234");

        SysUserEntity user = newUser(10L, "alice", null);
        AuthResponse auth = newAuthResponse("alice");
        when(captchaService.validate("captcha-key", "1234")).thenReturn(true);
        when(userService.register("alice", "Alice123!", null)).thenReturn(true);
        when(userService.findActiveByUsername("alice")).thenReturn(Optional.of(user));
        when(authTokenService.issueTokens(user)).thenReturn(auth);

        ApiResponse<?> response = controller.register(req);

        assertInstanceOf(AuthResponse.class, response.getData());
        assertSame(auth, response.getData());
        verify(authTokenService).issueTokens(user);
    }

    @Test
    void registerByEmailReturnsIssuedAuthResponse() {
        RegisterRequest req = new RegisterRequest();
        req.setRegisterType("EMAIL");
        req.setEmail("Alice@Example.com");
        req.setEmailCode("654321");
        req.setCaptchaKey("captcha-key");
        req.setCaptchaCode("1234");

        SysUserEntity user = newUser(11L, "alice", "alice@example.com");
        AuthResponse auth = newAuthResponse("alice");
        when(captchaService.validate("captcha-key", "1234")).thenReturn(true);
        when(emailService.validateCode("alice@example.com", "654321")).thenReturn(true);
        when(userService.registerByEmail("alice@example.com")).thenReturn("alice");
        when(userService.findActiveByUsername("alice")).thenReturn(Optional.of(user));
        when(authTokenService.issueTokens(user)).thenReturn(auth);

        ApiResponse<?> response = controller.register(req);

        assertInstanceOf(AuthResponse.class, response.getData());
        assertSame(auth, response.getData());
        verify(emailService).deleteCode("alice@example.com");
        verify(authTokenService).issueTokens(user);
    }

    private SysUserEntity newUser(Long id, String username, String email) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);
        user.setDeleted(false);
        return user;
    }

    private AuthResponse newAuthResponse(String username) {
        return new AuthResponse(
                "access-token",
                "refresh-token",
                "Bearer",
                900,
                username,
                List.of("ROLE_USER"),
                List.of("chat:conversation:use")
        );
    }
}
