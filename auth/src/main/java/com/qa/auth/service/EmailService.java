package com.qa.auth.service;

import com.myenglish.qacommon.dto.ApiCode;
import com.myenglish.qacommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @Value("${spring.mail.username}")
    private String from;

    private static final String CODE_PREFIX = "email:code:";
    private static final String LIMIT_PREFIX = "email:limit:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration LIMIT_TTL = Duration.ofSeconds(60);

    /** 发送邮箱验证码（60秒内同邮箱只能发一次） */
    public void sendVerificationCode(String toEmail) {
        String limitKey = LIMIT_PREFIX + toEmail;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            throw new BusinessException(ApiCode.TOO_MANY_REQUESTS, "发送过于频繁，请60秒后再试");
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
        redisTemplate.opsForValue().set(CODE_PREFIX + toEmail, code, CODE_TTL);
        redisTemplate.opsForValue().set(limitKey, "1", LIMIT_TTL);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("邮箱验证码");
            message.setText("您的验证码是：" + code + "，有效期5分钟，请勿泄露给他人。");
            mailSender.send(message);
            log.info("验证码已发送至邮箱: {}", toEmail);
        } catch (Exception e) {
            redisTemplate.delete(CODE_PREFIX + toEmail);
            redisTemplate.delete(limitKey);
            log.error("邮件发送失败: {}", toEmail, e);
            throw new BusinessException(ApiCode.THIRD_PARTY_ERROR, "邮件发送失败，请稍后重试");
        }
    }

    public boolean validateCode(String email, String code) {
        if (email == null || code == null) return false;
        String stored = redisTemplate.opsForValue().get(CODE_PREFIX + email);
        return code.trim().equals(stored);
    }

    public void deleteCode(String email) {
        redisTemplate.delete(CODE_PREFIX + email);
    }

    /** 发送默认密码（邮箱注册成功后调用） */
    public void sendDefaultPassword(String toEmail, String username, String password) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("注册成功 - 您的账号信息");
        message.setText("您好，" + username + "！\n\n"
                + "您的账号已注册成功，以下是您的登录信息：\n"
                + "用户名：" + username + "\n"
                + "密码：" + password + "\n\n"
                + "请登录后及时修改密码。");
        mailSender.send(message);
        log.info("默认密码已发送至邮箱: {}", toEmail);
    }
}
