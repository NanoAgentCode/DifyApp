package com.github.app.dify.common.exception;

/**
 * 资源未找到异常
 */
public class NotFoundException extends BusinessException {
    public NotFoundException(String message) {
        super(message, ErrorCode.NOT_FOUND);
    }
    
    public NotFoundException(String message, Integer code) {
        super(message, code);
    }
}
