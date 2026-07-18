package com.github.app.dify.auth.config;

import com.github.app.dify.auth.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JWT 拦截器，对 /api/** 进行鉴权并设置 request 中的 userId/username/role。
 * 未注册时，所有接口的 getUserId(request) 都会得到 null 导致 401。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/verification-code",
                        "/api/auth/forgot-password",
                        "/api/system-config/value/**",
                        "/api/system-config/group/**"
                );
    }
}
