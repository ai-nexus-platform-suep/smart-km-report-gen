package com.powerreport.gateway.filter;

import com.powerreport.gateway.config.RouteProperties;
import com.powerreport.gateway.util.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 全局 JWT 鉴权过滤器
 *
 * 对所有请求进行 JWT Token 校验，白名单路径除外。
 * 校验通过后，将用户信息注入请求头，传递给下游微服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGatewayFilterFactory implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_USERNAME_HEADER = "X-Username";
    private static final String X_ROLES_HEADER = "X-Roles";

    private final JwtTokenProvider jwtTokenProvider;
    private final RouteProperties routeProperties;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单路径直接放行
        if (isPublicPath(path)) {
            log.debug("Public path accessed: {}", path);
            return chain.filter(exchange);
        }

        // 获取 Authorization 请求头
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return unauthorizedResponse(exchange, "缺少认证信息，请先登录");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            return unauthorizedResponse(exchange, "Token 不能为空");
        }

        // 校验 Token
        try {
            if (!jwtTokenProvider.validateToken(token)) {
                return unauthorizedResponse(exchange, "Token 无效或已过期");
            }

            String username = jwtTokenProvider.getUsername(token);
            String roles = String.join(",", jwtTokenProvider.getRoles(token));

            log.debug("JWT auth success: username={}, path={}", username, path);

            // 将用户信息注入请求头，传递给下游服务
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(X_USERNAME_HEADER, username)
                    .header(X_ROLES_HEADER, roles)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("Token expired for path: {}", path);
            return unauthorizedResponse(exchange, "Token 已过期，请重新登录");
        } catch (JwtException e) {
            log.warn("Invalid token for path: {}, error: {}", path, e.getMessage());
            return unauthorizedResponse(exchange, "Token 无效");
        } catch (Exception e) {
            log.error("JWT authentication error for path: {}", path, e);
            return unauthorizedResponse(exchange, "认证服务异常");
        }
    }

    /**
     * 判断是否为白名单路径
     */
    private boolean isPublicPath(String path) {
        return routeProperties.getPublicPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 返回 401 未授权响应
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":401,\"message\":\"%s\",\"data\":null}",
                message
        );
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
