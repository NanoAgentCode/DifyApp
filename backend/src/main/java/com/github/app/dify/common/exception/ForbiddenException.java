package com.github.app.dify.common.exception;

/**
 * 禁止访问异常（403）
 */
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super(message, 403);
    }
}
