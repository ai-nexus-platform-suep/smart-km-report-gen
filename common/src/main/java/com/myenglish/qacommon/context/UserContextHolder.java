package com.myenglish.qacommon.context;

/**
 * 用户上下文 Holder
 * 存储当前请求的用户信息，供服务内部使用
 *
 */
public class UserContextHolder {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLES = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static void setRoles(String roles) {
        ROLES.set(roles);
    }

    public static String getRoles() {
        return ROLES.get();
    }
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        ROLES.remove();
    }


}
