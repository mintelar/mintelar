package com.hilo.rewards.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String requestId;

    public BusinessException(ErrorCode errorCode, String requestId) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.requestId = requestId;
    }

    public BusinessException(ErrorCode errorCode, String message, String requestId) {
        super(message);
        this.errorCode = errorCode;
        this.requestId = requestId;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String getRequestId() { return requestId; }
}
