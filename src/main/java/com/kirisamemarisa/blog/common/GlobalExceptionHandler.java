package com.kirisamemarisa.blog.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 参数校验异常统一处理
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.toList());
        String msg = errors.isEmpty() ? "参数校验失败" : String.join(", ", errors);
        return new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), msg, null);
    }

    // 其他已知业务异常（可自定义异常类）
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException ex) {
        return new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), null);
    }

    // 未知异常统一处理
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public Object handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
        // This happens when the servlet async request for an SSE emitter has
        // become unusable (client disconnected). It is benign for the original
        // request that triggered the notification — log at debug and swallow.
        logger.debug("Async request not usable (likely SSE client disconnected): {}", ex.toString());
        return null;
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        logger.error("服务器内部错误", ex);
        String ct = response.getContentType();
        if (ct != null && ct.contains("text/event-stream")) {
            // Can't write JSON into an SSE response. The client likely disconnected or
            // the response is an SSE stream — set status and stop.
            try {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            } catch (IllegalStateException ise) {
                logger.debug("Response already committed, cannot set status for SSE response");
            }
            return null;
        }
        return new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误", null);
    }
}
