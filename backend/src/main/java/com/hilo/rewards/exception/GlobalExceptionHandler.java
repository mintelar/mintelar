package com.hilo.rewards.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        log.warn("Business error: {} - {}", ex.getErrorCode().getCode(), ex.getMessage());
        return ResponseEntity
            .status(ex.getErrorCode().getStatus())
            .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), ex.getRequestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
        return ResponseEntity
            .status(ErrorCode.INVALID_INPUT.getStatus())
            .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
            .status(ErrorCode.INTERNAL_ERROR.getStatus())
            .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, null));
    }
}
