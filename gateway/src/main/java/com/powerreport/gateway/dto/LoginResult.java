package com.powerreport.gateway.dto;

import com.powerreport.gateway.entity.SysUserEntity;
import lombok.Getter;

import java.util.Optional;

/**
 * 登录结果，携带失败原因
 */
@Getter
public class LoginResult {

    /** 登录成功 */
    public static final int SUCCESS = 0;
    /** 用户不存在 */
    public static final int USER_NOT_FOUND = 1;
    /** 账号已禁用 */
    public static final int USER_DISABLED = 2;
    /** 密码错误 */
    public static final int PASSWORD_ERROR = 3;

    private final int code;
    private final SysUserEntity user;

    private LoginResult(int code, SysUserEntity user) {
        this.code = code;
        this.user = user;
    }

    public static LoginResult success(SysUserEntity user) {
        return new LoginResult(SUCCESS, user);
    }

    public static LoginResult userNotFound() {
        return new LoginResult(USER_NOT_FOUND, null);
    }

    public static LoginResult userDisabled() {
        return new LoginResult(USER_DISABLED, null);
    }

    public static LoginResult passwordError() {
        return new LoginResult(PASSWORD_ERROR, null);
    }

    public boolean isSuccess() {
        return code == SUCCESS;
    }

    public Optional<SysUserEntity> getUser() {
        return Optional.ofNullable(user);
    }
}
