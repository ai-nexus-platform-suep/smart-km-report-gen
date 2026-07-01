package com.powerreport.gateway.filter;

import com.powerreport.gateway.config.RouteProperties;
import com.powerreport.gateway.util.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

        if (isPublicPath(path)) {
            log.debug("Public path accessed: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return errorResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            return errorResponse(exchange, HttpStatus.UNAUTHORIZED, "Token is empty");
        }

        try {
            if (!jwtTokenProvider.validateToken(token)) {
                return errorResponse(exchange, HttpStatus.UNAUTHORIZED, "Token is invalid or expired");
            }

            String username = jwtTokenProvider.getUsername(token);
            List<String> roleList = jwtTokenProvider.getRoles(token);
            if (roleList == null) {
                roleList = List.of();
            }

            if (isAdminPath(path) && !hasAdminRole(roleList)) {
                log.warn("Forbidden admin path access: username={}, path={}, roles={}", username, path, roleList);
                return errorResponse(exchange, HttpStatus.FORBIDDEN, "Admin role is required");
            }

            String roles = String.join(",", roleList);
            log.debug("JWT auth success: username={}, path={}", username, path);

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(X_USERNAME_HEADER, username)
                    .header(X_ROLES_HEADER, roles)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("Token expired for path: {}", path);
            return errorResponse(exchange, HttpStatus.UNAUTHORIZED, "Token expired");
        } catch (JwtException e) {
            log.warn("Invalid token for path: {}, error: {}", path, e.getMessage());
            return errorResponse(exchange, HttpStatus.UNAUTHORIZED, "Token invalid");
        } catch (Exception e) {
            log.error("JWT authentication error for path: {}", path, e);
            return errorResponse(exchange, HttpStatus.UNAUTHORIZED, "Authentication service error");
        }
    }

    private boolean isPublicPath(String path) {
        return routeProperties.getPublicPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isAdminPath(String path) {
        List<String> adminPaths = routeProperties.getAdminPaths();
        if (adminPaths == null || adminPaths.isEmpty()) {
            return pathMatcher.match("/api/admin/**", path);
        }
        return adminPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean hasAdminRole(List<String> roles) {
        List<String> configuredRoles = routeProperties.getAdminRoles();
        List<String> allowedRoles = configuredRoles == null || configuredRoles.isEmpty()
                ? List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                : configuredRoles;
        return roles.stream().anyMatch(allowedRoles::contains);
    }

    private Mono<Void> errorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                status.value(),
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
