package com.km.security;

import com.km.common.dto.ApiResponse;
import com.km.common.exception.ErrorCode;
import com.km.entity.User;
import com.km.repository.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserMapper userMapper, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            if (!jwtUtil.validateToken(token)) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                ApiResponse<?> apiResponse = ApiResponse.error(
                        ErrorCode.INVALID_TOKEN.getCode(),
                        ErrorCode.INVALID_TOKEN.getMessage());
                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                return;
            }

            Long userId = jwtUtil.getUserIdFromToken(token);
            User user = userMapper.findById(userId);

            if (user == null) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                ApiResponse<?> apiResponse = ApiResponse.error(
                        ErrorCode.USER_NOT_FOUND.getCode(),
                        ErrorCode.USER_NOT_FOUND.getMessage());
                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                return;
            }

            if (user.getStatus() == 0) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                ApiResponse<?> apiResponse = ApiResponse.error(
                        ErrorCode.ACCOUNT_DISABLED.getCode(),
                        ErrorCode.ACCOUNT_DISABLED.getMessage());
                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                return;
            }

            // 校验数据库中 token 是否匹配（服务端可控吊销）
            // token 为空（已登出）或与数据库不匹配（其他设备登录）时拒绝
            if (user.getToken() == null) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                ApiResponse<?> apiResponse = ApiResponse.error(
                        ErrorCode.INVALID_TOKEN.getCode(),
                        "Token 已失效，请重新登录");
                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                return;
            }
            if (!user.getToken().equals(token)) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                ApiResponse<?> apiResponse = ApiResponse.error(
                        ErrorCode.INVALID_TOKEN.getCode(),
                        "Token 已在其他设备登录，请重新登录");
                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                return;
            }

            // 设置安全上下文
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId, null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            CurrentUserHolder.set(new CurrentUserHolder.CurrentUser(userId, user.getUsername(), user.getRole()));
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
