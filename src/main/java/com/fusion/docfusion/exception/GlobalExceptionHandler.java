package com.fusion.docfusion.exception;

import com.fusion.docfusion.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        log.warn("业务异常: errorCode={}, msg={}", e.getErrorCode(), e.getMessage());
        Result<String> r = new Result<>();
        r.setCode(e.getCode());
        r.setErrorCode(e.getErrorCode());
        r.setMessage(e.getMessage());
        return r;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        StringBuilder msg = new StringBuilder();
        for (FieldError err : fieldErrors) {
            msg.append(err.getField()).append(": ").append(err.getDefaultMessage()).append("; ");
        }
        log.warn("参数校验异常: {}", msg);
        return Result.error(ErrorCode.PARAM_VALIDATION_ERROR, msg.toString());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        String msg = "请求体格式错误，请检查 JSON 字段类型";
        if (e.getMessage() != null && e.getMessage().contains("Cannot deserialize value of type")) {
            msg = "请求体字段类型不匹配，请检查 ID/数字字段不要传对象或字符串";
        }
        log.warn("请求体解析异常: {}", e.getMessage());
        return Result.error(ErrorCode.REQUEST_BODY_INVALID, msg);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleBadRequest(Exception e) {
        log.warn("请求参数异常: {}", e.getMessage());
        return Result.error(ErrorCode.BAD_REQUEST, "请求参数错误，请检查必填项与参数类型");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<String> handleAccessDenied(AccessDeniedException e) {
        log.warn("无权限访问: {}", e.getMessage());
        return Result.error(ErrorCode.FORBIDDEN, "无权限访问该资源");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.INTERNAL_ERROR, "系统繁忙，请稍后重试");
    }
}
