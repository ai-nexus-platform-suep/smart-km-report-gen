package com.qa.auth.dto;

import com.qa.auth.entity.SysUserEntity;
import lombok.Getter;

import java.util.Optional;

/**
 * 登录结果，携带失败原因
 */
@Getter
public class LoginResult {

    public static final int SUCCESS = 0;
    public static final int USER_NOT_FOUND = 1;
    public static final int USER_DISABLED = 2;
    public static final int PASSWORD_ERROR = 3;
    public static final int ACCOUNT_LOCKED = 4;

    private final int code;
    private final SysUserEntity user;
    private final String message;

    private LoginResult(int code, SysUserEntity user, String message) {
        this.code = code;
        this.user = user;
        this.message = message;
    }

    public static LoginResult success(SysUserEntity user) {
        return new LoginResult(SUCCESS, user, null);
    }

    public static LoginResult userNotFound() {
        return new LoginResult(USER_NOT_FOUND, null, "用户不存在");
    }

    public static LoginResult userDisabled() {
        return new LoginResult(USER_DISABLED, null, "账号已被禁用");
    }

    public static LoginResult passwordError() {
        return new LoginResult(PASSWORD_ERROR, null, "密码错误");
    }

    public static LoginResult accountLocked(long remainingSeconds) {
        long minutes = (long) Math.ceil(remainingSeconds / 60.0);
        return new LoginResult(ACCOUNT_LOCKED, null,
                "账号已被锁定，请" + minutes + "分钟后再试");
    }

    public boolean isSuccess() {
        return code == SUCCESS;
    }

    public Optional<SysUserEntity> getUser() {
        return Optional.ofNullable(user);
    }
}
