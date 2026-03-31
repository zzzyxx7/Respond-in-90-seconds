package com.fusion.docfusion.common;

import com.fusion.docfusion.exception.ErrorCode;
import lombok.Data;

/**
 * 统一返回结果封装
 * code：与 HTTP 语义对齐的状态码（200 成功，4xx/5xx 失败）
 * errorCode：机器可读枚举名（失败时），成功时为 null
 * message：提示信息
 * data：具体数据
 */
@Data
public class Result<T> {

    private int code;
    /** 统一错误码枚举名，如 TASK_NOT_FOUND；成功时为 null */
    private String errorCode;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setErrorCode(null);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        Result<T> r = new Result<>();
        r.setCode(errorCode.getHttpStatus());
        r.setErrorCode(errorCode.name());
        r.setMessage(errorCode.getDefaultMessage());
        return r;
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        Result<T> r = new Result<>();
        r.setCode(errorCode.getHttpStatus());
        r.setErrorCode(errorCode.name());
        r.setMessage(message);
        return r;
    }

    /** @deprecated 请使用 {@link #error(ErrorCode)} */
    @Deprecated
    public static <T> Result<T> error(String message) {
        return error(ErrorCode.BUSINESS_ERROR, message);
    }

    /** @deprecated 请使用 {@link #error(ErrorCode, String)} */
    @Deprecated
    public static <T> Result<T> error(int code, String message) {
        ErrorCode ec = mapLegacyHttpCode(code);
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setErrorCode(ec.name());
        r.setMessage(message);
        return r;
    }

    private static ErrorCode mapLegacyHttpCode(int code) {
        return switch (code) {
            case 400 -> ErrorCode.BAD_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> ErrorCode.BUSINESS_ERROR;
        };
    }
}
