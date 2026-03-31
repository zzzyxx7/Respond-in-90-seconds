package com.fusion.docfusion.exception;

import lombok.Getter;

/**
 * 业务异常：携带 HTTP 语义状态码与统一 {@link ErrorCode}。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.code = errorCode.getHttpStatus();
        this.errorCode = errorCode.name();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getHttpStatus();
        this.errorCode = errorCode.name();
    }
}
