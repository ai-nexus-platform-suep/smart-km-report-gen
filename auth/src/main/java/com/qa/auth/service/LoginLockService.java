package com.qa.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败计数 & 锁定（使用 Redis）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLockService {

    private final StringRedisTemplate redisTemplate;

    private static final String FAIL_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";

    @Value("${app.login.max-fail-count:5}")
    private int maxFailCount;

    @Value("${app.login.lock-minutes:15}")
    private int lockMinutes;

    /** 记录一次失败 */
    public void recordFail(String username) {
        String key = FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(lockMinutes * 2));
        if (count != null && count >= maxFailCount) {
            lock(username);
        }
    }

    /** 是否被锁定 */
    public boolean isLocked(String username) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + username));
    }

    /** 剩余锁定秒数 */
    public long getLockRemainingSeconds(String username) {
        Long ttl = redisTemplate.getExpire(LOCK_PREFIX + username);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    /** 剩余尝试次数 */
    public int getRemainingAttempts(String username) {
        String count = redisTemplate.opsForValue().get(FAIL_PREFIX + username);
        int fails = count != null ? Integer.parseInt(count) : 0;
        return Math.max(0, maxFailCount - fails);
    }

    /** 登录成功后清除 */
    public void clearFail(String username) {
        redisTemplate.delete(FAIL_PREFIX + username);
        redisTemplate.delete(LOCK_PREFIX + username);
    }

    private void lock(String username) {
        redisTemplate.opsForValue().set(LOCK_PREFIX + username, "1", Duration.ofMinutes(lockMinutes));
        redisTemplate.delete(FAIL_PREFIX + username);
        log.warn("账号已锁定: {}, 锁定{}分钟", username, lockMinutes);
    }
}
