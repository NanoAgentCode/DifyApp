package com.github.app.dify.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
/**
 * JWT工具类
 */
@Component
public class JwtUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    /**
     * JWT密钥（从配置文件读取，如果没有则使用默认值）
     */
    @Value("${jwt.secret:dify-app-secret-key-2024-this-is-a-very-long-secret-key-for-security}")
    private String secret;
    
    /**
     * JWT过期时间（毫秒），默认7天
     */
    @Value("${jwt.expiration:604800000}")
    private Long expiration;
    
    /**
     * 获取安全的密钥（确保至少256位）
     * 如果密钥长度不够，使用SHA-256哈希后取32字节
     */
    private SecretKey getSecretKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        
        // 如果密钥长度已经 >= 32 字节（256位），直接使用
        if (keyBytes.length >= 32) {
            // 如果长度正好是32字节，直接使用
            if (keyBytes.length == 32) {
                return Keys.hmacShaKeyFor(keyBytes);
            }
            // 如果长度 > 32字节，截取前32字节
            byte[] truncatedKey = new byte[32];
            System.arraycopy(keyBytes, 0, truncatedKey, 0, 32);
            return Keys.hmacShaKeyFor(truncatedKey);
        }
        
        // 如果密钥长度 < 32字节，使用SHA-256哈希生成32字节的密钥
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedKey = digest.digest(keyBytes);
            return Keys.hmacShaKeyFor(hashedKey);
        } catch (Exception e) {
            logger.error("生成JWT密钥失败", e);
            // 如果哈希失败，使用默认的安全密钥生成方法（使用新的API）
            return Jwts.SIG.HS256.key().build();
        }
    }
    
    /**
     * 生成JWT Token
     * @param userId 用户ID
     * @param username 用户名
     * @param role 角色
     * @return JWT Token
     */
    public String generateToken(Long userId, String username, Integer role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        SecretKey key = getSecretKey();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }
    
    /**
     * 从Token中获取Claims
     * @param token JWT Token
     * @return Claims
     */
    public Claims getClaimsFromToken(String token) {
        try {
            SecretKey key = getSecretKey();
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            logger.error("解析JWT Token失败", e);
            return null;
        }
    }
    
    /**
     * 从Token中获取用户ID
     * @param token JWT Token
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims != null) {
            Object userId = claims.get("userId");
            if (userId instanceof Integer) {
                return ((Integer) userId).longValue();
            } else if (userId instanceof Long) {
                return (Long) userId;
            }
        }
        return null;
    }
    
    /**
     * 从Token中获取用户名
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getSubject() : null;
    }
    
    /**
     * 从Token中获取角色
     * @param token JWT Token
     * @return 角色
     */
    public Integer getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims != null) {
            Object role = claims.get("role");
            if (role instanceof Integer) {
                return (Integer) role;
            }
        }
        return null;
    }
    
    /**
     * 验证Token是否有效
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            if (claims == null) {
                return false;
            }
            Date expiration = claims.getExpiration();
            return expiration.after(new Date());
        } catch (Exception e) {
            logger.error("验证JWT Token失败", e);
            return false;
        }
    }
}