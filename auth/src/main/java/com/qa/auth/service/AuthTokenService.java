package com.qa.auth.service;

import com.qa.auth.config.JwtProperties;
import com.qa.auth.dto.UserAuthorities;
import com.qa.auth.dto.response.AuthResponse;
import com.qa.auth.entity.SysUserEntity;
import com.qa.auth.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final AuthQueryService authQueryService;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse issueTokens(SysUserEntity user) {
        UserAuthorities authorities = authQueryService.loadAuthorities(user.getId());
        List<String> roles = authorities.getRoles().isEmpty() ? List.of("ROLE_USER") : authorities.getRoles();
        List<String> permissions = authorities.getPermissions();

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), roles, permissions);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        saveRefreshToken(user.getId(), refreshToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtProperties.getAccessTokenExpiration(),
                user.getUsername(),
                roles,
                permissions
        );
    }

    private void saveRefreshToken(Long userId, String refreshToken) {
        long refreshExpiration = jwtProperties.getRefreshTokenExpiration();
        LocalDateTime refreshExpiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(Instant.now().getEpochSecond() + refreshExpiration),
                ZoneId.systemDefault());
        refreshTokenService.saveToken(userId, refreshToken, refreshExpiresAt);
    }
}
