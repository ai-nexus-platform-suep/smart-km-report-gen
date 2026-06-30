package com.qa.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qa.auth.dto.LoginResult;
import com.qa.auth.entity.SysUserEntity;
import com.qa.auth.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 用户业务服务
 * <p>
 * 提供用户注册、登录校验、密码加密等核心功能。
 * 密码使用 BCrypt 加密存储，保障安全性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;

    /**
     * BCrypt 密码编码器（线程安全）
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     *
     * @param username 用户名
     * @param password 明文密码
     * @param roles    角色列表（逗号分隔）
     * @return 注册成功返回 true；用户名已存在返回 false
     */
    public boolean register(String username, String password, String roles) {
        // 检查用户名是否已存在
        if (findByUsername(username).isPresent()) {
            log.warn("注册失败，用户名已存在: {}", username);
            return false;
        }

        // BCrypt 加密密码
        String encodedPassword = passwordEncoder.encode(password);

        SysUserEntity user = new SysUserEntity();
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setRoles(roles);
        user.setEnabled(true);

        sysUserMapper.insert(user);
        log.info("用户注册成功: username={}, roles={}", username, roles);
        return true;
    }

    /**
     * 用户登录校验
     *
     * @param username 用户名
     * @param rawPassword 明文密码
     * @return LoginResult，包含失败原因（用户不存在 / 账号已禁用 / 密码错误）
     */
    public LoginResult login(String username, String rawPassword) {
        Optional<SysUserEntity> userOpt = findByUsername(username);
        if (!userOpt.isPresent()) {
            log.warn("登录失败，用户不存在: {}", username);
            return LoginResult.userNotFound();
        }

        SysUserEntity user = userOpt.get();

        // 检查账号是否启用
        if (Boolean.FALSE.equals(user.getEnabled())) {
            log.warn("登录失败，账号已被禁用: {}", username);
            return LoginResult.userDisabled();
        }

        // BCrypt 密码校验
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("登录失败，密码错误: {}", username);
            return LoginResult.passwordError();
        }

        log.info("用户登录成功: username={}, roles={}", username, user.getRoles());
        return LoginResult.success(user);
    }

    /**
     * 根据用户名查询用户
     */
    public Optional<SysUserEntity> findByUsername(String username) {
        SysUserEntity user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserEntity>()
                        .eq(SysUserEntity::getUsername, username)
        );
        return Optional.ofNullable(user);
    }

    /**
     * 获取所有用户列表
     */
    public List<SysUserEntity> listAll() {
        return sysUserMapper.selectList(null);
    }

    /**
     * 删除用户
     */
    public boolean deleteUser(Long id) {
        int rows = sysUserMapper.deleteById(id);
        return rows > 0;
    }

    /**
     * 启用/禁用用户
     */
    public boolean setEnabled(Long id, boolean enabled) {
        SysUserEntity user = sysUserMapper.selectById(id);
        if (user == null) {
            return false;
        }
        user.setEnabled(enabled);
        sysUserMapper.updateById(user);
        log.info("用户状态变更: id={}, enabled={}", id, enabled);
        return true;
    }
}
