package com.github.app.dify.appauth.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 密码加密工具类
 * 用于加密/解密数据源密码等敏感信息
 * 使用AES对称加密算法
 */
@Component
public class PasswordEncryptionUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(PasswordEncryptionUtil.class);
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";
    
    /**
     * 加密密钥（从配置文件读取，如果没有配置则使用默认密钥）
     * 生产环境应该使用强密钥，建议通过环境变量或配置文件配置
     */
    @Value("${app.encryption.key:}")
    private String encryptionKey;
    
    /**
     * 默认密钥（仅用于开发环境，生产环境必须配置强密钥）
     */
    private static final String DEFAULT_KEY = "DifyAppDefaultKey123456789012345678901234567890"; // 32字节
    
    /**
     * 加密密码
     * 
     * @param plainPassword 明文密码
     * @return 加密后的密码（Base64编码）
     */
    public String encrypt(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            return plainPassword;
        }
        
        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] encryptedBytes = cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            logger.error("加密密码失败", e);
            throw new RuntimeException("加密密码失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解密密码
     * 
     * @param encryptedPassword 加密后的密码（Base64编码）
     * @return 明文密码
     */
    public String decrypt(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) {
            return encryptedPassword;
        }
        
        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("解密密码失败", e);
            throw new RuntimeException("解密密码失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取加密密钥
     * 
     * @return SecretKeySpec
     */
    private SecretKeySpec getSecretKey() {
        String key = encryptionKey != null && !encryptionKey.isEmpty() 
            ? encryptionKey 
            : DEFAULT_KEY;
        
        // 确保密钥长度为16、24或32字节（AES-128、AES-192、AES-256）
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 16) {
            // 如果密钥太短，使用SHA-256哈希
            try {
                java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
                keyBytes = sha.digest(keyBytes);
            } catch (Exception e) {
                logger.warn("生成密钥哈希失败，使用截断的密钥", e);
                byte[] newKeyBytes = new byte[16];
                System.arraycopy(keyBytes, 0, newKeyBytes, 0, Math.min(keyBytes.length, 16));
                keyBytes = newKeyBytes;
            }
        } else if (keyBytes.length > 32) {
            // 如果密钥太长，截断到32字节
            byte[] newKeyBytes = new byte[32];
            System.arraycopy(keyBytes, 0, newKeyBytes, 0, 32);
            keyBytes = newKeyBytes;
        } else if (keyBytes.length > 16 && keyBytes.length < 24) {
            // 扩展到24字节
            byte[] newKeyBytes = new byte[24];
            System.arraycopy(keyBytes, 0, newKeyBytes, 0, keyBytes.length);
            keyBytes = newKeyBytes;
        } else if (keyBytes.length > 24 && keyBytes.length < 32) {
            // 扩展到32字节
            byte[] newKeyBytes = new byte[32];
            System.arraycopy(keyBytes, 0, newKeyBytes, 0, keyBytes.length);
            keyBytes = newKeyBytes;
        }
        
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}

