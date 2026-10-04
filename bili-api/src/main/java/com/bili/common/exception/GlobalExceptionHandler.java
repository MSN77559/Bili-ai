package com.bili.common.exception;

import com.bili.common.result.ErrorCode;
import com.bili.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理（03 §6.2 / 05 §7 实现注意事项）：
 * 三类异常映射 + DuplicateKey→40901 + MaxUploadSize→41301。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity.status(ec.getHttpStatus()).body(Result.fail(ec, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = ErrorCode.PARAM_INVALID.getMessage();
        FieldError fe = e.getBindingResult().getFieldError();
        if (fe != null) {
            msg = fe.getDefaultMessage() != null ? fe.getDefaultMessage() : msg;
        }
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.PARAM_INVALID, msg));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        // 注册并发撞 uk_email 的兜底（05 §7 注意事项 3）
        String m = e.getMessage();
        log.warn("DuplicateKey: {}", m != null && m.length() > 120 ? m.substring(0, 120) : m);
        return ResponseEntity.status(ErrorCode.EMAIL_REGISTERED.getHttpStatus())
                .body(Result.fail(ErrorCode.EMAIL_REGISTERED));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUpload(MaxUploadSizeExceededException e) {
        // multipart 超限必须显式映射，否则默认 500（05 §7 注意事项 4）
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.getHttpStatus())
                .body(Result.fail(ErrorCode.FILE_TOO_LARGE));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        // query/path 参数类型不匹配（如 ?page=abc）→ 40001 而非默认 500（API-FO03/04 分页参数）
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.PARAM_INVALID, "分页参数不合法"));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException e) {
        // 方法不存在（如 POST /api/search/videos——白名单仅 GET，无 POST 路由）→ 405 归一映射 40001，
        // 防落入兜底 500（T09 白名单核对发现；对不存在的写形态统一按参数不合法处理）
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.PARAM_INVALID, "请求方法不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }
}
