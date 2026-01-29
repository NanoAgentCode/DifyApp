package com.github.app.dify.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MinIO 存储配置（绑定 minio.*）
 */
@Component
public class MinioConfig {

    @Value("${minio.bucket-name:knowledge-base}")
    private String bucketName;

    @Value("${minio.endpoint:http://localhost:19000}")
    private String endpoint;

    @Value("${minio.access-key:}")
    private String accessKey;

    @Value("${minio.secret-key:}")
    private String secretKey;

    public String getBucketName() {
        return bucketName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }
}
