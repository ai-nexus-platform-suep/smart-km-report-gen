package com.km.exception;

import com.km.common.dto.ApiResponse;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 对应 EPIC-05 05.6 参数校验增强
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 08.5: HTTP 状态码与业务错误码对齐，方便 feat-b 处理。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        HttpStatus status = resolveHttpStatus(ex.getCode());
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    private HttpStatus resolveHttpStatus(int code) {
        if (code == ErrorCode.NOT_FOUND.getCode()
                || code == ErrorCode.KM_KB_001.getCode()
                || code == ErrorCode.KM_DOC_001.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        if (code == ErrorCode.BAD_REQUEST.getCode()
                || code == ErrorCode.KM_DOC_003.getCode()
                || code == ErrorCode.KM_DOC_004.getCode()
                || code == ErrorCode.KM_DOC_005.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == ErrorCode.UNAUTHORIZED.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ErrorCode.FORBIDDEN.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {} for path {}", ex.getMethod(), ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(),
                        "Request method not supported: " + ex.getMethod()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage()));
    }

    /**
     * 处理 @RequestBody 参数校验异常
     * 对应 EPIC-05 05.6 参数校验增强
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[EPIC-05] Validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), message));
    }

    /**
     * 处理 @Validated 路径参数/查询参数校验异常
     * 对应 EPIC-05 05.6 参数校验增强
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[EPIC-05] Constraint violation: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}