package com.myenglish.qacommon.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户上下文拦截器
 *
 * 从网关传递的请求头中提取用户信息，设置到 UserContextHolder，
 * 请求结束后自动清理 ThreadLocal，防止内存泄漏。
 */
@Slf4j
public class UserContextInterceptor implements HandlerInterceptor {

    private static final String X_USER_ID_HEADER = "X-User-Id";
    private static final String X_USERNAME_HEADER = "X-Username";
    private static final String X_ROLES_HEADER = "X-Roles";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdStr = request.getHeader(X_USER_ID_HEADER);
        String username = request.getHeader(X_USERNAME_HEADER);
        String roles = request.getHeader(X_ROLES_HEADER);

        if (StringUtils.hasText(userIdStr)) {
            UserContextHolder.setUserId(Long.valueOf(userIdStr));
        }
        if (StringUtils.hasText(username)) {
            UserContextHolder.setUsername(username);
        }
        if (StringUtils.hasText(roles)) {
            UserContextHolder.setRoles(roles);
        }

        log.debug("UserContext restored from headers: userId={}, username={}", userIdStr, username);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}
