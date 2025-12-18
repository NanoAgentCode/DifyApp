package com.github.app.dify.common.exception;

/**
 * 未授权异常
 */
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String message) {
        super(message, 401);
    }
}
