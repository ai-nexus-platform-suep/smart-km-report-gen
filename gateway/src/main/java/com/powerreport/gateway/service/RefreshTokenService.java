package com.powerreport.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerreport.gateway.entity.RefreshTokenEntity;
import com.powerreport.gateway.mapper.RefreshTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * Refresh Token 管理服务
 * <p>
 * 实现 Refresh Token 的持久化存储、校验和失效机制：
 * - Token 以 SHA256 哈希形式存储，避免明文泄露
 * - 刷新时执行 Token 轮换（删除旧 Token，创建新 Token）
 * - 支持按用户批量失效（登出/改密）
 * - 支持定时清理过期记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenMapper refreshTokenMapper;

    /**
     * 将字节数组转换为十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * 将 Refresh Token 的 JWT 字符串进行 SHA256 哈希
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 保存 Refresh Token 记录
     *
     * @param username  用户名
     * @param token     Refresh Token 的 JWT 字符串
     * @param expiresAt 过期时间
     */
    public void saveToken(String username, String token, LocalDateTime expiresAt) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUsername(username);
        entity.setTokenHash(hashToken(token));
        entity.setExpiresAt(expiresAt);
        entity.setRevoked(false);

        refreshTokenMapper.insert(entity);
        log.debug("Refresh token saved for user: {}", username);
    }

    /**
     * 校验 Refresh Token 是否有效
     * <p>
     * 检查数据库中是否存在该 Token 的哈希记录，且未撤销、未过期
     *
     * @param token Refresh Token 的 JWT 字符串
     * @return true 如果 Token 有效
     */
    public boolean isValid(String token) {
        String tokenHash = hashToken(token);
        RefreshTokenEntity entity = refreshTokenMapper.findValidByHash(tokenHash);
        return entity != null;
    }

    /**
     * 删除指定的 Refresh Token 记录（Token 轮换时使用）
     *
     * @param token Refresh Token 的 JWT 字符串
     */
    public void deleteToken(String token) {
        String tokenHash = hashToken(token);
        LambdaQueryWrapper<RefreshTokenEntity> wrapper = new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getTokenHash, tokenHash);
        refreshTokenMapper.delete(wrapper);
        log.debug("Refresh token deleted (hash: {}...)", tokenHash.substring(0, 8));
    }

    /**
     * 删除指定用户的所有 Refresh Token 记录（登出/改密时使用）
     *
     * @param username 用户名
     */
    @Transactional
    public void deleteByUsername(String username) {
        LambdaQueryWrapper<RefreshTokenEntity> wrapper = new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getUsername, username);
        int deleted = refreshTokenMapper.delete(wrapper);
        if (deleted > 0) {
            log.info("Deleted {} refresh token(s) for user: {}", deleted, username);
        }
    }

    /**
     * 清理所有已过期的 Refresh Token 记录（可定时调用）
     */
    @Transactional
    public void cleanExpiredTokens() {
        LambdaQueryWrapper<RefreshTokenEntity> wrapper = new LambdaQueryWrapper<RefreshTokenEntity>()
                .lt(RefreshTokenEntity::getExpiresAt, LocalDateTime.now());
        int deleted = refreshTokenMapper.delete(wrapper);
        if (deleted > 0) {
            log.info("Cleaned {} expired refresh token(s)", deleted);
        }
    }
}
