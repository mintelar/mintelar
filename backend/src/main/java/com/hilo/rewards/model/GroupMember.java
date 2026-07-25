package com.hilo.rewards.model;

public record GroupMember(
    Long id,
    Long groupId,
    String userId,
    boolean approved
) {}
