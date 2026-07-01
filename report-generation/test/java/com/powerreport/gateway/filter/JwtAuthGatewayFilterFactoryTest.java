package com.powerreport.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.powerreport.gateway.config.RouteProperties;
import com.powerreport.gateway.util.JwtTokenProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class JwtAuthGatewayFilterFactoryTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private RouteProperties routeProperties;
    private JwtAuthGatewayFilterFactory filter;

    @BeforeEach
    void setUp() {
        routeProperties = new RouteProperties();
        routeProperties.setPublicPaths(List.of("/api/auth/**"));
        routeProperties.setAdminPaths(List.of("/api/admin/**"));
        routeProperties.setAdminRoles(List.of("ROLE_ADMIN"));
        filter = new JwtAuthGatewayFilterFactory(jwtTokenProvider, routeProperties);
    }

    @Test
    void publicPathSkipsJwtValidation() {
        AtomicBoolean called = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login").build());

        filter.filter(exchange, current -> {
            called.set(true);
            return Mono.empty();
        }).block();

        assertThat(called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void securedPathWithoutBearerTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/reports/history").build());

        filter.filter(exchange, current -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validTokenAddsUserHeadersBeforeForwarding() {
        when(jwtTokenProvider.validateToken("token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("token")).thenReturn("alice");
        when(jwtTokenProvider.getRoles("token")).thenReturn(List.of("ROLE_USER"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/reports/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .build());

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Username")).isEqualTo("alice");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Roles")).isEqualTo("ROLE_USER");
    }

    @Test
    void adminPathRequiresConfiguredAdminRole() {
        when(jwtTokenProvider.validateToken("token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("token")).thenReturn("alice");
        when(jwtTokenProvider.getRoles("token")).thenReturn(List.of("ROLE_USER"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .build());

        filter.filter(exchange, current -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
