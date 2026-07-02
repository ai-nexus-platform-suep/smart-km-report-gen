package com.myenglish.qacommon.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdStr = request.getHeader(UserContextHeaders.USER_ID);
        String username = request.getHeader(UserContextHeaders.USERNAME);
        String roles = request.getHeader(UserContextHeaders.ROLES);
        String permissions = request.getHeader(UserContextHeaders.PERMISSIONS);

        if (StringUtils.hasText(userIdStr)) {
            UserContextHolder.setUserId(Long.valueOf(userIdStr));
        }
        if (StringUtils.hasText(username)) {
            UserContextHolder.setUsername(username);
        }
        if (StringUtils.hasText(roles)) {
            UserContextHolder.setRoles(roles);
        }
        if (StringUtils.hasText(permissions)) {
            UserContextHolder.setPermissions(parsePermissions(permissions));
        }

        log.debug("UserContext: userId={}, username={}", userIdStr, username);
        return true;
    }

    static List<String> parsePermissions(String header) {
        if (!StringUtils.hasText(header)) {
            return Collections.emptyList();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}
