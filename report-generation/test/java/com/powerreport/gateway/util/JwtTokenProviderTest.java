package com.powerreport.gateway.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.powerreport.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    @Test
    void generateAccessTokenCanBeParsedBackToUserAndRoles() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties());

        String token = provider.generateAccessToken("alice", List.of("ROLE_USER", "ROLE_ADMIN"));

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsername(token)).isEqualTo("alice");
        assertThat(provider.getRoles(token)).containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThat(provider.isExpired(token)).isFalse();
    }

    @Test
    void generateRefreshTokenCarriesRefreshTypeClaim() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties());

        String token = provider.generateRefreshToken("alice");
        Claims claims = provider.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void validateTokenReturnsFalseForTamperedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties());
        String token = provider.generateAccessToken("alice", List.of("ROLE_USER"));

        assertThat(provider.validateToken(token + "tampered")).isFalse();
    }
}
