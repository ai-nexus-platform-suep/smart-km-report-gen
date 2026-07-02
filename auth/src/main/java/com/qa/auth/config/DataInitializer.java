package com.qa.auth.config;

import com.qa.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器
 * <p>
 * 应用启动时自动插入预置用户，方便开发测试。
 * 预置用户包括：
 * - admin / admin123（管理员）
 * - user / user123（普通用户）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;

    @Override
    public void run(String... args) {
        log.info("===== 开始初始化预置用户 =====");

        // 预置管理员
        if (userService.register("admin", "admin123", "ROLE_ADMIN,ROLE_USER")) {
            log.info("预置管理员创建成功: admin / admin123");
        } else {
            log.info("预置管理员已存在，跳过");
        }

        // 预置普通用户
        if (userService.register("user", "user123", "ROLE_USER")) {
            log.info("预置普通用户创建成功: user / user123");
        } else {
            log.info("预置普通用户已存在，跳过");
        }

        log.info("===== 预置用户初始化完成 =====");
    }
}
