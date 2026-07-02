package com.qa.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败锁定服务 - 基于 Redis 实现
 * - 同一用户名连续失败 N 次后锁定 M 分钟
 * - 成功后自动清除失败计数
 */
@Service
@RequiredArgsConstructor
public class LoginLockService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.login.max-fail-count:5}")
    private int maxFailCount;

    @Value("${app.login.lock-minutes:15}")
    private int lockMinutes;

    private static final String FAIL_COUNT_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";

    /**
     * 记录一次登录失败
     */
    public void recordFail(String username) {
        Duration ttl = Duration.ofMinutes(lockMinutes);
        String failKey = FAIL_COUNT_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(failKey);
        redisTemplate.expire(failKey, ttl);
        if (count != null && count >= maxFailCount) {
            redisTemplate.opsForValue().set(LOCK_PREFIX + username, "1", ttl);
        }
    }

    /**
     * 检查是否已被锁定
     */
    public boolean isLocked(String username) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + username));
    }

    /**
     * 获取剩余锁定秒数，未锁定时返回 0
     */
    public long getLockRemainingSeconds(String username) {
        Long ttl = redisTemplate.getExpire(LOCK_PREFIX + username);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    /**
     * 登录成功后清除失败记录
     */
    public void clearFail(String username) {
        redisTemplate.delete(FAIL_COUNT_PREFIX + username);
        redisTemplate.delete(LOCK_PREFIX + username);
    }

    /**
     * 获取剩余尝试次数
     */
    public int getRemainingAttempts(String username) {
        String countStr = redisTemplate.opsForValue().get(FAIL_COUNT_PREFIX + username);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        return Math.max(0, maxFailCount - count);
    }
}
