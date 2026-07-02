package com.myenglish.qacommon.context;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class UserContextHolder {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLES = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PERMISSIONS = new ThreadLocal<>();

    private UserContextHolder() {
    }

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

    public static List<String> getRoleList() {
        String roles = ROLES.get();
        if (roles == null || roles.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(roles.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static void setPermissions(List<String> permissions) {
        PERMISSIONS.set(permissions);
    }

    public static List<String> getPermissions() {
        List<String> list = PERMISSIONS.get();
        return list != null ? list : Collections.emptyList();
    }

    public static boolean hasRole(String roleCode) {
        return getRoleList().contains(roleCode);
    }

    public static boolean hasPermission(String permCode) {
        return getPermissions().contains(permCode);
    }

    public static boolean hasAnyPermission(String... permCodes) {
        List<String> owned = getPermissions();
        for (String code : permCodes) {
            if (owned.contains(code)) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        ROLES.remove();
        PERMISSIONS.remove();
    }
}
