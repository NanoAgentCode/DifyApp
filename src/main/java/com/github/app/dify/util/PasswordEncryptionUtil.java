package com.github.app.dify.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 密码加密工具类（用于数据源密码加密）
 * 使用 AES 加密，支持加密和解密
 */
@Component
public class PasswordEncryptionUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(PasswordEncryptionUtil.class);
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";
    
    /**
     * 加密密钥（从配置文件读取，如果没有则使用默认值）
     * 生产环境请修改为更安全的密钥
     */
    @Value("${data-source.password.encryption.key:dify-app-encryption-key-2024-32bytes!!}")
    private String encryptionKey;
    
    /**
     * 加密密码
     * @param password 原始密码
     * @return 加密后的密码（Base64编码）
     */
    public String encrypt(String password) {
        if (password == null || password.isEmpty()) {
            return password;
        }
        
        try {
            // 确保密钥长度为16、24或32字节（AES-128、AES-192、AES-256）
            byte[] keyBytes = getKeyBytes(encryptionKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] encryptedBytes = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            logger.error("密码加密失败", e);
            throw new RuntimeException("密码加密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解密密码
     * @param encryptedPassword 加密后的密码（Base64编码）
     * @return 原始密码
     */
    public String decrypt(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) {
            return encryptedPassword;
        }
        
        try {
            // 确保密钥长度为16、24或32字节
            byte[] keyBytes = getKeyBytes(encryptionKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("密码解密失败", e);
            throw new RuntimeException("密码解密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取密钥字节数组（确保长度为16、24或32字节）
     */
    private byte[] getKeyBytes(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        
        // AES 密钥长度必须是 16、24 或 32 字节
        if (keyBytes.length == 16 || keyBytes.length == 24 || keyBytes.length == 32) {
            return keyBytes;
        }
        
        // 如果长度不符合，截取或填充到32字节
        byte[] result = new byte[32];
        int length = Math.min(keyBytes.length, 32);
        System.arraycopy(keyBytes, 0, result, 0, length);
        
        // 如果长度不足32字节，用0填充
        if (length < 32) {
            for (int i = length; i < 32; i++) {
                result[i] = 0;
            }
        }
        
        return result;
    }
}

