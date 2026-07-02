package com.qa.auth.service;

import com.qa.auth.util.CaptchaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * 图形验证码服务
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);

    /** 生成验证码，返回 {captchaKey, captchaImage} */
    public Map<String, String> generate() {
        CaptchaUtil.CaptchaImage captcha = CaptchaUtil.generate();
        String key = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(PREFIX + key, captcha.code(), TTL);

        String base64 = bufferedImageToBase64(captcha.image());
        return Map.of("captchaKey", key, "captchaImage", base64);
    }

    /** 校验验证码（一次性，校验后删除） */
    public boolean validate(String key, String code) {
        if (key == null || code == null) return false;
        String stored = redisTemplate.opsForValue().get(PREFIX + key);
        if (stored == null) return false;
        boolean match = stored.equalsIgnoreCase(code.trim());
        if (match) {
            redisTemplate.delete(PREFIX + key);
        }
        return match;
    }

    private String bufferedImageToBase64(BufferedImage image) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", os);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(os.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("验证码生成失败", e);
        }
    }
}
