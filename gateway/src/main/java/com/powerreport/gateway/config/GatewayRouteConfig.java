package com.powerreport.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关路由配置
 *
 * 路由规则说明：
 * - /api/reports/** → report-outline-docx 服务（后端1：大纲+导出）
 * - /api/sections/** → report-stream 服务（后端2：SSE流式+历史记录）
 * - /api/admin/**   → report-stream 服务（后端2：管理后台）
 * - /api/auth/**    → gateway 自身处理（登录/注册）
 *
 * 开发阶段说明：
 * 由于 Nacos 已禁用（spring.cloud.nacos.discovery.enabled=false），
 * lb:// 协议无法解析服务实例，因此将此 Java 配置注释掉，
 * 改用 application.properties 中的静态路由配置（http://localhost:8081 / 8082）。
 * 部署到生产环境时，恢复此配置并启用 Nacos。
 */
@Slf4j
// @Configuration
public class GatewayRouteConfig {


    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 后端1：大纲生成、确认保存、DOCX导出、文件下载
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

                // 后端2：SSE流式生成、章节内容保存、历史记录、管理后台
                .route("report-stream", r -> r
                        .path(
                                "/api/reports/{reportId}/sections/**",
                                "/api/reports/history/**",
                                "/api/admin/**"
                        )
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://report-stream")
                )

                // 认证服务（gateway 自身处理）
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://gateway")
                )

                .build();
    }
}
