package com.github.app.dify.system.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * SpringDoc OpenAPI 配置（替代 SpringFox，支持 Spring Boot 3）
 */
@Configuration
public class SwaggerConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DifyApp API文档")
                        .description("Dify应用管理和对接API")
                        .version("1.0.0"));
    }
}

