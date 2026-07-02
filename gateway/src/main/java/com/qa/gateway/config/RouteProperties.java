package com.qa.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 白名单路由配置：无需 JWT 鉴权的公开接口
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.gateway")
public class RouteProperties {

    /**
     * 白名单路径列表（Ant 路径匹配模式）
     */
    private List<String> publicPaths = new ArrayList<>();
}
