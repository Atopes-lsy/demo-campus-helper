package com.campus.helper.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Translates exceptions into the API's {@link Result} envelope.
 *
 * <p>The catch-all {@code Exception} handler below would otherwise turn every framework
 * exception into a 500, which misreports a caller's mistake as a server fault: a client
 * cannot tell "you sent bad data" from "the service is broken", and status-based alerting
 * fires on ordinary 404s. The explicit handlers here keep those cases on their proper
 * status. They log at WARN without a stack trace — a malformed request is not an incident,
 * and a stack trace per bad request buries the errors that are.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result> handleBusinessException(BusinessException ex) {
        log.warn("Business error: {}", ex.getMessage());
        return ResponseEntity.status(toStatus(ex.getCode()))
                .body(Result.error(ex.getCode(), ex.getMessage()));
    }

    /** Body is unreadable: malformed JSON, or a value whose type does not match the target. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Result.error(400, "请求体格式错误"));
    }

    /** A query parameter that cannot be converted to the declared type, or is missing. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Result> handleBadRequestParameter(Exception ex) {
        log.warn("Invalid request parameter: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Result.error(400, "请求参数格式错误"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error(405, "请求方法不支持"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.error(415, "请求内容类型不支持"));
    }

    /** No handler and no static resource matched the path. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No handler for path: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "接口不存在"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(Result.error(400, message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(Result.error(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleException(Exception ex) {
        log.error("Internal server error", ex);
        return ResponseEntity.internalServerError().body(Result.error(500, "服务器内部错误"));
    }

    private HttpStatus toStatus(int code) {
        try {
            return HttpStatus.valueOf(code);
        } catch (IllegalArgumentException ignored) {
            return HttpStatus.BAD_REQUEST;
        }
    }
}
