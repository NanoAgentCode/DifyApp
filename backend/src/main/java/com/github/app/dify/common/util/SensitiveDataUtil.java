package com.github.app.dify.common.util;

/**
 * 敏感数据脱敏工具类
 * 用于对日志和异常消息中的敏感信息进行脱敏处理
 */
public class SensitiveDataUtil {

    /**
     * 脱敏用户ID（保留前2位，其余用*代替）
     */
    public static String maskUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        String idStr = userId.toString();
        if (idStr.length() <= 2) {
            return "**";
        }
        return idStr.substring(0, 2) + "*".repeat(idStr.length() - 2);
    }

    /**
     * 脱敏用户名（保留前2位，其余用*代替）
     */
    public static String maskUsername(String username) {
        if (username == null) {
            return null;
        }
        if (username.length() <= 2) {
            return "**";
        }
        return username.substring(0, 2) + "*".repeat(username.length() - 2);
    }

    /**
     * 脱敏文档ID（保留前4位，其余用*代替）
     */
    public static String maskDocumentId(String documentId) {
        if (documentId == null) {
            return null;
        }
        if (documentId.length() <= 4) {
            return "****";
        }
        return documentId.substring(0, 4) + "*".repeat(documentId.length() - 4);
    }

    /**
     * 脱敏知识库ID（保留前4位，其余用*代替）
     */
    public static String maskKnowledgeBaseId(String knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return null;
        }
        if (knowledgeBaseId.length() <= 4) {
            return "****";
        }
        return knowledgeBaseId.substring(0, 4) + "*".repeat(knowledgeBaseId.length() - 4);
    }

    /**
     * 脱敏文件路径（只显示文件名）
     */
    public static String maskFilePath(String filePath) {
        if (filePath == null) {
            return null;
        }
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return ".../" + filePath.substring(lastSlash + 1);
        }
        return filePath;
    }

    /**
     * 脱敏URL（隐藏协议和域名，只显示路径）
     */
    public static String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        int firstSlash = url.indexOf("://");
        if (firstSlash > 0) {
            int secondSlash = url.indexOf('/', firstSlash + 3);
            if (secondSlash > 0) {
                return "***" + url.substring(secondSlash);
            }
            return "***";
        }
        return url;
    }

    /**
     * 脱敏邮箱（保留前2位和@域名）
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return email;
        }
        String username = parts[0];
        if (username.length() <= 2) {
            return "**@" + parts[1];
        }
        return username.substring(0, 2) + "*".repeat(username.length() - 2) + "@" + parts[1];
    }

    /**
     * 脱敏手机号（保留前3位和后4位）
     */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        if (phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 脱敏IP地址（隐藏中间部分）
     */
    public static String maskIp(String ip) {
        if (ip == null) {
            return null;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return ip;
        }
        return parts[0] + ".*." + parts[3];
    }

    /**
     * 通用脱敏方法（用于未知类型的敏感数据）
     */
    public static String mask(String data) {
        if (data == null) {
            return null;
        }
        if (data.length() <= 4) {
            return "****";
        }
        return data.substring(0, 2) + "..." + data.substring(data.length() - 2);
    }
}
