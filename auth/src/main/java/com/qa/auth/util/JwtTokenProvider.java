package com.qa.auth.util;

import com.qa.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String username, List<String> roles,
                                       List<String> permissions, Long tokenVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration() * 1000);

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration() * 1000);

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(username)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    public Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        List<String> roles = parseToken(token).get("roles", List.class);
        return roles != null ? roles : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(String token) {
        List<String> permissions = parseToken(token).get("permissions", List.class);
        return permissions != null ? permissions : Collections.emptyList();
    }

    public Long getTokenVersion(String token) {
        return parseToken(token).get("tokenVersion", Long.class);
    }

    public boolean isExpired(String token) {
        try {
            parseToken(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public Date getExpiration(String token) {
        return parseToken(token).getExpiration();
    }
}
