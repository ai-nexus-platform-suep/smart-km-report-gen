package com.qa.auth.service;

import com.qa.auth.util.CaptchaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 图形验证码服务 - 基于 Redis 存储
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final StringRedisTemplate redisTemplate;

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final int CODE_LENGTH = 4;

    /**
     * 生成验证码，返回 {captchaKey, captchaImage(base64)}
     */
    public Map<String, String> generate() {
        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        String code = CaptchaUtil.generateCode(CODE_LENGTH);
        BufferedImage image = CaptchaUtil.generateImage(code);
        String base64 = CaptchaUtil.toBase64(image);

        redisTemplate.opsForValue().set(CAPTCHA_PREFIX + captchaKey, code.toLowerCase(), CAPTCHA_TTL);

        Map<String, String> result = new HashMap<>();
        result.put("captchaKey", captchaKey);
        result.put("captchaImage", "data:image/png;base64," + base64);
        return result;
    }

    /**
     * 校验验证码（一次性消费，验证通过后删除）
     */
    public boolean validate(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaCode == null || captchaKey.isBlank() || captchaCode.isBlank()) {
            return false;
        }
        String stored = redisTemplate.opsForValue().get(CAPTCHA_PREFIX + captchaKey);
        if (stored == null) {
            return false;
        }
        // 一次性消费，用完即删
        redisTemplate.delete(CAPTCHA_PREFIX + captchaKey);
        return stored.equalsIgnoreCase(captchaCode.trim());
    }
}
