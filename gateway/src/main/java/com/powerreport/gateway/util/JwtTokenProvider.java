package com.powerreport.gateway.util;

import com.powerreport.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * JWT Token 工具类：生成、解析、校验 Token
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Access Token
     *
     * @param username  用户名
     * @param roles     用户角色列表
     * @return JWT token 字符串
     */
    public String generateAccessToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration() * 1000);

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(username)
                .issuedAt(now)
                .expiration(expiration)
                .claim("roles", roles)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成 Refresh Token
     *
     * @param username 用户名
     * @return JWT refresh token 字符串
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration() * 1000);

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(username)
                .issuedAt(now)
                .expiration(expiration)
                .claim("type", "refresh")
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token，获取 Claims
     *
     * @param token JWT token
     * @return Claims
     * @throws JwtException 如果 token 无效或已过期
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 Token 是否有效
     *
     * @param token JWT token
     * @return true 有效，false 无效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * 从 Token 中提取用户名
     *
     * @param token JWT token
     * @return 用户名
     */
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 从 Token 中提取角色列表
     *
     * @param token JWT token
     * @return 角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Claims claims = parseToken(token);
        return claims.get("roles", List.class);
    }

    /**
     * 判断 Token 是否已过期
     *
     * @param token JWT token
     * @return true 已过期
     */
    public boolean isExpired(String token) {
        try {
            parseToken(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * 获取 Token 的过期时间
     *
     * @param token JWT token
     * @return 过期时间戳（毫秒）
     */
    public Date getExpiration(String token) {
        return parseToken(token).getExpiration();
    }
}
