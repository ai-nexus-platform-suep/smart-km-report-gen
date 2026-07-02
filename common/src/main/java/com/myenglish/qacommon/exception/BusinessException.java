package com.myenglish.qacommon.exception;

/**
 * 业务异常，由 GlobalExceptionHandler 统一转换响应
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
