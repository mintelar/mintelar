package com.hilo.rewards.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code,
    String message,
    String requestId,
    Long retryAfter
) {
    public static ErrorResponse of(ErrorCode errorCode, String requestId) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), requestId, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String requestId) {
        return new ErrorResponse(errorCode.getCode(), message, requestId, null);
    }

    public static ErrorResponse rateLimited(String requestId, long retryAfter) {
        return new ErrorResponse(
            ErrorCode.RATE_LIMITED.getCode(),
            ErrorCode.RATE_LIMITED.getMessage(),
            requestId,
            retryAfter
        );
    }
}
