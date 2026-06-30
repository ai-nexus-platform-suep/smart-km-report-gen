package com.powerreport.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

/**
 * Optional code-based gateway routes.
 *
 * Route source of truth is the Nacos Data ID "gateway".
 * Keep this class disabled unless code-based routes are explicitly required.
 */
@Slf4j
// @Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("report-outline-docx", r -> r
                        .path(
                                "/api/reports/outline/**",
                                "/api/reports/{reportId}/export/**",
                                "/api/reports/files/**",
                                "/api/health"
                        )
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://report-outline-docx")
                )
                .route("report-content-flow", r -> r
                        .path(
                                "/api/reports/{reportId}/sections/**",
                                "/api/reports/history",
                                "/api/reports/history/**",
                                "/api/content/health"
                        )
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://report-content-flow")
                )
                .route("report-admin", r -> r
                        .path("/api/admin/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://report-admin")
                )
                .build();
    }
}
