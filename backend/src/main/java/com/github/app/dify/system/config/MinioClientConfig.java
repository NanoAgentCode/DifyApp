package com.github.app.dify.system.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端 Bean 配置，供 MinioFileStorageServiceImpl 等使用。
 */
@Configuration
public class MinioClientConfig {

    @Bean
    public MinioClient minioClient(MinioConfig minioConfig) {
        String endpoint = minioConfig.getEndpoint();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            endpoint = "http://localhost:19000";
        }
        String accessKey = minioConfig.getAccessKey();
        String secretKey = minioConfig.getSecretKey();

        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint);

        if (accessKey != null && !accessKey.trim().isEmpty()
                && secretKey != null && !secretKey.trim().isEmpty()) {
            builder.credentials(accessKey.trim(), secretKey.trim());
        }

        return builder.build();
    }
}
