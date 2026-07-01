package com.powerreport.gateway.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.gateway")
public class RouteProperties {

    private List<String> publicPaths = new ArrayList<>();

    private List<String> adminPaths = new ArrayList<>();

    private List<String> adminRoles = new ArrayList<>();
}
