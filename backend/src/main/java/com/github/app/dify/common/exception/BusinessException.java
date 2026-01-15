package com.github.app.dify.common.exception;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {
    private Integer code;
    
    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.BAD_REQUEST;
    }
    
    public BusinessException(String message, Integer code) {
        super(message);
        this.code = code;
    }
    
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.BAD_REQUEST;
    }
    
    public BusinessException(String message, Integer code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    
    public Integer getCode() {
        return code;
    }
    
    public void setCode(Integer code) {
        this.code = code;
    }
}
