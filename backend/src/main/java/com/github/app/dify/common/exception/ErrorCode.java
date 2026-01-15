package com.github.app.dify.common.exception;

/**
 * 错误码定义
 * 统一管理系统错误码，避免硬编码
 */
public class ErrorCode {
    
    // ==================== 成功 ====================
    /**
     * 成功
     */
    public static final int SUCCESS = 200;
    
    // ==================== 客户端错误 (4xx) ====================
    /**
     * 请求参数错误
     */
    public static final int BAD_REQUEST = 400;
    
    /**
     * 未授权（未登录或token无效）
     */
    public static final int UNAUTHORIZED = 401;
    
    /**
     * 禁止访问（无权限）
     */
    public static final int FORBIDDEN = 403;
    
    /**
     * 资源未找到
     */
    public static final int NOT_FOUND = 404;
    
    /**
     * 请求方法不允许
     */
    public static final int METHOD_NOT_ALLOWED = 405;
    
    /**
     * 请求超时
     */
    public static final int REQUEST_TIMEOUT = 408;
    
    /**
     * 请求体过大
     */
    public static final int PAYLOAD_TOO_LARGE = 413;
    
    /**
     * 请求过于频繁
     */
    public static final int TOO_MANY_REQUESTS = 429;
    
    // ==================== 服务器错误 (5xx) ====================
    /**
     * 服务器内部错误
     */
    public static final int INTERNAL_SERVER_ERROR = 500;
    
    /**
     * 服务不可用
     */
    public static final int SERVICE_UNAVAILABLE = 503;
    
    /**
     * 网关超时
     */
    public static final int GATEWAY_TIMEOUT = 504;
    
    // ==================== 业务错误码 (1000-1999) ====================
    /**
     * 用户名或密码错误
     */
    public static final int LOGIN_FAILED = 1001;
    
    /**
     * Token无效或已过期
     */
    public static final int TOKEN_INVALID = 1002;
    
    /**
     * 用户不存在
     */
    public static final int USER_NOT_FOUND = 1003;
    
    /**
     * 用户已存在
     */
    public static final int USER_ALREADY_EXISTS = 1004;
    
    /**
     * 密码错误
     */
    public static final int PASSWORD_ERROR = 1005;
    
    /**
     * 旧密码错误
     */
    public static final int OLD_PASSWORD_ERROR = 1006;
    
    /**
     * 新密码不能与旧密码相同
     */
    public static final int PASSWORD_SAME_AS_OLD = 1007;
    
    /**
     * 验证码错误
     */
    public static final int CAPTCHA_ERROR = 1008;
    
    /**
     * 验证码已过期
     */
    public static final int CAPTCHA_EXPIRED = 1009;
    
    // ==================== 资源错误码 (2000-2999) ====================
    /**
     * 资源不存在
     */
    public static final int RESOURCE_NOT_FOUND = 2001;
    
    /**
     * 资源已存在
     */
    public static final int RESOURCE_ALREADY_EXISTS = 2002;
    
    /**
     * 资源被占用
     */
    public static final int RESOURCE_IN_USE = 2003;
    
    /**
     * 资源已删除
     */
    public static final int RESOURCE_DELETED = 2004;
    
    // ==================== 文件错误码 (3000-3999) ====================
    /**
     * 文件不存在
     */
    public static final int FILE_NOT_FOUND = 3001;
    
    /**
     * 文件上传失败
     */
    public static final int FILE_UPLOAD_FAILED = 3002;
    
    /**
     * 文件下载失败
     */
    public static final int FILE_DOWNLOAD_FAILED = 3003;

    public static final int FILE_DELETE_FAILED = 3007;
    
    /**
     * 文件类型不支持
     */
    public static final int FILE_TYPE_NOT_SUPPORTED = 3004;
    
    /**
     * 文件过大
     */
    public static final int FILE_TOO_LARGE = 3005;
    
    /**
     * 文件已存在
     */
    public static final int FILE_ALREADY_EXISTS = 3006;
    
    // ==================== AI相关错误码 (4000-4999) ====================
    /**
     * API调用失败
     */
    public static final int API_CALL_FAILED = 4001;
    
    /**
     * API超时
     */
    public static final int API_TIMEOUT = 4002;
    
    /**
     * API配置错误
     */
    public static final int API_CONFIG_ERROR = 4003;
    
    /**
     * 模型不存在
     */
    public static final int MODEL_NOT_FOUND = 4004;
    
