package com.github.app.dify.common.exception;

/**
 * 未授权异常
 */
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String message) {
        super(message, ErrorCode.UNAUTHORIZED);
    }
    
    public UnauthorizedException(String message, Integer code) {
        super(message, code);
    }
}
