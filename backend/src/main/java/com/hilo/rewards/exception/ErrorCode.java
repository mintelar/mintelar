package com.hilo.rewards.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHORIZED("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Insufficient permissions", HttpStatus.FORBIDDEN),
    INVALID_INPUT("INVALID_INPUT", "Invalid request data", HttpStatus.BAD_REQUEST),
    GROUP_NOT_FOUND("GROUP_NOT_FOUND", "Group not found", HttpStatus.NOT_FOUND),
    COURSE_INACTIVE("COURSE_INACTIVE", "Course is not active", HttpStatus.BAD_REQUEST),
    GROUP_EMPTY("GROUP_EMPTY", "Group has no members", HttpStatus.BAD_REQUEST),
    GROUP_NOT_COMPLETED("GROUP_NOT_COMPLETED", "Not all members are approved", HttpStatus.BAD_REQUEST),
    MEMBER_WITHOUT_WALLET("MEMBER_WITHOUT_WALLET", "Member has no wallet address", HttpStatus.BAD_REQUEST),
    INVALID_WALLET("INVALID_WALLET", "Invalid wallet address format", HttpStatus.BAD_REQUEST),
    DUPLICATE_WALLET("DUPLICATE_WALLET", "Duplicate wallet addresses found", HttpStatus.BAD_REQUEST),
    REWARD_ALREADY_EXISTS("REWARD_ALREADY_EXISTS", "Reward already processed", HttpStatus.CONFLICT),
    RATE_LIMITED("RATE_LIMITED", "Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    CONTRACT_ERROR("CONTRACT_ERROR", "Blockchain transaction failed", HttpStatus.INTERNAL_SERVER_ERROR),
    INSUFFICIENT_GAS("INSUFFICIENT_GAS", "Insufficient ETH for gas", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public HttpStatus getStatus() { return status; }
}
