package com.example.tcpprobe.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 HTTP 请求解析和参数校验错误转换为统一的 400 响应结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 JSON 已成功解析、但字段约束不满足的情况。
     *
     * @param exception Spring 参数校验异常
     * @return 包含逐字段错误信息的 HTTP 400 响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(errorBody("请求参数校验失败", errors));
    }

    /**
     * 处理请求体为空、JSON 语法错误或字段类型无法转换的情况。
     *
     * @param exception HTTP 请求体读取异常
     * @return 不暴露底层解析细节的 HTTP 400 响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(errorBody("请求体不是合法的 JSON", List.of()));
    }

    /** 创建字段顺序稳定的统一错误响应体。 */
    private Map<String, Object> errorBody(String message, List<String> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("message", message);
        body.put("details", details);
        return body;
    }
}
