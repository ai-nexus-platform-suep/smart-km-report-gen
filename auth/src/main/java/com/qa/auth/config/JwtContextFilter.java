package com.qa.auth.config;

import com.myenglish.qacommon.context.UserContextHeaders;
import com.myenglish.qacommon.context.UserContextHolder;
import com.qa.auth.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 直连 auth 服务时（无 Gateway 注入头），从 JWT 恢复用户上下文
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class JwtContextFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (request.getHeader(UserContextHeaders.USER_ID) == null) {
                String authHeader = request.getHeader("Authorization");
                if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7).trim();
                    if (jwtTokenProvider.validateToken(token)) {
                        UserContextHolder.setUserId(jwtTokenProvider.getUserId(token));
                        UserContextHolder.setUsername(jwtTokenProvider.getUsername(token));
                        List<String> roles = jwtTokenProvider.getRoles(token);
                        UserContextHolder.setRoles(String.join(",", roles));
                        UserContextHolder.setPermissions(jwtTokenProvider.getPermissions(token));
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            if (request.getHeader(UserContextHeaders.USER_ID) == null) {
                UserContextHolder.clear();
            }
        }
    }
}
