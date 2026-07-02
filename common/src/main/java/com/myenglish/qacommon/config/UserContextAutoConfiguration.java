package com.myenglish.qacommon.config;

import com.myenglish.qacommon.context.UserContextInterceptor;
import com.myenglish.qacommon.security.PermissionAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@EnableAspectJAutoProxy
public class UserContextAutoConfiguration implements WebMvcConfigurer {

    @Bean
    public UserContextInterceptor userContextInterceptor() {
        return new UserContextInterceptor();
    }

    @Bean
    public PermissionAspect permissionAspect() {
        return new PermissionAspect();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor())
<<<<<<< Updated upstream
                .addPathPatterns("/api/**")
=======
                .addPathPatterns("/api/**", "/internal/**")
>>>>>>> Stashed changes
                .order(1);
    }
}
