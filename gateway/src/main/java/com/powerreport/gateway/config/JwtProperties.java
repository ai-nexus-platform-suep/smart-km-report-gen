package com.powerreport.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * JWT 签名密钥（至少 256 位，建议使用 Base64 编码的密钥）
     */
    private String secret = "dGhpcyBpcyBhIHNlY3JldCBrZXkgZm9yIGp3dCB0b2tlbiAyNTYgYml0cyBsb25nAAAAAAAAAAA=";

    /**
     * Access Token 过期时间，单位：秒（默认 24 小时）
     */
    private long accessTokenExpiration = 86400;

    /**
     * Refresh Token 过期时间，单位：秒（默认 7 天）
     */
    private long refreshTokenExpiration = 604800;

    /**
     * Token 签发者
     */
    private String issuer = "tech-supervision-platform";
}
