package com.github.app.dify.service.impl;

import com.github.app.dify.config.MinioConfig;
import com.github.app.dify.service.FileStorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.GetObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
/**
 * MinIO文件存储服务实现
 */
@Service
public class MinioFileStorageServiceImpl implements FileStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(MinioFileStorageServiceImpl.class);
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private MinioConfig minioConfig;
    
    @Override
    public String uploadFile(MultipartFile file, String filePath) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(filePath)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            
            String fileUrl = getFileUrl(filePath);
            logger.info("文件上传成功: {}, URL: {}", filePath, fileUrl);
            return fileUrl;
        } catch (Exception e) {
            logger.error("文件上传失败: {}", filePath, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteFile(String filePath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(filePath)
                            .build()
            );
            logger.info("文件删除成功: {}", filePath);
        } catch (Exception e) {
            logger.error("文件删除失败: {}", filePath, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getFileUrl(String filePath) {
        // MinIO的访问URL格式：http://endpoint/bucket/path
        String endpoint = minioConfig.getEndpoint();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + minioConfig.getBucketName() + "/" + filePath;
    }
    
    @Override
    public InputStream downloadFile(String filePath) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(filePath)
                            .build()
            );
        } catch (Exception e) {
            logger.error("文件下载失败: {}", filePath, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean fileExists(String filePath) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(filePath)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}