    /**
     * 应用不存在
     */
    public static final int APP_NOT_FOUND = 4005;
    
    /**
     * 知识库不存在
     */
    public static final int KNOWLEDGE_BASE_NOT_FOUND = 4006;
    
    /**
     * 文档不存在
     */
    public static final int DOCUMENT_NOT_FOUND = 4007;
    
    /**
     * 对话不存在
     */
    public static final int CONVERSATION_NOT_FOUND = 4008;
    
    /**
     * 消息不存在
     */
    public static final int MESSAGE_NOT_FOUND = 4009;
    
    /**
     * 应用类型不匹配
     */
    public static final int APP_TYPE_MISMATCH = 4010;
    
    /**
     * 工作流执行失败
     */
    public static final int WORKFLOW_EXECUTION_FAILED = 4011;
    
    /**
     * 缺少必需的输入参数
     */
    public static final int MISSING_REQUIRED_INPUT = 4012;

    public static final int RAG_RETRIEVAL_ERROR = API_CALL_FAILED;
    
    // ==================== 数据库错误码 (5000-5999) ====================
    /**
     * 数据库操作失败
     */
    public static final int DATABASE_ERROR = 5001;
    
    /**
     * 数据库连接失败
     */
    public static final int DATABASE_CONNECTION_ERROR = 5002;
    
    /**
     * 数据库超时
     */
    public static final int DATABASE_TIMEOUT = 5003;
    
    /**
     * 数据已存在
     */
    public static final int DATA_ALREADY_EXISTS = 5004;
    
    /**
     * 数据验证失败
     */
    public static final int DATA_VALIDATION_FAILED = 5005;

    public static final int VALIDATION_ERROR = DATA_VALIDATION_FAILED;
    
    // ==================== 外部服务错误码 (6000-6999) ====================
    /**
     * Redis连接失败
     */
    public static final int REDIS_CONNECTION_ERROR = 6001;
    
    /**
     * MinIO连接失败
     */
    public static final int MINIO_CONNECTION_ERROR = 6002;
    
    /**
     * Elasticsearch连接失败
     */
    public static final int ELASTICSEARCH_CONNECTION_ERROR = 6003;
    
    /**
     * MCP服务不可用
     */
    public static final int MCP_SERVICE_UNAVAILABLE = 6004;
    
    /**
     * 外部服务超时
     */
    public static final int EXTERNAL_SERVICE_TIMEOUT = 6005;
    
    // ==================== 系统错误码 (7000-7999) ====================
    /**
     * 系统繁忙
     */
    public static final int SYSTEM_BUSY = 7001;
    
    /**
     * 系统维护中
     */
    public static final int SYSTEM_MAINTENANCE = 7002;
    
    /**
     * 功能未实现
     */
    public static final int NOT_IMPLEMENTED = 7003;
    
    /**
     * 配置错误
     */
    public static final int CONFIG_ERROR = 7004;
    
    /**
     * 限流
     */
    public static final int RATE_LIMIT_EXCEEDED = 7005;
    
    // ==================== 工具方法 ====================
    
    /**
     * 获取错误码对应的HTTP状态码
     * 
     * @param errorCode 错误码
     * @return HTTP状态码
     */
    public static int getHttpStatus(int errorCode) {
        if (errorCode >= 200 && errorCode < 300) {
            return 200;
        } else if (errorCode >= 400 && errorCode < 500) {
            return errorCode;
        } else if (errorCode >= 500 && errorCode < 600) {
            return errorCode;
        } else if (errorCode >= 1000 && errorCode < 2000) {
            return 400; // 业务错误返回400
        } else if (errorCode >= 2000 && errorCode < 7000) {
            return 400; // 资源、文件、AI、数据库、外部服务错误返回400
        } else if (errorCode >= 7000 && errorCode < 8000) {
            return 500; // 系统错误返回500
        }
        return 500;
    }
    
    /**
     * 判断是否为客户端错误
     * 
     * @param errorCode 错误码
     * @return true 如果是客户端错误
     */
    public static boolean isClientError(int errorCode) {
        return (errorCode >= 400 && errorCode < 500) || 
               (errorCode >= 1000 && errorCode < 7000);
    }
    
    /**
     * 判断是否为服务器错误
     * 
     * @param errorCode 错误码
     * @return true 如果是服务器错误
     */
    public static boolean isServerError(int errorCode) {
        return (errorCode >= 500 && errorCode < 600) || 
               (errorCode >= 7000 && errorCode < 8000);
    }
}
