package com.powerreport.gateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Refresh Token 持久化实体
 * <p>
 * 用于实现 Refresh Token 的失效机制：
 * - 登录时写入一条记录
 * - 刷新 Token 时删除旧记录、写入新记录（Token 轮换）
 * - 登出时删除该用户的所有 Refresh Token 记录
 * - 定时清理过期记录
 */
@Data
@TableName("refresh_token")
public class RefreshTokenEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的用户名 */
    private String username;

    /** Refresh Token 的 JWT 字符串（SHA256 哈希存储，避免明文泄露） */
    private String tokenHash;

    /** Token 过期时间 */
    private LocalDateTime expiresAt;

    /** 是否已撤销（用于管理员强制下线） */
    private Boolean revoked;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
